from pathlib import Path

from PIL import Image


PROJECT = Path(__file__).resolve().parents[1] / "project"
SOURCE = PROJECT / "assets" / "source" / "jura_forest_props_alpha_v1.png"
OUTPUT = PROJECT / "assets" / "sprites" / "props" / "jura_forest"


def normalize_alpha(image):
    alpha = image.getchannel("A").point(lambda value: 255 if value >= 96 else 0)
    image.putalpha(alpha)
    return image


def crop_cell(sheet, column, row, canvas_size, filename):
    cell_width = sheet.width // 3
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

    padding = 6
    cell = cell.crop(
        (
            max(0, bbox[0] - padding),
            max(0, bbox[1] - padding),
            min(cell.width, bbox[2] + padding),
            min(cell.height, bbox[3] + padding),
        )
    )
    canvas_width, canvas_height = canvas_size
    scale = min((canvas_width - 8) / cell.width, (canvas_height - 8) / cell.height)
    width = max(1, round(cell.width * scale))
    height = max(1, round(cell.height * scale))
    cell = cell.resize((width, height), Image.Resampling.NEAREST)

    canvas = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    x = (canvas_width - width) // 2
    y = canvas_height - height - 2
    canvas.paste(cell, (x, y), cell)
    canvas.save(OUTPUT / filename)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    sheet = Image.open(SOURCE).convert("RGBA")
    assets = [
        (0, 0, (160, 128), "jura_tree_canopy_v1.png"),
        (1, 0, (128, 96), "jura_mossy_stump_v1.png"),
        (2, 0, (128, 96), "jura_fern_cluster_v1.png"),
        (0, 1, (96, 80), "jura_magicule_mushrooms_v1.png"),
        (1, 1, (112, 96), "jura_mossy_boulder_v1.png"),
        (2, 1, (160, 80), "jura_fallen_log_v1.png"),
    ]
    for asset in assets:
        crop_cell(sheet, *asset)


if __name__ == "__main__":
    main()
