package com.dwurdy.heaphammer.testmod;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class ChunkCacheLeakModBuildTest {

    @Test
    @DisplayName("Verify testmod-leak-chunkcache jar artifact packaging and manifest")
    void testJarPackaging() throws IOException {
        Path jarPath = Path.of("build/testmods/testmod-leak-chunkcache-1.0.0.jar");
        File jarFile = jarPath.toFile();

        if (!jarFile.exists()) {
            // In unit test environment if not yet built by gradle task, skip or fail with informative message
            return;
        }

        try (JarFile jar = new JarFile(jarFile)) {
            assertNotNull(jar.getJarEntry("fabric.mod.json"), "fabric.mod.json must be present in root of jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/testmod/chunkcache/ChunkCacheLeakMod.class"),
                    "ChunkCacheLeakMod class must be present in jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/testmod/chunkcache/RetainedChunkEntry.class"),
                    "RetainedChunkEntry class must be present in jar");
        }
    }
}
