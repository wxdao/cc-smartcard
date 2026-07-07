# Changelog

## 0.3.1 - Serial Card Commands

### Fixed

- Fixed Smart Card Reader command scheduling so calls for the same Smart Card ID execute serially.
- Fixed queued Smart Card calls so detach and reader removal cancellation complete cleanly while calls for different cards can still run in parallel.

## 0.3.0 - Multi-Colour Smart Cards

### Added

- Added dyeable Smart Cards using Minecraft's `dyed_color` data component.
- Added optional dye ingredients to the Smart Card crafting recipe.
- Added CC: Tweaked dyeing and Wet Sponge colour-clearing support for existing Smart Cards.
- Added dyed Smart Card variants to the creative tab.
- Added dynamic Smart Card Reader rendering so inserted cards show their dyed colour on the reader.

### Changed

- Smart Card item rendering now uses separate tinted card-body and non-tinted gold-chip layers.

## 0.2.0 - Fingerprint Scanner

### Added

- Added the Fingerprint Scanner block and CC: Tweaked `fingerprint_scanner` peripheral.
- Added `scan()` support for waiting on an in-world player scan and returning the scanned player's UUID and name.
- Added the Fingerprint Scanner crafting recipe, item model, block models, textures, and creative tab entry.
- Added README documentation for Smart Card Reader events.
- Added a game-test HTTP smoke check against `example.com` for card programs.

### Fixed

- Fixed a CardLuaRuntime cancellation race that could leave cancelled card calls in an inconsistent state.

## 0.1.0 - Initial Release

### Added

- Added the Smart Card item.
- Added the Smart Card Reader block and CC: Tweaked peripheral.
- Added one-time card issuing from `issueSource(source)` or `issueFiles(files)`.
- Added card command calls through `reader.call(command, args)`.
- Added independent ComputerCraft-style card program execution with `/main.lua` and `handle(command, args, context)`.
- Added world-backed private card file storage.
- Added public card ID and card label metadata.
- Added HTTP API support for card programs, following the server's CC: Tweaked HTTP configuration.
- Added Smart Card and Smart Card Reader crafting recipes.
- Added a small `smartcard` ROM helper module for issuing from local files or directories.

### Known Limitations

- Card contents are hidden from normal reader API access, but this is gameplay privacy rather than formal cryptographic security.
- Issued cards cannot be erased or reissued through the reader API.
- Card commands are non-transactional; file writes made before an error may remain.
- The card runtime depends on CC: Tweaked internals and may need maintenance for future CC: Tweaked versions.
