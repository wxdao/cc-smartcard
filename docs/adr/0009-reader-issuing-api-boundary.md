# Reader Issuing API Boundary

The reader's peripheral API accepts either a single source string or an explicit table of file contents when issuing a card. Directory paths are supported by a bundled ComputerCraft Lua helper, which reads the caller's local filesystem and sends the resulting file table to the reader.

The bundled helper is part of the initial feature set, so normal players can issue cards from local files or directories without writing their own packer. It is exposed as a ComputerCraft ROM module, allowing programs to load it with `require("smartcard")`.

`issueFiles(files)` uses a flat table whose keys are absolute card paths and whose values are text file contents. The table must contain `/main.lua`; paths must start with `/`, must not contain `..`, and directory entries are not represented.

## Considered Options

- Reader accepts source strings and file tables; Lua helper supports local paths.
- Reader accepts local directory paths directly.

Direct path support was rejected because a peripheral method receives a string, not access to the calling computer's filesystem. Keeping path traversal in Lua makes the user experience convenient without confusing the reader's boundary.
