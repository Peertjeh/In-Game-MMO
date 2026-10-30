#!/usr/bin/env python3
"""Draws the mod's icon (256 x 256) as 32 x 32 pixel art, scaled up 8 times: the game's tooltip box (near-black violet
with its fading violet frame) holding a skill tree, its trunk in gold and its three branches ending in the four
categories' colours (Combat red, Gathering green, Survival aqua, Crafting gold).

Usage: make_icon.py <out.png> [<out2.png> ...]"""

import sys

from PIL import Image

N, SCALE = 32, 8
INK = (16, 0, 16)
FRAME_TOP, FRAME_FOOT = (80, 0, 255), (40, 0, 127)
RED, GREEN, AQUA, GOLD = (255, 85, 85), (85, 255, 85), (85, 255, 255), (255, 170, 0)
TRUNK, TRUNK_DARK = (196, 132, 32), (120, 76, 16)
LINE = (150, 140, 190)
OUTLINE = (0, 0, 0)


def mix(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def draw():
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = img.load()
    # The box: ground a pixel past its frame except at the corners, the frame fading from top to foot
    for y in range(N):
        for x in range(N):
            corner = (x in (0, N - 1)) and (y in (0, N - 1))
            if not corner:
                px[x, y] = INK + (245,)
    for y in range(1, N - 1):
        t = (y - 1) / (N - 3)
        c = mix(FRAME_TOP, FRAME_FOOT, t) + (255,)
        px[1, y] = c
        px[N - 2, y] = c
    for x in range(1, N - 1):
        px[x, 1] = FRAME_TOP + (255,)
        px[x, N - 2] = FRAME_FOOT + (255,)

    def put(x, y, c):
        if 2 <= x < N - 2 and 2 <= y < N - 2:
            px[x, y] = c + (255,)

    def node(cx, cy, c, r=1, w=None):
        # (a square of the colour with a dark edge and a light corner, like the GUI's tiles)
        w = r if w is None else w
        for y in range(cy - r - 1, cy + r + 2):
            for x in range(cx - w - 1, cx + w + 2):
                put(x, y, OUTLINE)
        for y in range(cy - r, cy + r + 1):
            for x in range(cx - w, cx + w + 1):
                put(x, y, c)
        put(cx - w, cy - r, mix(c, (255, 255, 255), 0.55))

    def line(x0, y0, x1, y1, c):
        steps = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(steps + 1):
            put(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps), c)

    # The trunk: two pixels wide, from the root up to where it branches
    for y in range(14, 26):
        put(15, y, TRUNK)
        put(16, y, TRUNK_DARK)
    # Roots
    line(15, 25, 11, 27, TRUNK_DARK)
    line(16, 25, 20, 27, TRUNK_DARK)
    # Three branches, two pixels wide, a mid node on each, and their ends in the categories' colours
    for d in (0, 1):
        line(15, 14 + d, 8, 8 + d, LINE)
        line(16, 14 + d, 23, 8 + d, LINE)
    line(15, 13, 15, 7, LINE)
    line(16, 13, 16, 7, LINE)
    node(11, 12, RED, 0)
    node(20, 12, GREEN, 0)
    node(7, 7, RED, 2)
    node(24, 7, GREEN, 2)
    node(15, 9, AQUA, 0, 1)
    node(15, 4, AQUA, 1, 2)
    # The trunk's own nodes (levels 5 to 20) and the root in gold
    for y in (17, 21):
        put(14, y, GOLD)
        put(17, y, GOLD)
    node(15, 27, GOLD, 1, 2)
    return img.resize((N * SCALE, N * SCALE), Image.NEAREST)


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    img = draw()
    for out in sys.argv[1:]:
        img.save(out)
        print("wrote", out)


if __name__ == "__main__":
    main()
