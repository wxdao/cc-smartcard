# Independent Card Runtime

Card programs run in their own independent ComputerCraft-style runtime, not inside the calling computer. They may use normal computer-like APIs provided by that runtime, but their filesystem and execution state belong to the card, and they do not inherit the caller's files, peripherals, shell, or session.

The command context starts with only public card metadata such as Card ID and label. It does not expose caller computer identity, reader position, or player identity in the initial design.

## Considered Options

- Run card programs in an independent card runtime.
- Execute card programs inside the calling computer's Lua environment.
- Heavily sandbox card programs with only a tiny API surface.

Running inside the caller was rejected because malicious cards could inspect or alter the caller's files and peripherals. A tiny sandbox was rejected for the initial design because the desired gameplay is closer to a small card-shaped computer than a narrow script callback.
