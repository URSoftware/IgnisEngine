from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Normaliza uma faixa RGBA em quadros de sprite com tamanho fixo."
    )
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--name", required=True)
    parser.add_argument("--frames", required=True, type=int)
    parser.add_argument("--frame-width", required=True, type=int)
    parser.add_argument("--frame-height", required=True, type=int)
    return parser.parse_args()


def normalize_frame(
    cell: Image.Image,
    frame_width: int,
    frame_height: int,
) -> Image.Image:
    bounds = cell.getbbox()
    figure = cell.crop(bounds) if bounds else cell
    figure.thumbnail(
        (frame_width - 2, frame_height - 2),
        Image.Resampling.LANCZOS,
    )
    frame = Image.new("RGBA", (frame_width, frame_height), (0, 0, 0, 0))
    position = (
        (frame_width - figure.width) // 2,
        frame_height - figure.height,
    )
    frame.alpha_composite(figure, position)
    return frame


def main() -> None:
    args = parse_args()
    if args.frames <= 0 or args.frame_width <= 2 or args.frame_height <= 2:
        raise ValueError("Frames e dimensoes devem ser positivos.")

    source = Image.open(args.input).convert("RGBA")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    sheet = Image.new(
        "RGBA",
        (args.frame_width * args.frames, args.frame_height),
        (0, 0, 0, 0),
    )

    for index in range(args.frames):
        left = round(source.width * index / args.frames)
        right = round(source.width * (index + 1) / args.frames)
        cell = source.crop((left, 0, right, source.height))
        frame = normalize_frame(cell, args.frame_width, args.frame_height)
        frame.save(
            args.output_dir / f"{args.name}_{index:02d}.png",
            optimize=True,
        )
        sheet.alpha_composite(frame, (index * args.frame_width, 0))

    sheet_path = args.output_dir / f"{args.name}_sheet.png"
    sheet.save(sheet_path, optimize=True)
    print(f"{sheet_path}: {sheet.width}x{sheet.height} {sheet.mode}")


if __name__ == "__main__":
    main()
