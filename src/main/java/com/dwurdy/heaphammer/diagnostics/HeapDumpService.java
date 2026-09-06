package com.dwurdy.heaphammer.diagnostics;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for triggering on-demand and conditional JVM heap dumps (.hprof) asynchronously (BR-008, Section 15.6).
 */
public class HeapDumpService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeapDumpService.class);

    private final HotSpotDiagnosticMXBean diagnosticMXBean;
    private final ExecutorService executor;

    public HeapDumpService() {
        this(findDiagnosticMXBean(), Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HeapHammer-HeapDump-Worker");
            t.setDaemon(true);
            return t;
        }));
    }

    public HeapDumpService(HotSpotDiagnosticMXBean diagnosticMXBean, ExecutorService executor) {
        this.diagnosticMXBean = diagnosticMXBean;
        this.executor = executor;
    }

    private static HotSpotDiagnosticMXBean findDiagnosticMXBean() {
        try {
            return ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        } catch (Throwable t) {
            LOGGER.warn("HotSpotDiagnosticMXBean unavailable: {}", t.getMessage());
            return null;
        }
    }

    public boolean isSupported() {
        return diagnosticMXBean != null;
    }

    /**
     * Triggers an asynchronous JVM heap dump.
     *
     * @param targetDir target directory
     * @param filename  base filename (will append .hprof if missing)
     * @param liveOnly  whether to dump only live (reachable) objects
     * @return CompletableFuture completing with the generated Path, or exceptionally if failed
     */
    public CompletableFuture<Path> dumpHeapAsync(Path targetDir, String filename, boolean liveOnly) {
        if (!isSupported()) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException(
                    "Heap dumping is not supported on this JVM (HotSpotDiagnosticMXBean missing)."
            ));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(targetDir);
                String name = filename.endsWith(".hprof") ? filename : filename + ".hprof";
                Path outputFile = targetDir.resolve(name);

                // HotSpotDiagnosticMXBean fails if the file already exists
                if (Files.exists(outputFile)) {
                    Files.delete(outputFile);
                }

                LOGGER.warn("Starting JVM heap dump to {} (liveOnly={}). This may cause a temporary STW pause.",
                        outputFile, liveOnly);
                long start = System.currentTimeMillis();

                diagnosticMXBean.dumpHeap(outputFile.toAbsolutePath().toString(), liveOnly);

                long duration = System.currentTimeMillis() - start;
                long sizeBytes = Files.exists(outputFile) ? Files.size(outputFile) : 0L;
                LOGGER.info("Heap dump complete in {} ms (size: {} MB) -> {}",
                        duration, sizeBytes / (1024 * 1024), outputFile);

                return outputFile;
            } catch (IOException e) {
                LOGGER.error("Failed to generate heap dump: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to generate heap dump: " + e.getMessage(), e);
            }
        }, executor);
    }
}
