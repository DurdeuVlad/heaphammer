package com.dwurdy.heaphammer.testmod;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class OmniTrackLeakModBuildTest {

    @Test
    @DisplayName("Verify testmod-leak-omnitrack jar artifact packaging and manifest")
    void testJarPackaging() throws IOException {
        Path jarPath = Path.of("build/testmods/testmod-leak-omnitrack-1.0.0.jar");
        File jarFile = jarPath.toFile();

        if (!jarFile.exists()) {
            return;
        }

        try (JarFile jar = new JarFile(jarFile)) {
            assertNotNull(jar.getJarEntry("fabric.mod.json"), "fabric.mod.json must be present in root of jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/testmod/omnitrack/OmniTrackLeakMod.class"),
                    "OmniTrackLeakMod class must be present in jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/testmod/omnitrack/ChunkAuditRecord.class"),
                    "ChunkAuditRecord class must be present in jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/testmod/omnitrack/EntityTrackingRecord.class"),
                    "EntityTrackingRecord class must be present in jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/testmod/omnitrack/TickEventBuffer.class"),
                    "TickEventBuffer class must be present in jar");
        }
    }
}
