package com.dwurdy.heaphammer.platform.forge1710;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production implementation of ForgeTicketBridge1710 utilizing reflection to interact with
 * net.minecraftforge.common.ForgeChunkManager on Minecraft 1.7.10 servers.
 * Supports legacy ChunkCoordIntPair coordinate packing and DimensionManager lookup.
 */
public class ReflectiveForgeTicketBridge1710 implements ForgeTicketBridge1710 {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-forge1710-tickets");

    private final Object modInstance;
    private Method requestTicketMethod;
    private Method forceChunkMethod;
    private Method unforceChunkMethod;
    private Method releaseTicketMethod;
    private Method setForcedChunkLoadingCallbackMethod;
    private Method getWorldMethod;
    private Constructor<?> chunkCoordConstructor;
    private Object normalTicketType;

    private boolean initialized = false;
    private final Map<String, Integer> dimensionIdCache = new ConcurrentHashMap<>();

    public ReflectiveForgeTicketBridge1710(Object modInstance) {
        this.modInstance = modInstance;
        initReflection();
    }

    private synchronized void initReflection() {
        if (initialized) return;
        try {
            Class<?> forgeChunkManagerClass = Class.forName("net.minecraftforge.common.ForgeChunkManager");
            Class<?> ticketTypeClass = Class.forName("net.minecraftforge.common.ForgeChunkManager$Type");
            Class<?> ticketClass = Class.forName("net.minecraftforge.common.ForgeChunkManager$Ticket");
            Class<?> loadingCallbackClass = Class.forName("net.minecraftforge.common.ForgeChunkManager$LoadingCallback");
            Class<?> dimensionManagerClass = Class.forName("net.minecraftforge.common.DimensionManager");
            Class<?> worldClass = Class.forName("net.minecraft.world.World");

            // In 1.7.10 chunk coordinates use ChunkCoordIntPair; modern backports may use ChunkPos
            Class<?> chunkCoordClass;
            try {
                chunkCoordClass = Class.forName("net.minecraft.world.ChunkCoordIntPair");
            } catch (ClassNotFoundException ignored) {
                chunkCoordClass = Class.forName("net.minecraft.util.math.ChunkPos");
            }

            for (Object constant : ticketTypeClass.getEnumConstants()) {
                if ("NORMAL".equals(constant.toString())) {
                    normalTicketType = constant;
                    break;
                }
            }

            requestTicketMethod = forgeChunkManagerClass.getMethod("requestTicket", Object.class, worldClass, ticketTypeClass);
            forceChunkMethod = forgeChunkManagerClass.getMethod("forceChunk", ticketClass, chunkCoordClass);
            unforceChunkMethod = forgeChunkManagerClass.getMethod("unforceChunk", ticketClass, chunkCoordClass);
            releaseTicketMethod = forgeChunkManagerClass.getMethod("releaseTicket", ticketClass);
            setForcedChunkLoadingCallbackMethod = forgeChunkManagerClass.getMethod(
                    "setForcedChunkLoadingCallback", Object.class, loadingCallbackClass);
            getWorldMethod = dimensionManagerClass.getMethod("getWorld", int.class);
            chunkCoordConstructor = chunkCoordClass.getConstructor(int.class, int.class);

            Object loadingCallback = Proxy.newProxyInstance(
                    loadingCallbackClass.getClassLoader(),
                    new Class<?>[]{loadingCallbackClass},
                    new ForgeLoadingCallbackInvocationHandler(ticket -> releaseTicketMethod.invoke(null, ticket)));
            setForcedChunkLoadingCallbackMethod.invoke(null, modInstance, loadingCallback);

            initialized = true;
            LOGGER.info("ReflectiveForgeTicketBridge1710 initialized successfully for Forge 1.7.10.");
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Forge 1.7.10 ChunkManager classes not present on current classpath (likely testing or non-Forge environment).");
        }
    }

    public int parseDimensionId(String dimension) {
        return dimensionIdCache.computeIfAbsent(dimension, dim -> {
            if ("minecraft:overworld".equalsIgnoreCase(dim) || "overworld".equalsIgnoreCase(dim) || "0".equals(dim)) {
                return 0;
            } else if ("minecraft:the_nether".equalsIgnoreCase(dim) || "the_nether".equalsIgnoreCase(dim) || "-1".equals(dim)) {
                return -1;
            } else if ("minecraft:the_end".equalsIgnoreCase(dim) || "the_end".equalsIgnoreCase(dim) || "1".equals(dim)) {
                return 1;
            }
            try {
                return Integer.parseInt(dim);
            } catch (NumberFormatException e) {
                return 0;
            }
        });
    }

    private Object getWorld(String dimension) {
        if (!initialized || getWorldMethod == null) return null;
        try {
            int dimId = parseDimensionId(dimension);
            return getWorldMethod.invoke(null, dimId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Object requestTicket(String dimension) {
        if (!initialized) return null;
        Object world = getWorld(dimension);
        if (world == null) return null;

        try {
            return requestTicketMethod.invoke(null, modInstance, world, normalTicketType);
        } catch (Exception e) {
            LOGGER.error("Failed to request Forge 1.7.10 ticket in dimension {}: {}", dimension, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean forceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
        if (!initialized || ticket == null) return false;
        try {
            Object chunkCoord = chunkCoordConstructor.newInstance(chunkX, chunkZ);
            forceChunkMethod.invoke(null, ticket, chunkCoord);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to forceChunk ({}, {}) on Forge 1.7.10: {}", chunkX, chunkZ, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean unforceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
        if (!initialized || ticket == null) return false;
        try {
            Object chunkCoord = chunkCoordConstructor.newInstance(chunkX, chunkZ);
            unforceChunkMethod.invoke(null, ticket, chunkCoord);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to unforceChunk ({}, {}) on Forge 1.7.10: {}", chunkX, chunkZ, e.getMessage());
            return false;
        }
    }

    @Override
    public void releaseTicket(Object ticket) {
        if (!initialized || ticket == null) return;
        try {
            releaseTicketMethod.invoke(null, ticket);
        } catch (Exception e) {
            LOGGER.error("Failed to release Forge 1.7.10 ticket: {}", e.getMessage());
        }
    }

    @Override
    public boolean isDimensionLoaded(String dimension) {
        return getWorld(dimension) != null;
    }
}

/**
 * Releases only tickets restored for HeapHammer's own Forge 1.7.10 mod instance.
 * The callback deliberately does not retain a world or any game object.
 */
final class ForgeLoadingCallbackInvocationHandler implements InvocationHandler {
    interface TicketReleaser {
        void release(Object ticket) throws Exception;
    }

    private final TicketReleaser ticketReleaser;

    ForgeLoadingCallbackInvocationHandler(TicketReleaser ticketReleaser) {
        this.ticketReleaser = ticketReleaser;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if ("ticketsLoaded".equals(method.getName()) && args != null && args.length > 0 && args[0] instanceof Iterable) {
            for (Object ticket : (Iterable<?>) args[0]) {
                try {
                    ticketReleaser.release(ticket);
                } catch (Exception e) {
                    LoggerFactory.getLogger("heaphammer-forge1710-tickets")
                            .warn("Failed to release a restored Forge ticket: {}", e.getMessage());
                }
            }
            return null;
        }
        if ("toString".equals(method.getName())) return "HeapHammerForge1710LoadingCallback";
        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
        if ("equals".equals(method.getName())) return proxy == (args == null ? null : args[0]);
        return null;
    }
}
