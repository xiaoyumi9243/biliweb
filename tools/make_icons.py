#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
python3 tools/make_icons.py
"""

import math
import os
import struct
import zlib

PINK = (0xFB, 0x72, 0x99)
WHITE = (0xFF, 0xFF, 0xFF)
DARK = (0x21, 0x21, 0x21)
BLACK = (0x1A, 0x1A, 0x1A)
OUTLINE = (0x00, 0x00, 0x00)  # 齿轮描边用的浓黑

# 启动图标：密度目录 -> 边长(px)
LAUNCHER = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# 齿轮图标是 24dp，按密度换算成像素
GEAR = {
    "drawable-mdpi": 24,
    "drawable-hdpi": 36,
    "drawable-xhdpi": 48,
    "drawable-xxhdpi": 72,
    "drawable-xxxhdpi": 96,
}

# 返回箭头也是 24dp
ARROW = dict(GEAR)

# 虚拟鼠标指针做得大一点，手指用起来看得清
CURSOR = {
    "drawable-mdpi": 32,
    "drawable-hdpi": 48,
    "drawable-xhdpi": 64,
    "drawable-xxhdpi": 96,
    "drawable-xxxhdpi": 128,
}

SS = 3  # 超采样倍数，用于抗锯齿
GEAR_OUTLINE = 0.035  # 齿轮黑色描边宽度（相对图标宽度，越大描边越粗）
RES_DIR = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
)


def in_round_rect(x, y, x0, y0, x1, y1, r):
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    cx = x0 + r if x < x0 + r else (x1 - r if x > x1 - r else x)
    cy = y0 + r if y < y0 + r else (y1 - r if y > y1 - r else y)
    dx, dy = x - cx, y - cy
    return dx * dx + dy * dy <= r * r


def in_capsule(x, y, x0, y0, x1, y1, half):
    vx, vy = x1 - x0, y1 - y0
    wx, wy = x - x0, y - y0
    seg = vx * vx + vy * vy
    t = 0.0 if seg == 0 else max(0.0, min(1.0, (wx * vx + wy * vy) / seg))
    dx, dy = wx - t * vx, wy - t * vy
    return dx * dx + dy * dy <= half * half


def render_launcher(size):
    """粉色圆角底 + 白色电视机身 + 两根天线。"""
    w = size * SS
    buf = bytearray(w * w * 4)

    def paint(x, y, color):
        i = (y * w + x) * 4
        buf[i] = color[0]
        buf[i + 1] = color[1]
        buf[i + 2] = color[2]
        buf[i + 3] = 255

    r = w * 0.22
    for y in range(w):
        for x in range(w):
            if in_round_rect(x + 0.5, y + 0.5, 0.0, 0.0, w - 1.0, w - 1.0, r):
                paint(x, y, PINK)

    for y in range(w):
        for x in range(w):
            if in_capsule(x + 0.5, y + 0.5, w * 0.50, w * 0.36, w * 0.33, w * 0.15, w * 0.035) or \
               in_capsule(x + 0.5, y + 0.5, w * 0.50, w * 0.36, w * 0.67, w * 0.15, w * 0.035):
                paint(x, y, WHITE)

    for y in range(w):
        for x in range(w):
            if in_round_rect(x + 0.5, y + 0.5, w * 0.16, w * 0.32, w * 0.84, w * 0.82, w * 0.10):
                paint(x, y, WHITE)

    for y in range(w):
        for x in range(w):
            if in_round_rect(x + 0.5, y + 0.5, w * 0.26, w * 0.42, w * 0.74, w * 0.72, w * 0.06):
                paint(x, y, PINK)

    # 屏幕里写上 WEB（白色，和机身同色，看起来像从屏幕里镂出来）
    screen_left, screen_top = w * 0.26, w * 0.42
    screen_right, screen_bottom = w * 0.74, w * 0.72
    screen_w, screen_h = screen_right - screen_left, screen_bottom - screen_top
    cols, rows = text_columns(SCREEN_TEXT), GLYPH_ROWS
    cell = min(screen_h * 0.62 / rows, screen_w * 0.80 / cols)
    text_w, text_h = cols * cell, rows * cell
    origin_x = screen_left + (screen_w - text_w) / 2.0
    origin_y = screen_top + (screen_h - text_h) / 2.0

    for y in range(w):
        for x in range(w):
            px, py = x + 0.5, y + 0.5
            if px < origin_x or py < origin_y:
                continue
            col = int((px - origin_x) / cell)
            row = int((py - origin_y) / cell)
            if text_pixel(SCREEN_TEXT, col, row):
                paint(x, y, WHITE)

    return downsample(buf, w, size)


def render_gear(size):
    """白色齿轮：8 个齿 + 中心挖空（透明）。

    外轮廓（齿顶 / 齿根 / 齿侧）和中心小圆环都描一圈浓黑边，
    这样在粉色悬浮球和白色顶栏上都层次分明、边缘清晰。
    """
    w = size * SS
    c = (w - 1) / 2.0
    r_tip = w * 0.47      # 齿顶半径
    r_body = w * 0.34     # 齿根（圆盘）半径
    r_hole = w * 0.13     # 中心孔半径
    teeth = 8
    period = 2.0 * math.pi / teeth
    stroke = max(3, int(round(w * GEAR_OUTLINE)))   # 黑色描边宽度（超采样像素）

    # 1) 先算出齿轮本体（不含描边）的实心区域
    inside = bytearray(w * w)
    for y in range(w):
        for x in range(w):
            dx = x + 0.5 - c
            dy = y + 0.5 - c
            r = math.sqrt(dx * dx + dy * dy)
            if r > r_tip or r < r_hole:
                continue
            ang = math.atan2(dy, dx) + math.pi          # 0 .. 2pi
            frac = (ang % period) / period              # 0 .. 1
            in_tooth = 0.30 <= frac < 0.70              # 中间 40% 是齿
            limit = r_tip if in_tooth else r_body
            if r <= limit:
                inside[y * w + x] = 1

    # 2) 积分图：O(1) 判断每个像素周围 stroke 邻域内有没有「外部」像素。
    #    有就说明它紧贴轮廓（齿顶、齿根、齿侧或中心孔），画成浓黑描边。
    stride = w + 1
    sat = [0] * (stride * stride)
    for y in range(w):
        base = (y + 1) * stride
        prev = y * stride
        row = y * w
        for x in range(w):
            outside = 0 if inside[row + x] else 1
            sat[base + x + 1] = (outside + sat[base + x]
                                 + sat[prev + x + 1] - sat[prev + x])

    buf = bytearray(w * w * 4)
    for y in range(w):
        y0 = y - stroke if y - stroke > 0 else 0
        y1 = y + stroke + 1 if y + stroke + 1 < w else w
        row = y * w
        for x in range(w):
            if not inside[row + x]:
                continue
            x0 = x - stroke if x - stroke > 0 else 0
            x1 = x + stroke + 1 if x + stroke + 1 < w else w
            outside_count = (sat[y1 * stride + x1] - sat[y0 * stride + x1]
                             - sat[y1 * stride + x0] + sat[y0 * stride + x0])
            color = OUTLINE if outside_count > 0 else WHITE
            i = (row + x) * 4
            buf[i] = color[0]
            buf[i + 1] = color[1]
            buf[i + 2] = color[2]
            buf[i + 3] = 255

    return downsample(buf, w, size)


def render_back_arrow(size):
    """深灰色返回箭头（"<" 形，由两段圆头线组成）"""
    w = size * SS
    buf = bytearray(w * w * 4)
    half = w * 0.055
    for y in range(w):
        for x in range(w):
            px, py = x + 0.5, y + 0.5
            if in_capsule(px, py, w * 0.66, w * 0.18, w * 0.34, w * 0.50, half) or \
               in_capsule(px, py, w * 0.34, w * 0.50, w * 0.66, w * 0.82, half):
                i = (y * w + x) * 4
                buf[i] = DARK[0]
                buf[i + 1] = DARK[1]
                buf[i + 2] = DARK[2]
                buf[i + 3] = 255
    return downsample(buf, w, size)


# 5x7 点阵字模（列优先，每列一个字节的低 7 位；经典的 5x7 字体数据）
FONT_5X7 = {
    "W": (0x3F, 0x40, 0x38, 0x40, 0x3F),
    "E": (0x7F, 0x49, 0x49, 0x49, 0x41),
    "B": (0x7F, 0x49, 0x49, 0x49, 0x36),
}

# 屏幕上写什么字
SCREEN_TEXT = "WEB"

# 点阵字模占的列数（5 列字 + 1 列间距，最后一列不算）
GLYPH_COLS = 5
GLYPH_ROWS = 7
GLYPH_GAP = 1


def text_columns(text):
    return len(text) * (GLYPH_COLS + GLYPH_GAP) - GLYPH_GAP


def text_pixel(text, col, row):
    """点阵里第 col 列、第 row 行是否有点"""
    if col < 0 or row < 0 or row >= GLYPH_ROWS:
        return False
    index = col // (GLYPH_COLS + GLYPH_GAP)
    if index >= len(text):
        return False
    offset = col - index * (GLYPH_COLS + GLYPH_GAP)
    if offset >= GLYPH_COLS:
        return False
    glyph = FONT_5X7.get(text[index])
    if glyph is None:
        return False
    return (glyph[offset] >> row) & 1 == 1


def in_polygon(x, y, pts):
    """射线法：点是否在多边形内"""
    inside = False
    n = len(pts)
    j = n - 1
    for i in range(n):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > y) != (yj > y):
            if x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                inside = not inside
        j = i
    return inside


def render_cursor(size, fill=BLACK, outline=WHITE):
    """经典鼠标箭头：黑多边形 + 白描边（描边用「到各边的距离」算），热点在左上角附近"""
    w = size * SS
    # 设计尺度 15 x 25 的箭头形状
    design = [(0.0, 0.0), (0.0, 20.0), (5.0, 15.0), (10.0, 25.0),
              (14.0, 23.0), (9.0, 13.0), (15.0, 13.0)]
    scale = (w * 0.90) / 25.0
    off = w * 0.045
    pts = [(off + x * scale, off + y * scale) for (x, y) in design]
    outline_width = max(1.0, w * 0.035)

    buf = bytearray(w * w * 4)

    def paint(x, y, color):
        i = (y * w + x) * 4
        buf[i] = color[0]
        buf[i + 1] = color[1]
        buf[i + 2] = color[2]
        buf[i + 3] = 255

    for y in range(w):
        for x in range(w):
            px, py = x + 0.5, y + 0.5
            if in_polygon(px, py, pts):
                paint(x, y, fill)
            else:
                n = len(pts)
                for i in range(n):
                    x1, y1 = pts[i]
                    x2, y2 = pts[(i + 1) % n]
                    if in_capsule(px, py, x1, y1, x2, y2, outline_width):
                        paint(x, y, outline)
                        break

    return downsample(buf, w, size)




def render_cursor_white(size):
    """给悬浮球用的白色鼠标指针（球是粉底，白色更清楚）"""
    return render_cursor(size, WHITE, DARK)

def downsample(buf, w, size):
    out = bytearray(size * size * 4)
    n = SS * SS
    for y in range(size):
        for x in range(size):
            r_ = g_ = b_ = a_ = 0
            for dy in range(SS):
                for dx in range(SS):
                    i = ((y * SS + dy) * w + (x * SS + dx)) * 4
                    r_ += buf[i]
                    g_ += buf[i + 1]
                    b_ += buf[i + 2]
                    a_ += buf[i + 3]
            j = (y * size + x) * 4
            out[j] = r_ // n
            out[j + 1] = g_ // n
            out[j + 2] = b_ // n
            out[j + 3] = a_ // n
    return bytes(out)


def write_png(path, size, rgba):
    raw = bytearray()
    stride = size * 4
    for y in range(size):
        raw.append(0)
        raw += rgba[y * stride:(y + 1) * stride]

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def emit(folders, name, renderer):
    for folder, size in sorted(folders.items()):
        out_dir = os.path.join(RES_DIR, folder)
        os.makedirs(out_dir, exist_ok=True)
        path = os.path.join(out_dir, name)
        write_png(path, size, renderer(size))
        print("生成 %s (%dx%d, %d bytes)" % (path, size, size, os.path.getsize(path)))


def main():
    emit(LAUNCHER, "ic_launcher.png", render_launcher)
    emit(GEAR, "ic_settings.png", render_gear)
    emit(ARROW, "ic_arrow_back.png", render_back_arrow)
    emit(CURSOR, "ic_mouse_cursor.png", render_cursor)
    emit(CURSOR, "ic_mouse_cursor_white.png", render_cursor_white)


if __name__ == "__main__":
    main()
