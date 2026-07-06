# Opaque Card Contents

Smart card contents are opaque after issuing: the reader may send card commands and read the public Card ID, but it must not expose the card program or card storage as a readable filesystem or raw data API. This preserves gameplay for credentials such as bank cards, tickets, and delivery tokens, where copying the visible contents would let a player clone the card and bypass the intended trust model.

## Considered Options

- Keep card contents opaque and interact only through card commands.
- Mount the card contents like a ComputerCraft disk.
- Expose read-only inspection of the card program.

Disk-style mounting and read-only inspection were rejected because even read access can reveal credentials or secrets embedded in a card program, making duplicate cards trivial to create through normal gameplay scripting.
