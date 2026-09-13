package com.dwurdy.heaphammer.platform.fabric;

import com.dwurdy.heaphammer.diagnostics.RetentionTracker;
import com.dwurdy.heaphammer.domain.EntityWorkloadProfile;
import com.dwurdy.heaphammer.platform.EntityLifecyclePort;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Profile-aware live entity operations across the supported Fabric APIs. */
public final class FabricEntityLifecyclePort implements EntityLifecyclePort {
    private static final String TEST_ENTITY_TAG = "heaphammer:test";
    private final Supplier<?> serverSupplier;
    private final ConcurrentHashMap<String, java.util.Set<UUID>> testEntities = new ConcurrentHashMap<>();
    private volatile RetentionTracker retentionTracker;

    public FabricEntityLifecyclePort(Supplier<?> serverSupplier) {
        this.serverSupplier = serverSupplier;
    }

    public void attach(RetentionTracker tracker) {
        retentionTracker = tracker;
    }

    @Override
    public UUID spawn(String dimension, String entityTypeId, double x, double y, double z, EntityWorkloadProfile profile) {
        Object server = serverSupplier.get();
        Object level = findLevel(server, dimension);
        Object type = entityType(entityTypeId);
        if (level == null || type == null || "minecraft:player".equals(entityTypeId)
                || !booleanValue(invoke(type, "canSummon"), true)) return null;

        Object entity = createEntity(type, level);
        if (entity == null) return null;
        invoke(entity, "moveTo", x, y, z, Float.valueOf(0.0F), Float.valueOf(0.0F));
        invoke(entity, "setNoAi", Boolean.TRUE);
        if (profile == EntityWorkloadProfile.PERSISTENT) invoke(entity, "setPersistenceRequired");
        invoke(entity, "addTag", TEST_ENTITY_TAG);

        Invocation added = invoke(level, "addFreshEntity", entity);
        if (!added.found || (added.value instanceof Boolean && !((Boolean) added.value))) return null;
        Object uuidValue = value(invoke(entity, "getUUID"));
        if (!(uuidValue instanceof UUID)) return null;
        UUID uuid = (UUID) uuidValue;
        testEntities.computeIfAbsent(dimension, ignored -> ConcurrentHashMap.newKeySet()).add(uuid);
        RetentionTracker tracker = retentionTracker;
        if (tracker != null) tracker.observe(entity.getClass().getName(), entity);
        return uuid;
    }

    @Override
    public boolean cycleChunk(String dimension, int chunkX, int chunkZ, EntityWorkloadProfile profile) {
        // Fabric has no stable public per-entity chunk unload/reload contract.
        return false;
    }

    @Override
    public long countMaterializedTestEntities(String dimension) {
        Object level = findLevel(serverSupplier.get(), dimension);
        if (level == null) return 0L;
        long count = 0L;
        for (Object entity : iterable(value(invoke(level, "getAllEntities")))) {
            if (hasTag(entity, TEST_ENTITY_TAG)) count++;
        }
        return count;
    }

    @Override
    public int cleanupTestEntities() {
        Object server = serverSupplier.get();
        if (server == null) return 0;
        int count = 0;
        for (Object level : iterable(value(invoke(server, "getAllLevels")))) {
            for (Object entity : iterable(value(invoke(level, "getAllEntities")))) {
                if (hasTag(entity, TEST_ENTITY_TAG)) {
                    invoke(entity, "discard");
                    count++;
                }
            }
        }
        testEntities.clear();
        return count;
    }

    private static Object entityType(String id) {
        Object location = resourceLocation(id);
        Object registry = staticField("net.minecraft.core.registries.BuiltInRegistries", "ENTITY_TYPE");
        if (location == null || registry == null) return null;
        Object result = value(invoke(registry, "getValue", location));
        if (result == null) result = value(invoke(registry, "get", location));
        return result;
    }

    private static Object createEntity(Object type, Object level) {
        for (Method method : methods(type.getClass(), "create")) {
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 0 || !types[0].isAssignableFrom(level.getClass())) continue;
            Object[] args = new Object[types.length];
            args[0] = level;
            for (int i = 1; i < types.length; i++) args[i] = defaultValue(types[i]);
            try {
                method.setAccessible(true);
                Object result = method.invoke(type, args);
                if (result != null) return result;
            } catch (Exception ignored) {
                // Try another version-specific overload.
            }
        }
        return null;
    }

    private static boolean hasTag(Object entity, String tag) {
        for (Object value : iterable(value(invoke(entity, "getTags")))) {
            if (tag.equals(String.valueOf(value))) return true;
        }
        return false;
    }

    private static Object findLevel(Object server, String dimension) {
        if (server == null || dimension == null) return null;
        for (Object level : iterable(value(invoke(server, "getAllLevels")))) {
            Object key = value(invoke(level, "dimension"));
            Object location = value(invoke(key, "location"));
            if (dimension.equals(String.valueOf(location))) return level;
        }
        return null;
    }

    private static Object resourceLocation(String id) {
        Class<?> type = load("net.minecraft.resources.ResourceLocation");
        if (type == null || id == null) return null;
        Invocation parsed = invokeStatic(type, "tryParse", id);
        if (parsed.found && parsed.value != null) return parsed.value;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!compatible(constructor.getParameterTypes(), new Object[]{id})) continue;
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(id);
            } catch (Exception ignored) {
                // Try another constructor.
            }
        }
        return null;
    }

    private static Object staticField(String className, String name) {
        Class<?> type = load(className);
        if (type == null) return null;
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Invocation invoke(Object target, String name, Object... args) {
        if (target == null) return Invocation.ABSENT;
        for (Method method : methods(target.getClass(), name)) {
            if (!compatible(method.getParameterTypes(), args)) continue;
            try {
                method.setAccessible(true);
                return new Invocation(true, method.invoke(target, args));
            } catch (Exception ignored) {
                // Try another overload, if present.
            }
        }
        return Invocation.ABSENT;
    }

    private static Invocation invokeStatic(Class<?> type, String name, Object... args) {
        if (type == null) return Invocation.ABSENT;
        for (Method method : methods(type, name)) {
            if (!Modifier.isStatic(method.getModifiers()) || !compatible(method.getParameterTypes(), args)) continue;
            try {
                method.setAccessible(true);
                return new Invocation(true, method.invoke(null, args));
            } catch (Exception ignored) {
                // Try another overload, if present.
            }
        }
        return Invocation.ABSENT;
    }

    private static Method[] methods(Class<?> type, String name) {
        List<Method> result = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (name.equals(method.getName())) result.add(method);
            }
        }
        return result.toArray(new Method[result.size()]);
    }

    private static boolean compatible(Class<?>[] types, Object[] args) {
        if (types.length != args.length) return false;
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) {
                if (types[i].isPrimitive()) return false;
            } else if (!wrap(types[i]).isAssignableFrom(args[i].getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return Boolean.FALSE;
        if (type == char.class) return Character.valueOf('\0');
        if (type == byte.class) return Byte.valueOf((byte) 0);
        if (type == short.class) return Short.valueOf((short) 0);
        if (type == int.class) return Integer.valueOf(0);
        if (type == long.class) return Long.valueOf(0L);
        if (type == float.class) return Float.valueOf(0.0F);
        if (type == double.class) return Double.valueOf(0.0D);
        return null;
    }

    private static boolean booleanValue(Invocation invocation, boolean fallback) {
        return invocation.found && invocation.value instanceof Boolean
                ? ((Boolean) invocation.value).booleanValue() : fallback;
    }

    private static Object value(Invocation invocation) {
        return invocation == null ? null : invocation.value;
    }

    private static List<Object> iterable(Object value) {
        if (!(value instanceof Iterable<?>)) return Collections.emptyList();
        List<Object> result = new ArrayList<>();
        for (Object item : (Iterable<?>) value) result.add(item);
        return result;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static final class Invocation {
        private static final Invocation ABSENT = new Invocation(false, null);
        private final boolean found;
        private final Object value;

        private Invocation(boolean found, Object value) {
            this.found = found;
            this.value = value;
        }
    }
}
