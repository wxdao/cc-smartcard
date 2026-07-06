# Card Program File Layout

Card programs are issued as a small file tree copied onto the smart card, with `/main.lua` as the entry file. A single source string is treated as the contents of `/main.lua`; a directory-based issue copies the directory as-is and requires `/main.lua`, keeping the model close to ComputerCraft disks instead of introducing a manifest or packaging format. After issuing, readers cannot replace the program, but self-modification by the card program is treated as undefined behavior.

Issued state is a one-way transition. A card with `issued == true` cannot be issued again, even if its own files are later deleted or damaged.

Issuing does not run or validate `/main.lua`. A malformed card program is discovered when a later card command attempts to load and call it, avoiding a separate validation runtime with its own side-effect rules.

## Considered Options

- Copy a directory as-is and use `/main.lua` as the entry file.
- Require a manifest or bundle format.
- Compile or concatenate a directory into one source file before issuing.

Manifest and compilation formats were rejected for the initial design because this is a gameplay mod, and the disk-like mental model is easier for players to understand and script against.
