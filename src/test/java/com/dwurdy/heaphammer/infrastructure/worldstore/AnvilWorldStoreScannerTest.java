package com.dwurdy.heaphammer.infrastructure.worldstore;

import com.dwurdy.heaphammer.diagnostics.WorldStoreDimensionSnapshot;
import com.dwurdy.heaphammer.diagnostics.WorldStoreSnapshot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnvilWorldStoreScannerTest {
    @Test
    void scansPersistedEntitiesAndItemAgeBucketsWithoutLoadingMinecraftObjects() throws Exception {
        Path root = Files.createTempDirectory("heaphammer-world-store");
        Path regionDir = root.resolve("entities").resolve("r");
        Files.createDirectories(regionDir);
        Files.write(regionDir.resolve("r.0.0.mca"), regionWithEntities());

        WorldStoreSnapshot snapshot = new AnvilWorldStoreScanner(root).capture();
        WorldStoreDimensionSnapshot overworld = snapshot.dimensions().get("minecraft:overworld");
        assertEquals(1L, overworld.regionFileCount());
        assertEquals(3L, overworld.entityCount());
        assertEquals(1L, overworld.persistentEntityCount());
        assertEquals(2L, overworld.itemEntityCount());
        assertEquals(2L, overworld.testEntityCount());
        assertEquals(1L, overworld.testItemEntityCount());
        assertEquals(Map.of("fresh", 1L, "despawned", 1L), overworld.itemAgeBuckets());
    }

    private static byte[] regionWithEntities() throws IOException {
        byte[] nbt = nbt();
        ByteArrayOutputStream chunkOut = new ByteArrayOutputStream();
        DataOutputStream chunk = new DataOutputStream(chunkOut);
        chunk.writeInt(nbt.length + 1);
        chunk.writeByte(3);
        chunk.write(nbt);
        chunk.flush();

        byte[] region = new byte[8192 + 4096];
        DataOutputStream header = new DataOutputStream(new ByteArrayOutputStream());
        int location = (2 << 8) | 1;
        region[0] = (byte) (location >>> 24);
        region[1] = (byte) (location >>> 16);
        region[2] = (byte) (location >>> 8);
        region[3] = (byte) location;
        byte[] chunkBytes = chunkOut.toByteArray();
        System.arraycopy(chunkBytes, 0, region, 8192, chunkBytes.length);
        return region;
    }

    private static byte[] nbt() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("");
        out.writeByte(9);
        out.writeUTF("Entities");
        out.writeByte(10);
        out.writeInt(3);
        entity(out, "minecraft:zombie", true, true, null);
        entity(out, "minecraft:item", false, true, (short) -1);
        entity(out, "minecraft:item", false, false, (short) 6000);
        out.writeByte(0);
        out.writeByte(0);
        out.flush();
        return bytes.toByteArray();
    }

    private static void entity(DataOutputStream out, String id, boolean persistent, boolean test, Short age) throws IOException {
        out.writeByte(8);
        out.writeUTF("id");
        out.writeUTF(id);
        if (persistent) {
            out.writeByte(1);
            out.writeUTF("PersistenceRequired");
            out.writeByte(1);
        }
        if (test) {
            out.writeByte(9);
            out.writeUTF("Tags");
            out.writeByte(8);
            out.writeInt(1);
            out.writeUTF("heaphammer:test");
        }
        if (age != null) {
            out.writeByte(2);
            out.writeUTF("Age");
            out.writeShort(age);
        }
        out.writeByte(0);
    }
}
