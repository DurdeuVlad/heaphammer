package com.dwurdy.heaphammer.infrastructure.worldstore;

import com.dwurdy.heaphammer.diagnostics.WorldStoreDimensionSnapshot;
import com.dwurdy.heaphammer.diagnostics.WorldStoreSnapshot;
import com.dwurdy.heaphammer.platform.WorldStoreMetricsPort;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Bounded, read-only scanner for Anvil entity region stores. It parses only the
 * NBT fields needed for retention attribution and never creates Minecraft objects.
 */
public final class AnvilWorldStoreScanner implements WorldStoreMetricsPort {
    private static final int REGION_HEADER_BYTES = 8192;
    private static final int SECTOR_BYTES = 4096;
    private static final int MAX_CHUNK_BYTES = 16 * 1024 * 1024;
    private static final int MAX_NBT_DEPTH = 128;

    private final Path worldRoot;

    public AnvilWorldStoreScanner(Path worldRoot) {
        this.worldRoot = worldRoot.toAbsolutePath().normalize();
    }

    @Override
    public WorldStoreSnapshot capture() {
        Map<String, WorldStoreDimensionSnapshot> dimensions = new LinkedHashMap<>();
        scanDimension(dimensions, "minecraft:overworld", worldRoot.resolve("entities"));
        scanDimension(dimensions, "minecraft:the_nether", worldRoot.resolve("DIM-1").resolve("entities"));
        scanDimension(dimensions, "minecraft:the_end", worldRoot.resolve("DIM1").resolve("entities"));

        Path dimensionsRoot = worldRoot.resolve("dimensions");
        if (Files.isDirectory(dimensionsRoot)) {
            try (java.util.stream.Stream<Path> namespaces = Files.list(dimensionsRoot)) {
                namespaces.filter(Files::isDirectory).forEach(namespace -> {
                    try (java.util.stream.Stream<Path> dimensionDirs = Files.list(namespace)) {
                        dimensionDirs.filter(Files::isDirectory).forEach(dimension -> {
                            String id = namespace.getFileName() + ":" + dimension.getFileName();
                            scanDimension(dimensions, id, dimension.resolve("entities"));
                        });
                    } catch (IOException ignored) {
                        // A dimension disappearing during a scan is reported by the next checkpoint.
                    }
                });
            } catch (IOException ignored) {
                // Return the dimensions that were readable.
            }
        }
        return new WorldStoreSnapshot(System.currentTimeMillis(), dimensions);
    }

    private void scanDimension(Map<String, WorldStoreDimensionSnapshot> dimensions, String id, Path entitiesRoot) {
        if (!Files.isDirectory(entitiesRoot)) return;
        long files = 0L;
        long bytes = 0L;
        Counters counters = new Counters();
        Path regionRoot = entitiesRoot.resolve("r");
        if (!Files.isDirectory(regionRoot)) return;
        try (java.util.stream.Stream<Path> stream = Files.list(regionRoot)) {
            for (Path region : (Iterable<Path>) stream.filter(path -> path.getFileName().toString().endsWith(".mca"))::iterator) {
                files++;
                try {
                    bytes += Files.size(region);
                    scanRegion(region, counters);
                } catch (IOException ignored) {
                    // Keep file/byte evidence even when one malformed region is skipped.
                }
            }
        } catch (IOException ignored) {
            return;
        }
        dimensions.put(id, new WorldStoreDimensionSnapshot(
                id, files, bytes, counters.entityCount, counters.persistentEntityCount,
                counters.itemEntityCount, counters.testEntityCount, counters.testItemEntityCount,
                counters.itemAgeBuckets));
    }

    private static void scanRegion(Path region, Counters counters) throws IOException {
        byte[] data = Files.readAllBytes(region);
        if (data.length < REGION_HEADER_BYTES) return;
        for (int index = 0; index < 1024; index++) {
            int headerOffset = index * 4;
            int offset = ((data[headerOffset] & 0xFF) << 16)
                    | ((data[headerOffset + 1] & 0xFF) << 8)
                    | (data[headerOffset + 2] & 0xFF);
            int sectorCount = data[headerOffset + 3] & 0xFF;
            if (offset == 0 || sectorCount == 0) continue;
            long start = (long) offset * SECTOR_BYTES;
            long end = start + (long) sectorCount * SECTOR_BYTES;
            if (start < REGION_HEADER_BYTES || end > data.length || end <= start) continue;
            int length = readInt(data, (int) start);
            if (length <= 1 || length > MAX_CHUNK_BYTES || start + 4L + length > end) continue;
            int compression = data[(int) start + 4] & 0xFF;
            int payloadLength = length - 1;
            byte[] payload = new byte[payloadLength];
            System.arraycopy(data, (int) start + 5, payload, 0, payloadLength);
            try (InputStream compressed = decompressor(compression, payload);
                 DataInputStream input = new DataInputStream(compressed)) {
                Object root = NbtReader.readRoot(input);
                if (root instanceof Map<?, ?> rootMap) {
                    Object entities = rootMap.get("Entities");
                    if (!(entities instanceof List<?>)) entities = rootMap.get("entities");
                    if (entities instanceof List<?> list) {
                        for (Object entity : list) {
                            if (entity instanceof Map<?, ?> map) scanEntity(map, counters);
                        }
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // A corrupt chunk must not prevent the rest of the region from being measured.
            }
        }
    }

    private static void scanEntity(Map<?, ?> entity, Counters counters) {
        counters.entityCount++;
        if (isTrue(entity.get("PersistenceRequired"))) counters.persistentEntityCount++;
        boolean test = containsTestTag(entity.get("Tags"));
        if (test) counters.testEntityCount++;
        Object id = entity.get("id");
        Object item = entity.get("Item");
        boolean isItem = "minecraft:item".equals(id) || item instanceof Map<?, ?>;
        if (isItem) {
            counters.itemEntityCount++;
            if (test) counters.testItemEntityCount++;
            long age = number(entity.get("Age"));
            String bucket = age < 0L ? "fresh" : age >= 6000L ? "despawned" : "aging";
            counters.itemAgeBuckets.merge(bucket, 1L, Long::sum);
        }
    }

    private static boolean containsTestTag(Object value) {
        if (!(value instanceof List<?> list)) return false;
        for (Object tag : list) {
            if (tag instanceof String string && ("heaphammer:test".equals(string) || string.startsWith("heaphammer:"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTrue(Object value) {
        return value instanceof Byte && ((Byte) value) != 0 || value instanceof Boolean && (Boolean) value;
    }

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static InputStream decompressor(int compression, byte[] payload) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(payload);
        return switch (compression) {
            case 1 -> new GZIPInputStream(input);
            case 2 -> new InflaterInputStream(input);
            case 3 -> input;
            default -> throw new IOException("Unsupported Anvil compression type " + compression);
        };
    }

    private static final class Counters {
        private long entityCount;
        private long persistentEntityCount;
        private long itemEntityCount;
        private long testEntityCount;
        private long testItemEntityCount;
        private final Map<String, Long> itemAgeBuckets = new LinkedHashMap<>();
    }

    private static final class NbtReader {
        private static Object readRoot(DataInputStream input) throws IOException {
            byte type = input.readByte();
            if (type != 10) throw new IOException("Anvil root is not a compound");
            input.readUTF();
            return readPayload(input, type, 0);
        }

        private static Object readPayload(DataInputStream input, int type, int depth) throws IOException {
            if (depth > MAX_NBT_DEPTH) throw new IOException("NBT nesting depth exceeded");
            return switch (type) {
                case 0 -> null;
                case 1 -> input.readByte();
                case 2 -> input.readShort();
                case 3 -> input.readInt();
                case 4 -> input.readLong();
                case 5 -> input.readFloat();
                case 6 -> input.readDouble();
                case 7 -> {
                    int length = input.readInt();
                    if (length < 0 || length > MAX_CHUNK_BYTES) throw new IOException("Invalid NBT byte array");
                    byte[] values = new byte[length];
                    input.readFully(values);
                    yield values;
                }
                case 8 -> input.readUTF();
                case 9 -> {
                    int elementType = input.readByte();
                    int length = input.readInt();
                    if (length < 0 || length > 1_000_000) throw new IOException("Invalid NBT list");
                    List<Object> values = new ArrayList<>(Math.min(length, 4096));
                    for (int i = 0; i < length; i++) values.add(readPayload(input, elementType, depth + 1));
                    yield values;
                }
                case 10 -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    while (true) {
                        int childType = input.readByte();
                        if (childType == 0) break;
                        String name = input.readUTF();
                        values.put(name, readPayload(input, childType, depth + 1));
                    }
                    yield values;
                }
                case 11 -> {
                    int length = input.readInt();
                    if (length < 0 || length > 1_000_000) throw new IOException("Invalid NBT int array");
                    int[] values = new int[length];
                    for (int i = 0; i < length; i++) values[i] = input.readInt();
                    yield values;
                }
                case 12 -> {
                    int length = input.readInt();
                    if (length < 0 || length > 1_000_000) throw new IOException("Invalid NBT long array");
                    long[] values = new long[length];
                    for (int i = 0; i < length; i++) values[i] = input.readLong();
                    yield values;
                }
                default -> throw new IOException("Unknown NBT tag " + type);
            };
        }
    }
}
