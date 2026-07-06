# Reader card color rendering

The Smart Card Reader renders the inserted card dynamically from its block entity rather than enumerating card colors in blockstate models. Card placement still records the clicked face in block state, but the visible card color comes from the inserted card's `minecraft:dyed_color` component so arbitrary dyed RGB colors can appear consistently in hand, inventory, and on the reader.

The reader must synchronize the inserted `ItemStack` to clients for rendering. This exposes only public item metadata such as color, label, ID, and issued state; card files remain in world-backed Card Storage and are not part of the item stack.
