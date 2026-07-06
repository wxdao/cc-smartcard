# CC SmartCard

This context defines the core language for player-held smart cards, their issued programs, and card-specific private file space.

## Language

**Smart Card**:
A card that a player can hold. It has a public Card ID and a private card storage.

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

**Issuing**:
The act of choosing a card program for a blank smart card and locking it in as that card's program.
_Avoid_: Burning

**Card Command**:
A single named operation request from a card reader to a smart card that has been placed in it. The card's program decides the response.
_Avoid_: Reader Invocation

**Player Identity**:
The stable UUID string used by identity devices to identify a Minecraft player; the current player name is display metadata only.
_Avoid_: Player Name as Identity
