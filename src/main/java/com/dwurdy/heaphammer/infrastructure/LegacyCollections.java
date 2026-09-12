package com.dwurdy.heaphammer.infrastructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Java 8-compatible replacement for the collection factories used by the shared core. */
public final class LegacyCollections {
    private LegacyCollections() {}

    @SafeVarargs
    public static <T> List<T> list(T... values) {
        if (values == null || values.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(values);
    }

    public static <T> List<T> copy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
