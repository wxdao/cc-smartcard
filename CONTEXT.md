# CC SmartCard

This context defines the core language for player-held smart cards, their issued programs, and card-specific private file space.

## Language

**Smart Card**:
A card that a player can hold. It has a public Card ID and a private card storage.

**Card ID**:
A public number assigned to a smart card when it first needs a persistent identity.

**Card Label**:
A public name assigned to a smart card by a player or program.

**Card Program**:
A card's issued behavior, chosen when the card is issued. It determines how the card responds to card commands.

**Card Storage**:
A private file space identified by a Card ID. It contains the issued card files and any later files kept by the card.

**Card Reader**:
The in-world device that holds a smart card for issuing and card commands.
_Avoid_: Card Writer, Card Issuer

**Issuing**:
The act of choosing a card program for a blank smart card and locking it in as that card's program.
_Avoid_: Burning

**Card Command**:
A single named operation request from a card reader to a smart card that has been placed in it. The card's program decides the response.
_Avoid_: Reader Invocation
