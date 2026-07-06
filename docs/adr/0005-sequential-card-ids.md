# Sequential Card IDs

Smart cards receive a numeric Card ID when they first need a persistent identity, following the same style as ComputerCraft's computer and disk IDs. A sequential number is easier for players to read, type, compare, and use in Lua programs than a UUID, while still giving each card a stable identity for normal gameplay.

Calling the reader's `getCardId()` on an inserted card may assign an ID to a previously unassigned blank card. Issuing also assigns an ID if the card does not already have one. A card may therefore have a Card ID before it is issued; issued state remains a separate concept.

## Considered Options

- Numeric Card IDs assigned by the mod.
- UUID-style Card IDs.
- IDs chosen by the issuing program.

UUIDs were rejected as unnecessarily awkward for the intended ComputerCraft-style gameplay, and issuer-chosen IDs were rejected because they make accidental or deliberate duplicate identities too easy.
