from pathlib import Path

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_AUTO_SHAPE_TYPE, MSO_CONNECTOR
from pptx.enum.text import MSO_ANCHOR, MSO_AUTO_SIZE, PP_ALIGN
from pptx.util import Inches, Pt


BASE_DIR = Path(__file__).resolve().parents[1]
OUT_PPTX = BASE_DIR / "docs" / "Negotiator_Pactum_15min.pptx"

FONT_DISPLAY = "Aptos Display"
FONT_BODY = "Aptos"
FONT_MONO = "Consolas"


def rgb(value: str) -> RGBColor:
    value = value.replace("#", "")
    return RGBColor(int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16))


NAVY = rgb("16253F")
INK = rgb("1F2937")
MUTED = rgb("6B7280")
STONE = rgb("9CA3AF")
PAPER = rgb("F6F3EE")
WHITE = rgb("FFFFFF")
SAND = rgb("DED5C8")
GRID = rgb("DDD6CC")
MIST = rgb("EEF2F7")
BLUE = rgb("2563EB")
BLUE_SOFT = rgb("E7F0FF")
TEAL = rgb("0F766E")
TEAL_SOFT = rgb("DDF4F1")
GREEN = rgb("2F855A")
GREEN_SOFT = rgb("DDF4E4")
AMBER = rgb("C47A0A")
AMBER_SOFT = rgb("F9E7C6")
ROSE = rgb("C24163")
ROSE_SOFT = rgb("F9DFE7")
NAVY_SOFT = rgb("E8EDF6")


STRATEGY_POINTS = [
    {"name": "Conceder", "round": 2.25, "utility": 0.5938, "color": ROSE},
    {"name": "Baseline", "round": 3.00, "utility": 0.7340, "color": BLUE},
    {"name": "Tit for Tat", "round": 3.05, "utility": 0.7340, "color": TEAL},
    {"name": "Boulware", "round": 5.00, "utility": 0.7583, "color": GREEN},
    {"name": "Meso", "round": 5.33, "utility": 0.6922, "color": AMBER},
]

STRATEGY_ROWS = [
    ("Conceder", "0.5938", "2.25", ROSE),
    ("Baseline", "0.7340", "3.00", BLUE),
    ("Tit for Tat", "0.7340", "3.00", TEAL),
    ("Boulware", "0.7583", "5.00", GREEN),
    ("Meso", "0.6922", "5.33", AMBER),
]

SCENARIO_MATRIX = [
    ("Hardliner", ["R8", "R8", "R8", "R8", "R8"]),
    ("Price floor", ["R2", "R8", "R2", "R2", "R2"]),
    ("Payment cap", ["R2", "R8", "R2", "A2", "R2"]),
    ("Delivery floor", ["R8", "A8", "R8", "A5", "R8"]),
    ("Near settle", ["A3", "A4", "A5", "A1", "A3"]),
    ("Deadline", ["A3", "A4", "A5", "A1", "A3"]),
]

PRICE_RESULTS = [
    ("Boulware", 106.50, "R7", "offer R5", GREEN),
    ("Baseline", 111.00, "R7", "offer R6", BLUE),
    ("Tit for Tat", 113.33, "R7", "offer R6", TEAL),
    ("Conceder", 120.00, "R7", "offer R5", ROSE),
    ("Meso", None, "", "no deal", AMBER),
]

PRICE_SCRIPT = [
    ("T1", "Anchor", "P120  D30  C24"),
    ("T2", "Delivery", "P120  D21  C24"),
    ("T3", "Delivery", "P120  D14  C24"),
    ("T4", "Contract", "P120  D14  C12"),
    ("T5", "Delivery", "P120   D7   C12"),
    ("T6", "Repeat", "P120   D7   C12"),
]


def set_background(slide, color: RGBColor) -> None:
    fill = slide.background.fill
    fill.solid()
    fill.fore_color.rgb = color


def add_backdrop(slide, primary: RGBColor, secondary: RGBColor) -> None:
    add_oval(slide, 9.9, -0.65, 4.1, 4.1, primary, primary, 0.5)
    add_oval(slide, -1.00, 5.55, 3.2, 3.2, secondary, secondary, 0.5)


def add_shape(slide, shape_type, x, y, w, h, fill_color, line_color=None, line_width=1.2):
    shape = slide.shapes.add_shape(shape_type, Inches(x), Inches(y), Inches(w), Inches(h))
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill_color
    shape.line.color.rgb = line_color or fill_color
    shape.line.width = Pt(line_width)
    return shape


def add_round_rect(slide, x, y, w, h, fill_color, line_color=None, line_width=1.2):
    return add_shape(slide, MSO_AUTO_SHAPE_TYPE.ROUNDED_RECTANGLE, x, y, w, h, fill_color, line_color, line_width)


def add_rect(slide, x, y, w, h, fill_color, line_color=None, line_width=1.2):
    return add_shape(slide, MSO_AUTO_SHAPE_TYPE.RECTANGLE, x, y, w, h, fill_color, line_color, line_width)


def add_oval(slide, x, y, w, h, fill_color, line_color=None, line_width=1.2):
    return add_shape(slide, MSO_AUTO_SHAPE_TYPE.OVAL, x, y, w, h, fill_color, line_color, line_width)


def add_line(slide, x1, y1, x2, y2, color, width=1.8):
    line = slide.shapes.add_connector(
        MSO_CONNECTOR.STRAIGHT,
        Inches(x1),
        Inches(y1),
        Inches(x2),
        Inches(y2),
    )
    line.line.color.rgb = color
    line.line.width = Pt(width)
    return line


def add_textbox(
    slide,
    x,
    y,
    w,
    h,
    text,
    *,
    size=18,
    color=INK,
    bold=False,
    font_name=FONT_BODY,
    align=PP_ALIGN.LEFT,
    vertical=MSO_ANCHOR.TOP,
    margins=(0.06, 0.04, 0.04, 0.02),
):
    shape = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = shape.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.auto_size = MSO_AUTO_SIZE.TEXT_TO_FIT_SHAPE
    tf.margin_left = Inches(margins[0])
    tf.margin_right = Inches(margins[1])
    tf.margin_top = Inches(margins[2])
    tf.margin_bottom = Inches(margins[3])
    tf.vertical_anchor = vertical

    for index, line in enumerate(text.split("\n")):
        p = tf.paragraphs[0] if index == 0 else tf.add_paragraph()
        p.text = line
        p.alignment = align
        p.space_after = Pt(0)
        p.space_before = Pt(0)
        p.font.size = Pt(size)
        p.font.bold = bold
        p.font.name = font_name
        p.font.color.rgb = color
    return shape


def add_card(slide, x, y, w, h, title, body, fill, accent):
    add_round_rect(slide, x, y, w, h, fill, accent, 1.6)
    add_textbox(slide, x + 0.16, y + 0.14, w - 0.32, 0.22, title, size=15.2, color=NAVY, bold=True)
    add_textbox(slide, x + 0.16, y + 0.46, w - 0.32, h - 0.56, body, size=11.4, color=INK)


def add_pill(slide, x, y, w, h, text, fill, text_color, line_color=None, font_size=10.4):
    add_round_rect(slide, x, y, w, h, fill, line_color or fill, 1)
    add_textbox(
        slide,
        x + 0.02,
        y + 0.06,
        w - 0.04,
        h - 0.10,
        text,
        size=font_size,
        color=text_color,
        bold=True,
        align=PP_ALIGN.CENTER,
        vertical=MSO_ANCHOR.MIDDLE,
    )


def title_block(slide, chip, title, subtitle):
    add_pill(slide, 0.64, 0.44, 2.18, 0.30, chip, AMBER_SOFT, AMBER, AMBER, 9.8)
    add_textbox(slide, 0.64, 0.92, 7.80, 0.38, title, size=28, color=NAVY, bold=True, font_name=FONT_DISPLAY)
    add_textbox(slide, 0.66, 1.34, 8.90, 0.22, subtitle, size=12.2, color=MUTED)
    add_line(slide, 0.66, 1.68, 12.68, 1.68, SAND, 1.1)


def style_cell(cell, text, *, fill, color=INK, bold=False, size=11.0, align=PP_ALIGN.CENTER, font_name=FONT_BODY):
    cell.fill.solid()
    cell.fill.fore_color.rgb = fill
    cell.margin_left = Inches(0.05)
    cell.margin_right = Inches(0.05)
    cell.margin_top = Inches(0.02)
    cell.margin_bottom = Inches(0.02)
    tf = cell.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.auto_size = MSO_AUTO_SIZE.TEXT_TO_FIT_SHAPE
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    p = tf.paragraphs[0]
    p.text = text
    p.alignment = align
    p.space_before = Pt(0)
    p.space_after = Pt(0)
    p.font.name = font_name
    p.font.size = Pt(size)
    p.font.bold = bold
    p.font.color.rgb = color


def build_slide_1(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    set_background(slide, PAPER)
    add_backdrop(slide, MIST, AMBER_SOFT)
    title_block(
        slide,
        "PACTUM CHALLENGE",
        "Negotiator",
        "Autonomous multi-issue negotiation with explicit buyer policy.",
    )

    add_textbox(
        slide,
        0.66,
        2.02,
        5.60,
        0.62,
        "We framed the task as a commercial decision system: safe trade-offs, explainable acceptance, and strategy behavior that can be measured in tests.",
        size=14.0,
        color=INK,
    )

    cards = [
        ("Multi-issue utility", "Every offer is scored across price, payment, delivery, and contract.", BLUE_SOFT, BLUE),
        ("Deterministic authority", "Accept / counter / reject stays in a rule-based engine.", TEAL_SOFT, TEAL),
        ("Measured strategy", "The tests reveal the cost of settling early versus holding value.", GREEN_SOFT, GREEN),
    ]
    y = 3.00
    for title, body, fill, accent in cards:
        add_card(slide, 0.74, y, 5.38, 0.96, title, body, fill, accent)
        y += 1.16

    add_round_rect(slide, 7.18, 2.18, 5.08, 4.34, NAVY, NAVY, 1)
    add_textbox(slide, 7.54, 2.48, 2.60, 0.24, "Decision surface", size=20, color=WHITE, bold=True)
    add_oval(slide, 9.14, 3.56, 1.54, 1.54, WHITE, WHITE, 1)
    add_textbox(slide, 9.39, 4.02, 1.02, 0.50, "Buyer\nutility", size=16, color=NAVY, bold=True, align=PP_ALIGN.CENTER)

    nodes = [
        ("Price", 8.00, 2.96, BLUE),
        ("Payment", 10.66, 2.96, TEAL),
        ("Delivery", 8.00, 5.20, GREEN),
        ("Contract", 10.66, 5.20, AMBER),
    ]
    for label, x, y, color in nodes:
        add_pill(slide, x, y, 1.16, 0.36, label, color, WHITE, color, 11.2)
    add_line(slide, 8.58, 3.32, 9.42, 3.96, WHITE, 2.0)
    add_line(slide, 11.24, 3.32, 10.40, 3.96, WHITE, 2.0)
    add_line(slide, 8.58, 5.20, 9.42, 4.90, WHITE, 2.0)
    add_line(slide, 11.24, 5.20, 10.40, 4.90, WHITE, 2.0)

    add_round_rect(slide, 7.56, 6.00, 4.32, 0.40, rgb("233244"), rgb("233244"), 1)
    add_textbox(
        slide,
        7.70,
        6.10,
        4.02,
        0.12,
        "AI helps interpret language. The engine owns commitments.",
        size=11.0,
        color=WHITE,
        align=PP_ALIGN.CENTER,
    )


def build_slide_2(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    set_background(slide, PAPER)
    add_backdrop(slide, BLUE_SOFT, NAVY_SOFT)
    title_block(slide, "SYSTEM BOUNDARY", "Architecture", "Language can vary. Buyer commitments cannot.")

    add_pill(slide, 0.88, 2.10, 1.72, 0.30, "LLM assistance", BLUE_SOFT, BLUE, BLUE, 10.2)
    add_pill(slide, 7.36, 2.10, 2.06, 0.30, "Deterministic engine", GREEN_SOFT, GREEN, GREEN, 10.2)

    add_card(slide, 0.84, 2.62, 2.36, 1.18, "Parse supplier text", "Convert free text into terms and constraints.", BLUE_SOFT, BLUE)
    add_card(slide, 0.84, 4.10, 2.36, 1.18, "Classify intent", "Accept, counter, clarify, or choose between options.", BLUE_SOFT, BLUE)
    add_card(slide, 0.84, 5.58, 2.36, 1.18, "Draft wording", "Language is generated only after the action is fixed.", BLUE_SOFT, BLUE)

    add_round_rect(slide, 4.32, 3.56, 1.72, 1.22, NAVY, NAVY, 1)
    add_textbox(
        slide,
        4.52,
        3.90,
        1.32,
        0.50,
        "Structured\noffer",
        size=18,
        color=WHITE,
        bold=True,
        align=PP_ALIGN.CENTER,
    )

    add_card(slide, 7.28, 2.62, 2.32, 1.18, "Score the offer", "Utility across price, payment, delivery, and contract.", GREEN_SOFT, GREEN)
    add_card(slide, 9.88, 2.62, 2.32, 1.18, "Apply policy", "Reservation limits, round target, settlement posture.", GREEN_SOFT, GREEN)
    add_card(slide, 8.58, 4.42, 2.32, 1.18, "Choose action", "Accept, reject, or return a buyer-safe counteroffer.", GREEN_SOFT, GREEN)

    add_line(slide, 3.20, 3.18, 4.32, 4.18, NAVY, 2.2)
    add_line(slide, 3.20, 4.66, 4.32, 4.18, NAVY, 2.2)
    add_line(slide, 3.20, 6.14, 4.32, 4.18, NAVY, 2.2)
    add_line(slide, 6.04, 4.18, 7.28, 3.18, NAVY, 2.2)
    add_line(slide, 6.04, 4.18, 9.88, 3.18, NAVY, 2.2)
    add_line(slide, 8.44, 3.80, 9.26, 4.42, NAVY, 2.2)
    add_line(slide, 11.04, 3.80, 10.24, 4.42, NAVY, 2.2)

    add_round_rect(slide, 7.24, 6.00, 4.98, 0.56, WHITE, SAND, 1)
    add_textbox(
        slide,
        7.42,
        6.18,
        4.62,
        0.16,
        "AI parses and drafts. The engine scores, constrains, and decides.",
        size=12.0,
        color=NAVY,
        bold=True,
        align=PP_ALIGN.CENTER,
    )


def build_slide_3(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    set_background(slide, PAPER)
    add_backdrop(slide, NAVY_SOFT, TEAL_SOFT)
    title_block(slide, "NEGOTIATION CORE", "Decision System", "The algorithm is layered so each rule has one job.")

    steps = [
        ("1", "Normalize", "Map each issue to a comparable score.", BLUE_SOFT, BLUE, "BuyerPreferenceScoring"),
        ("2", "Weight", "Aggregate into buyer utility.", TEAL_SOFT, TEAL, "BuyerUtilityCalculator"),
        ("3", "Target", "Adjust demand by round and strategy.", AMBER_SOFT, AMBER, "DecisionMaker"),
        ("4", "Settle", "Apply settlement posture and safety gates.", GREEN_SOFT, GREEN, "StrategySettlementPolicy"),
        ("5", "Counter", "Move the most valuable issue next.", ROSE_SOFT, ROSE, "CounterOfferGenerator"),
    ]
    x = 0.72
    for index, (num, title, body, fill, accent, owner) in enumerate(steps):
        add_round_rect(slide, x, 2.46, 2.26, 2.04, fill, accent, 1.6)
        add_oval(slide, x + 0.16, 2.64, 0.42, 0.42, accent, accent, 1)
        add_textbox(slide, x + 0.25, 2.73, 0.24, 0.10, num, size=12.0, color=WHITE, bold=True, align=PP_ALIGN.CENTER)
        add_textbox(slide, x + 0.68, 2.64, 1.30, 0.20, title, size=17, color=NAVY, bold=True)
        add_textbox(slide, x + 0.16, 3.12, 1.92, 0.42, body, size=11.0, color=INK)
        add_pill(slide, x + 0.16, 3.86, 1.94, 0.28, owner, WHITE, accent, accent, 9.2)
        if index < len(steps) - 1:
            add_line(slide, x + 2.26, 3.46, x + 2.46, 3.46, STONE, 2)
        x += 2.48

    add_round_rect(slide, 0.86, 5.10, 5.76, 1.20, WHITE, SAND, 1.2)
    add_textbox(slide, 1.12, 5.32, 1.80, 0.18, "Utility model", size=18, color=NAVY, bold=True)
    add_textbox(
        slide,
        1.10,
        5.68,
        4.96,
        0.24,
        "U_buyer = sum(normalized_score_i x normalized_weight_i)",
        size=14.0,
        color=INK,
        bold=True,
        font_name=FONT_MONO,
    )
    add_textbox(
        slide,
        1.12,
        5.98,
        4.92,
        0.12,
        "Price, payment, delivery, and contract all contribute to a single buyer-side score.",
        size=10.8,
        color=MUTED,
    )

    add_round_rect(slide, 6.90, 5.10, 5.52, 1.20, WHITE, SAND, 1.2)
    add_textbox(slide, 7.16, 5.32, 1.60, 0.18, "Code map", size=18, color=NAVY, bold=True)
    add_textbox(
        slide,
        7.16,
        5.68,
        4.92,
        0.42,
        "NegotiationEngine.java\nNegotiationEngineImpl.java\nDecisionMaker.java  |  StrategySettlementPolicy.java  |  CounterOfferGenerator.java",
        size=10.8,
        color=INK,
        font_name=FONT_MONO,
    )


def build_slide_4(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    set_background(slide, PAPER)
    add_backdrop(slide, GREEN_SOFT, BLUE_SOFT)
    title_block(slide, "STRATEGY COMPARISON", "Strategy Trade-offs", "Accepted averages from StrategySimulationMatrixTest.")

    add_round_rect(slide, 0.72, 2.10, 7.18, 4.82, WHITE, SAND, 1.2)
    left = 1.34
    top = 2.78
    right = 7.18
    bottom = 6.20
    add_line(slide, left, bottom, right, bottom, STONE, 2.2)
    add_line(slide, left, top, left, bottom, STONE, 2.2)

    xmin, xmax = 2.0, 5.5
    ymin, ymax = 0.58, 0.77
    for tick in [2, 3, 4, 5]:
        x = left + (tick - xmin) / (xmax - xmin) * (right - left)
        add_line(slide, x, top, x, bottom, GRID, 1)
        add_textbox(slide, x - 0.12, bottom + 0.08, 0.24, 0.12, str(tick), size=10.0, color=MUTED, align=PP_ALIGN.CENTER)
    for tick in [0.60, 0.65, 0.70, 0.75]:
        y = bottom - (tick - ymin) / (ymax - ymin) * (bottom - top)
        add_line(slide, left, y, right, y, GRID, 1)
        add_textbox(slide, 0.88, y - 0.06, 0.30, 0.12, f"{tick:.2f}", size=10.0, color=MUTED, align=PP_ALIGN.RIGHT)

    add_textbox(slide, 3.44, 6.44, 1.72, 0.12, "Avg accepted round", size=11.0, color=MUTED, align=PP_ALIGN.CENTER)
    add_textbox(slide, 0.24, 4.18, 0.64, 0.42, "Avg buyer\nutility", size=10.8, color=MUTED, align=PP_ALIGN.CENTER)
    add_pill(slide, 1.52, 2.28, 1.46, 0.24, "faster close", ROSE_SOFT, ROSE, ROSE, 9.4)
    add_pill(slide, 5.56, 2.28, 1.52, 0.24, "more value", GREEN_SOFT, GREEN, GREEN, 9.4)

    label_pos = {
        "Conceder": (-0.12, 0.18),
        "Baseline": (0.18, -0.26),
        "Tit for Tat": (0.18, 0.02),
        "Boulware": (-0.94, -0.24),
        "Meso": (-0.44, 0.10),
    }
    for point in STRATEGY_POINTS:
        x = left + (point["round"] - xmin) / (xmax - xmin) * (right - left)
        y = bottom - (point["utility"] - ymin) / (ymax - ymin) * (bottom - top)
        add_oval(slide, x - 0.10, y - 0.10, 0.20, 0.20, point["color"], WHITE, 1.6)
        dx, dy = label_pos[point["name"]]
        add_round_rect(slide, x + dx, y + dy, 0.96, 0.24, WHITE, WHITE, 0.5)
        add_textbox(slide, x + dx + 0.02, y + dy + 0.06, 0.92, 0.10, point["name"], size=10.0, color=point["color"], bold=True, align=PP_ALIGN.CENTER)

    add_round_rect(slide, 8.20, 2.10, 4.32, 2.28, WHITE, SAND, 1.2)
    add_textbox(slide, 8.46, 2.34, 2.40, 0.18, "Accepted averages", size=18, color=NAVY, bold=True)
    table = slide.shapes.add_table(6, 3, Inches(8.42), Inches(2.76), Inches(3.86), Inches(1.38)).table
    table.columns[0].width = Inches(1.80)
    table.columns[1].width = Inches(0.94)
    table.columns[2].width = Inches(1.00)
    headers = ["Strategy", "Buyer U", "Avg R"]
    for col, header in enumerate(headers):
        style_cell(table.cell(0, col), header, fill=NAVY, color=WHITE, bold=True, size=11.0)
    for row, (name, utility, round_value, color) in enumerate(STRATEGY_ROWS, start=1):
        row_fill = WHITE if row % 2 else MIST
        style_cell(table.cell(row, 0), name, fill=row_fill, color=color, bold=True, size=10.8, align=PP_ALIGN.LEFT)
        style_cell(table.cell(row, 1), utility, fill=row_fill, color=INK, size=10.8)
        style_cell(table.cell(row, 2), round_value, fill=row_fill, color=INK, size=10.8)

    insights = [
        ("Boulware", "highest accepted buyer utility", GREEN),
        ("Conceder", "earliest settlements", ROSE),
        ("Baseline", "center of the range", BLUE),
        ("Meso", "slower exploration", AMBER),
    ]
    y = 4.72
    for label, body, color in insights:
        add_round_rect(slide, 8.20, y, 4.32, 0.40, WHITE, SAND, 1)
        add_round_rect(slide, 8.38, y + 0.10, 0.16, 0.16, color, color, 1)
        add_textbox(slide, 8.64, y + 0.08, 1.00, 0.10, label, size=11.0, color=color, bold=True)
        add_textbox(slide, 9.64, y + 0.08, 2.60, 0.10, body, size=10.4, color=INK)
        y += 0.50

    add_round_rect(slide, 8.20, 6.44, 4.32, 0.34, NAVY, NAVY, 1)
    add_textbox(slide, 8.36, 6.53, 4.00, 0.10, "Source: StrategySimulationMatrixTest", size=9.6, color=WHITE, align=PP_ALIGN.CENTER)


def build_slide_5(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    set_background(slide, PAPER)
    add_backdrop(slide, AMBER_SOFT, NAVY_SOFT)
    title_block(slide, "SCENARIO HARNESS", "Scenario Matrix", "Only the buyer strategy changes across the grid.")

    table = slide.shapes.add_table(7, 6, Inches(0.76), Inches(2.14), Inches(11.90), Inches(3.92)).table
    table.columns[0].width = Inches(2.34)
    for col in range(1, 6):
        table.columns[col].width = Inches(1.91)
    table.rows[0].height = Inches(0.52)
    for row in range(1, 7):
        table.rows[row].height = Inches(0.54)

    headers = ["Scenario", "Baseline", "Meso", "Boulware", "Conceder", "Tit for Tat"]
    for col, header in enumerate(headers):
        align = PP_ALIGN.LEFT if col == 0 else PP_ALIGN.CENTER
        style_cell(table.cell(0, col), header, fill=NAVY, color=WHITE, bold=True, size=11.0, align=align)

    for row, (scenario, outcomes) in enumerate(SCENARIO_MATRIX, start=1):
        style_cell(table.cell(row, 0), scenario, fill=WHITE if row % 2 else MIST, color=INK, bold=True, size=10.8, align=PP_ALIGN.LEFT)
        for col, outcome in enumerate(outcomes, start=1):
            accepted = outcome.startswith("A")
            fill = GREEN_SOFT if accepted else ROSE_SOFT
            color = GREEN if accepted else ROSE
            style_cell(table.cell(row, col), outcome, fill=fill, color=color, bold=True, size=13.0)

    add_pill(slide, 0.88, 6.36, 1.24, 0.28, "A = accepted", GREEN_SOFT, GREEN, GREEN, 9.6)
    add_pill(slide, 2.26, 6.36, 1.18, 0.28, "R = rejected", ROSE_SOFT, ROSE, ROSE, 9.6)

    legend_cards = [
        ("Constraint cases", "price floor, payment cap, delivery floor", BLUE_SOFT, BLUE),
        ("Settlement pressure", "near settle and deadline scenarios", TEAL_SOFT, TEAL),
        ("Reservation discipline", "hardliner failure cases remain visible", AMBER_SOFT, AMBER),
    ]
    x = 4.10
    for title, body, fill, accent in legend_cards:
        add_round_rect(slide, x, 6.24, 2.72, 0.52, fill, accent, 1.2)
        add_textbox(slide, x + 0.12, 6.34, 1.24, 0.10, title, size=10.2, color=accent, bold=True)
        add_textbox(slide, x + 1.24, 6.34, 1.32, 0.10, body, size=9.5, color=INK)
        x += 2.84


def build_slide_6(prs):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    set_background(slide, PAPER)
    add_backdrop(slide, BLUE_SOFT, ROSE_SOFT)
    title_block(slide, "FIXED SUPPLIER FLOW", "Price Sensitivity", "PriceSensitivityTest holds the supplier script constant so buyer posture is easy to compare.")

    add_round_rect(slide, 0.74, 2.12, 12.00, 1.02, WHITE, SAND, 1.2)
    add_textbox(slide, 1.02, 2.34, 2.96, 0.18, "Scripted supplier concessions", size=18, color=NAVY, bold=True)
    x_positions = [1.02, 2.92, 4.82, 6.72, 8.62, 10.52]
    colors = [(BLUE_SOFT, BLUE), (TEAL_SOFT, TEAL), (BLUE_SOFT, BLUE), (AMBER_SOFT, AMBER), (GREEN_SOFT, GREEN), (ROSE_SOFT, ROSE)]
    for (turn, label, terms), x, (fill, accent) in zip(PRICE_SCRIPT, x_positions, colors):
        add_round_rect(slide, x, 2.64, 1.56, 0.34, fill, accent, 1.2)
        add_textbox(slide, x + 0.08, 2.72, 0.22, 0.10, turn, size=10.2, color=accent, bold=True)
        add_textbox(slide, x + 0.34, 2.70, 0.86, 0.10, label, size=10.0, color=INK, bold=True)
        add_textbox(slide, x + 0.12, 2.84, 1.24, 0.08, terms, size=8.8, color=MUTED, font_name=FONT_MONO)
    for left_x, right_x in zip(x_positions, x_positions[1:]):
        add_line(slide, left_x + 1.56, 2.82, right_x - 0.04, 2.82, STONE, 1.6)

    add_round_rect(slide, 0.74, 3.66, 7.04, 2.84, WHITE, SAND, 1.2)
    add_textbox(slide, 1.02, 3.92, 2.20, 0.18, "Observed closing price", size=18, color=NAVY, bold=True)
    axis_left = 2.46
    axis_right = 6.86
    axis_y = 6.10
    add_line(slide, axis_left, axis_y, axis_right, axis_y, STONE, 1.6)
    for value in [100, 110, 120]:
        x = axis_left + (value - 100) / 20.0 * (axis_right - axis_left)
        add_line(slide, x, 4.26, x, axis_y, GRID, 1)
        add_textbox(slide, x - 0.14, axis_y + 0.04, 0.28, 0.10, str(value), size=9.6, color=MUTED, align=PP_ALIGN.CENTER)

    y = 4.34
    for label, value, round_hit, note, color in PRICE_RESULTS:
        add_textbox(slide, 1.00, y + 0.02, 1.10, 0.12, label, size=11.0, color=color, bold=True)
        if value is None:
            add_round_rect(slide, axis_left, y, 1.18, 0.24, PAPER, color, 1.4)
            add_textbox(slide, axis_left + 0.20, y + 0.05, 0.78, 0.10, "No deal", size=10.2, color=color, bold=True, align=PP_ALIGN.CENTER)
        else:
            width = max(0.26, (value - 100.0) / 20.0 * (axis_right - axis_left))
            add_round_rect(slide, axis_left, y, width, 0.24, color, color, 1)
            add_textbox(slide, axis_left + width + 0.06, y + 0.03, 0.48, 0.10, f"{value:.2f}", size=10.0, color=INK)
        add_textbox(slide, 6.22, y + 0.03, 0.42, 0.10, round_hit, size=9.8, color=MUTED, align=PP_ALIGN.CENTER)
        add_textbox(slide, 6.62, y + 0.03, 0.82, 0.10, note, size=9.8, color=MUTED)
        y += 0.38

    add_textbox(
        slide,
        1.02,
        6.28,
        6.20,
        0.12,
        "Price stayed flat at 120 while delivery and contract improved, so the strategy posture becomes easy to see.",
        size=10.6,
        color=MUTED,
    )

    add_round_rect(slide, 8.06, 3.66, 4.68, 2.84, NAVY, NAVY, 1)
    add_textbox(slide, 8.36, 3.92, 2.42, 0.18, "Readout", size=18, color=WHITE, bold=True)
    notes = [
        ("Boulware", "held price hardest and still closed", GREEN),
        ("Baseline", "gave some price to keep momentum", BLUE),
        ("Tit for Tat", "stayed close to baseline", TEAL),
        ("Conceder", "closed at the ceiling", ROSE),
    ]
    y = 4.30
    for label, body, color in notes:
        add_round_rect(slide, 8.32, y, 4.14, 0.38, WHITE, WHITE, 1)
        add_round_rect(slide, 8.48, y + 0.10, 0.16, 0.16, color, color, 1)
        add_textbox(slide, 8.74, y + 0.08, 0.96, 0.10, label, size=10.8, color=color, bold=True)
        add_textbox(slide, 9.66, y + 0.08, 2.58, 0.10, body, size=10.0, color=INK)
        y += 0.44

    add_round_rect(slide, 8.32, 6.00, 4.14, 0.28, rgb("233244"), rgb("233244"), 1)
    add_textbox(slide, 8.50, 6.07, 3.78, 0.10, "Meso: no deal in this branch", size=9.8, color=WHITE, align=PP_ALIGN.CENTER)
    add_textbox(slide, 8.22, 6.34, 4.32, 0.10, "Sources: PriceSensitivityTest and StrategySimulationMatrixTest", size=9.0, color=rgb("DCE5F3"), align=PP_ALIGN.CENTER)


def build_presentation():
    prs = Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)

    build_slide_1(prs)
    build_slide_2(prs)
    build_slide_3(prs)
    build_slide_4(prs)
    build_slide_5(prs)
    build_slide_6(prs)

    OUT_PPTX.parent.mkdir(parents=True, exist_ok=True)
    prs.save(OUT_PPTX)


if __name__ == "__main__":
    build_presentation()
