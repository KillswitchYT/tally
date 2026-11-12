#!/usr/bin/env python3
"""Apply a custom package id, app name and icon to the container project before it is built.

Everything comes from environment variables (never from command line text), and every value is
validated here before it touches a file or a Gradle command:
  IN_BUILD_ID   short id for this build, lowercase letters, digits, dashes
  IN_PACKAGE    applicationId of the generated app
  IN_NAME       launcher name of the generated app
  IN_ICON       path of an image file (png, jpg, webp), optional
  IN_FIT        "fill" (image fills the icon, phone mask may crop edges) or "fit" (whole image visible)
  RES_DIR       container/app/src/main/res
  GITHUB_ENV    when set, CONTAINER_ID and BUILD_ID are appended to it (validated values only)
"""
import os
import re
import sys
from xml.sax.saxutils import escape

from PIL import Image, ImageDraw

RES = os.environ.get("RES_DIR", "container/app/src/main/res")
BAD_PREFIXES = ("android.", "com.android.", "com.google.android.", "com.sec.android.", "com.samsung.")
OWN_PACKAGES = ("com.killswitch.tally",)


def die(msg):
    print("INPUT ERROR: " + msg)
    sys.exit(1)


def check_build_id(v):
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{2,39}", v or ""):
        die("build id must be 3 to 40 chars: lowercase letters, digits, dashes")
    return v


def check_package(v):
    v = (v or "").strip()
    if len(v) > 150:
        die("package id is too long")
    if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", v):
        die("package id must look like com.name.app: lowercase letters, digits, underscores, at least two parts, each part starts with a letter")
    if v in OWN_PACKAGES or v.startswith(BAD_PREFIXES):
        die("package id is reserved, pick another one")
    return v


def check_name(v):
    v = (v or "").strip()
    if not 1 <= len(v) <= 30:
        die("app name must be 1 to 30 characters")
    for ch in v:
        if not (ch.isalnum() or ch in " -_."):
            die("app name may only contain letters, digits, space, dash, underscore, dot")
    return v


def write_name(name):
    done = 0
    for d in ("values", "values-zh-rCN", "values-zh-rTW"):
        p = os.path.join(RES, d, "strings.xml")
        if not os.path.isfile(p):
            continue
        s = open(p, encoding="utf-8").read()
        s2, n = re.subn(r'(<string name="app_name">)[^<]*(</string>)', lambda m: m.group(1) + escape(name) + m.group(2), s)
        if n != 1:
            die("could not find app_name exactly once in " + p)
        open(p, "w", encoding="utf-8").write(s2)
        done += 1
    if done == 0:
        die("no strings.xml found under " + RES)


def square_cover(img, size):
    w, h = img.size
    side = min(w, h)
    img = img.crop(((w - side) // 2, (h - side) // 2, (w - side) // 2 + side, (h - side) // 2 + side))
    return img.resize((size, size), Image.LANCZOS)


def average_color(img):
    flat = Image.new("RGB", img.size, (255, 255, 255))
    flat.paste(img, mask=img.split()[3])
    return flat.resize((1, 1), Image.BOX).getpixel((0, 0))


def rounded(img, radius_frac):
    size = img.size[0]
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_frac), fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, mask=mask)
    return out


def circle(img):
    size = img.size[0]
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, mask=mask)
    return out


def write_icon(path, fit):
    if os.path.getsize(path) > 8 * 1024 * 1024:
        die("icon file is bigger than 8 MB")
    try:
        img = Image.open(path)
        img.load()
    except Exception as e:  # noqa: BLE001
        die("icon is not a readable image: %s" % type(e).__name__)
    img = img.convert("RGBA")
    if min(img.size) < 64:
        die("icon is smaller than 64 px")

    # legacy PNGs for every density (also used on phones that ignore adaptive icons)
    for d, px in (("ldpi", 36), ("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        base = square_cover(img, px * 4)
        sq = rounded(base, 0.18).resize((px, px), Image.LANCZOS)
        rd = circle(base).resize((px, px), Image.LANCZOS)
        os.makedirs(os.path.join(RES, "mipmap-" + d), exist_ok=True)
        sq.save(os.path.join(RES, "mipmap-%s/ic_launcher.png" % d))
        rd.save(os.path.join(RES, "mipmap-%s/ic_launcher_round.png" % d))

    dn = os.path.join(RES, "drawable-nodpi")
    os.makedirs(dn, exist_ok=True)
    # plain PNG for the splash screen (a <bitmap> cannot point at an adaptive icon)
    square_cover(img, 192).save(os.path.join(dn, "ic_custom_legacy.png"))

    # adaptive icon layers, 432 px = 108 dp at xxxhdpi
    if fit == "fit":
        w, h = img.size
        scale = 288.0 / max(w, h)
        small = img.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
        fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
        fg.paste(small, ((432 - small.size[0]) // 2, (432 - small.size[1]) // 2), small)
        bg = Image.new("RGBA", (432, 432), average_color(img) + (255,))
    else:
        bg = square_cover(img, 432)
        fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    bg.save(os.path.join(dn, "ic_custom_bg.png"))
    fg.save(os.path.join(dn, "ic_custom_fg.png"))

    adaptive = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(adaptive, exist_ok=True)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@drawable/ic_custom_bg" />\n'
           '    <foreground android:drawable="@drawable/ic_custom_fg" />\n'
           '</adaptive-icon>\n')
    for n in ("ic_launcher.xml", "ic_launcher_round.xml"):
        open(os.path.join(adaptive, n), "w").write(xml)

    splash = os.path.join(RES, "drawable", "splash.xml")
    s = open(splash).read()
    if "@mipmap/ic_launcher" not in s:
        die("splash.xml does not reference @mipmap/ic_launcher, script needs an update")
    open(splash, "w").write(s.replace("@mipmap/ic_launcher", "@drawable/ic_custom_legacy"))


def main():
    build_id = check_build_id(os.environ.get("IN_BUILD_ID", ""))
    pkg = check_package(os.environ.get("IN_PACKAGE", ""))
    name = check_name(os.environ.get("IN_NAME", ""))
    fit = os.environ.get("IN_FIT", "fill") or "fill"
    if fit not in ("fill", "fit"):
        die("icon_fit must be fill or fit")
    icon = os.environ.get("IN_ICON", "").strip()

    write_name(name)
    if icon:
        icon_abs = os.path.realpath(icon)
        root = os.path.realpath(os.environ.get("ICON_ROOT", os.path.dirname(icon_abs)))
        if not icon_abs.startswith(root + os.sep) or not os.path.isfile(icon_abs):
            die("icon path is not a file inside the allowed folder")
        write_icon(icon_abs, fit)

    print("applied: package=%s name=%s icon=%s fit=%s" % (pkg, name, bool(icon), fit))
    env = os.environ.get("GITHUB_ENV")
    if env:
        with open(env, "a") as f:
            f.write("CONTAINER_ID=%s\nBUILD_ID=%s\n" % (pkg, build_id))


if __name__ == "__main__":
    main()
