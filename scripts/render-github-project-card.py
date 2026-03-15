from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "docs" / "assets" / "github-project-card.png"
ICON_SOURCE = ROOT / "img" / "icon.png"

WIDTH = 860
HEIGHT = 160
SCALE = 2

BG_START = (16, 23, 32)
BG_END = (24, 36, 51)
BORDER = (51, 71, 91)
ACCENT = (121, 199, 197)
TEXT_MAIN = (245, 248, 250)
TEXT_SUB = (157, 178, 195)
CARD_FILL = (15, 22, 30)
SHADOW = (0, 0, 0, 95)


def rounded_gradient(size: tuple[int, int], start: tuple[int, int, int], end: tuple[int, int, int]) -> Image.Image:
    width, height = size
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    px = image.load()
    for y in range(height):
        ratio = y / max(1, height - 1)
        r = int(start[0] + (end[0] - start[0]) * ratio)
        g = int(start[1] + (end[1] - start[1]) * ratio)
        b = int(start[2] + (end[2] - start[2]) * ratio)
        for x in range(width):
            px[x, y] = (r, g, b, 255)
    return image


def fit_icon(icon: Image.Image, size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fitted = icon.copy()
    fitted.thumbnail((int(size * 0.76), int(size * 0.76)), Image.Resampling.LANCZOS)
    x = (size - fitted.width) // 2
    y = (size - fitted.height) // 2
    canvas.alpha_composite(fitted, (x, y))
    return canvas


def load_font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size=size)


def main() -> None:
    canvas = Image.new("RGBA", (WIDTH * SCALE, HEIGHT * SCALE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    outer = (8 * SCALE, 8 * SCALE, (WIDTH - 8) * SCALE, (HEIGHT - 8) * SCALE)
    radius = 24 * SCALE

    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle(
        (outer[0], outer[1] + 8 * SCALE, outer[2], outer[3] + 8 * SCALE),
        radius=radius,
        fill=SHADOW,
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(10 * SCALE))
    canvas.alpha_composite(shadow)

    gradient = rounded_gradient((WIDTH * SCALE, HEIGHT * SCALE), BG_START, BG_END)
    mask = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(outer, radius=radius, fill=255)
    canvas.paste(gradient, mask=mask)
    draw.rounded_rectangle(outer, radius=radius, outline=BORDER, width=2 * SCALE)

    icon_tile = (42 * SCALE, 32 * SCALE, 138 * SCALE, 128 * SCALE)
    draw.rounded_rectangle(icon_tile, radius=24 * SCALE, fill=CARD_FILL)

    if ICON_SOURCE.exists():
        icon = Image.open(ICON_SOURCE).convert("RGBA")
        try:
            fitted = fit_icon(icon, 96 * SCALE)
            canvas.alpha_composite(fitted, (42 * SCALE, 32 * SCALE))
        finally:
            icon.close()

    font_regular = load_font(r"C:\Windows\Fonts\segoeui.ttf", 19 * SCALE)
    font_bold = load_font(r"C:\Windows\Fonts\segoeuib.ttf", 34 * SCALE)
    font_accent = load_font(r"C:\Windows\Fonts\segoeuib.ttf", 20 * SCALE)

    draw.text((170 * SCALE, 42 * SCALE), "GitHub repository", font=font_regular, fill=TEXT_SUB)
    draw.text((170 * SCALE, 72 * SCALE), "AntonStrobe / HeadacheInsight", font=font_bold, fill=TEXT_MAIN)
    draw.text(
        (170 * SCALE, 108 * SCALE),
        "Open the project, sources, release assets and setup guide",
        font=font_accent,
        fill=ACCENT,
    )

    arrow_y = 80 * SCALE
    draw.line((756 * SCALE, arrow_y, 792 * SCALE, arrow_y), fill=TEXT_MAIN, width=6 * SCALE)
    draw.line((777 * SCALE, 62 * SCALE, 795 * SCALE, 80 * SCALE), fill=TEXT_MAIN, width=6 * SCALE)
    draw.line((777 * SCALE, 98 * SCALE, 795 * SCALE, 80 * SCALE), fill=TEXT_MAIN, width=6 * SCALE)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    canvas = canvas.resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS)
    canvas.save(OUTPUT, format="PNG")


if __name__ == "__main__":
    main()
