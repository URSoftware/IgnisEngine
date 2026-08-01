from pathlib import Path

from PIL import Image


PROJECT = Path(__file__).resolve().parents[1] / "project"
SOURCE = PROJECT / "assets" / "source" / "goblin_village"
OUTPUT = PROJECT / "assets" / "sprites" / "props" / "goblin_village"
AMBIENT_SOURCE = SOURCE / "ambient"
AMBIENT_OUTPUT = PROJECT / "assets" / "sprites" / "npcs" / "goblin_village"


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


def normalize_ambient_npc(source_name, output_name):
    npc = Image.open(AMBIENT_SOURCE / source_name).convert("RGBA")
    alpha = npc.getchannel("A").point(lambda value: 0 if value < 64 else 255)
    npc.putalpha(alpha)

    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError(f"empty generated NPC: {source_name}")

    npc = npc.crop(bounds)
    npc.thumbnail((44, 46), Image.Resampling.NEAREST)

    canvas = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    canvas.alpha_composite(npc, ((48 - npc.width) // 2, 47 - npc.height))

    output_alpha = canvas.getchannel("A").point(lambda value: 0 if value < 128 else 255)
    color = canvas.convert("RGB").quantize(
        colors=96,
        method=Image.Quantize.MEDIANCUT,
        dither=Image.Dither.NONE,
    ).convert("RGBA")
    color.putalpha(output_alpha)
    color.save(AMBIENT_OUTPUT / output_name, optimize=True)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    AMBIENT_OUTPUT.mkdir(parents=True, exist_ok=True)

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

    ambient_npcs = {
        "goblin_village_ambient_builder_alpha_source_v1.png":
            "goblin_village_ambient_builder_v1.png",
        "goblin_village_ambient_carrier_alpha_source_v1.png":
            "goblin_village_ambient_carrier_v1.png",
        "goblin_village_ambient_caregiver_alpha_source_v1.png":
            "goblin_village_ambient_caregiver_v1.png",
    }
    for source_name, output_name in ambient_npcs.items():
        normalize_ambient_npc(source_name, output_name)


if __name__ == "__main__":
    main()
