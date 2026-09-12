package com.dwurdy.heaphammer.platform;

import com.dwurdy.heaphammer.diagnostics.RetentionTracker;
import com.dwurdy.heaphammer.domain.PlayerAction;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Loader-neutral reflective bridge for the server-side player lifecycle.
 * Minecraft changed PlayerList and Connection signatures across the supported
 * branches. Keeping the compatibility logic here lets Fabric and NeoForge
 * adapters share one implementation while still using the real login,
 * respawn, and disconnect path when the running version exposes it.
 */
public final class ReflectivePlayerLifecyclePort implements PlayerLifecyclePort {
    private static final String TEST_NAME_PREFIX = "hh_test_";
    private final Supplier<?> serverSupplier;
    private volatile RetentionTracker retentionTracker;

    public ReflectivePlayerLifecyclePort(Supplier<?> serverSupplier) {
        this.serverSupplier = serverSupplier;
    }

    public void attach(RetentionTracker tracker) {
        retentionTracker = tracker;
    }

    @Override
    public UUID join(String dimension, String profileName, UUID playerId, double x, double y, double z) {
        Object server = serverSupplier.get();
        Object playerList = value(invoke(server, "getPlayerList"));
        if (server == null || playerList == null || value(invoke(playerList, "getPlayer", playerId)) != null) {
            return null;
        }

        Object level = findLevel(server, dimension);
        Object profile = construct("com.mojang.authlib.GameProfile", playerId, profileName);
        Object cookie = createInitialCookie(profile);
        Object clientInformation = value(invoke(cookie, "clientInformation"));
        if (level == null || profile == null || cookie == null || clientInformation == null) {
            return null;
        }

        Invocation loginCheck = invoke(playerList, "canPlayerLogin", new InetSocketAddress("127.0.0.1", 0), profile);
        if (loginCheck.found && loginCheck.value != null) return null;

        Object player = construct("net.minecraft.server.level.ServerPlayer", server, level, profile, clientInformation);
        Object packetFlow = enumConstant("net.minecraft.network.protocol.PacketFlow", "SERVERBOUND");
        Object connection = construct("net.minecraft.network.Connection", packetFlow);
        if (player == null || connection == null) return null;

        invoke(player, "setPos", x, y, z);
        Invocation placed = invoke(playerList, "placeNewPlayer", connection, player, cookie);
        if (!placed.found || value(invoke(playerList, "getPlayer", playerId)) == null) return null;

        RetentionTracker tracker = retentionTracker;
        if (tracker != null) tracker.observe(player.getClass().getName(), player);
        return playerId;
    }

    @Override
    public boolean perform(String dimension, UUID playerId, PlayerAction action, String targetDimension,
                           double x, double y, double z) {
        Object server = serverSupplier.get();
        Object playerList = value(invoke(server, "getPlayerList"));
        Object player = value(invoke(playerList, "getPlayer", playerId));
        if (server == null || playerList == null || player == null) return false;

        if (action == PlayerAction.TELEPORT) return invoke(player, "teleportTo", x, y, z).found;
        if (action == PlayerAction.DIMCHANGE) {
            Object target = findLevel(server, targetDimension);
            if (target == null) return false;
            Object yaw = value(invoke(player, "getYRot"));
            Object pitch = value(invoke(player, "getXRot"));
            return invoke(player, "teleportTo", target, x, y, z, Collections.emptySet(),
                    yaw == null ? Float.valueOf(0.0F) : yaw,
                    pitch == null ? Float.valueOf(0.0F) : pitch).found;
        }
        if (action == PlayerAction.RESPAWN) return respawn(playerList, player);
        return true;
    }

    @Override
    public boolean quit(String dimension, UUID playerId) {
        Object playerList = value(invoke(serverSupplier.get(), "getPlayerList"));
        Object player = value(invoke(playerList, "getPlayer", playerId));
        return player != null && disconnect(player, "HeapHammer lifecycle cycle complete")
                && value(invoke(playerList, "getPlayer", playerId)) == null;
    }

    @Override
    public int activeTestPlayerCount() {
        Object playerList = value(invoke(serverSupplier.get(), "getPlayerList"));
        int count = 0;
        for (Object player : iterable(value(invoke(playerList, "getPlayers")))) {
            if (isTestPlayer(player)) count++;
        }
        return count;
    }

    @Override
    public int cleanupTestPlayers() {
        Object playerList = value(invoke(serverSupplier.get(), "getPlayerList"));
        List<Object> players = new ArrayList<>();
        for (Object player : iterable(value(invoke(playerList, "getPlayers")))) {
            if (isTestPlayer(player)) players.add(player);
        }
        int count = 0;
        for (Object player : players) {
            if (disconnect(player, "HeapHammer cleanup")) count++;
        }
        return count;
    }

    private static boolean respawn(Object playerList, Object player) {
        if (playerList == null || player == null) return false;
        for (Method method : methods(playerList.getClass(), "respawn")) {
            Class<?>[] types = method.getParameterTypes();
            if (types.length < 2 || !types[0].isAssignableFrom(player.getClass()) || !isBoolean(types[1])) continue;
            Object[] args = new Object[types.length];
            args[0] = player;
            args[1] = Boolean.FALSE;
            for (int i = 2; i < types.length; i++) args[i] = defaultValue(types[i]);
            try {
                method.setAccessible(true);
                return method.invoke(playerList, args) != null;
            } catch (Exception ignored) {
                // Try another version-specific overload, if present.
            }
        }
        return false;
    }

    private static boolean disconnect(Object player, String message) {
        Object connection = fieldValue(player, "connection");
        if (connection == null) return false;
        for (Method method : methods(connection.getClass(), "onDisconnect")) {
            if (method.getParameterTypes().length != 1) continue;
            Class<?> parameter = method.getParameterTypes()[0];
            Object reason = disconnectReason(parameter, message);
            if (reason == null && parameter.isPrimitive()) continue;
            try {
                method.setAccessible(true);
                method.invoke(connection, reason);
                return true;
            } catch (Exception ignored) {
                // Try another overload, if present.
            }
        }
        return false;
    }

    private static Object disconnectReason(Class<?> parameter, String message) {
        if ("net.minecraft.network.chat.Component".equals(parameter.getName())) {
            return value(invokeStatic(parameter, "literal", message));
        }
        if ("net.minecraft.network.DisconnectionDetails".equals(parameter.getName())) {
            Object component = value(invokeStatic("net.minecraft.network.chat.Component", "literal", message));
            return construct(parameter, component);
        }
        if (String.class.equals(parameter)) return message;
        return defaultValue(parameter);
    }

    private static Object createInitialCookie(Object profile) {
        if (profile == null) return null;
        Class<?> cookieClass = load("net.minecraft.server.network.CommonListenerCookie");
        if (cookieClass == null) return null;
        for (Method method : cookieClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !"createInitial".equals(method.getName())) continue;
            Class<?>[] types = method.getParameterTypes();
            if ((types.length != 1 && types.length != 2) || !types[0].isAssignableFrom(profile.getClass())) continue;
            try {
                method.setAccessible(true);
                return types.length == 1 ? method.invoke(null, profile) : method.invoke(null, profile, Boolean.FALSE);
            } catch (Exception ignored) {
                // Continue searching for a compatible overload.
            }
        }
        return null;
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

    private static boolean isTestPlayer(Object player) {
        Object profile = value(invoke(player, "getGameProfile"));
        Object name = value(invoke(profile, "getName"));
        return name != null && String.valueOf(name).startsWith(TEST_NAME_PREFIX);
    }

    private static List<Object> iterable(Object value) {
        if (!(value instanceof Iterable<?>)) return Collections.emptyList();
        List<Object> result = new ArrayList<>();
        for (Object item : (Iterable<?>) value) result.add(item);
        return result;
    }

    private static Object fieldValue(Object target, String name) {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
                // Search the superclass hierarchy.
            }
        }
        return null;
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

    private static Invocation invokeStatic(String className, String name, Object... args) {
        return invokeStatic(load(className), name, args);
    }

    private static Object construct(String className, Object... args) {
        return construct(load(className), args);
    }

    private static Object construct(Class<?> type, Object... args) {
        if (type == null) return null;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!compatible(constructor.getParameterTypes(), args)) continue;
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            } catch (Exception ignored) {
                // Try another overload, if present.
            }
        }
        return null;
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

    private static boolean isBoolean(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return type.isEnum() ? enumConstant(type, "DISCARDED") : null;
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

    private static Object enumConstant(String className, String name) {
        return enumConstant(load(className), name);
    }

    private static Object enumConstant(Class<?> type, String name) {
        if (type == null || !type.isEnum()) return null;
        Object[] constants = type.getEnumConstants();
        if (constants == null) return null;
        for (Object constant : constants) if (name.equals(String.valueOf(constant))) return constant;
        return null;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Object value(Invocation invocation) {
        return invocation == null ? null : invocation.value;
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
