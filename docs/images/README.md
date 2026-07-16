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

## Access Gate Demo

`access-gate-demo.gif` is an optimised, full-frame conversion of the short macOS gameplay recording used to demonstrate
the two-gate passage. Rebuild it from the source recording with:

```console
ffmpeg -i /path/to/recording.mov \
  -filter_complex "fps=10,scale=960:-2:flags=lanczos,split[v0][v1];[v0]palettegen=max_colors=128:stats_mode=diff[p];[v1][p]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle" \
  -loop 0 docs/images/access-gate-demo.gif
```

The committed GIF keeps the entire captured window, runs at 10 FPS with a 128-colour adaptive palette, and is scaled
to 960 pixels wide.
