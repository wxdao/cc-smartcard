# Reader Card Events

Card Readers queue ComputerCraft events when a card is inserted or removed, allowing connected computers to react without polling. Events follow the shape of ComputerCraft's disk events: the event includes the reader identity, and programs can query public card metadata through the reader API.

The event shapes are `smart_card_inserted(readerName)` and `smart_card_removed(readerName)`.

Polling-only readers were rejected because common uses such as ticket gates, delivery machines, and payment terminals should react immediately when a player presents or removes a card.
