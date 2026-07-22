from pathlib import Path

from PIL import Image


PROJECT = Path(__file__).resolve().parents[1] / "project"
SOURCE = PROJECT / "assets" / "source" / "goblin_village"
OUTPUT = PROJECT / "assets" / "sprites" / "props" / "goblin_village"


def crop_cell(image, box, size, filename):
    cell = image.crop(box).convert("RGBA")
    bbox = cell.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError(f"empty generated cell: {filename}")

    cell = cell.crop(
        (
            max(0, bbox[0] - 8),
            max(0, bbox[1] - 8),
            min(cell.width, bbox[2] + 8),
            min(cell.height, bbox[3] + 8),
        )
    )
    scale = min((size - 8) / cell.width, (size - 8) / cell.height)
    width = max(1, round(cell.width * scale))
    height = max(1, round(cell.height * scale))
    cell = cell.resize((width, height), Image.Resampling.NEAREST)

    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(cell, ((size - width) // 2, size - height - 2), cell)
    canvas.save(OUTPUT / filename)
    return canvas


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)

    props = Image.open(SOURCE / "goblin_village_defense_props_alpha_v1.png").convert("RGBA")
    cell_width = props.width // 4
    cell_height = props.height // 2
    names = [
        "goblin_village_palisade_neutral_v1.png",
        "goblin_village_palisade_reinforced_v1.png",
        "goblin_village_palisade_spiked_v1.png",
        "goblin_village_torch_unlit_v1.png",
        "goblin_village_torch_lit_v1.png",
        "goblin_village_controlled_bait_v1.png",
        "goblin_village_tent_scout_v1.png",
        "goblin_village_tent_supplies_v1.png",
    ]
    for index, name in enumerate(names):
        column = index % 4
        row = index // 4
        crop_cell(
            props,
            (column * cell_width, row * cell_height,
             (column + 1) * cell_width, (row + 1) * cell_height),
            64,
            name,
        )

    threshold = Image.open(SOURCE / "cave_forest_threshold_alpha_v1.png").convert("RGBA")
    threshold_width = threshold.width // 4
    for index in range(4):
        crop_cell(
            threshold,
            (index * threshold_width, 0, (index + 1) * threshold_width, threshold.height),
            96,
            f"cave_forest_threshold_{index + 1:02d}.png",
        )


if __name__ == "__main__":
    main()
