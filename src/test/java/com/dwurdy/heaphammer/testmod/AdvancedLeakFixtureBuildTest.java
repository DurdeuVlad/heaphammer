package com.dwurdy.heaphammer.testmod;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedLeakFixtureBuildTest {

    @Test
    @DisplayName("Verify player-session leak fixture jar packaging and manifest")
    void testPlayerSessionJarPackaging() throws IOException {
        Path jarPath = Path.of("build/testmods/testmod-leak-playersession-1.0.0.jar");
        File jarFile = jarPath.toFile();
        assertTrue(jarFile.exists(), "Run buildTestmods before packaging assertions");

        try (JarFile jar = new JarFile(jarFile)) {
            assertNotNull(jar.getJarEntry("fabric.mod.json"));
            assertNotNull(jar.getJarEntry(
                    "com/dwurdy/testmod/playersession/PlayerSessionLeakMod.class"));
            assertNotNull(jar.getJarEntry(
                    "com/dwurdy/testmod/playersession/PlayerSessionRecord.class"));
        }
    }

    @Test
    @DisplayName("Verify persistent-entity leak fixture jar packaging and manifest")
    void testPersistentEntityJarPackaging() throws IOException {
        Path jarPath = Path.of("build/testmods/testmod-leak-persistententity-1.0.0.jar");
        File jarFile = jarPath.toFile();
        assertTrue(jarFile.exists(), "Run buildTestmods before packaging assertions");

        try (JarFile jar = new JarFile(jarFile)) {
            assertNotNull(jar.getJarEntry("fabric.mod.json"));
            assertNotNull(jar.getJarEntry(
                    "com/dwurdy/testmod/persistententity/PersistentEntityLeakMod.class"));
            assertNotNull(jar.getJarEntry(
                    "com/dwurdy/testmod/persistententity/PersistentEntityRecord.class"));
        }
    }
}
