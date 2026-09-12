package com.dwurdy.heaphammer.command;

import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Method;
import java.util.function.IntPredicate;

/**
 * Validates operator and permission-node access for all HeapHammer commands.
 * Supports Fabric Permissions API (LuckPerms) via reflection, falling back to vanilla OP level 2.
 */
public final class CommandPermissions {

    public static final String PERM_ADMIN = "heaphammer.admin";
    public static final String PERM_USE = "heaphammer.use";
    public static final String PERM_RUN = "heaphammer.run";
    public static final String PERM_DIAGNOSTICS = "heaphammer.diagnostics";
    public static final String PERM_REPORT = "heaphammer.report";
    public static final String PERM_PLAN = "heaphammer.plan";
    public static final String PERM_STOP = "heaphammer.stop";
    public static final String PERM_CLEANUP = "heaphammer.cleanup";

    public static final int OP_LEVEL_ADMIN = 2;

    private CommandPermissions() {}

    /**
     * Checks whether the command source has permission for a given node at OP Level 2.
     */
    public static boolean check(CommandSourceStack source, String permissionNode) {
        return check(source, permissionNode, OP_LEVEL_ADMIN);
    }

    /**
     * Checks whether the command source has permission for a given node with a custom default OP level.
     */
    public static boolean check(CommandSourceStack source, String permissionNode, int defaultLevel) {
        if (source == null) return false;
        return check(source, source::hasPermission, permissionNode, defaultLevel);
    }

    /**
     * Checks permission using a fallback predicate (for testability without booting a Minecraft server).
     */
    public static boolean check(IntPredicate fallbackPredicate, String permissionNode) {
        return check(null, fallbackPredicate, permissionNode, OP_LEVEL_ADMIN);
    }

    /**
     * Full permission resolution: Fabric Permissions API reflection -> fallback predicate.
     */
    public static boolean check(Object rawSource, IntPredicate fallbackPredicate, String permissionNode, int defaultLevel) {
        if (rawSource == null && fallbackPredicate == null) {
            return false;
        }

        // 1. Try LuckPerms / Fabric Permissions API via reflection if a genuine source object is present
        if (rawSource != null) {
            try {
                Class<?> permsClass = Class.forName("me.lucko.fabric.permissions.Permissions");
                Method checkMethod = permsClass.getMethod("check", CommandSourceStack.class, String.class, int.class);

                // Check wildcard admin permission first
                boolean isAdmin = (boolean) checkMethod.invoke(null, rawSource, PERM_ADMIN, defaultLevel);
                if (isAdmin) return true;

                // Check specific permission node
                return (boolean) checkMethod.invoke(null, rawSource, permissionNode, defaultLevel);
            } catch (Throwable ignored) {
                // Permissions API not present, proceed to fallback predicate
            }
        }

        // 2. Fall back to vanilla OP level check
        if (fallbackPredicate != null) {
            return fallbackPredicate.test(defaultLevel);
        }

        return false;
    }
}
