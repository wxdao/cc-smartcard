# Access Gates and Passage Sensor

This document describes the implemented design for assembling generic access-control components into a two-gate automated border-control passage.

## Blocks

The feature adds two player-facing blocks:

- **Gate Cabinet** (`gate_cabinet`): a placeable side cabinet. Two opposing cabinets automatically pair across one or two clear passage blocks to form one Access Gate. Its crafting recipe produces two cabinets.
- **Passage Sensor** (`passage_sensor`): a ceiling-mounted peripheral which observes a configurable Detection Area. Its crafting recipe produces one sensor.

One internal block is also required:

- **Gate Barrier Part** (`gate_barrier`): an automatically managed glass-leaf segment with no item, recipe, pick result, or independent drop.

The facility reuses the existing Smart Card Reader, Fingerprint Scanner, CC computer, and optional CC monitor. It does not add a passport item or reader, face scanner, lane indicator, emergency-release block, or dedicated passage controller.

## Assembly

An Access Gate consists of two equal Gate Cabinets facing one another. Cabinets pair automatically only when there is a unique opposing cabinet and the clear width is one or two blocks.

```text
One-block gate: [cabinet][passage][cabinet]
Two-block gate: [cabinet][passage][passage][cabinet]
```

A typical Interlocked Passage uses four cabinets and one sensor:

```text
[entrance gate] [monitored passage] [exit gate]
```

The two Access Gates remain independent actuators. The facility's Lua program, not gate hardware, enforces the rule that at most one gate should be open.

## Access Gate Behaviour

- Both cabinets are equal access points to one shared gate state. A computer may connect through either side.
- The duplicated state stored by both cabinets carries a persisted modification version. If one cabinet's chunk runs
  alone, the two copies reconcile to the newer state when both chunks load again; an equal-version conflict resolves
  toward open in accordance with Fail-Open.
- The Gate ID is an opaque deterministic value derived from the dimension and paired cabinet positions.
- Newly assembled gates default to open.
- A complete open or close movement lasts 10 game ticks. An opposite command reverses from the current progress; a repeated command is idempotent and commands are never queued.
- The barrier looks like a relatively low glass wing but uses a 1.5-block collision height to prevent a normal jump over it.
- Closing leaves automatically reverse to fully open when obstructed by a player, creature, armour stand, or vehicle. Dropped items, experience orbs, and projectiles do not count as Gate Obstructions.
- Barrier parts cannot be obtained or broken independently. Breaking either cabinet removes the managed barrier parts and leaves the other cabinet unpaired and physically open.
- Gate Cabinets ignore redstone input. Right-click may show diagnostics but cannot command movement.
- Cabinet lights automatically show mechanical state: red for closed, amber for movement or faults, and green for open. Business prompts belong on a CC monitor.

Gate State values are:

- `closed`
- `opening`
- `open`
- `closing`
- `obstructed`
- `unpaired`

The `access_gate` peripheral exposes the shared gate through either cabinet. Its minimum API is:

- `getGateId()`
- `getState()`
- `getWidth()`
- `open()`
- `close()`

Movement commands return immediately after acceptance. Computers observe completion with `getState()` and `access_gate_state_changed(gateId, oldState, newState)`. Obstruction also queues `access_gate_obstructed(gateId)`.
The `obstructed` state is latched for two server ticks so it can be queried with `getState()` while the leaves are
already reversing. A computer attached to both cabinets receives each gate event once, deduplicated by its
ComputerCraft computer ID; distinct computers each receive the event.

## Fail-Open Lifecycle

When the final CC computer disconnects from both cabinets, the gate waits 20 game ticks. If no computer has reattached, its target changes to open. A restored gate also preserves its loaded state for 20 ticks before applying the same check, avoiding open-close flicker during chunk and peripheral remount.

Fail-Open deliberately prioritises escape and recovery over containment. A controller or network failure may open both gates in an Interlocked Passage.

## Passage Sensor Behaviour

- A new sensor defaults to the `1 x 3 x 1` column directly below it.
- Lua may configure an axis-aligned cuboid relative to the sensor. Each boundary may be at most eight blocks from the sensor and the total Detection Area may be at most 256 block volumes.
- The sensor scans every game tick while at least one CC computer is attached. It stops continuous detection when the final computer detaches.
- Every Minecraft entity whose bounds intersect the Detection Area is observed except spectator players. This includes players, creatures, vehicles, armour stands, projectiles, dropped items, and experience orbs.
- The sensor never reveals player UUIDs, names, or persistent entity IDs.

The `passage_sensor` peripheral exposes:

- `getArea()`
- `setArea(minimum, maximum)`
- `getEntities()`

An Entity Observation has this shape:

```lua
{
    token = "sensor-scoped-temporary-token",
    type = "minecraft:player",
    category = "player",
    position = { x = 0.0, y = -2.0, z = 1.0 },
    velocity = { x = 0.0, y = 0.0, z = 0.1 },
    size = { width = 0.6, height = 1.8 }
}
```

Observations exclude health, custom names, teams, equipment, inventories, and NBT. Lua programs filter observations by `type` or `category` according to their own policy.

The sensor queues:

- `passage_entity_entered(sensorName, observation)`
- `passage_entity_left(sensorName, token)`
- `passage_sensor_reset(sensorName)`

A newly attached computer receives entered events for entities already present. `getEntities()` is the authoritative reconciliation snapshot.

Changing the Detection Area or ending continuous detection resets the observation session. All old Observation Tokens become invalid; the next active session assigns new tokens and reports its current entities as entered.

## Example Border-Control Build

A functional lane uses:

- four Gate Cabinets, forming entrance and exit Access Gates;
- one Passage Sensor monitoring the space between them;
- one existing Smart Card Reader, with a Smart Card program acting as the travel credential;
- one existing Fingerprint Scanner for deliberate live Player Identity;
- one CC computer running authentication and interlock logic;
- optionally, one or more CC monitors for instructions and results.

The block recipes and vanilla-texture models ship with the implementation. Facility-specific Lua interlock and authentication programs remain the builder's responsibility.
