# HeapHammer v1.0.0 - Official Production Release

**Deterministic Minecraft server stress testing and retained-memory regression detection.**

### Supported Minecraft Versions
HeapHammer v1.0.0 provides dedicated, precompiled binaries for 5 major Minecraft version lines:

| Minecraft Version | Mod Loader | Java Target | Release Binary |
|---|---|---|---|
| **1.21.1** *(Primary)* | Fabric | Java 21 | `heaphammer-1.21.1-1.0.0.jar` |
| **1.20.1** | Fabric & Forge | Java 17 | `heaphammer-1.20.1-1.0.0.jar` |
| **1.18.2** | Fabric & Forge | Java 17 | `heaphammer-1.18.2-1.0.0.jar` |
| **1.16.5** | Forge & Fabric | Java 8 / 17 | `heaphammer-1.16.5-1.0.0.jar` |
| **1.12.2** | Forge | Java 8 | `heaphammer-1.12.2-1.0.0.jar` |

### Release Highlights
- **Hexagonal Core Architecture**: 100% pure Java domain engine with zero `net.minecraft.*` runtime coupling.
- **Statistical OLS Regression**: Ordinary Least Squares (y = mx + b) trend slope and plateau pattern detection vs GC noise.
- **Production Safety**: Safety ceilings (`config/heaphammer.json`), tick budgets (15ms max), and automated crash recovery journal.
- **Command Security**: Operator Level 2 gating and Fabric Permissions API / LuckPerms integration.

### Quickstart
1. Download the JAR corresponding to your server's Minecraft version from the assets below.
2. Place the JAR into your dedicated server's `mods/` directory and restart.
3. Run `/hh run chunks` to simulate 72 hours of player exploration in 90 seconds.
4. View the diagnostic verdict with `/hh report show last`.
5. Refer to the attached `HeapHammer_Server_Admin_Guide.pdf` for a complete walkthrough.

### Checksums (SHA-256)
```
e1c389bf060c44b5f704beb16e356a78d3ae7d43ed187103e5b8ca411b6a2c8f  heaphammer-1.0.0.jar
2dcad642ba74c0b6bc956bb0bb96a3d80e619098b4e81b159362fb8a37c57a6d  heaphammer-1.12.2-1.0.0.jar
333867296c9573e9f84014676f18e1d7d3ac0b1fff0baf28e36be46eba20aff6  heaphammer-1.16.5-1.0.0.jar
e3a13c4d3fa14061323b9c9de2f26172b5f48337ce853b0b125296904bcc58c9  heaphammer-1.18.2-1.0.0.jar
e9adcca915ce98717489cb28dfbc82e4bb43e8266f6123fe7a5352006955b749  heaphammer-1.20.1-1.0.0.jar
e1c389bf060c44b5f704beb16e356a78d3ae7d43ed187103e5b8ca411b6a2c8f  heaphammer-1.21.1-1.0.0.jar
```
