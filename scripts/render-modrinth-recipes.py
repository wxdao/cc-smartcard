#!/usr/bin/env python3

import argparse
import io
import json
import zipfile
from collections import Counter
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "docs" / "images" / "crafting-recipes.png"

WIDTH = 1600
HEIGHT = 900
FONT_PATH = ""
MINECRAFT_CLIENT_JAR: Path | None = None
ICONS: dict[str, Image.Image] = {}


def expected_minecraft_version() -> str:
    properties = ROOT / "gradle.properties"
    for raw_line in properties.read_text(encoding="utf-8").splitlines():
        key, separator, value = raw_line.partition("=")
        if separator and key.strip() == "minecraft_version":
            return value.strip()
    raise RuntimeError(f"minecraft_version is missing from {properties}")


def minecraft_jar_version(path: Path) -> str | None:
    try:
        with zipfile.ZipFile(path) as archive:
            return json.loads(archive.read("version.json"))["id"]
    except (KeyError, OSError, zipfile.BadZipFile, json.JSONDecodeError):
        return None


def find_minecraft_client_jar(explicit_path: Path | None) -> Path:
    expected = expected_minecraft_version()
    if explicit_path is not None:
        path = explicit_path.expanduser().resolve()
        actual = minecraft_jar_version(path)
        if actual != expected:
            raise RuntimeError(f"Expected a Minecraft {expected} client JAR, got {actual or 'an unreadable JAR'}: {path}")
        return path

    candidates = sorted(
        (Path.home() / ".gradle" / "caches" / "ng_execute").glob("*/client.jar"),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    for path in candidates:
        if minecraft_jar_version(path) == expected:
            return path

    raise RuntimeError(
        f"Could not find a Minecraft {expected} client JAR in the NeoGradle cache. "
        "Run ./gradlew build first or pass --client-jar."
    )


def select_font(explicit_path: Path | None) -> str:
    if explicit_path is not None:
        candidate = str(explicit_path.expanduser().resolve())
        try:
            ImageFont.truetype(candidate, size=16)
            return candidate
        except OSError as exc:
            raise RuntimeError(f"Could not load font: {candidate}") from exc

    candidates = [
        "/System/Library/Fonts/SFNSMono.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationMono-Regular.ttf",
        "C:/Windows/Fonts/consola.ttf",
        "DejaVuSansMono.ttf",
    ]
    for candidate in candidates:
        try:
            ImageFont.truetype(candidate, size=16)
            return candidate
        except OSError:
            continue
    raise RuntimeError("Could not find a monospace TrueType font. Pass one with --font.")


def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_PATH, size=size)


def minecraft_texture(relative: str) -> Image.Image:
    if MINECRAFT_CLIENT_JAR is None:
        raise RuntimeError("Minecraft client JAR is not configured")
    with zipfile.ZipFile(MINECRAFT_CLIENT_JAR) as archive:
        data = archive.read(f"assets/minecraft/textures/{relative}")
    return Image.open(io.BytesIO(data)).convert("RGBA")


def item_icon(name: str) -> Image.Image:
    directory = "block" if name in {"stone", "glass"} else "item"
    return minecraft_texture(f"{directory}/{name}.png")


def mod_texture(relative: str) -> Image.Image:
    return Image.open(ROOT / "src/main/resources/assets/cc_smartcard/textures" / relative).convert("RGBA")


def smart_card_icon() -> Image.Image:
    body = mod_texture("item/smart_card_body.png")
    chip = mod_texture("item/smart_card_chip.png")
    return Image.alpha_composite(body, chip)


def block_icon(relative: str) -> Image.Image:
    texture = mod_texture(relative)
    canvas = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    canvas.alpha_composite(texture, (2, 2))
    draw = ImageDraw.Draw(canvas)
    draw.line((2, 18, 17, 18), fill=(0, 0, 0, 110), width=2)
    draw.line((18, 2, 18, 18), fill=(0, 0, 0, 110), width=2)
    draw.line((2, 2, 17, 2), fill=(255, 255, 255, 55), width=1)
    draw.line((2, 2, 2, 17), fill=(255, 255, 255, 55), width=1)
    return canvas


def configure_resources(client_jar: Path, font_path: str) -> None:
    global MINECRAFT_CLIENT_JAR, FONT_PATH, ICONS
    MINECRAFT_CLIENT_JAR = client_jar
    FONT_PATH = font_path
    ICONS = {
        "paper": item_icon("paper"),
        "redstone": item_icon("redstone"),
        "copper": item_icon("copper_ingot"),
        "dye": item_icon("red_dye"),
        "stone": item_icon("stone"),
        "glass": item_icon("glass"),
        "card": smart_card_icon(),
        "reader": block_icon("block/smart_card_reader_top.png"),
        "scanner": block_icon("block/fingerprint_scanner.png"),
    }


INGREDIENT_ICONS = {
    "minecraft:stone": "stone",
    "minecraft:copper_ingot": "copper",
    "minecraft:glass_pane": "glass",
    "c:dusts/redstone": "redstone",
}

INGREDIENT_NAMES = {
    "stone": "Stone",
    "copper": "Copper Ingot",
    "glass": "Glass Pane",
    "redstone": "Redstone Dust",
}


def shaped_recipe(filename: str, ingredient_order: list[str]) -> tuple[list[list[str | None]], list[str]]:
    path = ROOT / "src/main/resources/data/cc_smartcard/recipe" / filename
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("type") != "minecraft:crafting_shaped":
        raise RuntimeError(f"Expected a shaped recipe in {path}")

    keys: dict[str, str] = {}
    for symbol, ingredient in data["key"].items():
        identifier = ingredient.get("item") or ingredient.get("tag")
        try:
            keys[symbol] = INGREDIENT_ICONS[identifier]
        except KeyError as exc:
            raise RuntimeError(f"No recipe icon mapping for {identifier} in {path}") from exc

    grid: list[list[str | None]] = []
    for row in data["pattern"]:
        grid.append([keys.get(symbol) if symbol != " " else None for symbol in row.ljust(3)])
    while len(grid) < 3:
        grid.append([None, None, None])

    counts = Counter(icon for row in grid for icon in row if icon is not None)
    lines = [f"{counts[icon]} × {INGREDIENT_NAMES[icon]}" for icon in ingredient_order]
    return grid, lines


def make_background() -> Image.Image:
    image = Image.new("RGB", (WIDTH, HEIGHT))
    pixels = image.load()
    for y in range(HEIGHT):
        t = y / (HEIGHT - 1)
        color = tuple(round(a + (b - a) * t) for a, b in zip((13, 21, 28), (28, 43, 48)))
        for x in range(WIDTH):
            pixels[x, y] = color
    draw = ImageDraw.Draw(image, "RGBA")
    for x in range(0, WIDTH, 32):
        draw.line((x, 0, x, HEIGHT), fill=(255, 255, 255, 5))
    for y in range(0, HEIGHT, 32):
        draw.line((0, y, WIDTH, y), fill=(255, 255, 255, 5))
    return image.convert("RGBA")


def draw_panel(draw: ImageDraw.ImageDraw, bounds: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = bounds
    draw.rounded_rectangle((x0 + 8, y0 + 10, x1 + 8, y1 + 10), radius=18, fill=(0, 0, 0, 80))
    draw.rounded_rectangle(bounds, radius=18, fill=(34, 46, 54, 246), outline=(90, 116, 124, 255), width=3)
    draw.line((x0 + 24, y0 + 92, x1 - 24, y0 + 92), fill=(79, 104, 113, 210), width=2)


def draw_slot(image: Image.Image, x: int, y: int, icon: Image.Image | None, size: int = 72) -> None:
    draw = ImageDraw.Draw(image, "RGBA")
    draw.rectangle((x, y, x + size - 1, y + size - 1), fill=(45, 45, 45, 255))
    draw.polygon(((x, y), (x + size - 1, y), (x + size - 7, y + 6), (x + 6, y + 6), (x + 6, y + size - 7), (x, y + size - 1)), fill=(228, 228, 228, 255))
    draw.polygon(((x + size - 1, y), (x + size - 1, y + size - 1), (x, y + size - 1), (x + 6, y + size - 7), (x + size - 7, y + size - 7), (x + size - 7, y + 6)), fill=(72, 72, 72, 255))
    draw.rectangle((x + 7, y + 7, x + size - 8, y + size - 8), fill=(139, 139, 139, 255))
    if icon is None:
        return
    target = size - 22
    scale = min(target / icon.width, target / icon.height)
    resized = icon.resize((round(icon.width * scale), round(icon.height * scale)), Image.Resampling.NEAREST)
    image.alpha_composite(resized, (x + (size - resized.width) // 2, y + (size - resized.height) // 2))


def draw_grid(image: Image.Image, x: int, y: int, recipe: list[list[str | None]]) -> None:
    for row in range(3):
        for column in range(3):
            key = recipe[row][column]
            draw_slot(image, x + column * 72, y + row * 72, ICONS[key] if key else None)


def draw_arrow(draw: ImageDraw.ImageDraw, x: int, y: int) -> None:
    color = (196, 216, 218, 255)
    draw.rectangle((x, y + 17, x + 54, y + 29), fill=color)
    draw.polygon(((x + 54, y), (x + 82, y + 23), (x + 54, y + 46)), fill=color)


def center_text(draw: ImageDraw.ImageDraw, text: str, x: int, y: int, selected_font: ImageFont.FreeTypeFont, fill=(235, 242, 242, 255)) -> None:
    draw.text((x, y), text, font=selected_font, fill=fill, anchor="mm")


def render(output: Path) -> None:
    title_font = font(50)
    panel_title_font = font(27)
    panel_subtitle_font = font(19)
    body_font = font(18)
    small_font = font(15)

    image = make_background()
    draw = ImageDraw.Draw(image, "RGBA")

    center_text(draw, "CRAFTING RECIPES", WIDTH // 2, 67, title_font)

    panel_xs = (35, 550, 1065)
    panel_width = 500
    panel_bounds = [(x, 135, x + panel_width, 835) for x in panel_xs]
    for bounds in panel_bounds:
        draw_panel(draw, bounds)

    reader_grid, reader_lines = shaped_recipe(
        "smart_card_reader.json",
        ["stone", "redstone", "copper"],
    )
    scanner_grid, scanner_lines = shaped_recipe(
        "fingerprint_scanner.json",
        ["stone", "copper", "redstone", "glass"],
    )

    recipes = [
        {
            "title": "SMART CARD",
            "subtitle": "Shapeless · dye is optional",
            # SmartCardRecipe is a custom Java recipe, so its ingredients are
            # represented explicitly here instead of coming from recipe JSON.
            "recipe": [["paper", "copper", "redstone"], ["dye", None, None], [None, None, None]],
            "result": "card",
            "lines": ["Paper + Redstone Dust", "+ Copper Ingot", "Optional: one or more dyes", "No dye produces a white card"],
        },
        {
            "title": "SMART CARD READER",
            "subtitle": "Shaped 3 × 3",
            "recipe": reader_grid,
            "result": "reader",
            "lines": reader_lines,
        },
        {
            "title": "FINGERPRINT SCANNER",
            "subtitle": "Shaped 3 × 3",
            "recipe": scanner_grid,
            "result": "scanner",
            "lines": scanner_lines,
        },
    ]

    for panel_x, recipe in zip(panel_xs, recipes):
        center_text(draw, recipe["title"], panel_x + panel_width // 2, 176, panel_title_font)
        center_text(draw, recipe["subtitle"], panel_x + panel_width // 2, 217, panel_subtitle_font, fill=(157, 193, 196, 255))

        grid_x = panel_x + 25
        grid_y = 260
        draw_grid(image, grid_x, grid_y, recipe["recipe"])
        draw_arrow(draw, panel_x + 258, 341)
        draw_slot(image, panel_x + 370, 322, ICONS[recipe["result"]], size=92)

        center_text(draw, "INGREDIENTS", panel_x + panel_width // 2, 527, panel_subtitle_font, fill=(220, 232, 232, 255))
        draw.line((panel_x + 76, 551, panel_x + panel_width - 76, 551), fill=(75, 101, 109, 210), width=2)
        for index, line in enumerate(recipe["lines"]):
            center_text(draw, line, panel_x + panel_width // 2, 586 + index * 38, body_font, fill=(201, 216, 217, 255))

        if recipe["result"] == "card":
            center_text(draw, "Dyes are mixed like CC: Tweaked disks", panel_x + panel_width // 2, 770, small_font, fill=(135, 164, 168, 255))

    output.parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(output, format="PNG", optimize=True, compress_level=9)
    print(output)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Render the Modrinth crafting recipe image from project and Minecraft textures.")
    parser.add_argument(
        "--client-jar",
        type=Path,
        help="Minecraft client JAR to read vanilla textures from; defaults to the matching NeoGradle cache entry.",
    )
    parser.add_argument(
        "--font",
        type=Path,
        help="Monospace TrueType font; defaults to a common system monospace font.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"Output PNG path (default: {DEFAULT_OUTPUT.relative_to(ROOT)}).",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    client_jar = find_minecraft_client_jar(args.client_jar)
    selected_font = select_font(args.font)
    configure_resources(client_jar, selected_font)
    render(args.output.resolve())
