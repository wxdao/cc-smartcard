# CC:T Internal Runtime

CC SmartCard may use CC: Tweaked internal runtime classes to run card programs as independent ComputerCraft-style computers. The public CC:T API exposes peripheral, media, filesystem mount, and integration hooks, but does not provide a stable API for creating a headless computer runtime, executing a Lua entrypoint, and returning values synchronously to a peripheral call.

The card runtime uses `CobaltLuaMachine` directly instead of constructing a temporary `Computer` or loading CC:T's `bios.lua`. It provides a small `MachineEnvironment`/`IAPIEnvironment`, mounts the card storage as `/`, mounts CC:T's ROM resources at `/rom`, and boots through a mod-owned Lua bootstrap which installs the expected ROM APIs, including the HTTP wrapper API.

## Considered Options

- Use CC:T internal runtime classes and version-lock the implementation to the supported CC:T/Minecraft target.
- Build and maintain a separate Lua runtime compatibility layer.

The separate runtime was rejected because the desired behavior is explicitly ComputerCraft-like, including familiar APIs, filesystem behavior, HTTP settings, and timeout semantics. Using internal classes increases upgrade risk, but better preserves player expectations for the initial NeoForge 1.21.1 and CC:T 1.120.0 target.
