# Card Color uses dyed color

Smart Card color is stored with Minecraft's `minecraft:dyed_color` data component, not a mod-owned `color` custom-data field. This matches CC: Tweaked disks: dyeing or clearing color changes only the public appearance component while preserving the card's identity, label, issued state, and storage reference. Smart Cards participate in CC: Tweaked's dyeable item recipes instead of defining a separate smart-card-only dyeing system.
