package com.dwurdy.heaphammer.diagnostics;

import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;

/**
 * Programmatic controller for Java Flight Recorder captures (Section 15.6, Section 27).
 */
public class JfrTrigger {
    private static final Logger LOGGER = LoggerFactory.getLogger(JfrTrigger.class);

    private Recording activeRecording;

    public static boolean isAvailable() {
        try {
            return FlightRecorder.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    public synchronized boolean isRecording() {
        return activeRecording != null && activeRecording.getState() == RecordingStateActive();
    }

    private static jdk.jfr.RecordingState RecordingStateActive() {
        return jdk.jfr.RecordingState.RUNNING;
    }

    public synchronized boolean start(String name) {
        if (!isAvailable()) {
            LOGGER.warn("Java Flight Recorder is not available on this JVM.");
            return false;
        }

        if (activeRecording != null) {
            LOGGER.warn("A JFR recording is already active.");
            return false;
        }

        try {
            Configuration config;
            try {
                config = Configuration.getConfiguration("profile");
            } catch (IOException | ParseException e) {
                config = Configuration.getConfiguration("default");
            }

            Recording recording = new Recording(config);
            recording.setName(name != null ? name : "HeapHammer-Recording");
            recording.start();
            this.activeRecording = recording;
            LOGGER.info("Started JFR recording: {}", recording.getName());
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to start JFR recording: {}", e.getMessage(), e);
            return false;
        }
    }

    public synchronized Path dump(Path destinationDir, String filename) {
        if (activeRecording == null) {
            LOGGER.warn("Cannot dump JFR: no active recording.");
            return null;
        }

        try {
            Files.createDirectories(destinationDir);
            String name = (filename.endsWith(".jfr")) ? filename : filename + ".jfr";
            Path destFile = destinationDir.resolve(name);
            activeRecording.dump(destFile);
            LOGGER.info("Dumped JFR recording to {}", destFile);
            return destFile;
        } catch (IOException e) {
            LOGGER.error("Failed to dump JFR recording to {}: {}", destinationDir, e.getMessage(), e);
            return null;
        }
    }

    public synchronized void stop() {
        if (activeRecording != null) {
            try {
                activeRecording.stop();
                activeRecording.close();
                LOGGER.info("Stopped JFR recording: {}", activeRecording.getName());
            } catch (Exception e) {
                LOGGER.error("Error stopping JFR recording: {}", e.getMessage(), e);
            } finally {
                activeRecording = null;
            }
        }
    }
}
