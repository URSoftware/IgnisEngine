from pathlib import Path

from PIL import Image


PROJECT_ROOT = Path(__file__).resolve().parents[1] / "project"
SOURCE_DIR = PROJECT_ROOT / "assets" / "source" / "jura_forest_scene"
BACKGROUND_DIR = PROJECT_ROOT / "assets" / "sprites" / "backgrounds"
CUTSCENE_DIR = PROJECT_ROOT / "assets" / "sprites" / "cutscenes" / "cave_exit"
VFX_DIR = PROJECT_ROOT / "assets" / "sprites" / "vfx" / "battle"


def quantized_rgb(image: Image.Image, size: tuple[int, int], colors: int = 128) -> Image.Image:
    resized = image.convert("RGB").resize(size, Image.Resampling.NEAREST)
    return resized.quantize(colors=colors, dither=Image.Dither.NONE).convert("RGB")


def center_crop_aspect(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    target_ratio = size[0] / size[1]
    source_ratio = image.width / image.height
    if source_ratio > target_ratio:
        crop_width = round(image.height * target_ratio)
        left = (image.width - crop_width) // 2
        return image.crop((left, 0, left + crop_width, image.height))
    crop_height = round(image.width / target_ratio)
    top = (image.height - crop_height) // 2
    return image.crop((0, top, image.width, top + crop_height))


def normalized_rgba(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    rgba = image.convert("RGBA").resize(size, Image.Resampling.NEAREST)
    alpha = rgba.getchannel("A").point(lambda value: 0 if value < 48 else 255)
    rgba.putalpha(alpha)
    return rgba


def split_grid(image: Image.Image, columns: int, rows: int, inset: int = 0):
    cell_width = image.width // columns
    cell_height = image.height // rows
    for row in range(rows):
        for column in range(columns):
            left = column * cell_width + inset
            top = row * cell_height + inset
            right = (column + 1) * cell_width - inset
            bottom = (row + 1) * cell_height - inset
            yield image.crop((left, top, right, bottom))


def process_background() -> None:
    source = Image.open(SOURCE_DIR / "jura_forest_approach_background_source_v2.png")
    result = quantized_rgb(center_crop_aspect(source, (640, 512)), (640, 512), 160)
    result.save(BACKGROUND_DIR / "jura_forest_approach_background_v2.png", optimize=True)


def process_cinematic() -> None:
    source = Image.open(SOURCE_DIR / "cutscene_cave_exit_story_sheet_source_v1.png")
    for index, frame in enumerate(split_grid(source, 2, 2), start=1):
        result = quantized_rgb(frame, (640, 480), 160)
        result.save(CUTSCENE_DIR / f"cutscene_cave_exit_story_{index:02d}.png", optimize=True)


def process_vfx(source_name: str, output_prefix: str, frame_count: int) -> None:
    source = Image.open(SOURCE_DIR / source_name)
    frames = list(split_grid(source, 3, 2, inset=4))
    for index, frame in enumerate(frames[:frame_count], start=1):
        result = normalized_rgba(frame, (192, 192))
        result.save(VFX_DIR / f"{output_prefix}_{index:02d}.png", optimize=True)


def main() -> None:
    BACKGROUND_DIR.mkdir(parents=True, exist_ok=True)
    CUTSCENE_DIR.mkdir(parents=True, exist_ok=True)
    VFX_DIR.mkdir(parents=True, exist_ok=True)
    process_background()
    process_cinematic()
    process_vfx("battle_hydrolamina_sheet_alpha_v1.png", "battle_hydrolamina_v1", 6)
    process_vfx("battle_guard_reaction_sheet_alpha_v1.png", "battle_guard_reaction_v1", 5)
    process_vfx("battle_dire_wolf_strike_sheet_alpha_v1.png", "battle_dire_wolf_strike_v1", 6)


if __name__ == "__main__":
    main()
