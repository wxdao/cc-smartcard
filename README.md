# CC SmartCard

CC SmartCard is a CC: Tweaked add-on for Minecraft 1.21.1 on NeoForge. It adds programmable Smart Cards, identity peripherals, and computer-controlled access gates for ComputerCraft-style credentials, identity checks, secure passages, and small sealed programs.

The core idea is simple: place a blank card in a reader, issue a Lua program onto it once, and then call that program through the reader. Card files live in a world-backed private file system. Computers can call the card, but they cannot browse or copy the card's contents through the reader API.

## Features

- Smart Card item with public card ID, optional label, and issued/blank state.
- Dyeable Smart Cards with a coloured card body and gold contact chip.
- Smart Card Reader block and CC: Tweaked peripheral.
- Fingerprint Scanner block and CC: Tweaked peripheral for player identity scans.
- Paired Gate Cabinet blocks with animated glass leaves and an `access_gate` peripheral.
- Passage Sensor peripheral with configurable anonymous entity detection.
- Insert a Smart Card by right-clicking any face of the reader with the card. Remove it by right-clicking the reader again.
- Cards can be issued from a single Lua source string or from a table of files.
- Issued cards cannot be reissued through the reader API.
- Card programs run in their own ComputerCraft-style runtime, separate from the calling computer.
- Card programs have their own world-backed file system storage.
- Card programs may use familiar ComputerCraft APIs, including the HTTP API when enabled by the server's CC: Tweaked configuration.
- Card programs can request secure random bytes from their private runtime.
- Card insertion/removal follows ComputerCraft-style peripheral event behavior.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.235 or newer in the 21.1 line
- CC: Tweaked 1.120.0 or newer
- Java 21

Install CC SmartCard on the server. For multiplayer, clients should also install it so blocks, items, models, and translations are available.

## Crafting

### Smart Card

Shapeless recipe:

- Paper
- Redstone Dust
- Copper Ingot
- Optional dye ingredients for a coloured card

Crafting with no dye produces the default white Smart Card. Crafting with one or more dyes produces a dyed Smart Card, mixing multiple dyes in the same style as CC: Tweaked disks.

Existing blank or issued Smart Cards can also be dyed with any dye through CC: Tweaked's dyeable item recipe. Craft a dyed Smart Card with a Wet Sponge to clear the colour. Dyeing or clearing a card keeps its card ID, label, issued state, and private card storage.

### Smart Card Reader

Shaped recipe:

```text
###
#R#
#C#
```

Where:

- `#` = Stone
- `R` = Redstone Dust, using the `c:dusts/redstone` tag
- `C` = Copper Ingot

The recipe is also visible through the recipe book or recipe viewer mods such as JEI/EMI when available.

### Fingerprint Scanner

Shaped recipe:

```text
SCS
RGR
SCS
```

Where:

- `S` = Stone
- `C` = Copper Ingot
- `R` = Redstone Dust, using the `c:dusts/redstone` tag
- `G` = Glass Pane

### Gate Cabinets

Shaped recipe producing two cabinets:

```text
SGS
PRP
SCS
```

Where:

- `S` = Stone
- `G` = Glass Pane
- `P` = Piston
- `R` = Redstone Dust, using the `c:dusts/redstone` tag
- `C` = Copper Ingot

### Passage Sensor

Shaped recipe:

```text
CGC
ROR
SSS
```

Where:

- `S` = Stone
- `C` = Copper Ingot
- `G` = Glass Pane
- `R` = Redstone Dust, using the `c:dusts/redstone` tag
- `O` = Observer

## Quick Start

1. Craft a Smart Card and a Smart Card Reader.
2. Place the reader next to a CC: Tweaked computer or connect it through a wired modem.
3. Insert a blank Smart Card into any face of the reader.
4. Wrap the reader as a peripheral.
5. Issue a card program.
6. Call commands on the issued card.

Minimal issuing example from a computer attached to the reader:

```lua
local reader = peripheral.find("smart_card_reader")
assert(reader, "No Smart Card Reader found")

local ok, err = reader.issueSource([[
function handle(command, args, context)
    if command == "ping" then
        return true, "pong", context.cardId
    end

    return nil, "unknown_command"
end
]])

assert(ok, err)

local success, message, cardId = reader.call("ping")
print(success, message, cardId)
```

CC SmartCard also includes a small ROM helper module, loadable with `require("smartcard")`, for issuing from local files or directories. You may use the reader API directly or use the helper when packaging a card program from files on a computer.

```lua
local smartcard = require("smartcard")
local reader = peripheral.find("smart_card_reader")
assert(reader, "No Smart Card Reader found")

-- Issue one local file as /main.lua on the card.
assert(smartcard.issueFromFile(reader, "cards/door_token.lua"))

-- Or issue a local directory tree. The directory must contain main.lua.
-- assert(smartcard.issueFromDir(reader, "cards/bank_card"))
```

The `smartcard` module is intended for computer-side issuing through a reader. It does not expose card runtime APIs and does not add reader peripheral methods.

## Fingerprint Scanner

The Fingerprint Scanner is a separate CC: Tweaked peripheral for systems that need a live player scan instead of only an item token. Place it next to a computer or connect it with a wired modem, then wrap the peripheral type `fingerprint_scanner`.

When a computer calls `scan()`, the Lua coroutine waits until a player right-clicks the scanner. The block briefly lights the face that was scanned and returns a table containing the scanned player's UUID and current game profile name.

```lua
local scanner = peripheral.find("fingerprint_scanner")
assert(scanner, "No Fingerprint Scanner found")

print("Waiting for scan...")
local scan, err = scanner.scan()
assert(scan, err)

print(("Scanned %s (%s)"):format(scan.name, scan.uuid))
```

If the scanner is removed or the computer detaches while a scan is pending, the pending scan is cancelled and `scan()` returns `nil, "scan cancelled"`.

## Access Gates and Passage Sensors

![An automated two-gate passage closing the entrance, waiting for approval, opening the exit, and resetting](docs/images/access-gate-demo.gif)

The demonstration uses two independent Access Gates and one Passage Sensor. The facility program starts with the
entrance open and exit closed, closes the entrance after one player enters, waits for approval at the computer, opens
the exit, and finally closes the exit and reopens the entrance after the passage is empty. This interlock policy lives
in Lua, so builders can replace it with their own authentication and traffic rules.

Place two Gate Cabinets facing each other with a one- or two-block clear gap. They pair automatically into one Access Gate. Both cabinets expose the same shared gate state, although they may have different ComputerCraft attachment names.

```text
[cabinet][one- or two-block passage][cabinet]
```

An Access Gate opens and closes over 10 game ticks. Its glass leaves reverse and reopen if a player, creature, armour stand, or vehicle obstructs them. Gate Cabinets ignore redstone and direct player interaction cannot move them.

The `obstructed` state remains queryable for two server ticks while reversal starts. If the same ComputerCraft computer
is attached through both cabinets, shared gate events are delivered once to that computer; different computers each
receive the event.

Gates fail open: when the final attached computer disconnects, the gate waits 20 game ticks and then opens unless a computer reconnects. Two gates do not enforce a hardware interlock; a facility program must coordinate them.
The cabinets persist versioned copies of their shared state, so a newer command or fail-open transition made while the
other cabinet's chunk is unloaded wins when the pair is loaded together again.

Mount a Passage Sensor on the ceiling between two gates. It reports anonymous observations of entities in a configurable area. It never returns player UUIDs, names, inventories, equipment, or NBT. See [the full assembly and behaviour design](docs/access-gates.md) for the complete model.

## Use Case Ideas

Smart Cards are useful when you want an in-world item to carry a private program and a little private storage, while exposing only a command interface to other computers.

- Bank cards: issue a card with an account ID and private verification logic. An ATM or shop terminal can call `reader.call("authorize", { amount = 25, nonce = "..." })`, and the card can return a signed or otherwise validated response. Other players can read the public card ID, but they cannot clone the card through the supported reader API because the card program and stored secret are not exposed.
- Parcel pickup tokens: a warehouse computer can call `reader.call("claim", parcelId)` and let the card decide whether that parcel may be released.
- Train tickets or event passes: gates can call `reader.call("validate", stationOrVenue)` and optionally let the card mark itself as used by writing to its own storage.
- Room keys and access badges: doors can call `reader.call("access", doorId)` and open only when the card program accepts the requested door.
- Loyalty or coupon cards: shops can call `reader.call("stamp", shopId)` or `reader.call("redeem", offerId)` while the card tracks its own points or redemption state.

For gameplay systems like banking, the usual pattern is to keep the issuer's source code and secrets private, issue each card once, and make readers call narrow commands such as `authorize`, `debit`, `validate`, or `claim`. Avoid adding a command which simply returns the card's secret data.

## Reader API

The Smart Card Reader peripheral type is `smart_card_reader`.

### Reader Events

Card Readers queue ComputerCraft events when a Smart Card is inserted or removed. The event shape follows ComputerCraft's disk events: the first argument is the reader's attachment name, and programs can query public card metadata through the reader API.

```lua
local event, readerName = os.pullEvent()

if event == "smart_card_inserted" then
    local reader = peripheral.wrap(readerName)
    print("Inserted card", reader.getCardId())
elseif event == "smart_card_removed" then
    print("Removed card from", readerName)
end
```

Events:

- `smart_card_inserted(readerName)`: queued when a card is inserted. If a computer attaches while a card is already present, it also receives this event.
- `smart_card_removed(readerName)`: queued when a card is removed or dropped because the reader block was destroyed.

### `hasCard()`

Returns `true` when a Smart Card is inserted, otherwise `false`.

### `getCardId()`

Returns the public numeric card ID for the inserted card, or `nil` if no card is inserted. A blank card may receive an ID the first time one is needed.

### `getLabel()`

Returns the card label, or `nil` if no card is inserted or no label is set.

### `setLabel(label)`

Sets the public card label. Returns:

```lua
true
```

On failure, returns:

```lua
nil, errorCode[, detail]
```

### `isIssued()`

Returns `true` for an issued card, `false` for a blank inserted card, or `nil` if no card is inserted.

### `issueSource(source)`

Issues a blank card using `source` as `/main.lua`.

On success:

```lua
true
```

On failure:

```lua
nil, errorCode[, detail]
```

Common error codes include `no_card`, `already_issued`, `bad_files`, `no_server`, and `io_error`.

### `issueFiles(files)`

Issues a blank card from a table of file contents. Keys are absolute card paths and values are text contents.

```lua
reader.issueFiles({
    ["/main.lua"] = "...",
    ["/lib/util.lua"] = "..."
})
```

Rules:

- The table must include `/main.lua`.
- Paths must start with `/`.
- Paths must not contain `..`.
- Directory entries are not represented.
- File contents must be strings.

### `call(command[, args])`

Runs the issued card's `/main.lua`, finds `handle`, and calls:

```lua
handle(command, args, context)
```

Return values from `handle` are returned to the calling computer using normal Lua multiple return values. Reader-level failures return:

```lua
nil, errorCode[, detail]
```

Common error codes include `no_card`, `not_issued`, `no_card_id`, `missing_main`, `cancelled`, and `runtime_error`.

## Card Runtime API

Card programs also receive a global `cc_smartcard` table while they run inside the smart card runtime. This API is only available to issued card code. It is not a normal computer helper module, not a Smart Card Reader peripheral method, and not part of `require("smartcard")`.

### `cc_smartcard.randomBytes(n)`

Returns `n` secure random bytes as a Lua binary string. The bytes come from the server JVM's secure random source.

```lua
function handle(command, args, context)
    if command == "nonce" then
        return cc_smartcard.randomBytes(32)
    end
end
```

Rules:

- `n` must be an integer from `0` to `4096`.
- `0` returns an empty binary string.
- Invalid values raise a Lua argument error containing `n must be an integer between 0 and 4096 bytes`.
- The returned string may contain any byte, including NUL. Use binary-safe Lua string operations such as `#bytes`, `bytes:byte(i)`, and `string.pack`/`string.unpack`.

## Fingerprint Scanner API

The Fingerprint Scanner peripheral type is `fingerprint_scanner`.

### `scan()`

Waits until a player right-clicks the scanner.

On success, returns:

```lua
{
  uuid = playerUuid,
  name = playerName,
}
```

`playerUuid` is the scanned player's UUID string. `playerName` is the player's current game profile name.

If the scanner is broken, replaced, or detached before a player scans it, the pending call is cancelled and returns `nil, "scan cancelled"`.

## Access Gate API

Each paired Gate Cabinet exposes the peripheral type `access_gate`.

### `getGateId()`

Returns the shared opaque Gate ID, or `nil` while the cabinet is unpaired.

### `getState()`

Returns one of `closed`, `opening`, `open`, `closing`, `obstructed`, or `unpaired`.

### `getWidth()`

Returns the clear passage width, either `1` or `2`, or `0` while unpaired.

### `open()` and `close()`

Accept an asynchronous movement command and return `true`. An unpaired cabinet returns `nil, "unpaired"`. Repeating the current target is idempotent, while the opposite command reverses movement from its current position.

Events:

- `access_gate_state_changed(gateId, oldState, newState)`
- `access_gate_obstructed(gateId)`

## Passage Sensor API

The Passage Sensor peripheral type is `passage_sensor`.

### `getArea()`

Returns `{ minimum = { x, y, z }, maximum = { x, y, z } }` using block offsets relative to the sensor.

### `setArea(minimum, maximum)`

Sets an inclusive, axis-aligned Detection Area. Each boundary coordinate must be between `-8` and `8`, and total volume must not exceed 256 blocks. Changing the area resets all previous observation tokens.

### `getEntities()`

Returns the current anonymous Entity Observations. Each observation contains:

```lua
{
  token = "temporary-sensor-token",
  type = "minecraft:player",
  category = "player",
  position = { x = 0, y = -2, z = 1 },
  velocity = { x = 0, y = 0, z = 0.1 },
  size = { width = 0.6, height = 1.8 },
}
```

The sensor observes all entities except spectator players and scans every game tick while at least one computer is attached.

Events:

- `passage_entity_entered(sensorName, observation)`
- `passage_entity_left(sensorName, token)`
- `passage_sensor_reset(sensorName)`

## Card Program Layout

A card program is a small file tree stored on the card. The entry file is always:

```text
/main.lua
```

When using `issueSource`, the source string becomes `/main.lua`. When using `issueFiles`, the file table is copied into the card's private storage.

`/main.lua` must provide a `handle(command, args, context)` function. The handler can return any ComputerCraft-serializable Lua values that the runtime supports.

The `context` table includes public call context such as the card's ID and label. Treat it as metadata for gameplay logic, not as a secret.

Card programs run independently from the calling computer. They do not inherit the caller's filesystem, shell session, or peripherals.

## Example: Simple Door Token

Issue this source onto a blank card:

```lua
function handle(command, args, context)
    if command == "check" then
        return args == "front_door", context.cardId
    end

    return nil, "unknown_command"
end
```

Then call it from a computer connected to the reader:

```lua
local reader = peripheral.find("smart_card_reader")
local allowed, cardId = reader.call("check", "front_door")

if allowed then
    print("Access granted for card " .. cardId)
else
    print("Access denied")
end
```

## Known Limitations

- Issuing is one-way through the reader API. There is no supported reissue or erase flow.
- Card programs are non-transactional. If a program writes files and then errors, earlier writes may remain.
- Card programs use CC: Tweaked internals for their independent runtime. Future CC: Tweaked updates may require compatibility work.
- The current release targets Minecraft 1.21.1, NeoForge 21.1.235, and CC: Tweaked 1.120.0.
- The reader API intentionally does not expose raw card file contents.
