package com.dwurdy.heaphammer.command.argument;

import com.dwurdy.heaphammer.domain.ExperimentSpec;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses named flags from command line arguments (e.g. --seed=123 --radius=6 --center=0,0).
 */
public class FlagParser {

    public static Map<String, String> parseRawFlags(String[] args, int startIndex) {
        Map<String, String> flags = new HashMap<>();
        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.startsWith("--") && arg.contains("=")) {
                int eq = arg.indexOf('=');
                String key = arg.substring(2, eq).toLowerCase();
                String value = arg.substring(eq + 1);
                flags.put(key, value);
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
                builder.radius(Math.max(1, Integer.parseInt(flags.get("radius"))));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("iterations")) {
            try {
                builder.iterations(Math.max(1, Integer.parseInt(flags.get("iterations"))));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("batch")) {
            try {
                builder.batchSize(Math.max(1, Integer.parseInt(flags.get("batch"))));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("strategy")) {
            builder.strategy(flags.get("strategy").toUpperCase());
        }
        if (flags.containsKey("warmup")) {
            try {
                builder.warmupIterations(Math.max(0, Integer.parseInt(flags.get("warmup"))));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("hold")) {
            try {
                builder.holdTicks(Math.max(0, Integer.parseInt(flags.get("hold"))));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("settle")) {
            try {
                builder.settleTicks(Math.max(0, Integer.parseInt(flags.get("settle"))));
            } catch (NumberFormatException ignored) {}
        }
        if (flags.containsKey("explicit-gc")) {
            builder.explicitGc(Boolean.parseBoolean(flags.get("explicit-gc")));
        }

        return builder.build();
    }
}
