#!/usr/bin/env python3
"""Draws the architecture diagrams of the SCO310 QR component suite.

The diagrams are generated from code (Pillow) instead of being drawn by hand so that the
documentation can be regenerated whenever the component wiring changes:

    python3 tools/make_diagrams.py            # writes docs/diagrams/*.png
"""
from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFont

SANS = "/usr/share/fonts/liberation/LiberationSans-Regular.ttf"
SANS_BOLD = "/usr/share/fonts/liberation/LiberationSans-Bold.ttf"
MONO = "/usr/share/fonts/liberation/LiberationMono-Regular.ttf"

INK = (24, 28, 35)
MUTED = (95, 104, 117)
LINE = (150, 158, 170)
BG = (255, 255, 255)
ACCENT = (13, 71, 161)
ACCENT_2 = (27, 94, 32)
ACCENT_3 = (183, 28, 28)
ACCENT_4 = (106, 27, 154)
FILL = (244, 246, 249)


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def box(draw, xy, title, lines=(), fill=FILL, outline=INK, accent=ACCENT, radius=10):
    x0, y0, x1, y1 = xy
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=2)
    draw.rectangle((x0, y0, x1, y0 + 30), fill=accent)
    draw.rounded_rectangle((x0, y0, x1, y0 + 30), radius=radius, fill=accent)
    draw.rectangle((x0, y0 + 18, x1, y0 + 30), fill=accent)
    draw.text((x0 + 10, y0 + 7), title, font=font(SANS_BOLD, 14), fill=(255, 255, 255))
    y = y0 + 40
    for line in lines:
        draw.text((x0 + 12, y), line, font=font(MONO, 11), fill=INK)
        y += 16
    return (x0, y0, x1, y1)


def arrow(draw, start, end, label=None, colour=LINE, width=2, dashed=False, both=False):
    if dashed:
        # manual dashed line
        import math
        x0, y0 = start
        x1, y1 = end
        length = math.hypot(x1 - x0, y1 - y0)
        segments = max(1, int(length // 10))
        for i in range(segments):
            t0 = i / segments
            t1 = (i + 0.55) / segments
            draw.line((x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0,
                       x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1), fill=colour, width=width)
    else:
        draw.line((start, end), fill=colour, width=width)
    head(draw, start, end, colour)
    if both:
        head(draw, end, start, colour)
    if label:
        mx, my = (start[0] + end[0]) / 2, (start[1] + end[1]) / 2
        tw = draw.textlength(label, font=font(MONO, 10))
        draw.rectangle((mx - tw / 2 - 4, my - 9, mx + tw / 2 + 4, my + 9), fill=BG)
        draw.text((mx - tw / 2, my - 7), label, font=font(MONO, 10), fill=MUTED)


def head(draw, start, end, colour):
    import math
    angle = math.atan2(end[1] - start[1], end[0] - start[0])
    size = 9
    for delta in (2.6, -2.6):
        draw.line((end[0], end[1],
                   end[0] + size * math.cos(angle + delta),
                   end[1] + size * math.sin(angle + delta)), fill=colour, width=2)


def caption(draw, width, text, y):
    draw.text((30, y), text, font=font(SANS, 12), fill=MUTED)
    return y + 20


def save(image: Image.Image, name: str):
    out = os.path.join("docs", "diagrams")
    os.makedirs(out, exist_ok=True)
    path = os.path.join(out, name)
    image.save(path)
    print("diagram written:", path, image.size)


# --------------------------------------------------------------------------- 1. component model
def diagram_component_model():
    width, height = 1500, 980
    image = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(image)
    draw.text((30, 22), "Figure 1 - Component model of the QR suite: provided (I) and required (D) interfaces",
              font=font(SANS_BOLD, 19), fill=INK)
    draw.text((30, 50), "Every box is a replaceable JavaBean. Solid arrows are connections that exist at run time; "
                        "dashed arrows are alternative implementations of the same role.",
              font=font(SANS, 12), fill=MUTED)

    box(draw, (60, 120, 520, 250), "QrCodeEncoder  (provided interface)",
        ["QrCode encode(String, QrErrorCorrection)",
         "EncoderInfo getEncoderInfo()",
         "String getComponentId()"], accent=ACCENT)
    box(draw, (60, 280, 520, 400), "PureJavaQrEncoderBean",
        ["componentId encoder.pure-java.v1", "in-house ISO/IEC 18004 engine",
         "numeric | alphanumeric | byte"], accent=ACCENT_2)
    box(draw, (60, 430, 520, 550), "ZxingQrEncoderAdapterBean",
        ["componentId encoder.zxing-adapter.v1", "adapter over com.google.zxing core 3.5.3",
         "optional: absent library is detected"], accent=ACCENT_2)

    box(draw, (60, 620, 520, 760), "QrCodeRenderer  (provided interface)",
        ["RenderedArtifact render(QrCode)", "String getRendererId()",
         "String getFormatName() / getMimeType()",
         "String describeConfiguration()"], accent=ACCENT)
    box(draw, (60, 800, 520, 900), "PngRendererBean | SvgRendererBean",
        ["AsciiRendererBean  (three interchangeable",
         "components fill the same role)"], accent=ACCENT_2)

    box(draw, (600, 120, 1040, 300), "QrStudioFacadeBean  (composite component)",
        ["provided: QrGenerationResult generate(String, EC, Renderer)",
         "provided: void addQrCodeGenerationListener(l)",
         "required: QrCodeEncoder, QrCodeRenderer,",
         "          QrCodeRepository, QrHistoryBean",
         "properties: defaultErrorCorrection, autoSave..."], accent=ACCENT_4)
    box(draw, (600, 360, 1040, 520), "FileSystemRepositoryBean",
        ["required interface of the facade:",
         "QrCodeRepository",
         "storeArtifact / appendHistory / loadHistory",
         "serialises records into history.ser"], accent=ACCENT_2)
    box(draw, (600, 580, 1040, 700), "QrHistoryBean",
        ["indexed property entries[]   (bound)",
         "maxEntries                   (constrained)"], accent=ACCENT_2)
    box(draw, (600, 760, 1040, 900), "QrCodeGeneratedEvent + QrCodeGenerationListener",
        ["event object, event source = facade,",
         "listeners = AuditLogListenerBean,",
         "              Swing status bar,",
         "              Swing history view"], accent=ACCENT_3)

    box(draw, (1120, 360, 1450, 520), "AuditLogListenerBean",
        ["implements the listener interface",
         "logFile / echoToConsole / verbose",
         "observes every generation"], accent=ACCENT_3)
    box(draw, (1120, 580, 1450, 700), "Desktop application",
        ["QrStudioGuiAssembly",
         "QrStudioFrame"], accent=ACCENT_4)
    box(draw, (1120, 120, 1450, 300), "ComponentRegistry + AssemblyProfile",
        ["catalogue: I, D, availability",
         "selects one implementation per role",
         "applies design-time properties"], accent=ACCENT_4)

    arrow(draw, (290, 250), (290, 278), "implements")
    arrow(draw, (290, 400), (290, 428), "implements")
    arrow(draw, (290, 550), (290, 618), "produces", dashed=True)
    arrow(draw, (290, 790), (290, 818), "implements")
    arrow(draw, (520, 170), (600, 160), "provides encoder", colour=ACCENT)
    arrow(draw, (520, 700), (600, 620), "provides renderer", colour=ACCENT)
    arrow(draw, (820, 300), (820, 358), "uses")
    arrow(draw, (820, 520), (820, 578), "uses")
    arrow(draw, (1040, 275), (1120, 400), "notifies", colour=ACCENT_3)
    arrow(draw, (1225, 520), (1225, 578), "wired by")
    arrow(draw, (1120, 210), (1040, 210), "catalogue / build", colour=ACCENT_4)

    caption(draw, width, "Szyperski's definition is applied literally: a component is (I) what it offers, "
                         "(D) what it needs, (B) a hidden implementation.", 955)
    save(image, "figure-1-component-model.png")


# --------------------------------------------------------------------------- 2. connection model
def diagram_connection_model():
    width, height = 1400, 700
    image = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(image)
    draw.text((30, 22), "Figure 2 - JavaBeans connection model: delegation of events and bound properties",
              font=font(SANS_BOLD, 19), fill=INK)

    box(draw, (60, 120, 420, 300), "Event source: QrStudioFacadeBean",
        ["1  encode via QrCodeEncoder", "2  render via QrCodeRenderer",
         "3  store via QrCodeRepository", "4  fire QrCodeGeneratedEvent"], accent=ACCENT)
    box(draw, (540, 120, 900, 300), "Event object: QrCodeGeneratedEvent",
        ["QrCode getSymbol()", "RenderedArtifact getArtifact()",
         "File getStoredFile()", "long getDurationMillis()"], accent=ACCENT_4)
    box(draw, (1020, 90, 1360, 200), "Listener 1: AuditLogListenerBean",
        ["writes the audit trail"], accent=ACCENT_3)
    box(draw, (1020, 230, 1360, 340), "Listener 2: QrStudioFrame (status bar)",
        ["updates the user interface"], accent=ACCENT_3)
    box(draw, (1020, 370, 1360, 480), "Listener 3: desktop status label",
        ["counts generations"], accent=ACCENT_3)

    box(draw, (60, 380, 420, 560), "Bound property source",
        ["PngRendererBean.moduleSize", "SvgRendererBean.mergeHorizontalRuns",
         "QrHistoryBean.entries (indexed)"], accent=ACCENT_2)
    box(draw, (540, 380, 900, 560), "PropertyChangeEvent",
        ["old value + new value"], accent=ACCENT_4)
    box(draw, (1020, 540, 1360, 650), "QrStudioFrame.propertyChange()",
        ["re-renders the live preview"], accent=ACCENT_2)

    arrow(draw, (420, 200), (540, 200), "creates")
    arrow(draw, (900, 160), (1020, 140), "addQrCodeGenerationListener")
    arrow(draw, (900, 210), (1020, 280), "delegation")
    arrow(draw, (900, 260), (1020, 420), "for every listener")
    arrow(draw, (420, 450), (540, 450), "fires")
    arrow(draw, (900, 520), (1020, 580), "reacts")
    arrow(draw, (540, 560), (1020, 600), "no polling needed", dashed=True, colour=MUTED)

    caption(draw, width, "The facade never references a concrete listener: it stores the interface, "
                         "which is what makes listeners (and renderers) replaceable.", 620)
    save(image, "figure-2-connection-model.png")


# --------------------------------------------------------------------------- 3. deployment model
def diagram_deployment_model():
    width, height = 1500, 860
    image = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(image)
    draw.text((30, 22), "Figure 3 - Deployment model: one compiled component set, two deployed assemblies",
              font=font(SANS_BOLD, 19), fill=INK)

    box(draw, (60, 100, 700, 330), "Deployable unit: qr-component-suite-1.0.0.jar",
        ["META-INF/MANIFEST.MF",
         "  Java-Bean: True   (per bean class file)",
         "  Main-Class, Class-Path: lib/core-3.5.3.jar",
         "ke/ku/sco310/qr/**/*.class  (all component classes)",
         "config/assembly.xml  (deployment descriptor: P)"], accent=ACCENT)

    box(draw, (60, 400, 700, 620), "Execution environment: Java SE 11+ (javac 21 --release 11)",
        ["no build tool required at deployment time",
         "optional: com.google.zxing core + javase",
         "JavaBeans introspection and persistence",
         "Swing desktop interface"], accent=ACCENT_2)

    box(draw, (60, 690, 700, 820), "Location: workspace / laboratory machine",
        ["out/classes, out/dist, out/generated",
         "docs/evidence  (verification artifacts)"], accent=ACCENT_3)

    box(draw, (800, 100, 1440, 240), "Assembly 1 - Desktop (QrStudioGuiAssembly)",
        ["profile: renderer.png.v1, moduleSize=10, GUI wiring",
         "renders the live preview and the history list"], accent=ACCENT_4)
    box(draw, (800, 480, 1440, 620), "Assembly 2 - Alternate renderer profile",
        ["renderer.svg.v1 or renderer.ascii.v1",
         "same encoder, repository and history"], accent=ACCENT_4)
    box(draw, (800, 670, 1440, 800), "Optional assembly 3 - any tool that reads the XML bean graph",
        ["java.beans.XMLDecoder rebuilds the wiring: no recompilation"], accent=ACCENT_2)

    arrow(draw, (700, 200), (800, 180), "deploy C")
    arrow(draw, (700, 240), (800, 550), "deploy C")
    arrow(draw, (700, 280), (800, 730), "deploy C")
    arrow(draw, (380, 330), (380, 400), "E")
    arrow(draw, (380, 620), (380, 690), "L")
    arrow(draw, (1120, 620), (1120, 670), "P changes only", dashed=True, colour=ACCENT_2)

    caption(draw, width, "D = (C, E, P, L): the component C is identical everywhere; only the deployment "
                         "parameters P and the environment E differ.", 835)
    save(image, "figure-3-deployment-model.png")


# --------------------------------------------------------------------------- 4. encoding pipeline
def diagram_pipeline():
    width, height = 1500, 1080
    image = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(image)
    draw.text((30, 22), "Figure 4 - Encoding pipeline of the in-house engine and the verification loop",
              font=font(SANS_BOLD, 19), fill=INK)

    steps = [
        ("1  analyse payload", ["numeric / alphanumeric / byte", "QrSegment.makeSegments()"],
         "2  choose version", ["smallest version whose data", "capacity fits the level"]),
        ("3  build bit stream", ["mode + count + data + terminator", "pad bits + 0xEC / 0x11"],
         "4  error correction", ["split into blocks, Reed-Solomon", "over GF(2^8), polynomial 0x11D"]),
        ("5  interleave", ["all data columns, then all", "error-correction columns"],
         "6  draw matrix", ["function patterns, zig-zag", "placement of the codewords"]),
        ("7  choose mask", ["8 masks scored with the", "four-part penalty function"],
         "8  format information", ["BCH(15,5) + XOR 0x5412,", "version bits BCH(18,6) for v>=7"]),
    ]
    top = 110
    for title_a, lines_a, title_b, lines_b in steps:
        box(draw, (60, top, 700, top + 150), title_a, lines_a, accent=ACCENT)
        box(draw, (760, top, 1400, top + 150), title_b, lines_b, accent=ACCENT_2)
        arrow(draw, (700, top + 75), (760, top + 75))
        arrow(draw, (1080, top + 150), (1080, top + 185))
        top += 185

    box(draw, (60, 960, 700, 1050), "Component boundary: PureJavaQrEncoderBean",
        ["the whole pipeline is hidden implementation (B)"], accent=ACCENT_4)
    box(draw, (760, 960, 1400, 1050), "Independent oracle: ZXing 3.5.3 decoder",
        ["decodes every produced symbol during verification"], accent=ACCENT_3)
    arrow(draw, (700, 1000), (760, 1000), "QrCode value object", dashed=True)
    save(image, "figure-4-encoding-pipeline.png")


# --------------------------------------------------------------------------- 5. evidence map
def diagram_verification():
    width, height = 1500, 820
    image = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(image)
    draw.text((30, 22), "Figure 5 - Verification strategy: how each claim is demonstrated",
              font=font(SANS_BOLD, 19), fill=INK)

    rows = [
        ("Implementation correctness", "decode every symbol with ZXing",
         "engine checks: 60 payload x level round trips,",
         "160 version/level capacity tests"),
        ("Parameter data correctness", "compare with the tables published by ZXing",
         "table-cross-check.csv: 160 combinations, 0 mismatches",
         ""),
        ("Renderer correctness", "read the artifacts back",
         "PNG pixels, SVG rectangles, ASCII glyphs reconstructed",
         "and compared module by module"),
        ("Bean contract conformance", "java.beans.Introspector + XMLEncoder",
         "public class, no-arg constructor, properties, events,",
         "XML persistence round trip"),
        ("Replaceability", "swap components at run time",
         "encoder swap (in-house vs third-party), renderer swap",
         "(PNG / SVG / ASCII) with unchanged callers"),
        ("Negative controls", "prove the checks can fail",
         "corrupted artifact is rejected, missing component fails,",
         "empty and over-capacity payloads are rejected"),
    ]
    y = 100
    for claim, method, line1, line2 in rows:
        box(draw, (60, y, 660, y + 105), claim, ["method: " + method], accent=ACCENT)
        box(draw, (720, y, 1440, y + 105), "evidence",
            [line for line in (line1, line2) if line], accent=ACCENT_2, fill=(248, 250, 248))
        arrow(draw, (660, y + 52), (720, y + 52))
        y += 120
    caption(draw, width, "All 108 automated checks pass; the raw transcript, CSV files and artifact digests "
                         "are stored in docs/evidence/.", 795)
    save(image, "figure-5-verification-strategy.png")


def main():
    diagram_component_model()
    diagram_connection_model()
    diagram_deployment_model()
    diagram_pipeline()
    diagram_verification()


if __name__ == "__main__":
    main()
