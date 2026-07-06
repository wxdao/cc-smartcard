# Modrinth Draft: CC SmartCard 0.1.0

## Project Form

- Project title: CC SmartCard
- Slug: cc-smartcard
- Summary: Programmable smart cards and reader peripherals for CC: Tweaked.
- Categories:
  - Technology
  - Utility
- Client support: Required
- Server support: Required
- License: MIT
- Source code: https://github.com/wxdao/cc-smartcard
- Issues: https://github.com/wxdao/cc-smartcard/issues

## Long Description

Use the contents of `README.md` as the Modrinth long description. It is written for players and includes:

- What CC SmartCard is
- Features
- Installation and dependencies
- Crafting recipes
- Quick start
- Reader API
- Card program layout
- Example
- Known limitations

## Version Metadata

- Version number: 0.1.0
- Version title: 0.1.0 - Initial Release
- Release channel: Beta
- Loaders: NeoForge
- Game versions:
  - 1.21.1
- Featured: Yes

## Dependencies

- CC: Tweaked
  - Required
  - Slug: `cc-tweaked`
  - Project ID: `gu7yAYhd`

## Upload

- JAR path: `build/libs/cc_smartcard-0.1.0.jar`

## Gallery And Icon Checklist

Project icon:

- `docs/release/icon.png`
  - 512x512 PNG, upscaled from the Smart Card item texture.

Suggested gallery images already captured locally:

- `run/client/screenshots/2026-07-07_00.04.02.png`
  - Suggested title: Smart Card Reader with an inserted card
  - Suggested description: A Smart Card Reader connected to a CC: Tweaked computer, with a Smart Card attached to the reader side.
- `run/client/screenshots/2026-07-07_00.05.14.png`
  - Suggested title: Smart Card Reader and Smart Card item
  - Suggested description: The reader block and the handheld Smart Card item in-game.

Optional before publishing:

- Optional screenshot of a CC: Tweaked terminal issuing or calling a card command.

## Release Notes

Copy/paste text:

```markdown
CC SmartCard 0.1.0 is the initial release for Minecraft 1.21.1, NeoForge 21.1.235, and CC: Tweaked 1.120.0.

### Added

- Smart Card item.
- Smart Card Reader block and CC: Tweaked peripheral.
- One-time issuing with `issueSource(source)` and `issueFiles(files)`.
- Card command calls with `reader.call(command, args)`.
- Card program entrypoint at `/main.lua` with `handle(command, args, context)`.
- World-backed private card storage.
- Public card IDs and labels.
- HTTP API support for card programs, following the server's CC: Tweaked HTTP settings.
- Crafting recipes for the Smart Card and Smart Card Reader.
- `smartcard` ROM helper for issuing from local files or directories.

### Known limitations

- Card contents are hidden from normal reader API access, but this is gameplay privacy rather than formal cryptographic security.
- Issued cards cannot be erased or reissued through the reader API.
- Card commands are non-transactional; file writes made before an error may remain.
- The card runtime depends on CC: Tweaked internals and may need maintenance for future CC: Tweaked versions.
```
