from pathlib import Path

from PIL import Image


PROJECT_ROOT = Path(__file__).resolve().parents[1] / "project"
SOURCE_DIR = PROJECT_ROOT / "assets" / "source"
OUTPUT_DIR = PROJECT_ROOT / "assets" / "sprites" / "props" / "cave_polish"


def normalize_alpha(image: Image.Image, threshold: int = 40) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A").point(lambda value: 0 if value < threshold else 255)
    rgba.putalpha(alpha)
    return rgba


def save_overlay(source_name: str, output_name: str, size: tuple[int, int]) -> None:
    source = normalize_alpha(Image.open(SOURCE_DIR / source_name))
    overlay = source.resize(size, Image.Resampling.NEAREST)
    overlay.save(OUTPUT_DIR / output_name, optimize=True)


def save_ring_frames() -> None:
    sheet = normalize_alpha(
        Image.open(SOURCE_DIR / "cave_awakening_rune_ring_sheet_alpha_v1.png")
    )
    cell_width = sheet.width // 4

    for index in range(4):
        frame = sheet.crop((index * cell_width, 0, (index + 1) * cell_width, sheet.height))
        frame = frame.resize((128, 128), Image.Resampling.NEAREST)
        frame.save(
            OUTPUT_DIR / f"cave_awakening_rune_ring_{index + 1:02d}.png",
            optimize=True,
        )


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    save_overlay(
        "cave_awakening_environment_overlay_alpha_v2.png",
        "cave_awakening_environment_overlay_v2.png",
        (640, 384),
    )
    save_overlay(
        "cave_gallery_environment_overlay_alpha_v2.png",
        "cave_gallery_environment_overlay_v2.png",
        (320, 512),
    )
    save_ring_frames()


if __name__ == "__main__":
    main()
