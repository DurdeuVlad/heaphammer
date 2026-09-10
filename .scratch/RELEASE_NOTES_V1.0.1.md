# HeapHammer v1.0.1 - Production Release

## Fixed

- Clean-build and validate the primary Minecraft 1.21.1 Fabric/NeoForge jar before staging it.
- Prevent a stale jar from a legacy version branch from being relabeled as the 1.21.1 artifact.
- Verify that `HeapHammer.class` and the required `ExampleMixin.class` are present in the staged production jar.
