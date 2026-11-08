"""Blender headless glyph authoring for Space24 3D card-face glyphs.

Authors 14 extruded-text meshes (10 digits + 4 operator glyphs) and exports
each as a standalone `.usdc` into `app/src/main/assets/glyphs/`. Also
supports a `preview` mode that renders PNG screenshots for visual QA without
touching the exported assets.

Run with Blender's own Python, headless:

    /Applications/Blender.app/Contents/MacOS/Blender --background \
        --python tools/glyphs/make_glyphs.py -- build [out_dir]

    /Applications/Blender.app/Contents/MacOS/Blender --background \
        --python tools/glyphs/make_glyphs.py -- preview [out_dir]

`out_dir` defaults to `app/src/main/assets/glyphs/` for `build` (resolved
relative to this script's location, i.e. repo root + that path) and to the
current directory for `preview`.

Shared authoring parameters (extrude/bevel/resolution/material) were measured
empirically by the SDD controller for this plan
(docs/superpowers/sdd/2026-09-21-space24-3d-glyphs-phase01/task-6-brief.md)
and must not be changed without going back through the plan.

All 14 glyphs are authored from SF Mono (`SF_MONO_PATH`). Per-glyph font and
target cap height are a DATA TABLE (`GLYPH_SPECS` below), not logic, even
though every row currently uses the same `_sf_mono(...)` helper: this plan
briefly mixed in Menlo for `digit_0`/`digit_1` to work around a Blender
3.6.5-specific mesh defect in SF Mono's own `0`/`1` outlines, then reverted
once a Blender version change (3.6.5 -> 5.2.2, forced by the host machine,
not chosen) incidentally fixed that defect. See
docs/superpowers/notes/2026-09-21-glyph-authoring.md for the full history —
a future reader should not "fix" an apparent SF-Mono-only inconsistency by
guessing there's supposed to be a Menlo mix; there isn't, not anymore. If a
future font swap is needed, use `_menlo_scaled(...)` (or add another
per-font helper) and edit the table — do not add a special case in the
authoring logic. `target_cap_height=None` (the default for every glyph
today) means "author at the font's natural size, no post-scale".
"""

import os
import sys
from collections import namedtuple

import bpy
import mathutils

# ---------------------------------------------------------------------------
# Shared authoring parameters (controller-measured; do not tune without
# re-running the plan's decision process — see module docstring).
# ---------------------------------------------------------------------------

SF_MONO_PATH = "/System/Library/Fonts/SFNSMono.ttf"
MENLO_PATH = "/System/Library/Fonts/Menlo.ttc"

EXTRUDE = 0.06
BEVEL_DEPTH = 0.012
BEVEL_RESOLUTION = 1
RESOLUTION_U = 4

# cyber theme labelPrimary = 0xFFE8F4FF (CyberTheme.kt). Direct sRGB-byte
# normalization, not a gamma-correct sRGB->linear conversion: this only needs
# to be "a visible PBR material aligned with the theme colour" per the brief.
# Task 7 did not do any material tuning (on-device or otherwise); this value
# shipped as-authored and is still the current baseline. Glyph colour is
# fixed and does not vary with selected/disabled state — see
# .spatialsdk/design-contract.md §5 for the accepted contrast trade-off that
# follows from that (this is not scheduled for a specific future task).
BASE_COLOR = (0xE8 / 255.0, 0xF4 / 255.0, 0xFF / 255.0, 1.0)

# The cap height (dims.y) SF Mono's own `digit_0` measures at — also the max
# across all 12 originally-SF-Mono glyphs. Not used by any GLYPH_SPECS row
# today (no glyph is currently scaled), but kept as the documented reference
# value: if a future font-mix episode needs `_menlo_scaled(...)` again, this
# is the height it should target, so GlyphLoader.capHeight()'s
# max-across-prototypes stays anchored to the SF Mono scale instead of
# jumping to a taller replacement font and squashing the rest of the set.
SF_MONO_REFERENCE_CAP_HEIGHT = 0.5069

# ---------------------------------------------------------------------------
# Per-glyph data table: which font authors each glyph, and what cap height
# (dims.y) to scale it to at authoring time. `target_cap_height=None` means
# "author at the font's natural size, no post-scale" (the default: unscaled
# SF Mono digits/operators define the reference scale themselves).
# ---------------------------------------------------------------------------

GlyphSpec = namedtuple("GlyphSpec", ["name", "char", "font", "target_cap_height"])


def _sf_mono(name, char):
    return GlyphSpec(name, char, SF_MONO_PATH, None)


def _menlo_scaled(name, char):
    return GlyphSpec(name, char, MENLO_PATH, SF_MONO_REFERENCE_CAP_HEIGHT)


GLYPH_SPECS = [
    _sf_mono("digit_0", "0"),   # was _menlo_scaled under Blender 3.6.5; fixed under 5.2.2, see notes doc
    _sf_mono("digit_1", "1"),   # same
    _sf_mono("digit_2", "2"),
    _sf_mono("digit_3", "3"),
    _sf_mono("digit_4", "4"),
    _sf_mono("digit_5", "5"),
    _sf_mono("digit_6", "6"),
    _sf_mono("digit_7", "7"),
    _sf_mono("digit_8", "8"),
    _sf_mono("digit_9", "9"),
    _sf_mono("op_plus", "+"),
    _sf_mono("op_minus", "−"),   # U+2212 MINUS SIGN, not ASCII hyphen
    _sf_mono("op_times", "×"),   # U+00D7 MULTIPLICATION SIGN
    _sf_mono("op_divide", "÷"),  # U+00F7 DIVISION SIGN
]

# Lookup by literal character, for composing preview strings (e.g. "-28561",
# "108") out of whichever spec actually owns that character today. Keeps
# preview composition in sync with GLYPH_SPECS without hand-duplicating font
# choices.
SPEC_BY_CHAR = {spec.char: spec for spec in GLYPH_SPECS}

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
DEFAULT_ASSET_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "glyphs")


def clear_scene():
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    for block in list(bpy.data.meshes):
        if block.users == 0:
            bpy.data.meshes.remove(block)
    for block in list(bpy.data.curves):
        if block.users == 0:
            bpy.data.curves.remove(block)
    for block in list(bpy.data.cameras):
        if block.users == 0:
            bpy.data.cameras.remove(block)
    for block in list(bpy.data.lights):
        if block.users == 0:
            bpy.data.lights.remove(block)


def make_material():
    mat = bpy.data.materials.new(name="GlyphMaterial")
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = BASE_COLOR
    return mat


def _build_glyph_mesh(name, char, font_path, target_cap_height):
    """Author one extruded-text mesh at the world origin. Does NOT clear the
    scene first (callers compose multiple glyphs into one scene for preview
    renders) and does NOT attach a material (callers do that once per mesh).

    If `target_cap_height` is not None, the mesh is uniformly scaled about
    its own bounding-box centre so its post-scale dims.y equals that value,
    and the scale is baked into the mesh data via `transform_apply` before
    returning — so an exporter reading this mesh's data directly (not via
    the object's transform) still gets the corrected size.

    Returns (mesh_obj, dims, raw_dims, tris, verts, font_name, applied_scale)
    where `dims` is post-scale and `raw_dims` is the font's natural size.
    """
    bpy.ops.object.text_add(location=(0, 0, 0))
    txt = bpy.context.object
    txt.data.body = char
    txt.data.font = bpy.data.fonts.load(font_path, check_existing=True)
    font_name = txt.data.font.name
    txt.data.align_x = 'CENTER'
    txt.data.align_y = 'CENTER'
    txt.data.extrude = EXTRUDE
    txt.data.bevel_depth = BEVEL_DEPTH
    txt.data.bevel_resolution = BEVEL_RESOLUTION
    txt.data.resolution_u = RESOLUTION_U

    bpy.ops.object.convert(target='MESH')
    mesh_obj = bpy.context.object
    mesh_obj.name = name

    # Curve-to-mesh bevel conversion can leave coincident-but-unwelded
    # duplicate vertices where two bevel profiles meet at a corner (e.g.
    # digit_1's vertical stroke meeting its base serif). bmesh reports these
    # as open boundary edges even though the surfaces visually touch — a
    # small dark notch renders at that seam. Weld them before anything else
    # reads this mesh's geometry or dimensions. threshold=0.0001 only welds
    # truly-coincident points, so it's safe to run unconditionally on every
    # glyph rather than special-casing which ones need it.
    bpy.ops.object.select_all(action='DESELECT')
    mesh_obj.select_set(True)
    bpy.context.view_layer.objects.active = mesh_obj
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.mesh.remove_doubles(threshold=0.0001)
    bpy.ops.object.mode_set(mode='OBJECT')

    bpy.ops.object.origin_set(type='ORIGIN_GEOMETRY', center='BOUNDS')
    mesh_obj.location = (0, 0, 0)

    raw_dims = mesh_obj.dimensions.copy()
    applied_scale = 1.0
    if target_cap_height is not None:
        applied_scale = target_cap_height / raw_dims.y
        mesh_obj.scale = (applied_scale, applied_scale, applied_scale)
        bpy.ops.object.select_all(action='DESELECT')
        mesh_obj.select_set(True)
        bpy.context.view_layer.objects.active = mesh_obj
        # Bake the scale into the mesh vertex data now, so the exported
        # .usdc carries the corrected size directly rather than relying on
        # a non-identity object transform surviving the USD export.
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)

    mesh_obj.data.calc_loop_triangles()
    dims = mesh_obj.dimensions.copy()
    tris = len(mesh_obj.data.loop_triangles)
    verts = len(mesh_obj.data.vertices)
    return mesh_obj, dims, raw_dims, tris, verts, font_name, applied_scale


def author_glyph(spec, material):
    """Author one glyph from `spec` alone in a freshly cleared scene, for the
    single-glyph export path. Returns the same tuple as `_build_glyph_mesh`."""
    clear_scene()
    mesh_obj, dims, raw_dims, tris, verts, font_name, applied_scale = _build_glyph_mesh(
        spec.name, spec.char, spec.font, spec.target_cap_height
    )
    mesh_obj.data.materials.append(material)
    return mesh_obj, dims, raw_dims, tris, verts, font_name, applied_scale


def build(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    material = make_material()
    report = []
    for spec in GLYPH_SPECS:
        mesh_obj, dims, raw_dims, tris, verts, font_name, applied_scale = author_glyph(spec, material)
        out_path = os.path.join(out_dir, f"{spec.name}.usdc")

        bpy.ops.object.select_all(action='DESELECT')
        mesh_obj.select_set(True)
        bpy.context.view_layer.objects.active = mesh_obj
        # convert_world_material=False: Blender 5.2.2's usd_export defaults
        # this to True, which bakes the scene's default World background
        # colour into a dome-light texture on every export regardless of the
        # mesh's own material — that's the stray textures/color_0C0C0C.exr
        # side-file, not anything to do with our Principled BSDF glyph
        # material. Without this flag every .usdc would ship an unwanted
        # texture directory alongside it.
        bpy.ops.wm.usd_export(
            filepath=out_path, selected_objects_only=True, convert_world_material=False
        )

        exists = os.path.exists(out_path)
        size = os.path.getsize(out_path) if exists else 0
        report.append({
            "name": spec.name, "char": spec.char, "font": font_name,
            "x": dims.x, "y": dims.y, "z": dims.z,
            "raw_y": raw_dims.y, "scale": applied_scale,
            "verts": verts, "tris": tris, "exported": exists, "size": size,
        })
        print(
            f"GLYPH name={spec.name} char={spec.char!r} font={font_name!r} "
            f"dims=({dims.x:.4f},{dims.y:.4f},{dims.z:.4f}) raw_cap_height={raw_dims.y:.4f} "
            f"scale={applied_scale:.4f} verts={verts} tris={tris} exported={exists} size={size}"
        )
    return report


def _point_at_origin(obj, world_up=mathutils.Vector((0.0, 1.0, 0.0))):
    """Aim `obj` at the origin, keeping `world_up` as the on-screen "up".

    `Vector.to_track_quat()` always aligns its up axis to world Z, which is
    wrong here: Blender text lies flat in the local XY plane (extrusion runs
    along Z), so a glyph's own "up" (reading direction) is world Y, not Z.
    Using the Z-up idiom here makes the camera's on-screen up spin freely
    once the view direction is close to parallel with Z, producing an
    arbitrarily rotated/diagonal render. Build the look-at matrix by hand
    against world Y instead.
    """
    forward = (mathutils.Vector((0.0, 0.0, 0.0)) - obj.location).normalized()
    up_ref = world_up
    if abs(forward.dot(up_ref)) > 0.999:
        up_ref = mathutils.Vector((0.0, 0.0, 1.0))
    right = forward.cross(up_ref).normalized()
    true_up = right.cross(forward).normalized()
    rot_mat = mathutils.Matrix((
        (right.x, true_up.x, -forward.x),
        (right.y, true_up.y, -forward.y),
        (right.z, true_up.z, -forward.z),
    )).to_4x4()
    obj.rotation_euler = rot_mat.to_euler()


def add_camera_and_light(span_x, span_y):
    """Near-face-on camera (with a slight tilt for bevel highlights) plus
    two-point lighting, sized to fit a scene spanning `span_x` x `span_y`.

    Blender's text object lies flat in the local XY plane with extrusion
    along Z (the "front" reading direction is looking down the Z axis, not
    -Y) — so the camera must be Z-dominant, not X/Y-dominant, or the glyph
    reads as a foreshortened ring instead of its actual character shape."""
    for o in list(bpy.data.objects):
        if o.type in ('CAMERA', 'LIGHT'):
            bpy.data.objects.remove(o, do_unlink=True)

    span = max(span_x, span_y, 0.01)
    distance = span * 2.2 + 0.5

    bpy.ops.object.camera_add(location=(distance * 0.22, -distance * 0.18, distance * 0.97))
    cam = bpy.context.object
    cam.data.lens = 50
    _point_at_origin(cam)
    bpy.context.scene.camera = cam

    bpy.ops.object.light_add(type='SUN', location=(distance * 0.5, -distance * 0.4, distance * 0.9))
    key = bpy.context.object
    key.data.energy = 3.0
    _point_at_origin(key)

    bpy.ops.object.light_add(type='SUN', location=(-distance * 0.6, distance * 0.3, distance * 0.5))
    fill = bpy.context.object
    fill.data.energy = 1.0
    _point_at_origin(fill)

    return cam


def render_preview(span_x, span_y, out_path, resolution=(1000, 750), samples=64):
    add_camera_and_light(span_x, span_y)
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = samples
    scene.render.resolution_x = resolution[0]
    scene.render.resolution_y = resolution[1]
    scene.render.image_settings.file_format = 'PNG'
    scene.render.filepath = out_path
    bpy.ops.render.render(write_still=True)
    print(f"RENDERED {out_path} exists={os.path.exists(out_path)}")


def build_string(specs, material, tracking=0.05):
    """Compose `specs` left-to-right into one scene (own glyph widths +
    fixed tracking gap), centred as a whole. Returns (span_x, span_y)."""
    clear_scene()
    placed = []
    for spec in specs:
        mesh_obj, dims, raw_dims, tris, verts, font_name, scale = _build_glyph_mesh(
            f"tmp_{spec.name}", spec.char, spec.font, spec.target_cap_height
        )
        mesh_obj.data.materials.append(material)
        placed.append((mesh_obj, dims))

    total_width = sum(d.x for _, d in placed) + tracking * (len(placed) - 1)
    max_height = max(d.y for _, d in placed)
    cursor = -total_width / 2
    for mesh_obj, dims in placed:
        mesh_obj.location.x = cursor + dims.x / 2
        cursor += dims.x + tracking
    return total_width, max_height


def build_grid(rows, material, tracking=0.08, row_gap=0.15):
    """Compose `rows` (list of list of GlyphSpec) into a centred grid, one
    row per list, laid out left-to-right within a row and top-to-bottom
    across rows. Returns (span_x, span_y)."""
    clear_scene()
    row_layout = []
    for row in rows:
        placed = []
        for spec in row:
            mesh_obj, dims, raw_dims, tris, verts, font_name, scale = _build_glyph_mesh(
                f"tmp_{spec.name}", spec.char, spec.font, spec.target_cap_height
            )
            mesh_obj.data.materials.append(material)
            placed.append((mesh_obj, dims))
        row_width = sum(d.x for _, d in placed) + tracking * (len(placed) - 1)
        row_height = max(d.y for _, d in placed)
        row_layout.append((placed, row_width, row_height))

    total_height = sum(rh for _, _, rh in row_layout) + row_gap * (len(row_layout) - 1)
    total_width = max(rw for _, rw, _ in row_layout)

    cursor_y = total_height / 2
    for placed, row_width, row_height in row_layout:
        cursor_y -= row_height / 2
        cursor_x = -row_width / 2
        for mesh_obj, dims in placed:
            mesh_obj.location.x = cursor_x + dims.x / 2
            mesh_obj.location.y = cursor_y
            cursor_x += dims.x + tracking
        cursor_y -= row_height / 2 + row_gap

    return total_width, total_height


def preview(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    material = make_material()

    # Sanity check: densest glyph, and the two that had the Blender
    # 3.6.5-era mesh defect (self-intersecting shard in "0", punched hole in
    # "1") — confirm clean geometry under the current Blender version before
    # trusting the full-sheet/string renders below.
    for ch in ("8", "0", "1"):
        spec = SPEC_BY_CHAR[ch]
        _, dims, raw_dims, tris, _, font_name, scale = author_glyph(spec, material)
        print(
            f"PREVIEW {spec.name} font={font_name!r} dims=({dims.x:.4f},{dims.y:.4f},{dims.z:.4f}) "
            f"tris={tris}"
        )
        render_preview(dims.x, dims.y, os.path.join(out_dir, f"preview_{spec.name}.png"))

    # Full 14-glyph sheet, in Glyph enum order, 7 per row.
    sheet_span_x, sheet_span_y = build_grid(
        [GLYPH_SPECS[0:7], GLYPH_SPECS[7:14]], material, tracking=0.08, row_gap=0.18
    )
    print(f"PREVIEW SHEET span=({sheet_span_x:.4f},{sheet_span_y:.4f})")
    render_preview(sheet_span_x, sheet_span_y, os.path.join(out_dir, "preview_sheet.png"), resolution=(1600, 500))

    # Worst-case 6-char card face.
    worst_case = "−28561"
    specs = [SPEC_BY_CHAR[ch] for ch in worst_case]
    span_x, span_y = build_string(specs, material)
    print(f"PREVIEW STRING {worst_case!r} span=({span_x:.4f},{span_y:.4f})")
    render_preview(span_x, span_y, os.path.join(out_dir, "preview_string.png"), resolution=(1400, 500))


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    mode = argv[0] if argv else "build"

    if mode == "build":
        out_dir = os.path.abspath(argv[1]) if len(argv) > 1 else DEFAULT_ASSET_DIR
        build(out_dir)
    elif mode == "preview":
        out_dir = os.path.abspath(argv[1]) if len(argv) > 1 else "."
        preview(out_dir)
    else:
        raise SystemExit(f"unknown mode {mode!r}, expected 'build' or 'preview'")


if __name__ == "__main__":
    main()
