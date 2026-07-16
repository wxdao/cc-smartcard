# CC SmartCard

This context defines the core language for player-held smart cards, their issued programs, and card-specific private file space.

## Language

**Smart Card**:
A generic card that a player can hold. It has a public Card ID and private card storage, while its issued program may give it a credential role such as a travel document without creating a separate kind of card.
_Avoid_: Passport Card, Visa Card

**Card ID**:
A public number assigned to a smart card when it first needs a persistent identity.

**Card Label**:
A public name assigned to a smart card by a player or program.

**Card Color**:
A public visual appearance assigned to a smart card. It may be absent, in which case the card uses its default appearance, and it does not change the card's identity, storage, or program.
_Avoid_: Card Type, Card Tier

**Card Program**:
A card's issued behavior, chosen when the card is issued. It determines how the card responds to card commands.

**Card Storage**:
A private file space identified by a Card ID. It contains the issued card files and any later files kept by the card.

**Card Reader**:
The in-world device that holds a smart card for issuing and card commands.
_Avoid_: Card Writer, Card Issuer

**Fingerprint Scanner**:
The in-world identity device that reports a player's Player Identity when that player scans it.
_Avoid_: Fingerprint Reader, Identity Reader

**Fingerprint Scan**:
An interaction with a fingerprint scanner that completes a pending `scan()` call with the player's Player Identity.
_Avoid_: Player Click, Touch Event

**Gate Cabinet**:
One of two opposing side units that together form an Access Gate. A cabinet is not a complete gate on its own.
_Avoid_: Gate Block, Left Gate, Right Gate

**Access Gate**:
A pair of opposing Gate Cabinets and their barrier leaves that permits or blocks passage. The same kind of gate may serve at either end of a controlled passage.
_Avoid_: A Gate, B Gate, Border Gate

**Gate ID**:
A public, opaque identifier determined by an Access Gate's location. The same pair of cabinet positions retains the same Gate ID across reloads and replacement, while a gate assembled elsewhere has a different identity.
_Avoid_: Gate Name, Cabinet ID

**Gate State**:
The current physical phase or exceptional condition of an Access Gate, such as closed, moving, open, obstructed, or unpaired.
_Avoid_: Open Flag, Door Status

**Gate Obstruction**:
A physical subject or vehicle intersecting the path of an Access Gate's barrier leaves during closure. Dropped items and projectiles are not obstructions; this concept concerns safe barrier movement, not general occupancy of the Interlocked Passage.
_Avoid_: Passage Occupancy, Tailgating

**Fail-Open**:
The Access Gate safety policy that opens a gate after its final controlling CC computer disconnects.
_Avoid_: Fail-Safe, Fail-Secure

**Interlocked Passage**:
A controlled space bounded by two Access Gates whose operation is coordinated so that passage can be admitted, contained, and released in stages.
_Avoid_: AB Door, Airlock

**Passage Sensor**:
An in-world device that reports Entity Observations within a defined passage area without controlling its Access Gates.
_Avoid_: Gate Sensor, Presence Scanner

**Entity Observation**:
An anonymous, temporary description of an entity currently detected by a Passage Sensor. It contains only the entity's kind and sensor-relative geometry or motion, never identity, status, equipment, inventory, or other gameplay metadata.
_Avoid_: Player Record, Entity Identity

**Observation Token**:
A temporary, sensor-scoped value that correlates one detected entity across snapshots and entry or exit changes while that entity remains in the Detection Area. It becomes invalid when the entity leaves or the sensor resets its observation session.
_Avoid_: Entity ID, Player ID

**Detection Area**:
The bounded space observed by a Passage Sensor.
_Avoid_: Scan Range, Sensor Zone

**Issuing**:
The act of choosing a card program for a blank smart card and locking it in as that card's program.
_Avoid_: Burning

**Card Command**:
A single named operation request from a card reader to a smart card that has been placed in it. The card's program decides the response.
_Avoid_: Reader Invocation

**Card Runtime Entropy**:
Secure random bytes available only to a card program while it runs inside the smart card runtime.
_Avoid_: Computer Random Service, Passport Crypto Helper

**Player Identity**:
The stable UUID string used by identity devices to identify a Minecraft player; the current player name is display metadata only.
_Avoid_: Player Name as Identity
