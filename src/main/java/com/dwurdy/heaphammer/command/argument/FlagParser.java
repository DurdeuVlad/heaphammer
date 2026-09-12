package com.dwurdy.heaphammer.command.argument;

import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.domain.DiagnosticCollector;
import com.dwurdy.heaphammer.domain.EntityWorkloadProfile;
import com.dwurdy.heaphammer.domain.PlayerAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Parses named flags from command line arguments (e.g. --seed=123 --radius=6 --center=0,0).
 */
public class FlagParser {

    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-flags");

    public static Map<String, String> parseRawFlags(String[] args, int startIndex) {
        Map<String, String> flags = new HashMap<>();
        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.startsWith("--")) {
                if (arg.contains("=")) {
                    int eq = arg.indexOf('=');
                    String key = arg.substring(2, eq).toLowerCase(Locale.ROOT);
                    String value = arg.substring(eq + 1);
                    flags.put(key, value);
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    String key = arg.substring(2).toLowerCase(Locale.ROOT);
                    String value = args[++i].trim();
                    flags.put(key, value);
                } else {
                    String key = arg.substring(2).toLowerCase(Locale.ROOT);
                    flags.put(key, "true");
                }
            }
        }
        return flags;
    }

    public static ExperimentSpec parseSpec(String[] args, int startIndex, int defaultCenterX, int defaultCenterZ) {
        Map<String, String> flags = parseRawFlags(args, startIndex);
        ExperimentSpec.Builder builder = ExperimentSpec.builder();

        builder.center(defaultCenterX, defaultCenterZ);

        if (flags.containsKey("seed")) {
            try {
                builder.seed(Long.parseLong(flags.get("seed")));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("center")) {
            String[] parts = flags.get("center").split(",");
            if (parts.length == 2) {
                try {
                    builder.center(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (flags.containsKey("radius")) {
            try {
                builder.radius(Integer.parseInt(flags.get("radius")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer for flag '--radius': " + flags.get("radius"));
            }
        }
        if (flags.containsKey("iterations")) {
            try {
                builder.iterations(Integer.parseInt(flags.get("iterations")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer for flag '--iterations': " + flags.get("iterations"));
            }
        }
        if (flags.containsKey("batch")) {
            try {
                builder.batchSize(Integer.parseInt(flags.get("batch")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer for flag '--batch': " + flags.get("batch"));
            }
        }
        if (flags.containsKey("strategy")) {
            builder.strategy(flags.get("strategy").toUpperCase(Locale.ROOT));
        }
        if (flags.containsKey("warmup")) {
            try {
                builder.warmupIterations(Integer.parseInt(flags.get("warmup")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer for flag '--warmup': " + flags.get("warmup"));
            }
        }
        if (flags.containsKey("hold")) {
            try {
                builder.holdTicks(Integer.parseInt(flags.get("hold")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer for flag '--hold': " + flags.get("hold"));
            }
        }
        if (flags.containsKey("settle")) {
            try {
                builder.settleTicks(Integer.parseInt(flags.get("settle")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer for flag '--settle': " + flags.get("settle"));
            }
        }
        if (flags.containsKey("explicit-gc")) {
            builder.explicitGc(Boolean.parseBoolean(flags.get("explicit-gc")));
        }
        if (flags.containsKey("coverage")) {
            try {
                double cov = Double.parseDouble(flags.get("coverage"));
                if (cov > 0.0 && cov <= 1.0) {
                    builder.coverage(cov);
                }
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("include-mod") || flags.containsKey("include")) {
            String val = flags.getOrDefault("include-mod", flags.get("include"));
            List<String> list = Arrays.stream(val.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            builder.includeMods(list);
        }
        if (flags.containsKey("exclude-mod") || flags.containsKey("exclude")) {
            String val = flags.getOrDefault("exclude-mod", flags.get("exclude"));
            List<String> list = Arrays.stream(val.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            builder.excludeMods(list);
        }

        if (flags.containsKey("profile")) {
            try {
                builder.entityProfile(EntityWorkloadProfile.valueOf(flags.get("profile").trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid entity profile: " + flags.get("profile") +
                        ". Valid values: transient, persistent, unticked_ring");
            }
        }
        if (flags.containsKey("logins-per-cycle") || flags.containsKey("logins")) {
            String value = flags.getOrDefault("logins-per-cycle", flags.get("logins"));
            try {
                builder.loginsPerCycle(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer for flag '--logins-per-cycle': " + value);
            }
        }
        if (flags.containsKey("actions")) {
            List<PlayerAction> actions = new ArrayList<>();
            for (String raw : flags.get("actions").split(",")) {
                if (!raw.trim().isEmpty()) {
                    try {
                        actions.add(PlayerAction.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid player action: " + raw +
                                ". Valid values: join, quit, respawn, dimchange, teleport");
                    }
                }
            }
            builder.playerActions(actions);
        }
        if (flags.containsKey("duration")) {
            builder.durationSeconds(parseDurationSeconds(flags.get("duration"), "duration"));
        }
        if (flags.containsKey("interval")) {
            builder.intervalSeconds(parseDurationSeconds(flags.get("interval"), "interval"));
        }
        if (flags.containsKey("diagnostics")) {
            List<DiagnosticCollector> collectors = new ArrayList<>();
            for (String raw : flags.get("diagnostics").split(",")) {
                if (!raw.trim().isEmpty() && !"none".equalsIgnoreCase(raw.trim())) {
                    try {
                        collectors.add(DiagnosticCollector.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid diagnostic collector: " + raw +
                                ". Valid values: histogram, retention, world-store, event-metrics");
                    }
                }
            }
            builder.diagnosticCollectors(collectors);
        }
        if (flags.containsKey("track-classes") || flags.containsKey("classes")) {
            String value = flags.getOrDefault("track-classes", flags.get("classes"));
            List<String> classes = Arrays.stream(value.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toList());
            builder.trackedClasses(classes);
            if (!classes.isEmpty() && !flags.containsKey("diagnostics")) {
                builder.diagnosticCollectors(List.of(DiagnosticCollector.RETENTION));
            }
        }

        return builder.build();
    }

    private static long parseDurationSeconds(String value, String flagName) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Invalid duration for flag '--" + flagName + "': " + value);
        }
        long multiplier = 1L;
        char suffix = normalized.charAt(normalized.length() - 1);
        if (suffix == 's' || suffix == 'm' || suffix == 'h' || suffix == 'd') {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
            multiplier = suffix == 's' ? 1L : suffix == 'm' ? 60L : suffix == 'h' ? 3600L : 86400L;
        }
        try {
            long valueNumber = Long.parseLong(normalized);
            if (valueNumber <= 0L || valueNumber > Long.MAX_VALUE / multiplier) {
                throw new NumberFormatException();
            }
            return valueNumber * multiplier;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration for flag '--" + flagName + "': " + value);
        }
    }
}
