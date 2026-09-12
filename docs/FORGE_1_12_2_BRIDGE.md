# Minecraft 1.12.2 Forge Bridge Specification

Minecraft 1.12.2 remains the classic standard for complex technical mega-packs (*GregTech: New Horizons*, *SevTech: Ages*, *Enigmatica 2*). Because 1.12.2 predates Fabric Loader and modern Mojmap naming, HeapHammer interfaces with it through an isolated **Forge Platform Adapter**.

---

## 1. Architectural Boundary

Hexagonal architecture guarantees that core domain logic remains 100% shared:

```text
┌─────────────────────────────────────────────────────────────┐
│          SHARED PORTABLE DOMAIN (100% Pure Java)            │
│   • ExperimentPlan / ExperimentStateMachine                 │
│   • ChunkScenarioGenerator (Spiral, Ring, Hotspot)          │
│   • OrdinaryLeastSquares / TrendDetectionEngine             │
│   • JsonReportService / ReportComparisonService             │
└──────────────────────────────┬──────────────────────────────┘
                               │ Implements PlatformAdapter Port
                               ▼
┌─────────────────────────────────────────────────────────────┐
│             ForgePlatformAdapter (1.12.2 Implementation)    │
│   • Lifecycle: @Mod and @SubscribeEvent on MinecraftForge Bus│
│   • Chunk Tickets: net.minecraftforge.common.ForgeChunkManager
│   • Command Dispatcher: CommandBase ("hh")                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Platform Subsystem Mapping

| Subsystem | Modern Fabric (1.20–1.21) | Classic Forge (1.12.2) |
|---|---|---|
| **Initialization** | `ModInitializer` | `@Mod(modid = "heaphammer")` |
| **Server Ticks** | `ServerTickEvents.END_SERVER_TICK` | `@SubscribeEvent public void onTick(ServerTickEvent e)` |
| **Chunk Tickets** | `TicketType<ChunkPos>` | `ForgeChunkManager.requestTicket` / `forceChunk` |
| **Ticket Release** | `removeRegionTicket` | `ForgeChunkManager.releaseTicket(ticket)` |
| **Command System** | Brigadier (`LiteralArgumentBuilder`) | `net.minecraft.command.CommandBase` |
| **Registries** | `BuiltInRegistries.BLOCK` | `GameRegistry.findRegistry(Block.class)` |
| **JVM Target** | Java 17 / 21 | Java 8 (or CleanroomMC Java 17/21) |

---

## 3. Forge Chunk Ticket Adapter Pattern

Chunk tickets are managed safely per-mod via `ForgeChunkManager`:

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

## 4. Build Tooling Strategy

1. **Gradle Tooling**: Uses CleanroomMC Loom or ForgeGradle 2.3 for modern Gradle daemon compatibility.
2. **Dedicated Branch**: `ver/1.12.2-forge` maintains the Forge adapter and build script.
3. **CI Integration**: Built by GitHub Actions on JDK 17 (`ci.yml` resolves `java_version` from `gradle.properties`).
