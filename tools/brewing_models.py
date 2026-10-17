#!/usr/bin/env python3
"""Generates the Beer brewing furniture and mugs in a vanilla style (stdlib only; run with `python3 -I`).

    python3 -I beer/tools/brewing_models.py [--lineup <out.bbmodel>]

Vanilla style: 16 px per block (1 px per unit), whole-pixel boxes, no rounded stepping, flat textures with 4 or 5
tones. Think cauldron, composter, lantern.

Writes, under beer/:
  models/brewing/{brew_kettle,fermenting_barrel,mug,table_mugs}.bbmodel   Blockbench projects, texture embedded
  resourcepack/assets/beer/
      textures/block/brewing.png                  64x64 atlas
      models/brewing/brew_kettle.json             a copper kettle on four feet, with a tap, loop handles and a paddle
      models/brewing/kettle_<liquid>.json         the surface: water, wort_straw, wort_gold, wort_copper, wort_dark
      models/brewing/barrel_<wood>.json           a barrel on its side on two chocks: pale or dark
      models/brewing/airlock_<state>.json         the glass airlock on top: idle or fermenting (liquid in it)
      models/brewing/barrel_drip.json             foam on the tap (ready)
      models/brewing/mug_<fill>.json              a 4x4x6 px glass mug: empty, or straw, gold, copper, dark (hazy ale)
                                                  and the same with _clear (lager)
      models/brewing/table_mug_<slot>_<fill>.json the same mug in one corner of a block (empty or a colour)
      items/brew_kettle.json                      composite: the kettle, then a select on strings[0] = liquid
      items/fermenting_barrel.json                composite: barrel by strings[0] = pale | dark; airlock by
                                                  strings[1] = fermenting; foam drip when strings[1] = ready
      items/mug.json                              select on strings[0] = <colour>[_clear]; anything else: empty mug;
                                                  in the inventory the flat icons from item_icons.py
      items/table_mugs.json                       composite: one select per slot, strings[slot] = empty | <colour>

The front of every piece is its south side (+Z), as with the other furniture. Faces sample the atlas "projected": a
face's texture window follows its position in the block, so walls line up and overlapping faces show the same pixels.
--lineup also writes a preview project with the fronts turned to -Z (for bbpreview's camera).
"""
import argparse
import base64
import json
import os
import struct
import uuid as _uuid
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ATLAS = 64
PX = 1                       # atlas pixels per model unit: vanilla density
UV = 16 / ATLAS
TEXTURE = "beer:block/brewing"

SPRITES = {
    "copper": (0, 0, 16, 16), "copper_inner": (16, 0, 16, 16),
    "staves_side_pale": (32, 0, 16, 16), "staves_side_dark": (48, 0, 16, 16),
    "staves_top_pale": (0, 16, 16, 16), "staves_top_dark": (16, 16, 16, 16),
    "end_pale": (32, 16, 12, 12), "end_dark": (44, 16, 12, 12), "iron": (56, 16, 8, 8), "chock": (56, 24, 8, 8),
    "water": (0, 32, 16, 16), "wort_straw": (16, 32, 16, 16), "wort_gold": (32, 32, 16, 16),
    "wort_copper": (48, 32, 16, 16), "wort_dark": (0, 48, 16, 16),
    "mug_empty": (16, 48, 4, 6), "mug_straw": (20, 48, 4, 6), "mug_gold": (24, 48, 4, 6), "mug_copper": (28, 48, 4, 6),
    "mug_dark": (32, 48, 4, 6), "mug_straw_clear": (36, 48, 4, 6), "mug_gold_clear": (40, 48, 4, 6),
    "mug_copper_clear": (44, 48, 4, 6), "mug_dark_clear": (48, 48, 4, 6),
    "foam": (52, 48, 4, 4), "foam_tan": (56, 48, 4, 4), "glass": (60, 48, 4, 4),
    "airlock_idle": (16, 54, 2, 3), "airlock_fermenting": (18, 54, 2, 3), "cork": (20, 54, 4, 4),
    "paddle": (24, 54, 2, 8), "table": (32, 56, 16, 8),
}

# close to vanilla copper: darkest (pits, seams) to brightest (sheen)
COPPER = [(110, 48, 34), (142, 66, 46), (172, 86, 60), (198, 106, 76), (222, 130, 98), (240, 160, 128)]
WOODS = {   # darkest (seams) to lightest (worn edges)
    "pale": [(82, 58, 32), (106, 78, 44), (128, 98, 58), (148, 116, 70), (168, 134, 84), (188, 154, 100)],   # oak-like
    "dark": [(30, 19, 9), (42, 28, 14), (54, 37, 19), (66, 46, 25), (80, 57, 31), (96, 70, 39)],             # dark oak-like
}
CHOCK = [(84, 60, 34), (108, 80, 46), (128, 98, 58)]                              # spruce-like
IRON = [(58, 58, 62), (80, 80, 86), (108, 108, 114), (140, 140, 146)]
CORK = [(130, 100, 64), (160, 128, 86)]
WATER = [(44, 82, 168), (56, 100, 196), (78, 126, 220)]
BEERS = {   # dark, mid, light, highlight
    "straw": [(176, 126, 40), (212, 166, 62), (234, 200, 98), (248, 232, 170)],
    "gold": [(160, 96, 16), (204, 138, 28), (232, 172, 50), (248, 214, 124)],
    "copper": [(98, 34, 14), (140, 58, 24), (176, 88, 40), (214, 140, 90)],
    "dark": [(24, 14, 10), (40, 26, 18), (62, 40, 26), (120, 84, 56)],
}
FOAM = [(218, 212, 198), (240, 236, 226), (252, 250, 244)]
FOAM_TAN = [(176, 142, 98), (204, 174, 128), (228, 206, 166)]
GLASS = (214, 236, 242)
GLASS_HI = (248, 254, 255)


def _hash(*v):
    h = 2166136261
    for x in v:
        h = ((h ^ (x & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    return h / 0xFFFFFFFF


# ------------------------------------------------------------------------------------------------ texture

def paint():
    px = [[(0, 0, 0, 0)] * ATLAS for _ in range(ATLAS)]

    def put(name, x, y, rgba):
        sx, sy, w, h = SPRITES[name]
        if 0 <= x < w and 0 <= y < h:
            px[sy + y][sx + x] = rgba if len(rgba) == 4 else (*rgba, 255)

    # copper: beaten metal like the vanilla copper block. Soft patches of two mid tones, broken diagonal sheen streaks
    # of different lengths, dark pits, a bright rolled rim with rivets under it, and a darker foot.
    streaks = {}
    for k in range(-16, 16):                             # one streak per diagonal x - y = k*5+..., random length
        if _hash(k, 3) > 0.25:
            start = int(_hash(k, 4) * 10)
            streaks[k] = (start, start + 3 + int(_hash(k, 5) * 6))
    for y in range(16):
        for x in range(16):
            t = 3 if _hash(x // 3, y // 2, 11) > 0.45 else 2
            diagonal = x - y
            if diagonal % 5 == 0 and diagonal // 5 in streaks:
                lo, hi = streaks[diagonal // 5]
                if lo <= y < hi:
                    t = 5 if y == lo + 1 else 4
            elif (diagonal - 1) % 5 == 0 and (diagonal - 1) // 5 in streaks and streaks[(diagonal - 1) // 5][0] <= y < streaks[(diagonal - 1) // 5][1]:
                t = min(t + 1, 4)                         # the streak's soft edge
            if _hash(x, y, 13) > 0.93:
                t = 1                                     # pits
            # the kettle's walls run from y 14 (sprite row 2) down to y 2 (row 14)
            if y == 2:
                t = 5 if x % 4 else 4                     # rolled rim
            elif y == 3:
                t = 1                                     # its shadow
            elif y == 4 and x % 5 == 2:
                t = 5                                     # rivet heads
            elif y == 5 and x % 5 == 2:
                t = 1                                     # and their shadows
            elif y == 9:
                t = 4 if x % 5 == 2 else 2                # a seam with rivets round the middle
            elif y >= 12:
                t = max(1, t - 1)                         # soot near the fire
            put("copper", x, y, COPPER[t])
            # inside: darker, hammered, with a faint bright dent here and there
            u = 1 if _hash(x // 2, y // 2, 17) > 0.5 else 0
            if _hash(x, y, 19) > 0.9:
                u = 2
            if _hash(x, y, 23) > 0.97:
                u = 3
            put("copper_inner", x, y, COPPER[u])
    # barrel staves: 4 px boards, each its own shade, with broken grain lines running along the board, the odd knot,
    # a dark seam and a worn lighter edge. Side boards run across, top boards down.
    def grain(along, across, width, salt):
        """Wood tone (0..5) of a pixel on boards `width` px wide; grain runs along `along`."""
        board, pos = divmod(across, width)
        base = 2 + (1 if _hash(board, salt, 1) > 0.45 else 0) + (1 if _hash(board, salt, 2) > 0.8 else 0)
        if pos == width - 1:
            return 0                                           # seam between boards
        t = base
        run = int(2 + _hash(across, salt, 3) * 5)              # grain streaks of 2..6 px, offset per row
        streak = _hash(across, (along + int(_hash(across, salt, 4) * 7)) // run, salt, 5)
        if streak > 0.72:
            t -= 1
        elif streak < 0.12:
            t += 1
        if pos == 0 and _hash(board, along // 3, salt, 6) > 0.4:
            t += 1                                             # worn edge catching the light
        knot = _hash(board, along // 4, salt, 7) > 0.9 and 0 < pos < width - 1
        if knot and along % 4 == 1:
            t = 0
        elif knot and along % 4 in (0, 2):
            t = max(1, t - 1)
        return max(0, min(5, t))

    for wood, tones in WOODS.items():
        for v in range(16):
            for u in range(16):
                put(f"staves_side_{wood}", u, v, tones[grain(u, v, 4, 11)])
                put(f"staves_top_{wood}", v, u, tones[grain(u, v, 4, 13)])
        # end cap: upright 3 px boards with grain running up and down, inside a dark worn rim
        for y in range(12):
            for x in range(12):
                t = grain(y, x - 1, 3, 17) if 0 < x < 11 else 0
                if x in (0, 11) or y in (0, 11):
                    t = 0 if _hash(x, y, 19) > 0.3 else 1
                put(f"end_{wood}", x, y, tones[t])
    for y in range(8):
        for x in range(8):
            put("iron", x, y, IRON[3] if y == 0 else IRON[0] if y == 7 else IRON[2 if (x + y) % 4 else 1])
            put("chock", x, y, CHOCK[0] if y % 4 == 3 or _hash(x // 2, y, 29) > 0.85 else CHOCK[2] if y % 4 == 0 else CHOCK[1])
    for y in range(4):
        for x in range(4):
            put("cork", x, y, CORK[(x + y) % 2])
    for y in range(8):
        for x in range(2):
            put("paddle", x, y, CHOCK[2 - x])
    for y in range(8):
        for x in range(16):
            put("table", x, y, WOODS["pale"][0] if y % 4 == 3 else WOODS["pale"][2])
    # liquids: flat with a few lighter ripples (water) or foam flecks (wort)
    for y in range(16):
        for x in range(16):
            ripple = (x + 2 * (y // 4)) % 7 == 0 and y % 4 == 1
            put("water", x, y, WATER[2] if ripple else WATER[1] if (x * 3 + y) % 5 else WATER[0])
            for colour, tones in BEERS.items():
                fleck = _hash(x, y, len(colour)) > 0.9
                foam = FOAM_TAN[1] if colour == "dark" else FOAM[1]
                put(f"wort_{colour}", x, y, foam if fleck else tones[2] if (x + y * 2) % 7 == 0 else tones[1])
    # mugs (4 x 6 per side): glass edges left and right, a rim on top, the beer in the middle with a highlight
    # column; a hazy ale has a lighter band, a clear lager two bubbles. The empty mug is frosted glass.
    for y in range(6):
        for x in range(4):
            edge = x in (0, 3)
            put("mug_empty", x, y, GLASS_HI if (x == 1 and 0 < y < 5) else GLASS if edge or y in (0, 5) else (188, 214, 222))
            for colour, tones in BEERS.items():
                foam = FOAM_TAN if colour == "dark" else FOAM
                mix = tuple((a + b) // 2 for a, b in zip(GLASS, tones[3]))   # glass seen over the beer
                if y == 5:
                    c = GLASS
                elif y == 0 and x in (1, 2):
                    c = foam[1] if x == 1 else foam[0]                    # foam running over the rim
                elif edge:
                    c = mix if x == 0 else tones[2]
                elif x == 1:
                    c = tones[3]
                else:
                    c = tones[2] if y < 3 else tones[1]
                put(f"mug_{colour}", x, y, c)
                if x == 2 and y in (2, 4):
                    c = tones[3]                                          # rising bubbles in a clear lager
                put(f"mug_{colour}_clear", x, y, c)
    for y in range(4):
        for x in range(4):
            put("foam", x, y, FOAM[2] if (x, y) in ((1, 1), (2, 2)) else FOAM[1] if (x + y) % 3 else FOAM[0])
            put("foam_tan", x, y, FOAM_TAN[2] if (x, y) in ((1, 1), (2, 2)) else FOAM_TAN[1] if (x + y) % 3 else FOAM_TAN[0])
            put("glass", x, y, GLASS if x in (0, 3) or y in (0, 3) else GLASS_HI)
    for y in range(3):
        for x in range(2):
            put("airlock_idle", x, y, GLASS_HI if x == 0 else GLASS)
            put("airlock_fermenting", x, y, (GLASS_HI if x == 0 else GLASS) if y == 0 else BEERS["gold"][2 - x])
    return px


def png(px):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(px[0]), len(px), 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# ------------------------------------------------------------------------------------------------ geometry

FACE_AXES = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}
ALL = ("north", "south", "east", "west", "up", "down")
SIDES = ("north", "south", "east", "west")


def _fit(lo, hi, size):
    """Shifts a pixel window [lo, hi) into 0..size; stretches it to the whole sprite if it is wider."""
    if hi - lo > size:
        return 0, size
    k = (lo // size) if lo >= 0 else -((-lo + size - 1) // size)
    lo, hi = lo - k * size, hi - k * size
    if hi > size:
        lo, hi = lo - (hi - size), size
    return lo, hi


class Model:
    def __init__(self, name):
        self.name = name
        self.elements = []

    def box(self, name, frm, to, sprite, faces=ALL, uv="proj", at=(0, 16, 0), rot=None):
        """A box. `sprite` is a name or {face: name}. uv: "proj" samples the sprite at the face's place in the block
        (`at` = x, top y, z of the sprite's corner), "full" maps the whole sprite onto the face. rot: (axis, angle,
        origin), vanilla style (one axis, a multiple of 22.5 degrees)."""
        out = {}
        for face in faces:
            name_ = sprite[face] if isinstance(sprite, dict) else sprite
            sx, sy, w, h = SPRITES[name_]
            a, b = FACE_AXES[face]
            if uv == "full":
                out[face] = (sx, sy, sx + w, sy + h)
                continue
            u1, u2 = _fit((frm[a] - at[a]) * PX, (to[a] - at[a]) * PX, w)
            if b == 1:
                v1, v2 = _fit((at[1] - to[1]) * PX, (at[1] - frm[1]) * PX, h)
            else:
                v1, v2 = _fit((frm[2] - at[2]) * PX, (to[2] - at[2]) * PX, h)
            out[face] = (sx + u1, sy + v1, sx + u2, sy + v2)
        self.elements.append({"name": name, "from": list(frm), "to": list(to), "faces": out, "rot": rot})


def kettle():
    """A square copper kettle like a cauldron: four feet, 2 px walls, a floor inside, a tap at the front, a loop
    handle on each side and a wooden paddle leaning in it."""
    m = Model("brew_kettle")
    for x in (2, 12):
        for z in (2, 12):
            m.box(f"foot_{x}_{z}", [x, 0, z], [x + 2, 2, z + 2], "copper_inner")
    walls = {"north": ([1, 2, 1], [15, 14, 3]), "south": ([1, 2, 13], [15, 14, 15]),
             "west": ([1, 2, 3], [3, 14, 13]), "east": ([13, 2, 3], [15, 14, 13])}
    inner = {"north": "south", "south": "north", "west": "east", "east": "west"}
    for name, (frm, to) in walls.items():
        m.box(f"wall_{name}", frm, to, {f: "copper_inner" if f == inner[name] else "copper" for f in ALL})
    m.box("bottom", [3, 2, 3], [13, 5, 13], {"up": "copper_inner", "down": "copper", "north": "copper",
                                              "south": "copper", "east": "copper", "west": "copper"}, faces=("up", "down"))
    m.box("tap", [7, 4, 15], [9, 6, 17], "copper")
    m.box("spout", [7, 3, 16], [9, 4, 17], "copper_inner", faces=SIDES + ("down",))
    for side, x0 in (("west", -1), ("east", 15)):
        m.box(f"handle_{side}_a", [x0, 11, 5], [x0 + 2, 12, 6], "iron", uv="full")
        m.box(f"handle_{side}_b", [x0, 11, 10], [x0 + 2, 12, 11], "iron", uv="full")
        bar = x0 if side == "west" else x0 + 1
        m.box(f"handle_{side}_bar", [bar, 11, 6], [bar + 1, 12, 10], "iron", uv="full")
    # rests on the floor left of centre and leans right over the rim; at the rim (y 14) it is near x 9, well inside
    lean = ("z", -22.5, [5.5, 5, 7.5])
    m.box("paddle", [5, 5, 7], [6, 20, 8], "paddle", rot=lean, uv="full")
    m.box("paddle_blade", [4, 5, 7], [7, 9, 8], "paddle", rot=lean, uv="full")
    return m


def kettle_liquid(liquid):
    m = Model(f"kettle_{liquid}")
    m.box("surface", [3, 12, 3], [13, 12, 13], liquid, faces=("up",))
    return m


def barrel(wood):
    """A barrel on its side along Z, its corners cut by one pixel, two iron hoops, two chocks under it, and a copper
    tap in the front end."""
    m = Model(f"barrel_{wood}")
    skin = {"east": f"staves_side_{wood}", "west": f"staves_side_{wood}", "up": f"staves_top_{wood}",
            "down": f"staves_top_{wood}", "north": f"end_{wood}", "south": f"end_{wood}"}
    # The cross sections are built from boxes that never overlap, and every hoop stands 1 px proud of the body, so no
    # two faces share a plane where they can be seen (that z-fought: the hoops flickered through the staves).
    for i, (x0, y0, x1, y1) in enumerate(((3, 2, 13, 14), (2, 3, 3, 13), (13, 3, 14, 13))):
        m.box(f"body_{i}", [x0, y0, 2], [x1, y1, 14], skin, at=(2, 14, 0))
    for k, z0 in enumerate((3, 11)):
        for i, (x0, y0, x1, y1) in enumerate(((3, 1, 13, 15), (2, 2, 3, 14), (13, 2, 14, 14), (1, 3, 2, 13), (14, 3, 15, 13))):
            m.box(f"hoop_{k}_{i}", [x0, y0, z0], [x1, y1, z0 + 2], "iron", uv="full")
    for k, z0 in enumerate((5, 9)):
        m.box(f"chock_{k}", [2, 0, z0], [14, 2, z0 + 2], "chock")
        m.box(f"chock_{k}_left", [2, 2, z0], [3, 3, z0 + 2], "chock")
        m.box(f"chock_{k}_right", [13, 2, z0], [14, 3, z0 + 2], "chock")
    m.box("tap", [7, 4, 14], [9, 6, 16], "copper")
    m.box("spout", [7, 3, 15], [9, 4, 16], "copper_inner", faces=SIDES + ("down",))
    m.box("bung", [7, 14, 7], [9, 15, 9], "cork", uv="full")
    return m


def airlock(state):
    m = Model(f"airlock_{state}")
    m.box("airlock", [7, 15, 7], [9, 18, 9], f"airlock_{state}", faces=SIDES, uv="full")
    m.box("airlock_cap", [7, 18, 7], [9, 19, 9], "cork", faces=SIDES + ("up",), uv="full")
    return m


def barrel_drip():
    m = Model("barrel_drip")
    m.box("drip", [7, 2, 15], [8, 3, 16], "foam", uv="full")
    return m


MUG_TONES = ("straw", "gold", "copper", "dark")
QUARTERS = [(4, 4, 0), (12, 4, 1), (4, 12, 3), (12, 12, 2)]     # (centre x, centre z, quarter turns)


def mug(m, cx, cz, turns, fill, prefix=""):
    """A 4 x 4 x 6 px glass mug centred on (cx, cz), handle to the east before `turns` quarter turns, foam on top.
    fill: None (empty) or a colour, '_clear' for lager."""
    def turn(x, z):
        for _ in range(turns):
            x, z = -z, x
        return x, z

    def add(name, a, b, sprite, faces=ALL):
        (x0, z0), (x1, z1) = turn(a[0], a[2]), turn(b[0], b[2])
        frm = [cx + min(x0, x1), a[1], cz + min(z0, z1)]
        to = [cx + max(x0, x1), b[1], cz + max(z0, z1)]
        m.box(prefix + name, frm, to, sprite, faces=faces, uv="full")

    body = f"mug_{fill}" if fill else "mug_empty"
    add("body", [-2, 0, -2], [2, 6, 2], {f: body for f in SIDES} | {"up": "glass", "down": "glass"},
        faces=SIDES + ("down",) + (() if fill else ("up",)))
    if fill:
        add("foam", [-2, 6, -2], [2, 7, 2], "foam_tan" if fill.startswith("dark") else "foam")
    add("handle_top", [2, 4, -1], [3, 5, 1], "glass")
    add("handle_low", [2, 1, -1], [3, 2, 1], "glass")
    add("handle_grip", [3, 1, -1], [4, 5, 1], "glass")


def mug_model(fill):
    m = Model(f"mug_{fill or 'empty'}")
    mug(m, 8, 8, 0, fill)
    return m


def table_mug(slot, fill):
    m = Model(f"table_mug_{slot}_{fill or 'empty'}")
    cx, cz, turns = QUARTERS[slot]
    mug(m, cx, cz, turns, fill)
    return m


# ------------------------------------------------------------------------------------------------ output

DISPLAY = {
    "brew_kettle": {"gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.55, 0.55, 0.55]}},
    "mug": {"gui": {"rotation": [20, -35, 0], "translation": [0, 1.5, 0], "scale": [1.6, 1.6, 1.6]},
            "ground": {"translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
            # Third person, the hand's frame is x out to the side, y forward, z up the arm (vanilla ItemInHandLayer): stand
            # the mug up (model y to z), point the handle back into the fist (model x to -y) and move the grip onto it.
            "thirdperson_righthand": {"rotation": [90, -90, 0], "translation": [0, 2.25, 5.5], "scale": [0.9, 0.9, 0.9]},
            # First person, x right, y up, z towards the camera: the handle turns back and right, towards the hand.
            "firstperson_righthand": {"rotation": [0, -45, 0], "translation": [1, 2.5, 0], "scale": [0.9, 0.9, 0.9]},
            "head": {"translation": [0, 14, 0], "scale": [1.5, 1.5, 1.5]}},
}


FACE_DIRS = {"north": (2, -1), "south": (2, 1), "west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1)}


def visible_coplanar(elements):
    """Pairs of unrotated elements with faces in the same plane, facing the same way and overlapping where nothing
    buries them: those z-fight in game (the barrel hoops once flickered through the staves)."""
    flat = [e for e in elements if not e.get("rotation")]

    def buried(pt):
        return any(all(e["from"][i] + 1e-6 < pt[i] < e["to"][i] - 1e-6 for i in range(3)) for e in flat)

    found = []
    for i, e1 in enumerate(flat):
        for e2 in flat[i + 1:]:
            for face in set(e1["faces"]) & set(e2["faces"]):
                a, d = FACE_DIRS[face]
                plane = e1["to"][a] if d > 0 else e1["from"][a]
                if plane != (e2["to"][a] if d > 0 else e2["from"][a]):
                    continue
                u, v = [k for k in range(3) if k != a]
                lo = (max(e1["from"][u], e2["from"][u]), max(e1["from"][v], e2["from"][v]))
                hi = (min(e1["to"][u], e2["to"][u]), min(e1["to"][v], e2["to"][v]))
                if lo[0] >= hi[0] or lo[1] >= hi[1]:
                    continue
                for pu in (lo[0] + 0.25, (lo[0] + hi[0]) / 2, hi[0] - 0.25):
                    for pv in (lo[1] + 0.25, (lo[1] + hi[1]) / 2, hi[1] - 0.25):
                        pt = [0.0, 0.0, 0.0]
                        pt[a], pt[u], pt[v] = plane + d * 0.01, pu, pv
                        if not buried(pt) and (e1["name"], e2["name"], face) not in found:
                            found.append((e1["name"], e2["name"], face))
    return found


def to_java(m, display=None):
    elements = []
    for e in m.elements:
        el = {"name": e["name"], "from": e["from"], "to": e["to"],
              "faces": {f: {"uv": [round(v * UV, 4) for v in uv], "texture": "#0"} for f, uv in e["faces"].items()}}
        if e["rot"]:
            axis, angle, origin = e["rot"]
            el["rotation"] = {"origin": origin, "axis": axis, "angle": angle}
        elements.append(el)
    fights = visible_coplanar(elements)
    assert not fights, f"{m.name}: faces that would z-fight: {fights}"
    out = {"credit": "beer/tools/brewing_models.py", "parent": "minecraft:block/block",
           "textures": {"0": TEXTURE, "particle": TEXTURE}, "elements": elements}
    if display:
        out["display"] = display
    return out


def uid(*parts):
    return str(_uuid.uuid5(_uuid.NAMESPACE_URL, "beer-brewing/" + "/".join(parts)))


def to_bbmodel(name, models, atlas_png, placements=None):
    """placements: per model (dx, dz, turned) to lay several out side by side; turned rotates it 180 degrees about
    its block centre so the front faces -Z (bbpreview's camera side)."""
    elements, outliner = [], []
    for gi, m in enumerate(models):
        dx, dz, turned = (placements or [(0, 0, False)] * len(models))[gi]

        def place(p):
            x, y, z = p
            if turned:
                x, z = 16 - x, 16 - z
            return [x + dx, y, z + dz]

        children = []
        for i, e in enumerate(m.elements):
            frm, to = place(e["from"]), place(e["to"])
            frm, to = [min(a, b) for a, b in zip(frm, to)], [max(a, b) for a, b in zip(frm, to)]
            faces = {}
            for f in ALL:
                src = {"north": "south", "south": "north", "east": "west", "west": "east"}.get(f, f) if turned else f
                faces[f] = {"uv": list(e["faces"][src]), "texture": 0} if src in e["faces"] else {"uv": [0, 0, 0, 0], "texture": None}
            el = {"name": e["name"], "box_uv": False, "rescale": False, "locked": False, "light_emission": 0,
                  "render_order": "default", "allow_mirror_modeling": True, "from": frm, "to": to,
                  "autouv": 0, "color": gi % 8, "origin": [8 + dx, 8, 8 + dz], "faces": faces, "type": "cube",
                  "uuid": uid(name, m.name, str(gi), str(i))}
            if e["rot"]:
                axis, angle, origin = e["rot"]
                if turned and axis in ("x", "z"):
                    angle = -angle
                el["origin"] = place(origin)
                el["rotation"] = [angle if axis == "x" else 0, angle if axis == "y" else 0, angle if axis == "z" else 0]
            elements.append(el)
            children.append(el["uuid"])
        outliner.append({"name": m.name, "origin": place([8, 0, 8]), "color": gi % 8, "uuid": uid(name, m.name, str(gi)),
                         "export": True, "mirror_uv": False, "isOpen": False, "locked": False, "visibility": True,
                         "autouv": 0, "children": children})
    texture = {"path": "", "name": "brewing.png", "folder": "", "namespace": "", "id": "0", "group": "",
               "width": ATLAS, "height": ATLAS, "uv_width": ATLAS, "uv_height": ATLAS, "particle": True,
               "use_as_default": False, "layers_enabled": False, "render_mode": "default", "render_sides": "auto",
               "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False,
               "visible": True, "internal": True, "saved": False, "uuid": uid(name, "texture"),
               "source": "data:image/png;base64," + base64.b64encode(atlas_png).decode()}
    return {"meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False}, "name": name,
            "parent": "", "ambientocclusion": True, "front_gui_light": False, "visible_box": [1, 1, 0],
            "resolution": {"width": ATLAS, "height": ATLAS}, "elements": elements, "outliner": outliner,
            "textures": [texture], "animations": []}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(data, bytes):
        with open(path, "wb") as handle:
            handle.write(data)
    else:
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(data, handle, indent=1 if path.endswith(".bbmodel") else None,
                      separators=None if path.endswith(".bbmodel") else (",", ":"))


def ref(name):
    return {"type": "minecraft:model", "model": f"beer:brewing/{name}"}


def select(index, cases, fallback=None):
    return {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": index,
            "cases": [{"when": when, "model": model} for when, model in cases],
            "fallback": fallback or {"type": "minecraft:empty"}}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lineup", help="also write one .bbmodel with everything side by side, fronts to -Z")
    args = parser.parse_args(argv)
    assets = os.path.join(ROOT, "resourcepack", "assets", "beer")
    out_models = os.path.join(assets, "models", "brewing")
    for stale in os.listdir(out_models) if os.path.isdir(out_models) else ():
        os.remove(os.path.join(out_models, stale))             # names changed between versions; start clean
    atlas = png(paint())
    write(os.path.join(assets, "textures", "block", "brewing.png"), atlas)

    kettle_model = kettle()
    liquids = {name: kettle_liquid(name) for name in ("water", *(f"wort_{c}" for c in MUG_TONES))}
    barrels = {wood: barrel(wood) for wood in WOODS}
    airlocks = {state: airlock(state) for state in ("idle", "fermenting")}
    drip = barrel_drip()
    fills = [None, *MUG_TONES, *(f"{c}_clear" for c in MUG_TONES)]
    mugs = {fill: mug_model(fill) for fill in fills}
    table = {(slot, fill): table_mug(slot, fill) for slot in range(4) for fill in (None, *MUG_TONES)}

    models = {}
    for m in (kettle_model, *liquids.values()):
        models[m.name] = to_java(m, DISPLAY["brew_kettle"])
    for m in (*barrels.values(), *airlocks.values(), drip, *table.values()):
        models[m.name] = to_java(m)
    for m in mugs.values():
        models[m.name] = to_java(m, DISPLAY["mug"])
    for name, java in models.items():
        for e in java["elements"]:
            assert all(-16 <= v <= 32 for v in e["from"] + e["to"]), (name, e["name"])
        write(os.path.join(out_models, f"{name}.json"), java)

    write(os.path.join(assets, "items", "brew_kettle.json"), {"model": {"type": "minecraft:composite", "models": [
        ref("brew_kettle"), select(0, [(name, ref(f"kettle_{name}")) for name in liquids])]}})
    write(os.path.join(assets, "items", "fermenting_barrel.json"), {"model": {"type": "minecraft:composite", "models": [
        select(0, [("dark", ref("barrel_dark"))], ref("barrel_pale")),
        select(1, [("fermenting", ref("airlock_fermenting"))], ref("airlock_idle")),
        select(1, [("ready", ref("barrel_drip"))])]}})
    # the inventory shows the flat icon (item_icons.py); hands, frames and the ground show the 3D mug
    icon = lambda name: {"type": "minecraft:model", "model": f"beer:item/{name}"}
    write(os.path.join(assets, "items", "mug.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": ["gui"], "model": select(0, [(fill, icon(f"beer_{fill}")) for fill in fills if fill],
                                                     icon("mug_empty"))}],
        "fallback": select(0, [(fill, ref(f"mug_{fill}")) for fill in fills if fill], ref("mug_empty"))}})
    write(os.path.join(assets, "items", "table_mugs.json"), {"model": {"type": "minecraft:composite", "models": [
        select(slot, [(fill or "empty", ref(f"table_mug_{slot}_{fill or 'empty'}")) for fill in (None, *MUG_TONES)])
        for slot in range(4)]}})

    out = os.path.join(ROOT, "models", "brewing")
    write(os.path.join(out, "brew_kettle.bbmodel"), to_bbmodel("brew_kettle", [kettle_model, liquids["wort_gold"]], atlas))
    write(os.path.join(out, "fermenting_barrel.bbmodel"),
          to_bbmodel("fermenting_barrel", [barrels["pale"], airlocks["fermenting"]], atlas))
    write(os.path.join(out, "mug.bbmodel"), to_bbmodel("mug", [mugs["gold"]], atlas))
    write(os.path.join(out, "table_mugs.bbmodel"),
          to_bbmodel("table_mugs", [table[(s, c)] for s, c in zip(range(4), MUG_TONES)], atlas))
    print(f"kettle {len(kettle_model.elements)} elements, barrel {len(barrels['pale'].elements)}, "
          f"mug {len(mugs['gold'].elements)}; {len(models)} models")

    if args.lineup:
        # kettle (gold wort) | kettle (water) | pale barrel, fermenting | dark barrel, ready | 4 mugs | table
        groups = [[kettle_model, liquids["wort_gold"]], [kettle_model, liquids["water"]],
                  [barrels["pale"], airlocks["fermenting"]], [barrels["dark"], airlocks["idle"], drip]]
        parts, places = [], []
        for slot, group in enumerate(groups):
            for m in group:
                parts.append(m)
                places.append((-(slot - 2.5) * 20, 0, True))
        loose = Model("lineup_mugs")
        for i, fill in enumerate(("copper", "dark", "straw", "gold_clear")):
            cx, cz, _ = QUARTERS[i]
            mug(loose, cx, cz, 0, fill, prefix=f"m{i}_")
        parts.append(loose)
        places.append((-(4 - 2.5) * 20, 0, True))
        top = Model("lineup_table")
        top.box("table", [0, -3, 0], [16, 0, 16], "table", at=(0, 0, 0))
        parts.append(top)
        places.append((-(5 - 2.5) * 20, 0, True))
        for slot, fill in zip(range(4), (None, "gold", "copper", "straw")):
            parts.append(table[(slot, fill)])
            places.append((-(5 - 2.5) * 20, 0, True))
        write(args.lineup, to_bbmodel("brewing_lineup", parts, atlas, places))


if __name__ == "__main__":
    main()
