package com.dwurdy.heaphammer.scenario.targeting;

import java.util.ArrayList;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Filter for mod namespaces and resource locations supporting inclusion, exclusion, and wildcards.
 */
public class ModFilter {

    private final List<String> includedMods;
    private final List<String> excludedMods;

    public ModFilter(List<String> includedMods, List<String> excludedMods) {
        this.includedMods = (includedMods == null) ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(includedMods));
        this.excludedMods = (excludedMods == null) ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(excludedMods));
    }

    public static ModFilter all() {
        return new ModFilter(Collections.emptyList(), Collections.emptyList());
    }

    public List<String> getIncludedMods() {
        return Collections.unmodifiableList(includedMods);
    }

    public List<String> getExcludedMods() {
        return Collections.unmodifiableList(excludedMods);
    }

    public boolean matches(String entry) {
        if (entry == null || entry.trim().isEmpty()) {
            return false;
        }

        String namespace = extractNamespace(entry).toLowerCase(Locale.ROOT);

        // 1. Check exclusions first
        for (String excluded : excludedMods) {
            if (matchesPattern(namespace, excluded.trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // 2. If no inclusions specified, everything not excluded matches
        if (includedMods.isEmpty()) {
            return true;
        }

        // 3. Check inclusions
        for (String included : includedMods) {
            if (matchesPattern(namespace, included.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    public static String extractNamespace(String resourceLocationOrNamespace) {
        int colon = resourceLocationOrNamespace.indexOf(':');
        if (colon >= 0) {
            return resourceLocationOrNamespace.substring(0, colon);
        }
        return resourceLocationOrNamespace;
    }

    private boolean matchesPattern(String namespace, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return namespace.startsWith(prefix);
        }
        return namespace.equalsIgnoreCase(pattern);
    }
}
