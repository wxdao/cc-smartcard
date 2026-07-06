# Card Storage File Space

Card Storage is one private file space owned by the smart card, rather than a structured table passed into card commands or separate read-only/read-write partitions. This keeps storage close to ComputerCraft's disk model: issuing copies files into the card, card programs can read and write their own files, and readers and computers still cannot inspect the card's contents directly.

If a card program overwrites its own entry file or support files, the result is undefined behavior. The mod does not try to preserve a read-only program partition inside the card.

## Considered Options

- A private card file space.
- Separate read-only program files and writable storage files.
- A structured storage table passed to the card program.

The table model was rejected for now because it is more abstract than the mod needs, while a file space gives players a familiar ComputerCraft-shaped model for program state. Separate read-only and writable partitions were rejected because the extra structure is not worth it for the initial gameplay model.
