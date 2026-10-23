import math
import os
import sys
from urllib.parse import quote_plus
from reportlab.lib.pagesizes import A4, landscape
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph
from reportlab.lib.styles import ParagraphStyle

FONT_DIRS = ["/usr/share/fonts/truetype/dejavu/", "/usr/share/fonts/truetype/freefont/"]

def register(name, *candidates):
    """Register the first font file that exists, so missing DejaVu variants fall back cleanly."""
    for f in candidates:
        for d in FONT_DIRS:
            if os.path.exists(d + f):
                pdfmetrics.registerFont(TTFont(name, d + f))
                return
    raise FileNotFoundError(f"No font found for {name}: {candidates}")

register("Sans", "DejaVuSans.ttf")
register("SansB", "DejaVuSans-Bold.ttf")
register("Serif", "DejaVuSerif.ttf")
register("SerifB", "DejaVuSerif-Bold.ttf")
register("SerifI", "DejaVuSerifCondensed-Italic.ttf", "DejaVuSerif-Italic.ttf", "FreeSerifItalic.ttf")

OUT = sys.argv[1] if len(sys.argv) > 1 else "Sculptural_Wood_Furniture_Gallery.pdf"

W, H = landscape(A4)
INK = HexColor("#2B2520")
MUTED = HexColor("#7A6E63")
PAPER = HexColor("#FAF7F2")
LINE = HexColor("#E3DBD0")

CATS = {
    "LAM":  ("Laminated & Bent Veneer", HexColor("#8B5E3C"), HexColor("#F1E6DA")),
    "STM":  ("Steam-Bent Strip", HexColor("#5E6B45"), HexColor("#E8ECDD")),
    "PLY":  ("Molded Plywood", HexColor("#A0763A"), HexColor("#F5EBD8")),
    "CNC":  ("CNC-Sculpted / Sectional", HexColor("#3F5A66"), HexColor("#DFE8EB")),
    "HAND": ("Studio Craft Masters", HexColor("#6B4A4A"), HexColor("#EEE1DF")),
}

def gimg(q):
    return "https://www.google.com/search?tbm=isch&q=" + quote_plus(q)

# (no, title, designer, cat, note, image query, source url or None)
P = [
 (1, "Enignum Chair", "Joseph Walsh · Ireland", "LAM",
  "Thin layers of olive ash or walnut are stripped and reassembled so each layer follows the next; the benchmark for free-form lamination.",
  "Joseph Walsh Enignum chair", "https://josephwalshstudio.com/?p=716"),
 (2, "Enignum Canopy Bed / Lilium", "Joseph Walsh · Ireland", "LAM",
  "The same laminating method pushed to architectural scale: a canopy bed over six metres tall in olive ash.",
  "Joseph Walsh Enignum canopy bed", "https://irishartsreview.com/?p=162156"),
 (3, "Ethereal Lounge Chair", "Marc Fish · UK", "LAM",
  "Laminated sycamore veneers spaced apart as open fins rather than a solid mass, with resin details.",
  "Marc Fish Ethereal lounge chair", "https://www.1stdibs.com/furniture/seating/lounge-chairs/marc-fish-contemporary-organic-modern-sculptural-lounge-chair-edition-5-7/id-f_41400512"),
 (4, "Nautilus Table", "Marc Fish · UK", "LAM",
  "A study in micro stack-lamination, using veneer as the primary raw material for a spiralling form.",
  "Marc Fish Nautilus table", "https://www.gazette-drouot.com/lots/10815687"),
 (5, "Möbius Console", "Pierre Renart · France", "LAM",
  "A ribbon of wood curling over itself without end, inspired by the Möbius strip (2013).",
  "Pierre Renart Mobius console", "https://2022.homofaber.com/it/discover/discover-pierre-renart"),
 (6, "Ruban Chair & Wave Bench", "Pierre Renart for Longchamp · 2025", "LAM",
  "American walnut assembled from ultra-thin bent layers with leather seats; the chair took over ten years to refine.",
  "Pierre Renart Ruban chair Longchamp", "https://www.dezeen.com/2025/04/09/pierre-renart-longchamp-furniture-collection/"),
 (7, "Escale", "Pierre Renart · France", "LAM",
  "A hammock-like seat of seven hornbeam strips that converge like the fibres of a muscle.",
  "Pierre Renart Escale hornbeam", "https://private-air-mag.com/designandarchitecture/pierre-renart-escale"),
 (8, "Stack-Laminated Chairs", "Wendell Castle · USA", "LAM",
  "Stacked, glued and carved solid laminations: the historical root of most organic wood seating.",
  "Wendell Castle stack laminated chair", None),
 (9, "Steam-Bent Benches", "Matthias Pliessnig · USA", "STM",
  "Ash and white oak strips steam-bent into twisting, CAD-drafted seating surfaces.",
  "Matthias Pliessnig steam bent bench", "https://www.thisiscolossal.com/2018/08/benches-made-by-steam-bent-hardwood-by-matthias-pliessnig/"),
 (10, "Steam Bent Series", "Bae Se Hwa · Korea", "STM",
  "Hundreds of walnut strips shaped to the contours of the body; among the most ergonomic pieces here.",
  "Bae Se Hwa steam bent chair", "https://www.dezeen.com/2019/03/25/bae-se-hwa-steam-bent-series-r-company-exhibit/"),
 (11, "Wooden Chair (1988)", "Marc Newson", "STM",
  "Steam-bent beech slats coiled into a continuous loop; one repeated element builds the whole volume.",
  "Marc Newson Wooden Chair 1988", None),
 (12, "Bentwood Rocking Chair", "Thonet", "STM",
  "The 19th-century origin of solid-wood steam bending: continuous scrolls with minimal joinery.",
  "Thonet bentwood rocking chair", None),
 (13, "Silva Collection", "KFI Studios / Union Design", "STM",
  "A production example: solid beech rear legs flow to the backrest in one steam-bent arc.",
  "KFI Studios Silva chair steam bent beech", "https://www.yankodesign.com/2025/11/18/silva-wood-collection-by-kfi-studios-steam-bent-beech-furniture-designed-by-union-design/amp/"),
 (14, "Steam-Bent Ribbon Work", "Tom Raffield · UK", "STM",
  "Looping ash and oak ribbons; the studio's process videos are excellent for learning the technique.",
  "Tom Raffield steam bent furniture", None),
 (15, "CH07 Shell Chair", "Hans J. Wegner · Carl Hansen", "PLY",
  "The three-legged 'smiling' molded shell; comfort without upholstery mass.",
  "Hans Wegner CH07 shell chair", None),
 (16, "Paimio Chair", "Alvar Aalto · Artek", "PLY",
  "Bent birch plywood scroll seat designed for sanatorium patients; ergonomic by brief.",
  "Alvar Aalto Paimio chair", None),
 (17, "LCW (Lounge Chair Wood)", "Charles & Ray Eames", "PLY",
  "Separate compound-curve plywood seat and back that follow the body.",
  "Eames LCW plywood chair", None),
 (18, "Lounge Chair 670", "Charles & Ray Eames", "PLY",
  "Veneered molded shells in a reclined geometry; still the reference for lounge ergonomics.",
  "Eames lounge chair 670 walnut", None),
 (19, "Series 7 / Ant Chair", "Arne Jacobsen · Fritz Hansen", "PLY",
  "A single pressed 3D veneer shell with a waist cut-in that gives it flex.",
  "Arne Jacobsen Series 7 chair veneer", None),
 (20, "GJ Chair (1963)", "Grete Jalk", "PLY",
  "Two folded plywood sheets; shows how far developable surfaces can be pushed.",
  "Grete Jalk GJ chair plywood", None),
 (21, "Osuu Chair", "Foster + Partners · Walter Knoll", "PLY",
  "An all-wood chair combining bentwood and plywood in a light modern production piece.",
  "Foster Partners Osuu chair Walter Knoll", "https://www.walterknoll.de/en/lp/osuu-chair"),
 (22, "Void Lounge Chair", "João Teixeira · Teixeira Design Studio", "PLY",
  "A single sheet of plywood transformed into a sculptural, functional lounge chair.",
  "Teixeira Design Void lounge chair plywood", "https://www.yankodesign.com/2026/10/06/bent-wood-is-stronger-than-straight-wood-these-5-designs-prove-it/amp/"),
 (23, "Cowrie & Alpha Chairs", "Brodie Neill · Made in Ratio", "CNC",
  "CNC routing used as a production tool so components merge into one seamless mold.",
  "Brodie Neill Cowrie chair", "https://www.woodworkingnetwork.com/furniture/cnc-routing-sculptural-wood-manufacturing-chairs"),
 (24, "Vivien Chair", "Luca Nichetto · De La Espada", "CNC",
  "Solid wood machined on CNC, then carved, assembled and finished by hand. A CAD-first workflow.",
  "Luca Nichetto Vivien chair", "https://www.thefutureperfect.com/product/furniture/seating/dining-seating/vivien-dining-chair"),
 (25, "Murena & Möbius Benches", "Oleg Soroko", "CNC",
  "Stacked CNC-cut 18 mm plywood sections reinforced with metal rods; waffle-frame adjacent.",
  "Oleg Soroko parametric plywood bench", "https://www.1stdibs.com/furniture/seating/sofas/organic-plywood-bench-murena-inspired-parametric-cnc-design/id-f_43651532"),
 (26, "Lolo & Aida Chairs", "Piegatto · Guatemala", "CNC",
  "Sectional contouring from laminated birch plywood slices, done in a production-friendly way.",
  "Piegatto Lolo chair plywood", None),
 (27, "Rocking Chair", "Sam Maloof · USA", "HAND",
  "Sculpted walnut joints flow into one another and the seat is carved to the sitter.",
  "Sam Maloof rocking chair", None),
 (28, "Conoid Chair", "George Nakashima · USA", "HAND",
  "Cantilevered sled base and spindle back; the structure itself is the aesthetic.",
  "George Nakashima Conoid chair", None),
 (29, "Sculptural Chairs", "Wharton Esherick · USA", "HAND",
  "The American studio-furniture origin of faceted, expressive organic wood forms.",
  "Wharton Esherick chair", None),
 (30, "PK15 Chair", "Poul Kjærholm · Fritz Hansen", "HAND",
  "A quieter, minimalist counterpoint defined by its bent-wood lines.",
  "Poul Kjaerholm PK15 chair", "https://www.trendhunter.com/trends/pk15"),
]

def motif(c, x, y, w, h, cat, seed, col):
    """Abstract line study per technique, drawn in vector."""
    c.saveState()
    p = c.beginPath(); p.rect(x, y, w, h); c.clipPath(p, stroke=0, fill=0)
    c.setStrokeColor(col)
    if cat == "LAM":  # nested flowing laminations
        for i in range(14):
            c.setLineWidth(0.6); c.setStrokeAlpha(0.25 + 0.05 * (i % 4))
            pp = c.beginPath(); off = i * 3.2
            pp.moveTo(x - 10, y + h * 0.25 + off)
            pp.curveTo(x + w * 0.3, y + h * (0.9 + 0.02 * seed) - off * 0.3,
                       x + w * 0.6, y + h * 0.05 + off * 0.8,
                       x + w + 10, y + h * 0.65 + off * 0.4)
            c.drawPath(pp)
    elif cat == "STM":  # slatted wave
        n = 22
        for i in range(n):
            t = i / (n - 1)
            xx = x + 12 + t * (w - 24)
            a = math.sin(t * math.pi * (1.4 + 0.1 * (seed % 3)) + seed) * h * 0.22
            c.setLineWidth(1.6); c.setStrokeAlpha(0.55)
            pp = c.beginPath()
            pp.moveTo(xx, y + h * 0.2 + a * 0.4)
            pp.curveTo(xx + 6, y + h * 0.45 + a, xx - 6, y + h * 0.6 + a, xx, y + h * 0.82 + a * 0.3)
            c.drawPath(pp)
    elif cat == "PLY":  # shell contours
        cx, cy = x + w / 2, y + h * 0.18
        for i in range(9):
            r = 14 + i * 7
            c.setLineWidth(0.9); c.setStrokeAlpha(0.6 - i * 0.05)
            c.arc(cx - r * 1.5, cy - r * 0.6, cx + r * 1.5, cy + r * 1.9, 10 + seed, 160)
    elif cat == "CNC":  # sectional ribs
        n = 26
        for i in range(n):
            t = i / (n - 1)
            xx = x + 10 + t * (w - 20)
            top = y + h * (0.45 + 0.3 * math.sin(t * math.pi * 1.2 + seed * 0.7))
            c.setLineWidth(1.3); c.setStrokeAlpha(0.55)
            c.line(xx, y + h * 0.15, xx, top)
        c.setLineWidth(0.8); c.setStrokeAlpha(0.8)
        pp = c.beginPath(); pp.moveTo(x + 10, y + h * 0.15); pp.lineTo(x + w - 10, y + h * 0.15); c.drawPath(pp)
    else:  # hand: carved grain
        for i in range(12):
            c.setLineWidth(0.7); c.setStrokeAlpha(0.35)
            r = 8 + i * 6
            c.ellipse(x + w * 0.55 - r * 1.6, y + h * 0.5 - r * 0.55, x + w * 0.55 + r * 1.6, y + h * 0.5 + r * 0.55)
    c.restoreState()

note_style = ParagraphStyle("n", fontName="Sans", fontSize=7.6, leading=10.4, textColor=INK)

def button(c, x, y, label, url, col, filled):
    c.setFont("SansB", 6.8)
    tw = c.stringWidth(label, "SansB", 6.8)
    bw, bh = tw + 16, 14
    if filled:
        c.setFillColor(col); c.roundRect(x, y, bw, bh, 7, stroke=0, fill=1)
        c.setFillColor(HexColor("#FFFFFF"))
    else:
        c.setStrokeColor(col); c.setLineWidth(0.8); c.roundRect(x, y, bw, bh, 7, stroke=1, fill=0)
        c.setFillColor(col)
    c.drawString(x + 8, y + 4.3, label)
    c.linkURL(url, (x, y, x + bw, y + bh), relative=0, thickness=0)
    return bw

def card(c, x, y, w, h, item):
    no, title, des, cat, note, q, src = item
    name, col, tint = CATS[cat]
    c.setFillColor(HexColor("#FFFFFF")); c.setStrokeColor(LINE); c.setLineWidth(0.6)
    c.roundRect(x, y, w, h, 6, stroke=1, fill=1)
    # visual band
    bh = h * 0.48
    c.saveState()
    p = c.beginPath(); p.roundRect(x, y + h - bh, w, bh, 6); c.clipPath(p, stroke=0, fill=0)
    c.setFillColor(tint); c.rect(x, y + h - bh, w, bh, stroke=0, fill=1)
    c.restoreState()
    c.setFillColor(tint); c.rect(x, y + h - bh, w, 8, stroke=0, fill=1)
    motif(c, x, y + h - bh, w, bh - 36, cat, no, col)
    c.linkURL(gimg(q), (x, y + h - bh, x + w, y + h), relative=0, thickness=0)
    # number
    c.setFillColor(col); c.setFont("SerifB", 20)
    c.drawString(x + 12, y + h - 28, f"{no:02d}")
    c.setFont("Sans", 6.2); c.setFillColor(col)
    c.drawRightString(x + w - 12, y + h - 18, name.upper())
    # text
    ty = y + h - bh - 20
    c.setFillColor(INK); c.setFont("SerifB", 11.5)
    c.drawString(x + 12, ty, title)
    c.setFillColor(MUTED); c.setFont("SerifI", 8.4)
    c.drawString(x + 12, ty - 13, des)
    para = Paragraph(note, note_style)
    pw, ph = para.wrap(w - 24, 60)
    para.drawOn(c, x + 12, ty - 20 - ph)
    # buttons
    bx = x + 12
    bx += button(c, bx, y + 10, "VIEW IMAGES  ↗", gimg(q), col, True) + 6
    if src:
        button(c, bx, y + 10, "SOURCE  ↗", src, col, False)

def page_bg(c):
    c.setFillColor(PAPER); c.rect(0, 0, W, H, stroke=0, fill=1)

def header(c, cat_key, pg):
    name, col, _ = CATS[cat_key]
    c.setFillColor(INK); c.setFont("Serif", 18)
    c.drawString(40, H - 46, "Sculptural & Ergonomic Wood Furniture")
    c.setFillColor(MUTED); c.setFont("Sans", 8)
    c.drawRightString(W - 40, H - 44, f"{pg:02d}")
    c.setStrokeColor(LINE); c.setLineWidth(0.6); c.line(40, H - 56, W - 40, H - 56)

def cover(c):
    page_bg(c)
    # big motif field
    c.saveState()
    for i in range(40):
        c.setStrokeColor(HexColor("#8B5E3C")); c.setStrokeAlpha(0.10 + 0.012 * (i % 10)); c.setLineWidth(0.7)
        pp = c.beginPath(); off = i * 6
        pp.moveTo(W * 0.38, -20 + off)
        pp.curveTo(W * 0.6, H * 0.9 - off * 0.4, W * 0.75, off * 0.6, W + 20, H * 0.55 + off * 0.5)
        c.drawPath(pp)
    c.restoreState()
    c.setFillColor(MUTED); c.setFont("Sans", 9)
    c.drawString(56, H - 90, "VISUAL REFERENCE GALLERY  ·  30 PIECES")
    c.setFillColor(INK); c.setFont("Serif", 40)
    c.drawString(56, H - 170, "Sculptural &")
    c.drawString(56, H - 216, "Ergonomic Wood")
    c.drawString(56, H - 262, "Furniture")
    c.setFillColor(MUTED); c.setFont("SerifI", 12)
    c.drawString(56, H - 296, "Laminated veneer, steam-bent strip, molded plywood,")
    c.drawString(56, H - 312, "CNC-sculpted sections and studio-carved masters.")
    # legend
    y = 150
    c.setFont("Sans", 7.5); c.setFillColor(MUTED)
    c.drawString(56, y + 22, "TECHNIQUE KEY")
    for k, (name, col, tint) in CATS.items():
        c.setFillColor(col); c.circle(62, y + 3, 4, stroke=0, fill=1)
        c.setFillColor(INK); c.setFont("Sans", 9); c.drawString(74, y, name)
        y -= 18
    c.setFillColor(MUTED); c.setFont("Sans", 7.5)
    c.drawString(56, 46, "Tap any card’s colour panel or “View images” to open a live image search; “Source” opens the original article or listing.")

def closing(c, pg):
    page_bg(c)
    c.setFillColor(INK); c.setFont("Serif", 18)
    c.drawString(40, H - 46, "Patterns Worth Taking Into Your Own Work")
    c.setStrokeColor(LINE); c.line(40, H - 56, W - 40, H - 56)
    c.setFillColor(MUTED); c.setFont("Sans", 8); c.drawRightString(W - 40, H - 44, f"{pg:02d}")
    pts = [
        ("Let the grain set the curve", "Walsh, Fish and Renart bend thin laminations instead of carving against the grain. The curve is whatever the stacked layers allow, which is why the forms look effortless and stay strong."),
        ("Start from the body, let structure follow", "The most comfortable pieces (Bae Se Hwa, Maloof, Wegner, Aalto) are built from seat and back contours outward; legs and frames are resolved last."),
        ("Pick the process before the form", "Steam-bent strips suit open, slatted surfaces; molded plywood suits shells; stack lamination suits massive, carved volumes. Designing within one process keeps the piece buildable."),
        ("CAD-first workflows that translate", "Nichetto's CNC-then-hand-finish approach and Soroko / Piegatto's sectional builds are the most direct routes from a 3D model to a sculptural wood piece."),
        ("Repetition creates complexity", "Newson, Pliessnig and the sectional benches all use one repeated element — a slat, a strip, a rib — to generate a complex overall volume with simple parts."),
    ]
    y = H - 100
    st = ParagraphStyle("b", fontName="Sans", fontSize=9.6, leading=14, textColor=INK)
    for i, (t, b) in enumerate(pts):
        c.setFillColor(HexColor("#8B5E3C")); c.setFont("SerifB", 22); c.drawString(48, y - 6, f"{i+1}")
        c.setFillColor(INK); c.setFont("SerifB", 12.5); c.drawString(84, y, t)
        p = Paragraph(b, st); pw, ph = p.wrap(W - 180, 100); p.drawOn(c, 84, y - 8 - ph)
        y -= 26 + ph + 18

c = canvas.Canvas(OUT, pagesize=(W, H))
c.setTitle("Sculptural & Ergonomic Wood Furniture — Visual Reference Gallery")
cover(c); c.showPage()

cols, rows = 3, 2
mx, top, gap = 40, H - 72, 14
cw = (W - 2 * mx - gap * (cols - 1)) / cols
ch = (top - 30 - gap * (rows - 1)) / rows
pg = 2
for start in range(0, 30, 6):
    page_bg(c)
    chunk = P[start:start + 6]
    header(c, chunk[0][3], pg)
    for i, item in enumerate(chunk):
        r, k = divmod(i, cols)
        x = mx + k * (cw + gap)
        y = top - (r + 1) * ch - r * gap
        card(c, x, y, cw, ch, item)
    c.showPage(); pg += 1
closing(c, pg); c.showPage()
c.save()
print("ok", OUT)
