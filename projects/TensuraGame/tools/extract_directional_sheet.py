#!/usr/bin/env python3
"""Extract a 3x4 directional sprite source into runtime-ready PNG frames."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


DIRECTIONS = ("down", "left", "right", "up")
POSES = ("walk_01", "idle", "walk_02")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--prefix", required=True)
    parser.add_argument("--canvas", type=int, default=64)
    parser.add_argument("--padding", type=int, default=2)
    parser.add_argument("--colors", type=int, default=48)
    parser.add_argument("--alpha-threshold", type=int, default=32)
    return parser.parse_args()


def crop_cell(source: Image.Image, column: int, row: int) -> Image.Image:
    left = round(column * source.width / len(POSES))
    right = round((column + 1) * source.width / len(POSES))
    top = round(row * source.height / len(DIRECTIONS))
    bottom = round((row + 1) * source.height / len(DIRECTIONS))
    return source.crop((left, top, right, bottom))


def prepare_frame(cell: Image.Image, canvas_size: int, padding: int,
                  colors: int, alpha_threshold: int) -> Image.Image:
    alpha = cell.getchannel("A").point(lambda value: 255 if value >= alpha_threshold else 0)
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError("Sprite cell has no opaque pixels.")

    subject = cell.crop(bounds)
    subject_alpha = alpha.crop(bounds)
    available = canvas_size - padding * 2
    scale = min(available / subject.width, available / subject.height)
    size = (max(1, round(subject.width * scale)), max(1, round(subject.height * scale)))

    subject = subject.resize(size, Image.Resampling.NEAREST)
    subject_alpha = subject_alpha.resize(size, Image.Resampling.NEAREST)
    quantized = subject.convert("RGB").quantize(
        colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE
    ).convert("RGBA")
    quantized.putalpha(subject_alpha)

    frame = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    x = (canvas_size - size[0]) // 2
    y = canvas_size - padding - size[1]
    frame.alpha_composite(quantized, (x, y))
    return frame


def frame_name(prefix: str, pose: str, direction: str) -> str:
    if pose == "idle":
        return f"{prefix}_idle_{direction}.png"
    return f"{prefix}_walk_{direction}_{pose[-2:]}.png"


def main() -> None:
    args = parse_args()
    if args.canvas <= args.padding * 2:
        raise ValueError("Canvas must be larger than twice the padding.")

    source = Image.open(args.input).convert("RGBA")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    runtime_sheet = Image.new(
        "RGBA", (args.canvas * len(POSES), args.canvas * len(DIRECTIONS)), (0, 0, 0, 0)
    )

    for row, direction in enumerate(DIRECTIONS):
        for column, pose in enumerate(POSES):
            frame = prepare_frame(
                crop_cell(source, column, row),
                args.canvas,
                args.padding,
                args.colors,
                args.alpha_threshold,
            )
            output = args.output_dir / frame_name(args.prefix, pose, direction)
            frame.save(output)
            runtime_sheet.alpha_composite(frame, (column * args.canvas, row * args.canvas))

    runtime_sheet.save(args.output_dir / f"{args.prefix}_map_walk_runtime_sheet_v1.png")


if __name__ == "__main__":
    main()
