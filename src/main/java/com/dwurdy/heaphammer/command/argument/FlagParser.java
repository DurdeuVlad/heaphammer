package com.dwurdy.heaphammer.command.argument;

import com.dwurdy.heaphammer.domain.ExperimentSpec;

import java.util.*;

/**
 * Parses named flags from command line arguments (e.g. --seed=123 --radius=6 --center=0,0).
 */
public class FlagParser {

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
        com.dwurdy.heaphammer.infrastructure.config.HeapHammerConfig config =
                com.dwurdy.heaphammer.infrastructure.config.ConfigManager.getActiveConfig();
        int maxRadius = config != null ? config.getMaxRadius() : 32;
        int maxIterations = config != null ? config.getMaxIterations() : 50;
        int maxBatchSize = config != null ? config.getMaxBatchSize() : 128;
        int maxWarmup = config != null ? config.getMaxWarmupIterations() : 10;
        int maxHold = config != null ? config.getMaxHoldTicks() : 1200;
        int maxSettle = config != null ? config.getMaxSettleTicks() : 1200;

        if (flags.containsKey("radius")) {
            try {
                int r = Integer.parseInt(flags.get("radius"));
                builder.radius(Math.min(maxRadius, Math.max(1, r)));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("iterations")) {
            try {
                int iters = Integer.parseInt(flags.get("iterations"));
                builder.iterations(Math.min(maxIterations, Math.max(1, iters)));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("batch")) {
            try {
                int batch = Integer.parseInt(flags.get("batch"));
                builder.batchSize(Math.min(maxBatchSize, Math.max(1, batch)));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("strategy")) {
            builder.strategy(flags.get("strategy").toUpperCase(Locale.ROOT));
        }
        if (flags.containsKey("warmup")) {
            try {
                int warmup = Integer.parseInt(flags.get("warmup"));
                builder.warmupIterations(Math.min(maxWarmup, Math.max(0, warmup)));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("hold")) {
            try {
                int hold = Integer.parseInt(flags.get("hold"));
                builder.holdTicks(Math.min(maxHold, Math.max(0, hold)));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("settle")) {
            try {
                int settle = Integer.parseInt(flags.get("settle"));
                builder.settleTicks(Math.min(maxSettle, Math.max(0, settle)));
            } catch (NumberFormatException ignored) {}
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

        return builder.build();
    }
}
