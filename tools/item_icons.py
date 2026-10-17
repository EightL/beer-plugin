#!/usr/bin/env python3
"""Writes the Beer item icons (stdlib only; run with `python3 -I`).

    python3 -I beer/tools/item_icons.py [--preview <out.png>]

Each icon is 16x16 pixel art in a vanilla style, drawn as a character grid below (one character per pixel, '.' is
transparent). The grid marks edges with 'k' or 'B'; they are not drawn as a flat dark line but "selectively outlined":
each edge pixel becomes a darker shade of the colours next to it, lighter on the lit top-left side and darker on the
shadow side. Under beer/resourcepack/assets/beer/ it writes, for barley, hops, malt, dark_malt, the Beer Journal,
wort_bucket_<colour>, mug_empty and beer_<colour>[_clear]:
    textures/item/<id>.png      the sprite
    models/item/<id>.json       item/generated with that sprite
    items/<id>.json             the item definition the plugin points item_model at (beer:<id>), where the item has one
"""
import argparse
import json
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Shared colours: the plant's muted green palette, plus seed browns.
COLORS = {
    "k": (22, 44, 20),      # outline, green items
    "d": (46, 76, 32),     # dark green
    "g": (72, 106, 46),     # green
    "l": (100, 134, 62),    # light green
    "h": (136, 162, 94),   # highlight green
    "w": (200, 196, 176),   # white pistil hair
    "o": (170, 96, 44),    # orange pistil hair
    "f": (196, 204, 188),   # frost
    "B": (46, 32, 24),      # outline, seeds and wood
    "b": (92, 70, 48),      # seed dark
    "m": (138, 112, 80),    # seed mid
    "t": (184, 160, 118),   # seed light
    "y": (222, 206, 160),   # seed highlight
    "s": (52, 82, 34),     # stem
    "S": (72, 106, 44),     # stem light
    "W": (98, 84, 56),    # woody
    "V": (122, 108, 78),    # woody light
}

def png(rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


EDGES = "kB"


def _shade(rgb, factor, cool):
    """Darkens a colour; shadows drift slightly toward blue so they do not look muddy."""
    r, g, b = (c * factor for c in rgb)
    return (int(r * (1 - cool)), int(g * (1 - cool * 0.5)), int(min(255, b + 18 * cool)))


def pixels(art, colors=COLORS):
    lines = [line for line in art.strip("\n").split("\n")]
    if len(lines) != 16 or any(len(line) != 16 for line in lines):
        raise ValueError(f"icon must be 16x16, got {[len(line) for line in lines]}")
    out = [[(*colors[c], 255) if c not in "." + EDGES else (0, 0, 0, 0) for c in line] for line in lines]
    for y, line in enumerate(lines):
        for x, c in enumerate(line):
            if c not in EDGES:
                continue
            # colours and direction of the filled (non-edge) pixels around this edge pixel
            near, dx, dy = [], 0.0, 0.0
            for radius in (1, 2):
                for ny in range(y - radius, y + radius + 1):
                    for nx in range(x - radius, x + radius + 1):
                        if 0 <= ny < 16 and 0 <= nx < 16 and lines[ny][nx] not in "." + EDGES:
                            near.append(colors[lines[ny][nx]])
                            dx, dy = dx + nx - x, dy + ny - y
                if near:
                    break
            if not near:
                out[y][x] = (*colors[c], 255)
                continue
            base = tuple(sum(col[i] for col in near) / len(near) for i in range(3))
            inner = all(0 <= y + oy < 16 and 0 <= x + ox < 16 and lines[y + oy][x + ox] != "."
                        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if inner:                                  # a crease inside the shape
                out[y][x] = (*_shade(base, 0.62, 0.15), 255)
                continue
            # the fill lies toward (dx, dy); an edge whose fill is down-right faces the light (top-left)
            length = (dx * dx + dy * dy) ** 0.5 or 1.0
            lit = (dx + dy) / (length * 2 ** 0.5)      # 1 = lit edge, -1 = shadow edge
            factor = 0.62 + 0.16 * lit
            out[y][x] = (*_shade(base, factor, 0.2 * (1 - lit) / 2), 255)
    return out


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    mode = "wb" if isinstance(data, bytes) else "w"
    with open(path, mode) as handle:
        handle.write(data if isinstance(data, bytes) else json.dumps(data, separators=(",", ":")))


def preview(path, icons, scale=12, gap=4):
    """All icons side by side, scaled up, on a mid-grey background."""
    width = len(icons) * (16 * scale + gap) + gap
    height = 16 * scale + 2 * gap
    rows = [[(140, 140, 140, 255)] * width for _ in range(height)]
    for i, px in enumerate(icons):
        ox = gap + i * (16 * scale + gap)
        for y in range(16 * scale):
            for x in range(16 * scale):
                p = px[y // scale][x // scale]
                if p[3]:
                    rows[gap + y][ox + x] = p
    write(path, png(rows))


# ------------------------------------------------------------------------------------------------ icons

def _canvas():
    return [["."] * 16 for _ in range(16)]


def _outline(grid, open_keys="w"):
    """Puts an edge pixel ('k') round every filled pixel except the open ones (thin details such as awns)."""
    filled = [[c != "." for c in row] for row in grid]
    for y in range(16):
        for x in range(16):
            if filled[y][x]:
                continue
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < 16 and 0 <= ny < 16 and filled[ny][nx] and grid[ny][nx] not in open_keys + "k":
                    grid[y][x] = "k"
                    break
    return "\n" + "\n".join("".join(row) for row in grid) + "\n"


def _barley_sheaf():
    """A sheaf like vanilla wheat: three stalks from the bottom left, tied with twine, three ears fanning out at the
    top right (one up, one along, one nodding), each with long awns, and a leaf hanging from the bundle."""
    g = _canvas()
    for x in range(0, 8):
        for k, total in enumerate((14, 15, 16)):
            if 0 <= total - x < 16:
                g[total - x][x] = "s" if k == 1 else "S"
    for x in (3, 4):
        for total in (14, 15, 16):
            g[total - x][x] = "t" if (x + total) % 2 else "T"
    for x, y in ((6, 11), (7, 12), (8, 12), (9, 13)):
        g[y][x] = "L"
    ears = [([(6, 7), (6, 6), (6, 5), (7, 4), (7, 3)], (1, 0), [(7, 2), (7, 1), (6, 1), (5, 0), (9, 2), (10, 1)]),
            ([(8, 6), (9, 5), (10, 4), (11, 3), (12, 2)], (1, 0), [(13, 1), (14, 0), (12, 1), (12, 0), (14, 2), (15, 1)]),
            ([(8, 8), (9, 8), (10, 9), (11, 9), (12, 10)], (0, 1), [(13, 11), (14, 12), (13, 10), (14, 10), (12, 12),
                                                                     (12, 13)])]
    for spine, (dx, dy), awns in ears:
        for i, (x, y) in enumerate(spine):
            g[y][x] = "E" if i % 2 == 0 else "e"
            g[y + dy][x + dx] = "e" if i % 2 == 0 else "a"
        for x, y in awns:
            if g[y][x] == ".":
                g[y][x] = "w"
    return _outline(g)


def _compose(*layers):
    """A 16 x 16 grid from stamps drawn in order, (x, y, rows); '.' in a stamp leaves what is below."""
    grid = [["."] * 16 for _ in range(16)]
    for x0, y0, rows in layers:
        for dy, row in enumerate(rows):
            for dx, c in enumerate(row):
                if c != "." and 0 <= x0 + dx < 16 and 0 <= y0 + dy < 16:
                    grid[y0 + dy][x0 + dx] = c
    return "\n" + "\n".join("".join(row) for row in grid) + "\n"


# a hop cone: overlapping papery bracts, lit tops, a speck of yellow lupulin; and a three-lobed leaf with a vein
HOP_CONE = ["..kkk..", ".kCCCk.", "kCCcCCk", "kcnCnck", "kCcCcCk", "kncynck", ".kCcCk.", ".kncnk.", "..kck..", "...k..."]
HOP_LEAF = ["...k...", "..kLk..", "k.kLk.k", "kLkLkLk", "kLLVLLk", ".kLVLk.", "..kVk..", "...k..."]
HOP_BINE = [(x, y, ["g"]) for x, y in ((6, 5), (7, 4), (8, 3), (9, 2), (10, 2), (11, 2), (12, 1), (13, 1), (14, 0))]

# a burlap sack, open at the top to show a heap of grain, a few kernels spilled beside it
SACK = """
................
................
......kkkk......
....kkytmtkk....
...kytmtytmtk...
..kRRRRRRRRRRk..
..krRRRRRRRrrk..
.kHUUUUUUUUUbbk.
.kHUUUUUUWUUbbk.
.kHUWUUUUUUUbbk.
.kHUUUUUUUUWbbk.
.kHUUUUUWUUUbbk.
.kHUUWUUUUUUbbk.
..kHUUUUUWUbbkty
...kkkkkkkkkkmty
...........mtyt.
"""

BREW_ICONS = {
    # the Beer Journal: a brown leather book, a golden mug with a foam head on the cover
    "beer_journal": ("""
................
..kkkkkkkkkkkk..
..kggggggggggk..
..kgggyyyyggpk..
..kggyffffygpk..
..kggyyyyyysyk..
..kggyyyyyygsk..
..kggyyyyyysyk..
..kgggyyyyggpk..
..khhhhhhhhhpk..
..kggggggggggk..
..kddddddddddk..
..kppppppppppk..
..kkkkkkkrkkkk..
.........r......
................
""", {"k": (40, 24, 14), "g": (112, 70, 40), "h": (146, 98, 58), "d": (78, 46, 24), "y": (222, 176, 70),
      "s": (176, 130, 46), "f": (246, 240, 222), "p": (226, 218, 190), "r": (60, 110, 54)}),

    # a sheaf of three barley ears with long awns, tied with red twine, a leaf hanging out
    "barley": (_barley_sheaf(), {"E": (242, 216, 138), "e": (216, 178, 96), "a": (178, 136, 62), "w": (246, 232, 176),
                                 "S": (206, 166, 84), "s": (170, 130, 60), "L": (150, 168, 76), "t": (190, 70, 52),
                                 "T": (140, 46, 36)}),
    # two hop cones on a bine, a leaf and a curling tendril
    "hops": (_compose(*HOP_BINE, (0, 0, HOP_LEAF), (8, 3, HOP_CONE), (3, 6, HOP_CONE)),
             {"L": (112, 172, 72), "V": (150, 200, 104), "g": (92, 116, 50), "C": (222, 236, 164),
              "c": (182, 210, 114), "n": (132, 166, 76), "y": (234, 202, 72)}),
    "malt": (SACK, {"H": (200, 172, 124), "U": (182, 152, 106), "W": (192, 162, 116), "b": (148, 120, 80),
                    "R": (206, 180, 132), "r": (170, 142, 98),
                    "y": (236, 210, 150), "t": (210, 170, 104), "m": (172, 128, 70)}),
    "dark_malt": (SACK, {"H": (160, 130, 92), "U": (142, 112, 76), "W": (152, 122, 86), "b": (114, 88, 58),
                         "R": (168, 138, 98), "r": (134, 106, 72),
                         "y": (140, 102, 70), "t": (92, 60, 38), "m": (56, 36, 22)}),
}

# A dimpled glass stein: a frothy head spilling over with a drip, beer with dimple facets, a glass highlight and a
# thick glass handle. '*' marks bubbles (a clear lager) or haze (an ale).
MUG = """
...kkkkk........
..kFFFFFkkk.....
.kFFFfFFFFFk....
kFFfFFFFFfFFk...
kfFFFFfFFFFfk...
.kofffooffok....
.kGhf11112gkkkk.
.kGho11*23gkGGgk
.kGh112z23gkkGgk
.kGh112*23gk.Ggk
.kGh1z2223gk.Ggk
.kGh122*33gkkGgk
.kGh233z34gkGggk
.kGGGGGGGGgkkkk.
..kkkkkkkkk.....
................
"""
_rows = ["................"] * 5 + [".kGGGGGGGGgk...."] + MUG.strip("\n").split("\n")[6:]    # no foam: just the rim
MUG_EMPTY = "\n" + "\n".join(_rows).translate(str.maketrans("12*zof34", "eeeeeeEE")) + "\n"
# an iron bucket full of hot wort: a wide surface with foam bubbles, a band round the middle, steam through the handle
BUCKET = """
......s...S.....
.....S...s......
......s...S.....
....kkkkkkkk....
..kkiiiiiiiikk..
.ki11b1111b11ik.
.kI2111b11112Ik.
.kI3222222233Ik.
.kiiiiiiiiiiiik.
..kIiIIIIIIMMk..
..kIiIIIIIIMMk..
..knnnnnnnnnnk..
...kiIIIIIIMk...
...kiIIIIIIMk...
....kkkkkkkk....
................
"""
BEER_ICON_COLOURS = {   # light, mid, dark, darkest, bubble
    "straw": [(238, 204, 104), (214, 168, 64), (184, 134, 44), (156, 108, 32), (252, 240, 190)],
    "gold": [(238, 180, 58), (208, 142, 30), (172, 104, 18), (140, 80, 12), (252, 222, 140)],
    "copper": [(190, 98, 48), (150, 62, 26), (112, 40, 16), (84, 28, 10), (226, 152, 104)],
    "dark": [(76, 50, 32), (48, 31, 21), (32, 20, 13), (20, 12, 8), (138, 100, 70)],
}
GLASS_EDGE = (216, 238, 244)


def brewing_icons(assets):
    """Writes the icons and their item models; returns them for previews. Barley, Hops, Malt, Dark Malt and the
    Beer Journal get item definitions here; the Bucket of Wort selects its colour on strings[0]; the mug icons are used by the
    mug's own item definition (brewing_models.py), flat in the inventory and 3D everywhere else."""
    sprites = []

    def emit(name, art, colors, item=True):
        px = pixels(art, {**COLORS, **colors})
        sprites.append(px)
        write(os.path.join(assets, "textures", "item", f"{name}.png"), png(px))
        write(os.path.join(assets, "models", "item", f"{name}.json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"beer:item/{name}"}})
        if item:
            write(os.path.join(assets, "items", f"{name}.json"),
                  {"model": {"type": "minecraft:model", "model": f"beer:item/{name}"}})

    for name, (art, colors) in BREW_ICONS.items():
        emit(name, art, colors)
    iron = {"i": (214, 214, 220), "I": (176, 176, 184), "M": (118, 118, 126), "n": (98, 98, 106),
            "s": (236, 236, 232), "S": (204, 204, 200)}
    for colour, (light, mid, dark, _, bubble) in BEER_ICON_COLOURS.items():
        foam = (204, 176, 132) if colour == "dark" else (240, 232, 212)
        emit(f"wort_bucket_{colour}", BUCKET, {**iron, "1": light, "2": mid, "3": dark, "b": foam}, item=False)
    write(os.path.join(assets, "items", "wort_bucket.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
        "cases": [{"when": c, "model": {"type": "minecraft:model", "model": f"beer:item/wort_bucket_{c}"}}
                  for c in BEER_ICON_COLOURS],
        "fallback": {"type": "minecraft:model", "model": "beer:item/wort_bucket_gold"}}})
    emit("mug_empty", MUG_EMPTY, {"e": (204, 228, 236), "E": (178, 206, 218), "G": (228, 244, 250),
                                  "g": (170, 198, 210), "h": (252, 254, 255)}, item=False)
    for colour, (light, mid, dark, darkest, bubble) in BEER_ICON_COLOURS.items():
        foam = ({"F": (232, 212, 174), "f": (206, 178, 134), "o": (170, 138, 98)} if colour == "dark" else
                {"F": (252, 250, 244), "f": (234, 228, 214), "o": (206, 196, 176)})
        edge = tuple((a + b) // 2 for a, b in zip(GLASS_EDGE, light))
        shade = tuple((a + b) // 2 for a, b in zip(GLASS_EDGE, dark))
        base = {**foam, "1": light, "2": mid, "3": dark, "4": darkest, "G": edge, "g": shade, "h": (252, 254, 255)}
        emit(f"beer_{colour}", MUG, {**base, "*": mid, "z": bubble}, item=False)     # hazy ale: light specks
        emit(f"beer_{colour}_clear", MUG, {**base, "*": bubble, "z": mid}, item=False)  # clear lager: bubbles
    return sprites


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", help="also write a scaled-up sheet of every icon")
    args = parser.parse_args(argv)
    sprites = brewing_icons(os.path.join(ROOT, "resourcepack", "assets", "beer"))
    if args.preview:
        preview(args.preview, sprites)


if __name__ == "__main__":
    main()
