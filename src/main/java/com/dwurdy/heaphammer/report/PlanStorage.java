package com.dwurdy.heaphammer.report;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.infrastructure.FileStorage;
import com.dwurdy.heaphammer.infrastructure.json.GsonCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Storage manager for persisting and loading ExperimentPlan files (BR-002).
 */
public class PlanStorage {
    private final Path rootDir;

    public PlanStorage(Path baseDir) {
        this.rootDir = Objects.requireNonNull(baseDir, "baseDir must not be null").resolve("plans");
    }

    public Path savePlan(ExperimentPlan plan) throws IOException {
        Path target = rootDir.resolve(plan.id().value() + ".json").normalize();
        if (!target.startsWith(rootDir.normalize())) {
            throw new SecurityException("Path traversal attempt detected in plan ID: " + plan.id());
        }
        String json = GsonCodec.toJson(plan);
        FileStorage.writeStringAtomic(target, json);
        return target;
    }

    public Optional<ExperimentPlan> loadPlan(ExperimentId id) throws IOException {
        Path target = rootDir.resolve(id.value() + ".json").normalize();
        if (!target.startsWith(rootDir.normalize())) {
            throw new SecurityException("Path traversal attempt detected in plan ID: " + id);
        }
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        String json = FileStorage.readString(target);
        return Optional.of(GsonCodec.fromJson(json, ExperimentPlan.class));
    }

    public Optional<ExperimentPlan> loadLatestPlan() throws IOException {
        if (!Files.exists(rootDir)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.list(rootDir)) {
            Optional<Path> latest = stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .max(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }));
            if (latest.isPresent()) {
                String json = FileStorage.readString(latest.get());
                return Optional.of(GsonCodec.fromJson(json, ExperimentPlan.class));
            }
        }
        return Optional.empty();
    }

    public List<String> listPlanIds() throws IOException {
        if (!Files.exists(rootDir)) return Collections.emptyList();
        try (Stream<Path> stream = Files.list(rootDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted(Comparator.reverseOrder())
                    .collect(java.util.stream.Collectors.toList());
        }
    }
}
