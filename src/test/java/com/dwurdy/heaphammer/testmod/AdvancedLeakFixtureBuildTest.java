package com.dwurdy.heaphammer.testmod;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedLeakFixtureBuildTest {

    @Test
    @DisplayName("Verify player-session leak fixture jar packaging and manifest")
    void testPlayerSessionJarPackaging() throws IOException {
        assumeAdvancedFixtureBuild();
        Path jarPath = Paths.get("build/testmods/testmod-leak-playersession-1.0.0.jar");
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
        assumeAdvancedFixtureBuild();
        Path jarPath = Paths.get("build/testmods/testmod-leak-persistententity-1.0.0.jar");
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

    private static void assumeAdvancedFixtureBuild() throws IOException {
        Path properties = Paths.get("gradle.properties");
        String minecraftVersion = Files.isRegularFile(properties)
                ? Files.readAllLines(properties, StandardCharsets.UTF_8).stream()
                .filter(line -> line.trim().startsWith("minecraft_version="))
                .map(line -> line.trim().substring("minecraft_version=".length()))
                .findFirst()
                .orElse("")
                : "";
        Assumptions.assumeTrue(Arrays.asList("1.21.4", "1.21.1", "1.20.6", "1.20.4", "1.20.1")
                        .contains(minecraftVersion),
                "Advanced fixture packaging is defined for modern Fabric targets only");
    }
}
