# Modrinth Image Assets

The screenshots in this directory are prepared manually from the development client. The crafting recipe image is reproducible from project recipe data and real Minecraft and CC SmartCard textures.

## Crafting Recipe Image

Install Pillow, build the project once so NeoGradle has prepared the matching Minecraft client JAR, and run:

```console
python3 -m pip install Pillow
python3 scripts/render-modrinth-recipes.py
```

The script reads `minecraft_version` from `gradle.properties`, finds a matching `client.jar` under the NeoGradle cache, loads shaped recipe patterns from the project recipe JSON files, and writes `docs/images/crafting-recipes.png`.

Use explicit paths when auto-detection is not suitable:

```console
python3 scripts/render-modrinth-recipes.py \
  --client-jar /path/to/client.jar \
  --font /path/to/monospace.ttf \
  --output docs/images/crafting-recipes.png
```

The Smart Card uses a custom Java recipe without an ingredient list in its recipe JSON, so that panel's paper, redstone, copper, and optional dye inputs are represented explicitly in the renderer. Update the renderer if `SmartCardRecipe` changes.
