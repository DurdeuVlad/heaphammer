package com.dwurdy.heaphammer.platform.forge1710;

import com.dwurdy.heaphammer.domain.EnvironmentFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class ForgePlatformAdapter1710Test {

    private static String expectedHeapHammerVersion() {
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream in = ForgePlatformAdapter1710Test.class
                .getResourceAsStream("/heaphammer-version.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (java.io.IOException ignored) {
        }
        return props.getProperty("mod_version", "dev").trim();
    }

    private ForgePlatformAdapter1710 adapter;
    private MockForgeTicketBridge1710 mockBridge;

    @BeforeEach
    void setUp() {
        mockBridge = new MockForgeTicketBridge1710();
        ForgeChunkTicketManager1710 ticketManager = new ForgeChunkTicketManager1710(mockBridge);
        adapter = new ForgePlatformAdapter1710(ticketManager);
    }

    @Test
    @DisplayName("Fingerprint captures Forge 1.7.10 metadata correctly")
    void testCaptureFingerprint() {
        EnvironmentFingerprint fp = adapter.captureFingerprint();
        assertNotNull(fp);
        assertEquals("1.7.10", fp.minecraftVersion());
        assertTrue(fp.loaderVersion().contains("forge-10.13.4.1614"));
        assertEquals(expectedHeapHammerVersion(), fp.heapHammerVersion());
        assertTrue(fp.installedMods().containsKey("forge"));
        assertTrue(fp.installedMods().containsKey("minecraft"));
        assertTrue(fp.installedMods().containsKey("heaphammer"));
    }

    @Test
    @DisplayName("Entity lifecycle tracks, spawns, and cleans up entities safely in 1.7.10")
    void testEntityLifecycle() {
        List<String> types = adapter.getAvailableEntityTypes();
        assertFalse(types.isEmpty());
        assertTrue(types.contains("minecraft:zombie"));

        UUID uuid1 = adapter.spawnEntity("minecraft:overworld", "minecraft:zombie", 0, 64, 0);
        assertNotNull(uuid1);
        assertEquals(1, adapter.getActiveEntityCount("minecraft:overworld"));

        UUID uuid2 = adapter.spawnEntity("minecraft:overworld", "minecraft:skeleton", 1, 64, 1);
        assertNotNull(uuid2);
        assertEquals(2, adapter.getActiveEntityCount("minecraft:overworld"));

        assertTrue(adapter.removeEntity("minecraft:overworld", uuid1, "DISCARD"));
        assertEquals(1, adapter.getActiveEntityCount("minecraft:overworld"));

        int removed = adapter.removeAllTestEntities("minecraft:overworld");
        assertEquals(1, removed);
        assertEquals(0, adapter.getActiveEntityCount("minecraft:overworld"));
    }

    @Test
    @DisplayName("Block entity lifecycle places, tracks, and cleans up blocks in 1.7.10")
    void testBlockEntityLifecycle() {
        List<String> types = adapter.getAvailableBlockEntityTypes();
        assertFalse(types.isEmpty());
        assertTrue(types.contains("minecraft:chest"));

        assertTrue(adapter.placeBlockEntity("minecraft:overworld", "minecraft:chest", 10, 64, 10));
        assertTrue(adapter.removeBlockEntity("minecraft:overworld", 10, 64, 10));

        adapter.placeBlockEntity("minecraft:overworld", "minecraft:chest", 10, 64, 10);
        adapter.placeBlockEntity("minecraft:overworld", "minecraft:furnace", 11, 64, 10);
        int removed = adapter.removeAllTestBlockEntities("minecraft:overworld");
        assertEquals(2, removed);
    }

    @Test
    @DisplayName("Server tick hook receives 1.7.10 tick notifications")
    void testServerTickHook() {
        AtomicLong lastTick = new AtomicLong(0);
        adapter.registerServerTickHook(lastTick::set);

        adapter.onServerTick();
        assertEquals(1L, lastTick.get());

        adapter.onServerTick();
        assertEquals(2L, lastTick.get());
    }

    @Test
    @DisplayName("CommandHeapHammer1710 parses subcommands, flags, and sends feedback")
    void testCommandHeapHammer1710() {
        CommandHeapHammer1710 cmd = new CommandHeapHammer1710(adapter);
        assertEquals("hh", cmd.getCommandName());
        assertTrue(cmd.getCommandAliases().contains("heaphammer"));

        List<String> messages = new ArrayList<>();
        int statusResult = cmd.execute(new String[]{"status"}, (msg, ok) -> messages.add(msg));
        assertEquals(1, statusResult);
        assertFalse(messages.isEmpty());
        assertTrue(messages.get(0).contains("Status: IDLE"));

        messages.clear();
        int verResult = cmd.execute(new String[]{"version"}, (msg, ok) -> messages.add(msg));
        assertEquals(1, verResult);
        assertTrue(messages.stream().anyMatch(m -> m.contains("1.7.10")));

        messages.clear();
        int runResult = cmd.execute(new String[]{"run", "chunks", "--iterations=10", "--batch=20", "--strategy=SPIRAL"},
                (msg, ok) -> messages.add(msg));
        assertEquals(1, runResult);
        assertTrue(messages.stream().anyMatch(m -> m.contains("Starting chunk leak benchmark") && m.contains("10 iterations")));

        messages.clear();
        int abortResult = cmd.execute(new String[]{"abort"}, (msg, ok) -> messages.add(msg));
        assertEquals(1, abortResult);
        assertTrue(messages.stream().anyMatch(m -> m.contains("Abort requested")));
    }

    private static class MockForgeTicketBridge1710 implements ForgeTicketBridge1710 {
        @Override
        public Object requestTicket(String dimension) {
            return new Object();
        }

        @Override
        public boolean forceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public boolean unforceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void releaseTicket(Object ticket) {}

        @Override
        public boolean isDimensionLoaded(String dimension) {
            return true;
        }
    }
}
