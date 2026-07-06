# Card Command Return Values

Reader calls return the values produced by the card program's command handler, using ComputerCraft-style multiple return values. Reader-level failures such as missing cards, unissued cards, missing entry files, or runtime errors return `nil` plus an error code instead of throwing a Lua-facing exception for ordinary gameplay errors.

Filesystem failures from the card runtime, including running out of card space, surface as runtime errors unless the card program catches them.

## Considered Options

- Multiple return values.
- A structured response object.

A structured response object was rejected because plain multiple returns match normal Lua and ComputerCraft conventions, and keep card programs small.
