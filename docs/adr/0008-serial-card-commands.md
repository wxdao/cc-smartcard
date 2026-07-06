# Serial Card Commands

A physical smart card can be placed in only one reader at a time, and commands against the inserted card are executed serially by that reader. Multiple computers may reach the same reader, but the reader must not run two commands against the same card concurrently.

Concurrent card commands were rejected because card programs share one private file space, and overlapping writes would create race conditions that are hard for players to understand.
