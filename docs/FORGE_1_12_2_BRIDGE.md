# Minecraft 1.12.2 Forge Bridge Architecture Specification

Minecraft 1.12.2 is the most played classic modpack version in the history of modded Minecraft (powering iconic mega-packs such as *GregTech: New Horizons*, *SevTech: Ages*, and *Enigmatica 2: Expert*).

Because Minecraft 1.12.2 predates the Fabric Loader and modern Mojang mappings, HeapHammer interfaces with 1.12.2 via a dedicated **Forge Platform Adapter**.

---

## 1. Architectural Boundary

HeapHammer's Hexagonal Architecture guarantees that the domain layer does not change:

```text
┌─────────────────────────────────────────────────────────────┐
│          SHARED PORTABLE DOMAIN (100% Shared Logic)         │
│                                                             │
│   • ExperimentPlan / ExperimentStateMachine                 │
│   • ChunkScenarioGenerator (Spiral, Ring, Hotspot)          │
│   • OrdinaryLeastSquares / TrendDetectionEngine             │
│   • JsonReportService / ReportComparisonService             │
└──────────────────────────────┬──────────────────────────────┘
                               │ Implements PlatformAdapter Port
                               ▼
┌─────────────────────────────────────────────────────────────┐
│             ForgePlatformAdapter (1.12.2 Implementation)    │
│                                                             │
│   • Event Bus: @SubscribeEvent on FML Lifecycle / Forge Bus │
│   • Chunk Tickets: net.minecraftforge.common.ForgeChunkManager
│   • Entity Lifecycle: EntityJoinWorldEvent                  │
│   • Command Dispatcher: CommandBase ("hh")                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Platform Subsystem Mapping Table

| Subsystem | Modern Fabric (1.20–1.21) | Classic MinecraftForge (1.12.2) |
|---|---|---|
| **Mod Initialization** | `net.fabricmc.api.ModInitializer` | `@Mod(modid = "heaphammer", version = "...")` |
| **Server Ticks** | `ServerTickEvents.END_SERVER_TICK` | `@SubscribeEvent public void onTick(TickEvent.ServerTickEvent e)` |
| **Chunk Tickets** | `TicketType<ChunkPos>` | `ForgeChunkManager.requestTicket` / `forceChunk` |
| **Ticket Release** | `serverLevel.getChunkSource().removeRegionTicket` | `ForgeChunkManager.releaseTicket(ticket)` |
| **Command System** | Brigadier (`LiteralArgumentBuilder`) | `net.minecraft.command.CommandBase` |
| **Registries** | `BuiltInRegistries.BLOCK` | `GameRegistry.findRegistry(Block.class)` |
| **JVM Target** | Java 17 / 21 | Java 8 (or CleanroomMC Java 17/21 backport) |

---

## 3. Forge Chunk Ticket Adapter Pattern

In Minecraft 1.12.2, chunk tickets are registered per-mod through `ForgeChunkManager`:

```java
package com.dwurdy.heaphammer.platform.forge;

import com.dwurdy.heaphammer.platform.ChunkTicketManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.12.2 Forge implementation of ChunkTicketManager using ForgeChunkManager.
 */
public class ForgeChunkTicketManager implements ChunkTicketManager {
    private final Object modInstance;
    private final Map<ChunkPos, Ticket> activeTickets = new ConcurrentHashMap<>();

    public ForgeChunkTicketManager(Object modInstance) {
        this.modInstance = modInstance;
    }

    @Override
    public boolean addTicket(int chunkX, int chunkZ) {
        World world = getOverworld();
        Ticket ticket = ForgeChunkManager.requestTicket(modInstance, world, Type.NORMAL);
        if (ticket != null) {
            ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            ForgeChunkManager.forceChunk(ticket, pos);
            activeTickets.put(pos, ticket);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeTicket(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Ticket ticket = activeTickets.remove(pos);
        if (ticket != null) {
            ForgeChunkManager.unforceChunk(ticket, pos);
            ForgeChunkManager.releaseTicket(ticket);
            return true;
        }
        return false;
    }

    @Override
    public void removeAllTickets() {
        for (Map.Entry<ChunkPos, Ticket> entry : activeTickets.entrySet()) {
            ForgeChunkManager.unforceChunk(entry.getValue(), entry.getKey());
            ForgeChunkManager.releaseTicket(entry.getValue());
        }
        activeTickets.clear();
    }

    @Override
    public int getActiveTicketCount() {
        return activeTickets.size();
    }
}
```

---

## 4. Build Tooling Strategy for 1.12.2

For building 1.12.2 Forge:
1. **CleanroomMC Loom** or **ForgeGradle 2.3**: Modern Gradle-compatible plugins capable of compiling 1.12.2 Forge without legacy Gradle 4 incompatibilities.
2. **Dedicated Branch**: `ver/1.12.2-forge` branch maintaining the ForgeGradle/Loom configuration and `ForgePlatformAdapter`.
3. **Jenkins Matrix Pipeline**: Detected automatically via `Jenkinsfile` binding `JDK8`.
