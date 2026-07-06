# Non-Transactional Card Commands

Card commands are not transactional. If a card program times out, crashes, or is otherwise stopped, the reader returns an error for that command, but file changes already made by the card program are not guaranteed to be rolled back.

This matches the ComputerCraft-style execution model and avoids building a separate transaction layer around the card file space.
