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

class ProductionJarPackagingTest {

    @Test
    @DisplayName("Production jar contains every compiled main class and declared Fabric entrypoints")
    void productionJarContainsCompiledMainClasses() throws IOException {
        Path jarPath = Path.of(System.getProperty("heaphammer.jar"));
        Path mainClasses = Path.of("build/classes/java/main");

        assertFalse(Files.notExists(jarPath), "Production jar must exist: " + jarPath);
        assertFalse(Files.notExists(mainClasses), "Compiled main classes must exist: " + mainClasses);

        try (JarFile jar = new JarFile(jarPath.toFile());
             Stream<Path> classFiles = Files.walk(mainClasses)) {
            assertNotNull(jar.getJarEntry("com/dwurdy/heaphammer/HeapHammer.class"),
                    "Fabric main entrypoint must be present in the production jar");
            assertNotNull(jar.getJarEntry("com/dwurdy/heaphammer/mixin/ExampleMixin.class"),
                    "Required mixin must be present in the production jar");

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
