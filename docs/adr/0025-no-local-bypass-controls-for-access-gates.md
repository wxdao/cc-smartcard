# No Local Bypass Controls for Access Gates

Gate Cabinets do not accept redstone input or direct player interaction to open, close, or otherwise command their shared Access Gate. Movement is controlled only through the `access_gate` peripheral and the gate's built-in fail-open lifecycle, preventing nearby circuitry or a right-click from bypassing the facility's credential and biometric policy. Right-clicking a cabinet may report diagnostic information, but never changes Gate State.
