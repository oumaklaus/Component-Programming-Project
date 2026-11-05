package ke.ku.sco310.qr.test;

import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrCodeRenderer;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.api.RenderedArtifact;
import ke.ku.sco310.qr.components.PureJavaQrEncoderBean;
import ke.ku.sco310.qr.editor.HexColorPropertyEditor;
import ke.ku.sco310.qr.render.AsciiRendererBean;
import ke.ku.sco310.qr.render.PngRendererBean;
import ke.ku.sco310.qr.render.SvgRendererBean;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Verification of the three renderer components.
 *
 * <p>Each renderer is checked by <em>reading its own output back</em>: the PNG is decoded through
 * the ZXing image pipeline, the SVG is parsed rectangle by rectangle, and the ASCII art is parsed
 * glyph by glyph. A renderer that only "looks right" is not accepted; the reconstructed module
 * matrix must equal the encoder's output exactly.</p>
 */
final class RendererTest {

    private static final String PAYLOAD = "Kenyatta University";

    private RendererTest() {
    }

    static void run(TestHarness harness, List<String> rendererCsv) throws Exception {
        QrCode symbol = new PureJavaQrEncoderBean().encode(PAYLOAD, QrErrorCorrection.QUARTILE);

        // ---------------------------------------------------------------- PNG
        PngRendererBean png = new PngRendererBean();
        png.setModuleSize(6);
        png.setQuietZone(4);
        png.setForegroundColor(new Color(0x10, 0x18, 0x20));
        png.setBackgroundColor(Color.WHITE);
        RenderedArtifact pngArtifact = png.render(symbol);

        int expectedPixels = (symbol.getSize() + 8) * 6;
        harness.checkEquals("renderer.png", "artifact size follows moduleSize and quietZone",
                expectedPixels, pngArtifact.getWidth());
        harness.checkEquals("renderer.png", "PNG mime type is published",
                "image/png", pngArtifact.getMimeType());

        String decodedFromPng = ZxingVerify.decodePng(pngArtifact.getContent());
        harness.checkEquals("renderer.png", "PNG artifact decodes back to the payload",
                PAYLOAD, decodedFromPng);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngArtifact.getContent()));
        harness.checkEquals("renderer.png", "quiet zone is painted with the background colour",
                png.getBackgroundColor(), pixelAt(image, 0, 0));
        int mismatchedModules = countMismatchedModules(image, symbol, 6, 4);
        harness.check("renderer.png", "every module is painted at the right position and colour",
                mismatchedModules == 0, mismatchedModules + " of "
                        + (symbol.getSize() * symbol.getSize()) + " modules mismatched");

        int beforeHeight = png.getPixelSize(symbol);
        png.setModuleSize(11);
        harness.check("renderer.png", "bound moduleSize property resizes the artifact",
                png.getPixelSize(symbol) > beforeHeight,
                "before=" + beforeHeight + "px after=" + png.getPixelSize(symbol) + "px");
        rendererCsv.add("PNG,moduleSize=" + png.getModuleSize() + ",quietZone=" + png.getQuietZone()
                + ",pixels=" + png.getPixelSize(symbol) + ",bytes=" + pngArtifact.getSizeInBytes()
                + ",decoded=" + PAYLOAD.equals(decodedFromPng));

        // ---------------------------------------------------------------- SVG
        SvgRendererBean svg = new SvgRendererBean();
        svg.setModuleSize(10);
        svg.setQuietZone(4);
        svg.setForegroundColor("#1B5E20");
        svg.setPhysicalWidthMillimetres(60);
        RenderedArtifact svgArtifact = svg.render(symbol);
        String svgText = svgArtifact.getContentAsText();
        harness.check("renderer.svg", "document declares absolute millimetre dimensions",
                svgText.contains("width=\"60.00mm\""), "first 60 chars: "
                        + svgText.substring(0, Math.min(60, svgText.length())).replace('\n', ' '));
        harness.check("renderer.svg", "declares the vector namespace",
                svgText.contains("http://www.w3.org/2000/svg"), "svg root present");
        boolean merged = svgArtifact.getRendererId().contains("rects");
        harness.check("renderer.svg", "horizontal run merging reduces the element count",
                svg.countRectangles(symbol) < countSingleModuleRectangles(symbol),
                "merged=" + svg.countRectangles(symbol)
                        + " single=" + countSingleModuleRectangles(symbol));
        int svgMismatches = compareSvgWithSymbol(svgText, symbol, 10, 4);
        harness.check("renderer.svg", "parsing the SVG back reproduces the module matrix",
                svgMismatches == 0, svgMismatches + " module mismatches");
        rendererCsv.add("SVG,moduleSize=" + svg.getModuleSize() + ",quietZone=" + svg.getQuietZone()
                + ",rects=" + svg.countRectangles(symbol) + ",bytes=" + svgArtifact.getSizeInBytes()
                + ",decoded=" + (svgMismatches == 0));

        SvgRendererBean svgUnmerged = new SvgRendererBean();
        svgUnmerged.setModuleSize(10);
        svgUnmerged.setMergeHorizontalRuns(false);
        int unmergedRectangles = svgUnmerged.countRectangles(symbol);
        harness.check("renderer.svg", "switching the optimisation off restores one rect per module",
                unmergedRectangles == symbol.countDarkModules(),
                "rects=" + unmergedRectangles + " darkModules=" + symbol.countDarkModules());

        // ---------------------------------------------------------------- ASCII
        AsciiRendererBean ascii = new AsciiRendererBean();
        ascii.setBorder(2);
        RenderedArtifact asciiArtifact = ascii.render(symbol);
        int asciiMismatches = compareAsciiWithSymbol(asciiArtifact.getContentAsText(), symbol, 2,
                ascii.getDarkGlyph(), ascii.getLightGlyph());
        harness.check("renderer.ascii", "parsing the text art reproduces the module matrix",
                asciiMismatches == 0, asciiMismatches + " module mismatches");
        harness.checkEquals("renderer.ascii", "artifact is flagged as textual",
                true, asciiArtifact.isTextual());
        rendererCsv.add("ASCII,glyph=" + ascii.getDarkGlyph().length() + " chars,border="
                + ascii.getBorder() + ",bytes=" + asciiArtifact.getSizeInBytes()
                + ",decoded=" + (asciiMismatches == 0));

        // ---------------------------------------------------------------- interchangeability
        List<RenderedArtifact> artifacts = new ArrayList<>();
        for (QrCodeRenderer renderer : new QrCodeRenderer[]{new PngRendererBean(), new SvgRendererBean(),
                new AsciiRendererBean()}) {
            artifacts.add(renderer.render(symbol));
        }
        harness.check("renderer", "three renderer components fill the same role without changes",
                artifacts.size() == 3
                        && !artifacts.get(0).getFormatName().equals(artifacts.get(1).getFormatName())
                        && !artifacts.get(1).getFormatName().equals(artifacts.get(2).getFormatName()),
                artifacts.get(0).getFormatName() + ", " + artifacts.get(1).getFormatName()
                        + ", " + artifacts.get(2).getFormatName());
        harness.check("renderer", "artifacts carry the identifier of the component that produced them",
                artifacts.get(0).getRendererId().startsWith("renderer.png")
                        && artifacts.get(1).getRendererId().startsWith("renderer.svg")
                        && artifacts.get(2).getRendererId().startsWith("renderer.ascii"),
                artifacts.get(0).getRendererId() + " / " + artifacts.get(1).getRendererId()
                        + " / " + artifacts.get(2).getRendererId());
        harness.checkEquals("renderer", "custom property editor parses #RRGGBB", "#1B5E20",
                HexColorPropertyEditor.toHex(HexColorPropertyEditor.fromHex("#1b5e20")));
        harness.expectFailure("renderer", "invalid colour text is rejected",
                () -> HexColorPropertyEditor.fromHex("#12GG45"));
        harness.expectFailure("renderer", "invalid module size is rejected",
                () -> new PngRendererBean().setModuleSize(0));

        // ---------------------------------------------------------------- negative control
        byte[] corrupted = artifacts.get(0).getContent().clone();
        for (int i = corrupted.length / 2; i < corrupted.length / 2 + 40; i++) {
            corrupted[i] = (byte) (corrupted[i] ^ 0xFF);
        }
        boolean decodedCorrupted = true;
        try {
            ZxingVerify.decodePng(corrupted);
        } catch (Exception e) {
            decodedCorrupted = false;
        }
        harness.check("renderer", "negative control: a corrupted PNG no longer decodes",
                !decodedCorrupted, "decoder rejected the damaged artifact as expected");
    }

    private static Color pixelAt(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(x, y));
    }

    private static int countMismatchedModules(BufferedImage image, QrCode symbol, int moduleSize,
                                              int quietZone) {
        int mismatches = 0;
        for (int y = 0; y < symbol.getSize(); y++) {
            for (int x = 0; x < symbol.getSize(); x++) {
                int px = (quietZone + x) * moduleSize + moduleSize / 2;
                int py = (quietZone + y) * moduleSize + moduleSize / 2;
                boolean paintedDark = pixelAt(image, px, py).equals(new Color(0x10, 0x18, 0x20));
                if (paintedDark != symbol.isDark(x, y)) {
                    mismatches++;
                }
            }
        }
        return mismatches;
    }

    private static int countSingleModuleRectangles(QrCode symbol) {
        int count = 0;
        for (int y = 0; y < symbol.getSize(); y++) {
            for (int x = 0; x < symbol.getSize(); x++) {
                if (symbol.isDark(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int compareSvgWithSymbol(String svgText, QrCode symbol, int moduleSize, int quietZone) {
        boolean[][] painted = new boolean[symbol.getSize()][symbol.getSize()];
        Pattern rect = Pattern.compile("<rect x=\"(\\d+)\" y=\"(\\d+)\" width=\"(\\d+)\" height=\"(\\d+)\"/>");
        Matcher matcher = rect.matcher(svgText);
        while (matcher.find()) {
            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            int width = Integer.parseInt(matcher.group(3));
            int height = Integer.parseInt(matcher.group(4));
            int firstColumn = x / moduleSize - quietZone;
            int lastColumn = (x + width) / moduleSize - quietZone - 1;
            int row = y / moduleSize - quietZone;
            for (int column = firstColumn; column <= lastColumn; column++) {
                if (row >= 0 && row < symbol.getSize() && column >= 0 && column < symbol.getSize()) {
                    painted[row][column] = true;
                }
            }
            if (height != moduleSize) {
                return -1;
            }
        }
        int mismatches = 0;
        for (int y = 0; y < symbol.getSize(); y++) {
            for (int x = 0; x < symbol.getSize(); x++) {
                if (painted[y][x] != symbol.isDark(x, y)) {
                    mismatches++;
                }
            }
        }
        return mismatches;
    }

    private static int compareAsciiWithSymbol(String text, QrCode symbol, int border,
                                              String darkGlyph, String lightGlyph) {
        String[] lines = text.split("\n", -1);
        boolean[][] painted = new boolean[symbol.getSize()][symbol.getSize()];
        int mismatches = 0;
        for (int row = 0; row < symbol.getSize(); row++) {
            String line = lines[row + border];
            int position = border * lightGlyph.length();
            for (int column = 0; column < symbol.getSize(); column++) {
                boolean dark;
                if (line.startsWith(darkGlyph, position)) {
                    dark = true;
                } else if (line.startsWith(lightGlyph, position)) {
                    dark = false;
                } else {
                    return -1; // unexpected glyph: the artifact is not parseable
                }
                painted[row][column] = dark;
                position += dark ? darkGlyph.length() : lightGlyph.length();
                if (dark != symbol.isDark(column, row)) {
                    mismatches++;
                }
            }
        }
        return mismatches;
    }
}
