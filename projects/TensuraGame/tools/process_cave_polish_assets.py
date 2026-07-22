from pathlib import Path

from PIL import Image


PROJECT = Path(__file__).resolve().parents[1] / "project"
SOURCE = PROJECT / "assets" / "source" / "cave_polish_dressing_alpha_v1.png"
RUNTIME_TILESET = PROJECT / "assets" / "tilesets" / "cave_seal_tileset_v1.png"
OUTPUT = PROJECT / "assets" / "sprites" / "props" / "cave_polish"
BACKGROUND_OUTPUT = PROJECT / "assets" / "sprites" / "backgrounds"


def normalize_alpha(image):
    alpha = image.getchannel("A").point(lambda value: 255 if value >= 96 else 0)
    image.putalpha(alpha)
    return image


def crop_cell(sheet, column, row, canvas_size, filename, anchor):
    cell_width = sheet.width // 4
    cell_height = sheet.height // 2
    cell = sheet.crop(
        (
            column * cell_width,
            row * cell_height,
            (column + 1) * cell_width,
            (row + 1) * cell_height,
        )
    ).convert("RGBA")
    cell = normalize_alpha(cell)
    bbox = cell.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError(f"empty generated cell: {filename}")

    cell = cell.crop(
        (
            max(0, bbox[0] - 6),
            max(0, bbox[1] - 6),
            min(cell.width, bbox[2] + 6),
            min(cell.height, bbox[3] + 6),
        )
    )
    canvas_width, canvas_height = canvas_size
    scale = min((canvas_width - 8) / cell.width, (canvas_height - 8) / cell.height)
    width = max(1, round(cell.width * scale))
    height = max(1, round(cell.height * scale))
    cell = cell.resize((width, height), Image.Resampling.NEAREST)

    canvas = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    x = (canvas_width - width) // 2
    if anchor == "top":
        y = 2
    elif anchor == "bottom":
        y = canvas_height - height - 2
    else:
        y = (canvas_height - height) // 2
    canvas.paste(cell, (x, y), cell)
    canvas.save(OUTPUT / filename)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    BACKGROUND_OUTPUT.mkdir(parents=True, exist_ok=True)
    sheet = Image.open(SOURCE).convert("RGBA")

    static_assets = [
        (0, 0, (96, 64), "cave_ceiling_stalactites_cyan_v1.png", "top"),
        (1, 0, (128, 64), "cave_ceiling_overhang_cyan_v1.png", "top"),
        (2, 0, (64, 64), "cave_crystal_vein_cyan_v1.png", "bottom"),
        (3, 0, (96, 48), "cave_floor_rubble_cyan_v1.png", "bottom"),
    ]
    for asset in static_assets:
        crop_cell(sheet, *asset)

    for index in range(4):
        crop_cell(
            sheet,
            index,
            1,
            (128, 96),
            f"cave_magicule_motes_ambient_{index + 1:02d}.png",
            "center",
        )

    tileset = Image.open(RUNTIME_TILESET).convert("RGB")
    cave_floor = tileset.crop((0, 0, 32, 32))
    seamless = Image.new("RGB", (256, 256))
    for row in range(8):
        for column in range(8):
            seamless.paste(cave_floor, (column * 32, row * 32))
    dark_overlay = Image.new("RGB", seamless.size, (4, 9, 18))
    seamless = Image.blend(seamless, dark_overlay, 0.34)
    seamless.save(BACKGROUND_OUTPUT / "cave_backdrop_texture_v3.png")


if __name__ == "__main__":
    main()
