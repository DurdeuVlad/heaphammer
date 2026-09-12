package com.dwurdy.heaphammer.integration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Cross-platform Java process harness for managing a live Minecraft dedicated server
 * during automated adversarial and empirical regression testing.
 */
public class MinecraftServerProcess implements AutoCloseable {

    private final File projectDir;
    private final List<String> logLines = new CopyOnWriteArrayList<>();
    private Process process;
    private BufferedWriter writer;
    private Thread logReaderThread;
    private volatile boolean isRunning = false;

    public MinecraftServerProcess(File projectDir) {
        this.projectDir = projectDir;
    }

    public static MinecraftServerProcess forProjectRoot() {
        return new MinecraftServerProcess(new File(System.getProperty("user.dir")));
    }

    /**
     * Starts the dedicated server via Gradle and blocks until "Done (...s)!" is logged
     * or timeout occurs.
     */
    public synchronized void start(Duration timeout) throws IOException, TimeoutException, InterruptedException {
        if (isRunning) {
            throw new IllegalStateException("Server process is already running");
        }

        logLines.clear();

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb;
        if (isWindows) {
            pb = new ProcessBuilder("cmd.exe", "/c", "gradlew.bat", "runServer", "--no-daemon");
        } else {
            pb = new ProcessBuilder("./gradlew", "runServer", "--no-daemon");
        }

        pb.directory(projectDir);
        pb.redirectErrorStream(true);

        process = pb.start();
        isRunning = true;

        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        logReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logLines.add(line);
                    System.out.println("[SERVER] " + line);
                }
            } catch (IOException ignored) {
            } finally {
                isRunning = false;
            }
        }, "MinecraftServer-LogReader");
        logReaderThread.setDaemon(true);
        logReaderThread.start();

        // Wait until server is fully booted and ready for commands
        waitForLine(line -> line.contains("Done (") && line.contains("For help, type \"help\""), timeout);
    }

    /**
     * Sends a command line to the server console via standard input.
     */
    public synchronized void sendCommand(String command) throws IOException {
        if (!isAlive()) {
            throw new IllegalStateException("Cannot send command to non-running server process");
        }
        System.out.println("[TEST-COMMAND] " + command);
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    /**
     * Waits for a log line matching the predicate to appear within the specified timeout.
     */
    public String waitForLine(Predicate<String> matcher, Duration timeout) throws TimeoutException, InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        int searchStartIndex = 0;

        while (Instant.now().isBefore(deadline)) {
            int currentSize = logLines.size();
            for (int i = searchStartIndex; i < currentSize; i++) {
                String line = logLines.get(i);
                if (matcher.test(line)) {
                    return line;
                }
            }
            searchStartIndex = currentSize;

            if (!isAlive() && currentSize == logLines.size()) {
                throw new TimeoutException("Server process terminated unexpectedly while waiting for log line. Last 10 lines:\n"
                        + getLastLogSnippet(10));
            }

            Thread.sleep(100);
        }

        throw new TimeoutException("Timed out after " + timeout.toSeconds() + "s waiting for matching log line. Last 15 lines:\n"
                + getLastLogSnippet(15));
    }

    /**
     * Waits for a log line containing the specified substring.
     */
    public String waitForLineContaining(String substring, Duration timeout) throws TimeoutException, InterruptedException {
        return waitForLine(line -> line.contains(substring), timeout);
    }

    /**
     * Stops the server cleanly by sending the 'stop' command and waiting for process termination.
     */
    public synchronized void stopCleanly(Duration timeout) throws IOException, InterruptedException {
        if (!isAlive()) {
            return;
        }

        try {
            sendCommand("stop");
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                System.err.println("[TEST-WARN] Server did not stop cleanly within " + timeout.toSeconds() + "s. Destroying process tree forcibly.");
                killHard();
            }
        } finally {
            cleanupHandles();
        }
    }

    /**
     * Forcibly destroys the server process and all its child JVM descendants immediately.
     * Simulates an abrupt server crash (SIGKILL / power outage).
     */
    public synchronized void killHard() {
        if (process != null) {
            try {
                process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("[TEST-WARN] Exception during killHard: " + e.getMessage());
            } finally {
                cleanupHandles();
            }
        }
    }

    public synchronized boolean isAlive() {
        return process != null && process.isAlive();
    }

    public List<String> getLogLines() {
        return logLines;
    }

    private String getLastLogSnippet(int count) {
        int size = logLines.size();
        int from = Math.max(0, size - count);
        return String.join("\n", logLines.subList(from, size));
    }

    private void cleanupHandles() {
        isRunning = false;
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        if (isAlive()) {
            killHard();
        }
    }

    public static class TimeoutException extends Exception {
        public TimeoutException(String message) {
            super(message);
        }
    }
}
