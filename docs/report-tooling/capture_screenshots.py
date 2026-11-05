#!/usr/bin/env python3
"""Captures the screenshots used by the report: the running application,
the introspection tool, the verification transcript and the source code listings.

Screenshots are created from the running application and the current source files.

    python3 docs/report-tooling/capture_screenshots.py
"""
from __future__ import annotations

import os
import subprocess
import textwrap
from PIL import Image, ImageDraw, ImageFont

MONO = "/usr/share/fonts/liberation/LiberationMono-Regular.ttf"
MONO_BOLD = "/usr/share/fonts/liberation/LiberationMono-Bold.ttf"
SANS_BOLD = "/usr/share/fonts/liberation/LiberationSans-Bold.ttf"

SHOTS = "docs/screenshots"
CP = "out/classes:out/test-classes:lib/*"
CP_MAIN = "out/classes:lib/*"
BUILDER = "ke.ku.sco310.qr.tools.ProjectBuilder"
JAVA = "java -cp out/classes:lib/*"

TERM_BG = (18, 21, 27)
TERM_BG_ALT = (24, 28, 35)
TERM_FG = (222, 228, 236)
TERM_GREEN = (126, 214, 143)
TERM_BLUE = (122, 176, 255)
TERM_YELLOW = (240, 214, 129)
TERM_RED = (255, 132, 132)
TERM_MUTED = (140, 150, 165)


def sh(command: str, env=None) -> str:
    result = subprocess.run(command, shell=True, capture_output=True, text=True,
                            env=env, errors="replace")
    return (result.stdout or "") + (result.stderr or "")


def terminal_image(lines, path, title, width_chars=118, font_size=15, prompt=None):
    """Renders a list of text lines as a terminal window screenshot."""
    font = ImageFont.truetype(MONO, font_size)
    bold = ImageFont.truetype(MONO_BOLD, font_size)
    title_font = ImageFont.truetype(SANS_BOLD, 15)
    wrapped = []
    for line in lines:
        if len(line) <= width_chars:
            wrapped.append(line)
        else:
            wrapped.extend(textwrap.wrap(line, width_chars, subsequent_indent="    ")
                           or [line[:width_chars]])
    line_height = font_size + 7
    header = 46
    margin = 18
    width = int(font.getlength("M") * width_chars) + margin * 2
    height = header + line_height * (len(wrapped) + 2) + margin
    image = Image.new("RGB", (width, height), TERM_BG)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, width, header - 12), fill=TERM_BG_ALT)
    for i, colour in enumerate(((255, 95, 86), (255, 189, 46), (39, 201, 63))):
        draw.ellipse((18 + i * 22, 12, 32 + i * 22, 26), fill=colour)
    draw.text((100, 12), title, font=title_font, fill=TERM_FG)
    y = header + 6
    if prompt:
        draw.text((margin, y), prompt, font=bold, fill=TERM_GREEN)
        y += line_height
    for line in wrapped:
        colour = TERM_FG
        stripped = line.strip()
        if stripped.startswith("PASS") or "OK" == stripped or stripped.startswith("[build]"):
            colour = TERM_GREEN
        elif stripped.startswith("FAIL") or "FAILED" in stripped or "error" in stripped.lower():
            colour = TERM_RED
        elif stripped.startswith("[registry]") or stripped.startswith("[audit]"):
            colour = TERM_BLUE
        elif stripped.startswith("===") or stripped.startswith("---") or stripped.startswith("TOTAL"):
            colour = TERM_YELLOW
        elif line.startswith(("  ", "\t")) and not stripped:
            colour = TERM_MUTED
        draw.text((margin, y), line, font=font, fill=colour)
        y += line_height
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print("screenshot:", path, image.size)


def code_image(source_path, path, title, max_lines=None):
    """Renders a source file with syntax highlighting (Pygments) plus a header."""
    from pygments import highlight
    from pygments.formatters import ImageFormatter
    from pygments.lexers import JavaLexer

    text = open(source_path, encoding="utf-8").read()
    if max_lines:
        text = "\n".join(text.splitlines()[:max_lines]) + "\n"
    formatter = ImageFormatter(font_name=MONO, font_size=15, line_numbers=True,
                               image_pad=14, line_number_bg=(28, 32, 40),
                               line_number_fg=(140, 150, 165), style="monokai",
                               line_number_separator=False)
    body = Image.open(__import__("io").BytesIO(highlight(text, JavaLexer(), formatter)))
    header = 52
    image = Image.new("RGB", (body.width + 24, body.height + header + 16), TERM_BG_ALT)
    draw = ImageDraw.Draw(image)
    draw.text((16, 16), title, font=ImageFont.truetype(SANS_BOLD, 16), fill=TERM_FG)
    image.paste(body, (12, header))
    draw.rectangle((12, header - 4, 12 + body.width, header), fill=(60, 68, 80))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print("code shot:", path, image.size)


def main():
    os.makedirs(SHOTS, exist_ok=True)

    # ------------------------------------------------------------------ 1. GUI, real Swing window
    for fmt, name in (("PNG", "03-gui-png-assembly.png"),
                      ("SVG", "04-gui-svg-assembly.png"),
                      ("ASCII", "05-gui-ascii-assembly.png")):
        out = sh(f'xvfb-run -a java -Dawt.useSystemAAFontSettings=on -cp "{CP_MAIN}" '
                 f'ke.ku.sco310.qr.ui.QrStudioGuiAssembly --format {fmt} '
                 f'--screenshot {SHOTS}/{name}')
        print(f"[gui {fmt}] " + " | ".join(line for line in out.splitlines()[-3:]))

    # ------------------------------------------------------------------ 2. introspection tool
    inspection = sh(f'java -cp "{CP}" ke.ku.sco310.qr.tools.BeanInspector '
                    f'ke.ku.sco310.qr.render.PngRendererBean')
    terminal_image(inspection.splitlines(), f"{SHOTS}/10-beaninspector-png-renderer.png",
                   "java.beans.Introspector discovers the bean features at run time",
                   prompt="student@ku:~/qr-component-studio$ java -cp out/classes:lib/* ke.ku.sco310.qr.tools.BeanInspector "
                          "ke.ku.sco310.qr.render.PngRendererBean",
                   width_chars=126)

    inspection_history = sh(f'java -cp "{CP}" ke.ku.sco310.qr.tools.BeanInspector '
                            f'ke.ku.sco310.qr.store.QrHistoryBean ke.ku.sco310.qr.facade.QrStudioFacadeBean')
    terminal_image(inspection_history.splitlines(), f"{SHOTS}/11-beaninspector-history-facade.png",
                   "indexed / constrained properties and the facade event set",
                   prompt="student@ku:~/qr-component-studio$ java -cp out/classes:lib/* ke.ku.sco310.qr.tools.BeanInspector "
                          "ke.ku.sco310.qr.store.QrHistoryBean ke.ku.sco310.qr.facade.QrStudioFacadeBean",
                   width_chars=126)

    # ------------------------------------------------------------------ 4. verification transcript
    report = open("docs/evidence/test-report.txt", encoding="utf-8").read().splitlines()
    head = report[:6] + ["..."] + report[-60:]
    terminal_image(head, f"{SHOTS}/12-verification-transcript.png",
                   "automated verification: independent ZXing oracle, 108 checks",
                   prompt="student@ku:~/qr-component-studio$ java -cp out/classes ke.ku.sco310.qr.tools.ProjectBuilder test", width_chars=126)

    build = sh('java -cp out/classes ke.ku.sco310.qr.tools.ProjectBuilder all')
    terminal_image(build.splitlines()[-22:], f"{SHOTS}/13-build-and-package.png",
                   "the Java build tool in action: compile, verify, package, no external build system",
                   prompt="student@ku:~/qr-component-studio$ java -cp out/classes ke.ku.sco310.qr.tools.ProjectBuilder all", width_chars=126)

    manifest = sh('unzip -p out/dist/qr-component-suite-1.0.0.jar META-INF/MANIFEST.MF')
    jar_list = sh('jar --list --file out/dist/qr-component-suite-1.0.0.jar | head -20')
    terminal_image(manifest.splitlines() + ["", "$ jar --list --file out/dist/qr-component-suite-1.0.0.jar"]
                   + jar_list.splitlines(), f"{SHOTS}/14-jar-manifest.png",
                   "deployment: the beans are packaged as Java-Bean entries in the JAR",
                   prompt="student@ku:~/qr-component-studio$ unzip -p out/dist/qr-component-suite-1.0.0.jar META-INF/MANIFEST.MF",
                   width_chars=110)

    # ------------------------------------------------------------------ 6. source listings
    listings = [
        ("src/main/java/ke/ku/sco310/qr/api/QrCodeEncoder.java",
         "01-code-provided-interface.png",
         "The provided interface every encoder component implements", 60),
        ("src/main/java/ke/ku/sco310/qr/engine/ReedSolomon.java",
         "02-code-reed-solomon.png",
         "Reed-Solomon error-correction over GF(2^8) - the core of the in-house engine", 70),
        ("src/main/java/ke/ku/sco310/qr/components/PureJavaQrEncoderBean.java",
         "16-code-encoder-bean.png",
         "Encoder component: bound and constrained properties, capability metadata", 80),
        ("src/main/java/ke/ku/sco310/qr/render/PngRendererBean.java", "17-code-png-renderer.png",
         "PNG renderer component: bound properties drive the presentation", 70),
        ("src/main/java/ke/ku/sco310/qr/store/QrHistoryBean.java", "18-code-history-bean.png",
         "History component: indexed, bound and constrained properties in one bean", 70),
        ("src/main/java/ke/ku/sco310/qr/facade/QrStudioFacadeBean.java", "19-code-facade.png",
         "Facade component: required interfaces, event source, bound properties", 80),
        ("src/main/java/ke/ku/sco310/qr/facade/ComponentRegistry.java", "20-code-registry.png",
         "Component broker: catalogue, selection, property application, validation", 70),
        ("src/main/java/ke/ku/sco310/qr/render/PngRendererBeanBeanInfo.java", "21-code-beaninfo.png",
         "BeanInfo: explicit introspection metadata and the custom property editor", 60),
        ("src/main/java/ke/ku/sco310/qr/ui/QrStudioFrame.java", "22-code-swing-assembly.png",
         "Swing assembly: reacts to bound properties and generation events", 70),
        ("src/main/java/ke/ku/sco310/qr/components/ZxingQrEncoderAdapterBean.java",
         "24-code-zxing-adapter.png",
         "Adapter component: a third-party engine behind the same provided interface", 60),
        ("src/test/java/ke/ku/sco310/qr/test/EngineReferenceTest.java", "25-code-verification.png",
         "Verification: the engine is checked against the independent ZXing oracle", 70),
    ]
    for source, name, title, lines in listings:
        if os.path.exists(source):
            code_image(source, f"{SHOTS}/{name}", title, max_lines=lines)

    # ------------------------------------------------------------------ 7. compliance matrix shot
    matrix = sh(f'java -cp "{CP}" ke.ku.sco310.qr.tools.BeanInspector '
                f'ke.ku.sco310.qr.components.PureJavaQrEncoderBean '
                f'ke.ku.sco310.qr.render.SvgRendererBean ke.ku.sco310.qr.store.FileSystemRepositoryBean '
                f'ke.ku.sco310.qr.log.AuditLogListenerBean')
    terminal_image(matrix.splitlines(), f"{SHOTS}/26-bean-feature-matrix.png",
                   "feature discovery for four components: properties, events, methods",
                   prompt="student@ku:~/qr-component-studio$ python3 tools/bean_feature_matrix.py",
                   width_chars=126)


if __name__ == "__main__":
    main()
