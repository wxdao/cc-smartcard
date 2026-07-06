# Single Card Command Entrypoint

Card programs receive reader commands through a single fixed entrypoint, `handle(command, args)`, rather than by exporting multiple named functions. This matches the reader's intentionally narrow interface and the smart-card style of command dispatch, while making permissions, auditing, error codes, and invocation limits easier to enforce consistently.

## Considered Options

- Single fixed entrypoint: `handle(command, args)`.
- Multiple named functions exported by each card program.

Multiple named functions were rejected because they make the reader-card boundary wider and less predictable, which weakens centralized control over permissions, auditing, error reporting, and invocation limits.
