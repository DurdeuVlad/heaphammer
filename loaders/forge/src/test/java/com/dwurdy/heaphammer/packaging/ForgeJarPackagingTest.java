package com.dwurdy.heaphammer.packaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Packaging guard for the nested Forge production jar: the artifact must carry
 * Forge mod metadata and entrypoint, the full compiled main classes, and must never
 * leak Fabric loader metadata into the published file.
 */
class ForgeJarPackagingTest {

    @Test
    @DisplayName("Forge jar contains entrypoint, mods.toml, all compiled classes, and no Fabric metadata")
    void forgeJarContainsCompiledMainClasses() throws IOException {
        Path jarPath = Path.of(System.getProperty("heaphammer.jar"));
        Path mainClasses = Path.of("build/classes/java/main");

        assertFalse(Files.notExists(jarPath), "Production jar must exist: " + jarPath);
        assertFalse(Files.notExists(mainClasses), "Compiled main classes must exist: " + mainClasses);

        try (JarFile jar = new JarFile(jarPath.toFile());
             Stream<Path> classFiles = Files.walk(mainClasses)) {
            assertNotNull(jar.getJarEntry("META-INF/mods.toml"),
                    "Forge mod metadata must be present in the production jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/heaphammer/HeapHammerForge.class"),
                    "Forge @Mod entrypoint must be present in the production jar");
            assertNull(jar.getJarEntry("fabric.mod.json"),
                    "Fabric metadata must never leak into the Forge jar");
            assertNull(jar.getJarEntry("com/dwurdy/heaphammer/HeapHammer.class"),
                    "Fabric entrypoint must never leak into the Forge jar");

            List<Path> missingClasses = classFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .map(mainClasses::relativize)
                    .filter(relativePath -> jar.getJarEntry(relativePath.toString().replace(java.io.File.separatorChar, '/')) == null)
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertFalse(missingClasses.stream().findAny().isPresent(),
                    "Compiled main classes missing from production jar: " + missingClasses);
        }
    }
}
