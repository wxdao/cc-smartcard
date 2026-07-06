# Fingerprint Scanner Scan API

Fingerprint Scanners expose a blocking `scan()` peripheral method instead of queueing a ComputerCraft event. ComputerCraft events may already be queued before a program calls `os.pullEvent`, which makes event-only identity checks easy to misuse when the program needs a fresh player interaction. `scan()` means "wait from now for the next player scan on this scanner", while visual feedback remains a world-level block state.

`scan()` returns `playerUuid, playerName` for the next scan after the call begins. It has no default timeout and the scanner has no persistent "scanning" state, so Lua programs may safely abandon a call with `parallel.waitForAny`. If the scanner is destroyed or the attached computer stops before a scan arrives, the pending call fails instead of returning a fake identity.

## Considered Options

- Queue a `fingerprint_scan(readerName, playerUuid, playerName)` event like other ComputerCraft peripherals.
- Expose only `scan()` and avoid a public fingerprint event.

The event-only design was rejected because it could accidentally authenticate a stale queued scan. A method-only design is less event-like, but it gives Lua programs a clearer authentication boundary.
