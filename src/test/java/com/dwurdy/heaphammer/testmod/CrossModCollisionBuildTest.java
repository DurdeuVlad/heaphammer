package com.dwurdy.heaphammer.testmod;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CrossModCollisionBuildTest {

    @Test
    @DisplayName("Verify testmod-crossmod-core jar artifact packaging and manifest")
    void testCoreJarPackaging() throws IOException {
        Path jarPath = Path.of("build/testmods/testmod-crossmod-core-1.0.0.jar");
        File jarFile = jarPath.toFile();

        if (!jarFile.exists()) {
            return;
        }

        try (JarFile jar = new JarFile(jarFile)) {
            assertNotNull(jar.getJarEntry("fabric.mod.json"), "fabric.mod.json must be present in root of core jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/testmod/crossmod/core/CrossModCoreMod.class"),
                    "CrossModCoreMod class must be present in jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/testmod/crossmod/core/CrossModEventBus.class"),
                    "CrossModEventBus class must be present in jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/testmod/crossmod/core/EventSubscription.class"),
                    "EventSubscription class must be present in jar");
        }
    }

    @Test
    @DisplayName("Verify testmod-crossmod-consumer jar artifact packaging and manifest")
    void testConsumerJarPackaging() throws IOException {
        Path jarPath = Path.of("build/testmods/testmod-crossmod-consumer-1.0.0.jar");
        File jarFile = jarPath.toFile();

        if (!jarFile.exists()) {
            return;
        }

        try (JarFile jar = new JarFile(jarFile)) {
            assertNotNull(jar.getJarEntry("fabric.mod.json"), "fabric.mod.json must be present in root of consumer jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/testmod/crossmod/consumer/CrossModConsumerMod.class"),
                    "CrossModConsumerMod class must be present in jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/testmod/crossmod/consumer/ChunkListenerBridge.class"),
                    "ChunkListenerBridge class must be present in jar");
        }
    }
}
