# World-Backed Card Storage

Smart card items store public metadata and a Card ID, while the card's private file space is stored in world-level data keyed by that Card ID. The item is therefore a handle to card storage rather than the container for every card file. Blank cards may have a Card ID before they are issued, but their card storage is still empty until files are written.

When issuing an unissued card which already has a Card ID, the existing file space for that Card ID is cleared before the issued files are written. Once `issued == true`, the reader may no longer replace that file space through issuing.

Card storage follows ComputerCraft's floppy disk capacity model: one configurable total space limit, `smart_card_space_limit`, defaulting to `125000` bytes. The mod does not add separate file-count or per-file-size limits for cards.

Space-limit failures are reported by the card runtime's filesystem operations. Readers do not preflight command execution against available space.

## Considered Options

- Store card files in world-level data keyed by Card ID.
- Store card files directly on the item.

Item-backed files were rejected because card programs may create many or large files, and rewriting bulky item data on every card write would be awkward. World-backed storage also matches ComputerCraft's style of persistent computer and disk file spaces.
