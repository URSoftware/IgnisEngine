from pathlib import Path

from PIL import Image


PROJECT_ROOT = Path(__file__).resolve().parents[1] / "project"
SOURCE_DIR = PROJECT_ROOT / "assets" / "source" / "battle_hud_polish"
OUTPUT_DIR = PROJECT_ROOT / "assets" / "sprites" / "ui" / "battle" / "commands"
ICON_SIZE = 128
SUBJECT_SIZE = 112

COMMANDS = (
    "defend",
    "goblin_support",
    "negotiate",
)


def normalize_icon(source: Image.Image) -> Image.Image:
    rgba = source.convert("RGBA")
    alpha = rgba.getchannel("A").point(lambda value: 0 if value < 64 else 255)
    rgba.putalpha(alpha)

    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError("source icon has no visible pixels")

    subject = rgba.crop(bounds)
    subject.thumbnail((SUBJECT_SIZE, SUBJECT_SIZE), Image.Resampling.NEAREST)

    canvas = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
    offset = (
        (ICON_SIZE - subject.width) // 2,
        (ICON_SIZE - subject.height) // 2,
    )
    canvas.alpha_composite(subject, offset)

    output_alpha = canvas.getchannel("A").point(lambda value: 0 if value < 128 else 255)
    color = canvas.convert("RGB").quantize(
        colors=160,
        method=Image.Quantize.MEDIANCUT,
        dither=Image.Dither.NONE,
    ).convert("RGBA")
    color.putalpha(output_alpha)
    return color


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for command in COMMANDS:
        source_path = SOURCE_DIR / f"battle_command_{command}_alpha_source_v1.png"
        output_path = OUTPUT_DIR / f"battle_command_{command}_v1.png"
        with Image.open(source_path) as source:
            normalize_icon(source).save(output_path, optimize=True)
        print(output_path.relative_to(PROJECT_ROOT))


if __name__ == "__main__":
    main()
