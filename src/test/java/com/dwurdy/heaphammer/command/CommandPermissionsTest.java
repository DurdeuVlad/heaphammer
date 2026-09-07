package com.dwurdy.heaphammer.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

class CommandPermissionsTest {

    @Test
    @DisplayName("CommandPermissions grants access when fallback OP level check passes")
    void testOpPermissionPass() {
        IntPredicate opSource = level -> level <= 2;

        assertTrue(CommandPermissions.check(null, opSource, CommandPermissions.PERM_RUN, 2));
        assertTrue(CommandPermissions.check(null, opSource, CommandPermissions.PERM_ADMIN, 2));
        assertTrue(CommandPermissions.check(null, opSource, CommandPermissions.PERM_USE, 2));
    }

    @Test
    @DisplayName("CommandPermissions denies access when fallback OP level check fails")
    void testOpPermissionFail() {
        IntPredicate nonOpSource = level -> level <= 0;

        assertFalse(CommandPermissions.check(null, nonOpSource, CommandPermissions.PERM_RUN, 2));
        assertFalse(CommandPermissions.check(null, nonOpSource, CommandPermissions.PERM_ADMIN, 2));
        assertFalse(CommandPermissions.check(null, nonOpSource, CommandPermissions.PERM_USE, 2));
    }

    @Test
    @DisplayName("CommandPermissions denies access when source or predicate is null")
    void testNullSource() {
        assertFalse(CommandPermissions.check((IntPredicate) null, CommandPermissions.PERM_RUN));
        assertFalse(CommandPermissions.check((net.minecraft.commands.CommandSourceStack) null, CommandPermissions.PERM_RUN));
    }

    @Test
    @DisplayName("Permission nodes conform to canonical heaphammer.* namespace")
    void testPermissionNodes() {
        assertEquals("heaphammer.admin", CommandPermissions.PERM_ADMIN);
        assertEquals("heaphammer.use", CommandPermissions.PERM_USE);
        assertEquals("heaphammer.run", CommandPermissions.PERM_RUN);
        assertEquals("heaphammer.diagnostics", CommandPermissions.PERM_DIAGNOSTICS);
        assertEquals("heaphammer.report", CommandPermissions.PERM_REPORT);
        assertEquals("heaphammer.plan", CommandPermissions.PERM_PLAN);
        assertEquals("heaphammer.stop", CommandPermissions.PERM_STOP);
        assertEquals("heaphammer.cleanup", CommandPermissions.PERM_CLEANUP);
    }
}
