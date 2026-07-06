# Command-Scoped Card Execution

Each card command loads the card's `/main.lua`, runs the command entrypoint, writes any file changes, returns a result, and then discards the Lua runtime state. Persistent card state lives only in the card's private file space.

## Considered Options

- Reload the card program for every command.
- Keep a long-lived Lua runtime while the card is inserted.

Long-lived runtimes were rejected because they make unloads, reader removal, server restarts, concurrent calls, and block ticking much harder to reason about. Command-scoped execution is simpler and matches the card-as-file-space model.
