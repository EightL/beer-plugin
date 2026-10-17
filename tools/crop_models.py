#!/usr/bin/env python3
"""Generates the brewing crops, barley and hops, in a vanilla style (stdlib only; run with `python3 -I`).

    python3 -I beer/tools/crop_models.py [--lineup <out.bbmodel>]

Vanilla style: 16 px per block and flat sprites, like the wheat and sweet berry textures. Barley is four planes in a
# pattern (the vanilla crop model) with one 16 x 16 picture per stage. Hops are a 2 x 2 px pole three blocks tall, a
# of vine planes round it per block, and, when ripe, chunky cones hanging off it like cocoa pods.

Writes, under beer/:
  models/barley/<stage>.bbmodel, models/hops/<stage>.bbmodel     Blockbench projects, texture embedded
  resourcepack/assets/beer/
      textures/block/plant/{barley,hops}.png     64x64 atlases
      models/plant/barley/<stage>.json           8 stages: planted sprout seedling tillering jointing heading ripening ripe
      models/plant/hops/<stage>.json             6 stages: planted shoots climbing leafy burrs ripe
      items/plant.json                           one item; custom_model_data strings[0] = "<crop>/<stage>"
  resourcepack/pack.mcmeta

Space: 16 units = 1 block, the plant's own block spans y 0..16 and farmland's top is y = -1 (the crop planes start
there, as in vanilla). Hops reach y = 47: an item model only reaches y = 32, so the hops models are written 15 units
lower and the plugin raises the display by 15/16 of a block.
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
UV = 16 / ATLAS
GROUND = -1
HOPS_SHIFT = -15

BARLEY_STAGES = ["planted", "sprout", "seedling", "tillering", "jointing", "heading", "ripening", "ripe"]
HOPS_STAGES = ["planted", "shoots", "climbing", "leafy", "burrs", "ripe"]

SPRITES = {f"barley_{s}": (16 * (i % 4), 16 * (i // 4), 16, 16) for i, s in enumerate(BARLEY_STAGES)}
SPRITES.update({
    "vine_shoots": (0, 0, 12, 16), "vine_half": (12, 0, 12, 16), "vine_full": (24, 0, 12, 16), "vine_top": (36, 0, 12, 16),
    "vine_full_burrs": (0, 16, 12, 16), "vine_top_burrs": (12, 16, 12, 16), "vine_top_ripe": (24, 16, 12, 16),
    "pole": (48, 0, 2, 16), "pole_end": (50, 0, 2, 2), "twine": (52, 0, 2, 2), "dirt": (48, 16, 4, 4),
    "cone": (54, 0, 3, 4), "cone_end": (57, 0, 3, 3), "stalk": (60, 0, 1, 1),
})

# barley: blue-green while growing, straw-gold when ripe
LEAF = [(34, 76, 46), (50, 104, 60), (74, 134, 76), (106, 164, 96)]
EAR_GREEN = [(62, 112, 56), (94, 146, 76), (130, 174, 102)]
AWN_GREEN = (170, 202, 134)
STEM_TURN = [(120, 122, 50), (150, 150, 66)]
EAR_TURN = [(132, 128, 52), (168, 162, 74), (200, 192, 106)]
AWN_TURN = (216, 212, 152)
STEM_RIPE = [(150, 112, 48), (188, 150, 76)]
EAR_RIPE = [(170, 126, 52), (210, 168, 84), (234, 204, 122)]
AWN_RIPE = (244, 230, 172)
LEAF_DRY = [(136, 104, 50), (170, 136, 70)]
# hops
HOP_LEAF = [(30, 72, 30), (46, 102, 40), (68, 132, 52), (98, 160, 68)]
BINE = (84, 100, 46)
SHOOT = [(100, 46, 78), (146, 78, 108), (176, 112, 132)]
BURR = [(198, 212, 158), (234, 238, 206)]
CONE = [(118, 150, 58), (156, 190, 86), (196, 222, 124), (228, 240, 170)]
LUPULIN = (232, 200, 70)
POLE = [(76, 54, 32), (102, 76, 46), (124, 96, 60)]
TWINE = (196, 176, 124)
DIRT = [(92, 62, 40), (116, 82, 52), (136, 98, 66)]


# ------------------------------------------------------------------------------------------------ drawing

def line(c, x0, y0, x1, y1, col):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        c[(x0, y0)] = col
        if (x0, y0) == (x1, y1):
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def blade(c, x, h, lean, tones, base=15):
    """A grass-like blade from the ground up `h` pixels, shifting one pixel sideways every |lean| pixels (0: upright).
    Darker at the foot, lighter at the tip."""
    for i in range(h):
        shift = 0 if lean == 0 else (i // abs(lean)) * (1 if lean > 0 else -1)
        c[(x + shift, base - i)] = tones[1] if i < h * 0.4 else tones[2] if i < h - 1 else tones[3]


def ear_up(c, x, top, tones, awn):
    """An upright ear, 2 px wide and 4 tall, with awns fanning up from it."""
    for i in range(4):
        c[(x, top + i)] = tones[2] if i % 2 == 0 else tones[1]
        c[(x + 1, top + i)] = tones[1] if i % 2 == 0 else tones[0]
    for k in range(1, 3):
        c[(x - (k + 1) // 2, top - k)] = awn
        c[(x + 1 + (k + 1) // 2, top - k)] = awn
    c[(x, top - 1)] = awn


def ear_tilted(c, x, top, d, tones, awn):
    """An ear leaning over at 45 degrees towards d (-1 or +1) from the stalk top at (x, top)."""
    for i in range(4):
        c[(x + d * i, top - i)] = tones[2] if i % 2 else tones[1]
        c[(x + d * (i + 1), top - i)] = tones[1] if i % 2 else tones[0]
    for j in range(2):
        c[(x + d * (5 + j), top - 4 - j)] = awn
        c[(x + d * (4 + j), top - 5 - j)] = awn


def ear_nodding(c, x, top, d, tones, awn, length=5):
    """The stalk arches over towards d and the ear hangs down from it, awns trailing below."""
    c[(x + d, top - 1)] = tones[1]
    for i in range(length):
        c[(x + d, top + i)] = tones[2] if i % 2 == 0 else tones[1]
        c[(x + 2 * d, top + i)] = tones[1] if i % 2 == 0 else tones[0]
    for j in range(3):
        c[(x + 2 * d + d * (j // 2 + 1), top + length + j)] = awn
        c[(x + d - d * (j // 2), top + length + j)] = awn


def stalk(c, x, h, tones, base=15):
    for i in range(h):
        c[(x, base - i)] = tones[(i // 2) % len(tones)]


def paint_barley_stage(stage):
    """One 16 x 16 picture per stage, as dense as vanilla wheat: many stalks, leaves between them."""
    c = {}
    if stage == "planted":
        for x, h in ((3, 2), (8, 2), (12, 2)):
            blade(c, x, h, 0, LEAF)
        c[(9, 13)] = LEAF[3]
    elif stage == "sprout":
        for x, h, lean in ((1, 3, 0), (4, 4, -3), (7, 3, 0), (10, 4, 3), (13, 3, 0)):
            blade(c, x, h, lean, LEAF)
    elif stage == "seedling":
        for x, h in ((1, 5), (3, 6), (6, 7), (9, 6), (12, 7), (14, 5)):
            blade(c, x, h, 0, LEAF)
            blade(c, x + 1, h - 2, 3 if x % 2 else -3, LEAF)
    elif stage == "tillering":
        for x, h in ((1, 8), (3, 9), (5, 7), (7, 9), (9, 8), (11, 9), (13, 8), (15, 7)):
            blade(c, x, h, 0, LEAF)
            blade(c, x + (1 if x % 4 == 1 else -1), h - 3, 2 if x % 4 == 1 else -2, LEAF)
    elif stage == "jointing":
        for x, h in ((0, 10), (2, 11), (4, 10), (6, 12), (8, 11), (10, 12), (12, 10), (14, 11)):
            stalk(c, x, h, LEAF[1:3])
            c[(x, 15 - h)] = LEAF[3]
            line(c, x, 15 - h // 2, x + 1, 15 - h // 2 - 2, LEAF[2])        # a leaf off the stalk
        for x in (1, 5, 9, 13):
            blade(c, x, 6, -2 if x % 8 == 1 else 2, LEAF)
    elif stage == "heading":
        for x, h in ((0, 8), (3, 9), (6, 8), (9, 9), (12, 8), (14, 9)):
            stalk(c, x, h, LEAF[1:3])
            ear_up(c, x, 15 - h - 3, EAR_GREEN, AWN_GREEN)
        for x in (2, 5, 8, 11, 15):
            blade(c, x, 6, 2 if x % 2 else -2, LEAF)
    elif stage == "ripening":
        for x, h, d in ((1, 9, 1), (4, 8, -1), (7, 10, 1), (10, 8, -1), (13, 9, 1)):
            stalk(c, x, h, [LEAF[1], STEM_TURN[0], STEM_TURN[1]])
            ear_tilted(c, x, 15 - h, d, EAR_TURN, AWN_TURN)
        for x in (3, 9, 15):
            blade(c, x, 5, 2, LEAF)
    else:   # ripe
        for x, h, d in ((1, 10, 1), (4, 11, -1), (6, 9, 1), (9, 11, 1), (12, 10, -1), (14, 9, -1)):
            stalk(c, x, h, STEM_RIPE)
            ear_nodding(c, x, 15 - h, d, EAR_RIPE, AWN_RIPE)
        for x, lean in ((3, -2), (8, 2), (11, -3), (15, -2)):
            blade(c, x, 4, lean, [LEAF_DRY[0], LEAF_DRY[0], LEAF_DRY[1], LEAF_DRY[1]])
    return c


# hop leaf: a small three-lobed leaf pointing up (offsets from its stalk end; tone 1 edge, 2 fill, 3 vein, 0 stalk)
HOP_LEAF_STAMP = {(0, -4): 2, (-2, -3): 1, (0, -3): 3, (2, -3): 1, (-2, -2): 1, (-1, -2): 2, (0, -2): 3, (1, -2): 2,
                  (2, -2): 1, (-1, -1): 1, (0, -1): 2, (1, -1): 1, (0, 0): 0}
SMALL_LEAF_STAMP = {(0, -2): 2, (-1, -1): 1, (0, -1): 3, (1, -1): 1, (0, 0): 0}


def stamp(c, x, y, direction, small=False):
    """A hop leaf whose stalk end is at (x, y), pointing up, left or right."""
    for (dx, dy), t in (SMALL_LEAF_STAMP if small else HOP_LEAF_STAMP).items():
        if direction == "left":
            dx, dy = dy, -dx
        elif direction == "right":
            dx, dy = -dy, dx
        c[(x + dx, y + dy)] = HOP_LEAF[t] if t else BINE


def bine_path(c, y_top, y_bottom=15):
    """The bine twining up the middle: it crosses in front of the pole every four pixels."""
    for y in range(y_top, y_bottom + 1):
        c[(5 + (y // 4) % 2, y)] = BINE


def paint_vine(kind):
    c = {}
    if kind == "shoots":
        for x, h in ((2, 4), (6, 5), (9, 3)):
            for i in range(h):
                c[(x, 15 - i)] = SHOOT[0] if i < h - 1 else SHOOT[1]
            c[(x + 1, 15 - h)] = SHOOT[2]                                  # the hooked tip
        stamp(c, 4, 13, "up", small=True)
        return c
    base = kind.split("_")[0]
    top = {"half": 8, "full": 0, "top": 6}[base]
    bine_path(c, top)
    for y, side in ((13, "left"), (11, "right"), (9, "left"), (7, "right"), (5, "left"), (3, "right"), (1, "left")):
        if y >= top + 3:
            stamp(c, 4 if side == "left" else 7, y, side)
    for y in range(top + 4, 16, 4):                                        # small leaves in front of the bine
        stamp(c, 5 + (y // 4) % 2, y, "up", small=True)
    if base != "full":                                                     # the growing tip curls over
        c[(6, top - 1)] = BINE
        c[(7, top - 2)] = BINE
        c[(6, top - 3)] = HOP_LEAF[3]
        stamp(c, 5, top + 1, "up", small=True)
    if kind.endswith("burrs"):
        for x, y in ((3, 12), (8, 9), (2, 6), (9, 4), (3, 2)):
            if y > top + 1:
                c[(x, y)] = BURR[1]
                c[(x + 1, y)] = BURR[0]
                c[(x, y + 1)] = BURR[0]
    if kind.endswith("ripe"):
        for x, y in ((3, 11), (8, 9)):
            c[(x, y)] = CONE[2]
            c[(x, y + 1)] = CONE[1]
    return c


def paint(crop):
    px = [[(0, 0, 0, 0)] * ATLAS for _ in range(ATLAS)]

    def blit(name, pixels):
        sx, sy, w, h = SPRITES[name]
        for (x, y), rgb in pixels.items():
            if 0 <= x < w and 0 <= y < h:
                px[sy + y][sx + x] = (*rgb, 255)

    if crop == "barley":
        for stage in BARLEY_STAGES:
            blit(f"barley_{stage}", paint_barley_stage(stage))
    else:
        for kind in ("shoots", "half", "full", "top", "full_burrs", "top_burrs", "top_ripe"):
            blit(f"vine_{kind}", paint_vine(kind))
        blit("pole", {(x, y): POLE[0] if y % 5 == 0 else POLE[2 - x] for x in range(2) for y in range(16)})
        blit("pole_end", {(x, y): POLE[2] for x in range(2) for y in range(2)})
        blit("twine", {(x, y): TWINE for x in range(2) for y in range(2)})
        blit("dirt", {(x, y): DIRT[(x * 3 + y * 5) % 3] for x in range(4) for y in range(4)})
        cone = [[3, 2, 3], [1, 3, 1], [2, None, 2], [1, 2, 0]]             # overlapping bracts, lupulin peeking out
        blit("cone", {(x, y): CONE[t] if t is not None else LUPULIN for y, row in enumerate(cone) for x, t in enumerate(row)})
        blit("cone_end", {(x, y): CONE[3] if (x, y) == (1, 1) else CONE[1] for x in range(3) for y in range(3)})
        blit("stalk", {(0, 0): BINE})
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", ATLAS, ATLAS, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# ------------------------------------------------------------------------------------------------ geometry

class Plant:
    def __init__(self, name):
        self.name = name
        self.elements = []
        self.groups = []

    def group(self, name):
        self.groups.append((name, []))

    def _add(self, e):
        self.elements.append(e)
        self.groups[-1][1].append(len(self.elements) - 1)

    def box(self, name, frm, to, sprite, faces=("north", "south", "east", "west", "up", "down"), side=None):
        """A box; every face shows the whole sprite (`side` for the four sides if given)."""
        out = {}
        for face in faces:
            sx, sy, w, h = SPRITES[side if side and face in ("north", "south", "east", "west") else sprite]
            out[face] = (sx, sy, sx + w, sy + h)
        self._add({"name": name, "from": list(frm), "to": list(to), "faces": out})

    def plane(self, name, axis, at, lo, hi, y0, y1, sprite, mirror=False):
        """A vertical sprite plane at x = at (axis "x") or z = at (axis "z"), spanning lo..hi, seen from both sides."""
        sx, sy, w, h = SPRITES[sprite]
        uv = (sx + w, sy, sx, sy + h) if mirror else (sx, sy, sx + w, sy + h)
        if axis == "x":
            frm, to, faces = [at, y0, lo], [at, y1, hi], {"east": uv, "west": uv}
        else:
            frm, to, faces = [lo, y0, at], [hi, y1, at], {"north": uv, "south": uv}
        self._add({"name": name, "from": frm, "to": to, "faces": faces})

    def hash_planes(self, name, a, b, lo, hi, y0, y1, sprite):
        """Four planes in a # pattern: at x = a and b, and z = a and b (the z ones mirrored, for variety)."""
        for i, at in enumerate((a, b)):
            self.plane(f"{name}_x{i}", "x", at, lo, hi, y0, y1, sprite)
            self.plane(f"{name}_z{i}", "z", at, lo, hi, y0, y1, sprite, mirror=True)


def barley(stage):
    pl = Plant(stage)
    pl.group("crop")
    pl.hash_planes("crop", 4, 12, 0, 16, GROUND, GROUND + 16, f"barley_{stage}")
    return pl


def hops(stage):
    """Pole, a dirt mound, vine planes per block segment as far as the stage has climbed, and cones when ripe."""
    pl = Plant(stage)
    pl.group("pole")
    pl.box("mound", (5, GROUND, 5), (11, GROUND + 1, 11), "dirt", faces=("north", "south", "east", "west", "up"))
    for i in range(3):
        y0 = GROUND + 16 * i
        pl.box(f"pole{i}", (7, y0, 7), (9, min(y0 + 16, 46), 9), "pole", faces=("north", "south", "east", "west"))
    pl.box("pole_end", (7, 45, 7), (9, 46, 9), "pole_end", faces=("up",))
    pl.box("twine", (6, 42, 6), (10, 43, 10), "twine")
    segments = {"planted": [], "shoots": ["vine_shoots"], "climbing": ["vine_full", "vine_half"],
                "leafy": ["vine_full", "vine_full", "vine_top"],
                "burrs": ["vine_full_burrs", "vine_full_burrs", "vine_top_burrs"],
                "ripe": ["vine_full", "vine_full", "vine_top_ripe"]}[stage]
    if segments:
        pl.group("vine")
    for i, sprite in enumerate(segments):
        y0 = GROUND + 16 * i
        pl.hash_planes(f"vine{i}", 6, 10, 2, 14, y0, y0 + 16, sprite)
    if stage == "ripe":
        pl.group("cones")
        # (x, z of the cone's corner, top y): round the column, outside the planes, like cocoa pods on a trunk
        spots = [(2, 6, 12), (11, 7, 9), (7, 2, 6), (6, 11, 14), (2, 8, 25), (11, 6, 28), (6, 2, 21),
                 (7, 11, 24), (2, 7, 37), (11, 8, 40), (7, 2, 34), (6, 11, 41)]
        for k, (x, z, top) in enumerate(spots):
            pl.box(f"cone{k}_stalk", (x + 1, top, z + 1), (x + 2, top + 1, z + 2), "stalk")
            pl.box(f"cone{k}", (x, top - 4, z), (x + 3, top, z + 3), "cone_end", side="cone")
            pl.box(f"cone{k}_tip", (x + 1, top - 5, z + 1), (x + 2, top - 4, z + 2), "cone_end", side="cone_end")
    return pl


# ------------------------------------------------------------------------------------------------ output

def uid(*parts):
    return str(_uuid.uuid5(_uuid.NAMESPACE_URL, "beer-crops/" + "/".join(parts)))


def to_java(pl, texture, shift=0):
    def s(p):
        return [p[0], p[1] + shift, p[2]]

    elements = []
    for e in pl.elements:
        faces = {f: {"uv": [round(v * UV, 4) for v in uv], "texture": "#0"} for f, uv in e["faces"].items()}
        elements.append({"name": e["name"], "from": s(e["from"]), "to": s(e["to"]), "faces": faces})
    groups = [{"name": name, "origin": [8, 0, 8], "color": 0, "children": idx} for name, idx in pl.groups]
    return {"credit": "beer/tools/crop_models.py", "ambientocclusion": False,
            "textures": {"0": texture, "particle": texture}, "elements": elements, "groups": groups,
            "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                        "gui": {"rotation": [25, 45, 0], "translation": [0, -3, 0], "scale": [0.42, 0.42, 0.42]},
                        "ground": {"translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]}}}


def to_bbmodel(pl, offset=(0, 0, 0)):
    elements, outliner = [], []
    shift = lambda p: [p[0] + offset[0], p[1] + offset[1], p[2] + offset[2]]
    for gi, (gname, idx) in enumerate(pl.groups):
        children = []
        for i in idx:
            e = pl.elements[i]
            faces = {f: ({"uv": list(e["faces"][f]), "texture": 0} if f in e["faces"] else {"uv": [0, 0, 0, 0], "texture": None})
                     for f in ("north", "east", "south", "west", "up", "down")}
            el = {"name": e["name"], "box_uv": False, "rescale": False, "locked": False, "light_emission": 0,
                  "render_order": "default", "allow_mirror_modeling": True, "from": shift(e["from"]), "to": shift(e["to"]),
                  "autouv": 0, "color": gi % 8, "origin": shift((8, 0, 8)), "faces": faces, "type": "cube",
                  "uuid": uid(pl.name, str(offset), str(i))}
            elements.append(el)
            children.append(el["uuid"])
        outliner.append({"name": f"{pl.name}_{gname}" if any(offset) else gname, "origin": shift((8, 0, 8)),
                         "color": gi % 8, "uuid": uid(pl.name, str(offset), "group", gname), "export": True,
                         "mirror_uv": False, "isOpen": False, "locked": False, "visibility": True, "autouv": 0,
                         "children": children})
    return elements, outliner


def bbmodel_file(name, elements, outliner, atlas_png):
    texture = {"path": "", "name": f"{name}.png", "folder": "", "namespace": "", "id": "0", "group": "",
               "width": ATLAS, "height": ATLAS, "uv_width": ATLAS, "uv_height": ATLAS, "particle": True,
               "use_as_default": False, "layers_enabled": False, "render_mode": "default", "render_sides": "auto",
               "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False,
               "visible": True, "internal": True, "saved": False, "uuid": uid(name, "texture"),
               "source": "data:image/png;base64," + base64.b64encode(atlas_png).decode()}
    return {"meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False}, "name": name,
            "parent": "", "ambientocclusion": False, "front_gui_light": False, "visible_box": [1, 3, 0],
            "resolution": {"width": ATLAS, "height": ATLAS}, "elements": elements, "outliner": outliner,
            "textures": [texture], "animations": []}


def write(path, data, binary=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb" if binary else "w", **({} if binary else {"encoding": "utf-8"})) as handle:
        if binary:
            handle.write(data)
        else:
            json.dump(data, handle, indent=1 if path.endswith(".bbmodel") else None,
                      separators=None if path.endswith(".bbmodel") else (",", ":"))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lineup", help="also write <out>_barley.bbmodel and <out>_hops.bbmodel, every stage side by side")
    args = parser.parse_args(argv)
    pack = os.path.join(ROOT, "resourcepack")
    assets = os.path.join(pack, "assets", "beer")
    cases = []
    for crop, make, stages, shift, spacing in (("barley", barley, BARLEY_STAGES, 0, 20),
                                               ("hops", hops, HOPS_STAGES, HOPS_SHIFT, 20)):
        atlas = paint(crop)
        write(os.path.join(assets, "textures", "block", "plant", f"{crop}.png"), atlas, binary=True)
        plants = [make(stage) for stage in stages]
        for pl in plants:
            java = to_java(pl, f"beer:block/plant/{crop}", shift)
            for e in java["elements"]:
                assert all(-16 <= v <= 32 for v in e["from"] + e["to"]), (crop, pl.name, e["name"])
            write(os.path.join(assets, "models", "plant", crop, f"{pl.name}.json"), java)
            cases.append({"when": f"{crop}/{pl.name}", "model": {"type": "minecraft:model", "model": f"beer:plant/{crop}/{pl.name}"}})
            elements, outliner = to_bbmodel(pl)
            write(os.path.join(ROOT, "models", crop, f"{pl.name}.bbmodel"), bbmodel_file(pl.name, elements, outliner, atlas))
        print(f"{crop}: " + ", ".join(f"{pl.name} {len(pl.elements)}" for pl in plants) + " elements")
        if args.lineup:
            all_el, all_out = [], []
            for i, pl in enumerate(plants):
                el, out = to_bbmodel(pl, offset=(-(i - (len(plants) - 1) / 2) * spacing, 0, 0))
                all_el += el
                all_out += out
            write(args.lineup.replace(".bbmodel", f"_{crop}.bbmodel"), bbmodel_file(f"{crop}_lineup", all_el, all_out, atlas))
    write(os.path.join(assets, "items", "plant.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
        "fallback": {"type": "minecraft:model", "model": "beer:plant/barley/ripe"}}})
    write(os.path.join(pack, "pack.mcmeta"), {"pack": {"description": "Beer", "min_format": 75, "max_format": 97}})


if __name__ == "__main__":
    main()
