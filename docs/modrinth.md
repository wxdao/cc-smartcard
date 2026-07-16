# CC SmartCard

![A Fingerprint Scanner, CC: Tweaked computer, and Smart Card Reader with an inserted card](https://raw.githubusercontent.com/wxdao/cc-smartcard/main/docs/images/modrinth-hero.png)

**Programmable Smart Cards, identity scanners, passage sensors, and access gates for CC: Tweaked.**

CC SmartCard adds player-held cards that can run small Lua programs from private, world-backed storage. Issue a blank card once, insert it into any reader, and call its program through a narrow command interface.

It is designed for ComputerCraft-style access cards, identity checks, tickets, tokens, banking systems, and other builds that need more than a plain item ID.

## Smart Cards

![The CC SmartCard creative tab showing white and dyed Smart Cards](https://raw.githubusercontent.com/wxdao/cc-smartcard/main/docs/images/smart-card-colours.png)

- Give each card a public ID and optional label.
- Dye cards without changing their identity, program, or stored files.
- Issue a card from one Lua source string or a directory of files.
- Keep the issued program and its storage private from the normal reader API.
- Run card programs independently from the calling computer.

Issued cards cannot be erased or reissued through the supported reader API.

## Smart Card Reader

Place the reader beside a CC: Tweaked computer or connect it through a wired modem. Insert or remove a card by right-clicking any face of the reader.

The `smart_card_reader` peripheral can inspect public card metadata, issue a blank card, call commands on an issued card, and emit insertion or removal events.

```lua
local reader = peripheral.find("smart_card_reader")
assert(reader, "No Smart Card Reader found")

local message, cardId = reader.call("ping")
print(message, cardId)
```

## Fingerprint Scanner

![A Fingerprint Scanner before and after a successful player scan](https://raw.githubusercontent.com/wxdao/cc-smartcard/main/docs/images/fingerprint-scanner-states.png)

The `fingerprint_scanner` peripheral waits for a player to right-click the scanner, briefly lights its scanned face, and returns the player's stable UUID together with their current profile name.

```lua
local scanner = peripheral.find("fingerprint_scanner")
local player = assert(scanner.scan())
print(player.name, player.uuid)
```

## Access Gates and Passage Sensors

![An automated two-gate passage closing the entrance, waiting for approval, opening the exit, and resetting](https://raw.githubusercontent.com/wxdao/cc-smartcard/main/docs/images/access-gate-demo.gif)

Build automated border-control lanes, secure vestibules, station barriers, or any other two-stage passage. The demonstration uses two independent Access Gates and one Passage Sensor: the entrance starts open, closes after one player enters, the inside computer authorises the exit, and the lane resets after the player leaves.

Place two Gate Cabinets facing each other across a one- or two-block gap to form one animated `access_gate` peripheral. Both cabinets control the same logical gate.

- Glass leaves open or close asynchronously over 10 game ticks.
- Obstructed leaves automatically reverse and reopen.
- Cabinet lights show red for closed, amber for movement or faults, and green for open.
- Gates ignore redstone and cannot be moved by direct player interaction.
- When the final attached computer disconnects, the gate waits one second and fails open.
- Gate state changes and obstructions are available as ComputerCraft events.

Two gates remain independent: your Lua program decides how authentication works and whether only one gate may be open. This makes the blocks useful beyond a single hard-coded border-control workflow.

Mount a `passage_sensor` above the controlled space to receive anonymous observations for players, creatures, vehicles, items, and other entities. Its detection cuboid is configurable from Lua, up to 256 blocks within eight blocks of the sensor. Programs can query a snapshot with `getEntities()` or react to entry, exit, and reset events. Lua chooses what counts as a traveller; the sensor never exposes player UUIDs, names, equipment, inventories, or NBT.

## How Cards Work

1. Craft a Smart Card and Smart Card Reader.
2. Connect the reader to a CC: Tweaked computer.
3. Insert a blank card and issue its Lua program once.
4. Insert the issued card into other readers and call commands such as `authorize`, `validate`, `claim`, or `redeem`.

Card programs receive their own file storage and may use familiar ComputerCraft APIs. Secure random bytes are also available inside the card runtime for programs that need nonces or secret material.

## Build Ideas

- Bank cards and shop terminals
- Room keys and access badges
- Train tickets and event passes
- Parcel pickup tokens
- Loyalty and coupon cards
- Two-factor systems combining a Smart Card with a live fingerprint scan
- Automated border-control lanes and two-gate interlocked passages
- Prison, bank, warehouse, and station access gates

## Crafting

![The crafting recipes for the Smart Card, Smart Card Reader, and Fingerprint Scanner](https://raw.githubusercontent.com/wxdao/cc-smartcard/main/docs/images/crafting-recipes.png)

The image covers the original card and identity blocks. Gate Cabinet and Passage Sensor recipes are included in the text reference below.

<details>
<summary><strong>Text recipe reference</strong></summary>

### Smart Card

Shapeless: Paper, Redstone Dust, Copper Ingot, and optional dyes.

### Smart Card Reader

```text
###
#R#
#C#
```

- `#` = Stone
- `R` = Redstone Dust
- `C` = Copper Ingot

### Fingerprint Scanner

```text
SCS
RGR
SCS
```

- `S` = Stone
- `C` = Copper Ingot
- `R` = Redstone Dust
- `G` = Glass Pane

### Gate Cabinets (makes 2)

```text
SGS
PRP
SCS
```

- `S` = Stone
- `G` = Glass Pane
- `P` = Piston
- `R` = Redstone Dust
- `C` = Copper Ingot

### Passage Sensor

```text
CGC
ROR
SSS
```

- `S` = Stone
- `C` = Copper Ingot
- `G` = Glass Pane
- `R` = Redstone Dust
- `O` = Observer

</details>

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.235 or newer in the 21.1 line
- CC: Tweaked 1.120.0 or newer
- Java 21

Install CC SmartCard on the server. Multiplayer clients should also install it so its blocks, items, models, and translations are available.

## Documentation

The [full README and API reference](https://github.com/wxdao/cc-smartcard#readme) covers issuing, reader events, every peripheral method, the card runtime API, file layout, and complete examples.

[Source code](https://github.com/wxdao/cc-smartcard) · [Issue tracker](https://github.com/wxdao/cc-smartcard/issues)

Card privacy is a gameplay boundary, not formal protection against server administrators or other out-of-game access.
