package ke.ku.sco310.qr.components;

import ke.ku.sco310.qr.api.BoundPropertySource;
import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrCodeDecoder;
import ke.ku.sco310.qr.api.QrDecodingException;
import ke.ku.sco310.qr.api.QrEncodingMode;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.api.RenderedArtifact;
import ke.ku.sco310.qr.engine.QrTables;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Pure-Java decoder for symbols and the PNG, SVG and ASCII artifacts produced by this suite.
 *
 * <p>The decoder is deliberately an independent component rather than a utility hidden inside a
 * renderer. It reads format information, reverses the standard placement and mask rules,
 * de-interleaves the codewords, verifies the Reed-Solomon remainder and parses numeric,
 * alphanumeric, byte, Kanji and ECI segments. It does not depend on ZXing or any other library.</p>
 */
public class PureJavaQrDecoderBean implements QrCodeDecoder, Serializable, BoundPropertySource {

    private static final long serialVersionUID = 1L;
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
    private static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");
    private static final Pattern SVG_VIEWBOX = Pattern.compile("viewBox=\\\"0 0 (\\d+) (\\d+)\\\"");
    private static final Pattern SVG_RECT = Pattern.compile("<rect\\b([^>]*)>");
    private static final Pattern SVG_ATTRIBUTE = Pattern.compile("(x|y|width|height)=\\\"([0-9]+)");

    /** Component instance identifier. */
    public static final String COMPONENT_ID = "decoder.pure-java.v1";

    private transient PropertyChangeSupport propertyChanges = new PropertyChangeSupport(this);
    private boolean verifyErrorCorrection = true;

    public PureJavaQrDecoderBean() {
    }

    @Override
    public String decode(QrCode symbol) throws QrDecodingException {
        if (symbol == null) {
            throw new QrDecodingException(COMPONENT_ID, "No QR symbol was supplied");
        }
        return decodeModules(symbol.getModules());
    }

    @Override
    public String decode(RenderedArtifact artifact) throws QrDecodingException {
        if (artifact == null || artifact.getContent().length == 0) {
            throw new QrDecodingException(COMPONENT_ID, "No rendered artifact was supplied");
        }
        try {
            if ("PNG".equalsIgnoreCase(artifact.getFormatName())) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(artifact.getContent()));
                if (image == null) {
                    throw new QrDecodingException(COMPONENT_ID, "The artifact is not a readable PNG image");
                }
                return decodeImage(image);
            }
            if ("SVG".equalsIgnoreCase(artifact.getFormatName())) {
                return decodeSvg(artifact.getContentAsText());
            }
            if ("ASCII".equalsIgnoreCase(artifact.getFormatName())
                    || "TEXT".equalsIgnoreCase(artifact.getFormatName())) {
                return decodeAscii(artifact.getContentAsText());
            }
            throw new QrDecodingException(COMPONENT_ID,
                    "Unsupported artifact format: " + artifact.getFormatName());
        } catch (QrDecodingException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new QrDecodingException(COMPONENT_ID, "Could not decode the rendered artifact", e);
        }
    }

    /** @return stable component identifier. */
    @Override
    public String getDecoderId() {
        return COMPONENT_ID;
    }

    /** @return human-readable decoder name. */
    @Override
    public String getFormatName() {
        return "Pure-Java QR decoder";
    }

    /** @return whether Reed-Solomon remainders are checked before parsing. */
    public boolean isVerifyErrorCorrection() {
        return verifyErrorCorrection;
    }

    /** Bound property used by diagnostic tools that want to inspect damaged matrices. */
    public void setVerifyErrorCorrection(boolean verifyErrorCorrection) {
        boolean old = this.verifyErrorCorrection;
        this.verifyErrorCorrection = verifyErrorCorrection;
        propertyChanges.firePropertyChange("verifyErrorCorrection", old, verifyErrorCorrection);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.removePropertyChangeListener(listener);
    }

    private String decodeModules(boolean[][] modules) throws QrDecodingException {
        if (modules == null || modules.length < 21 || modules.length > 177) {
            throw new QrDecodingException(COMPONENT_ID, "QR matrix size is outside versions 1..40");
        }
        int size = modules.length;
        for (boolean[] row : modules) {
            if (row == null || row.length != size) {
                throw new QrDecodingException(COMPONENT_ID, "QR matrix is not square");
            }
        }
        int version = (size - 17) / 4;
        if (version < 1 || version > 40 || version * 4 + 17 != size) {
            throw new QrDecodingException(COMPONENT_ID, "Matrix side " + size + " is not a QR version");
        }
        Format format = readFormat(modules, version);
        boolean[][] function = functionModules(version);
        byte[] codewords = readCodewords(modules, function, format.mask, QrTables.totalCodewords(version));
        byte[][] dataBlocks = splitData(codewords, version, format.level);
        BitReader data = new BitReader(join(dataBlocks));
        return parseSegments(data, version);
    }

    private String decodeImage(BufferedImage image) throws QrDecodingException {
        int background = image.getRGB(0, 0);
        boolean[][] pixels = new boolean[image.getHeight()][image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                pixels[y][x] = image.getRGB(x, y) != background;
            }
        }
        for (int moduleSize = 1; moduleSize <= 64; moduleSize++) {
            if (image.getWidth() % moduleSize != 0 || image.getHeight() % moduleSize != 0) {
                continue;
            }
            int grid = image.getWidth() / moduleSize;
            for (int quiet = 0; quiet <= 32 && grid - 2 * quiet >= 21; quiet++) {
                int side = grid - 2 * quiet;
                if ((side - 17) % 4 != 0) {
                    continue;
                }
                boolean[][] modules = sample(pixels, side, moduleSize, quiet);
                if (looksLikeQr(modules)) {
                    return decodeModules(modules);
                }
            }
        }
        throw new QrDecodingException(COMPONENT_ID, "Could not infer QR module size and quiet zone from image");
    }

    private String decodeSvg(String svg) throws QrDecodingException {
        Matcher viewBox = SVG_VIEWBOX.matcher(svg);
        if (!viewBox.find() || !viewBox.group(1).equals(viewBox.group(2))) {
            throw new QrDecodingException(COMPONENT_ID, "SVG has no square numeric viewBox");
        }
        int pixels = Integer.parseInt(viewBox.group(1));
        boolean[][] painted = new boolean[pixels][pixels];
        Matcher rects = SVG_RECT.matcher(svg);
        while (rects.find()) {
            int x = -1;
            int y = -1;
            int width = -1;
            int height = -1;
            Matcher attributes = SVG_ATTRIBUTE.matcher(rects.group(1));
            while (attributes.find()) {
                int value = Integer.parseInt(attributes.group(2));
                switch (attributes.group(1)) {
                    case "x": x = value; break;
                    case "y": y = value; break;
                    case "width": width = value; break;
                    case "height": height = value; break;
                    default: break;
                }
            }
            if (x >= 0 && y >= 0 && width > 0 && height > 0) {
                for (int row = y; row < Math.min(pixels, y + height); row++) {
                    for (int column = x; column < Math.min(pixels, x + width); column++) {
                        painted[row][column] = true;
                    }
                }
            }
        }
        for (int moduleSize = 1; moduleSize <= 64; moduleSize++) {
            if (pixels % moduleSize != 0) {
                continue;
            }
            int grid = pixels / moduleSize;
            for (int quiet = 0; quiet <= 32 && grid - 2 * quiet >= 21; quiet++) {
                int side = grid - 2 * quiet;
                if ((side - 17) % 4 != 0) {
                    continue;
                }
                boolean[][] modules = sample(painted, side, moduleSize, quiet);
                if (looksLikeQr(modules)) {
                    return decodeModules(modules);
                }
            }
        }
        throw new QrDecodingException(COMPONENT_ID, "Could not infer QR geometry from SVG");
    }

    private String decodeAscii(String text) throws QrDecodingException {
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length > 0 && lines[lines.length - 1].isEmpty()) {
            String[] trimmed = new String[lines.length - 1];
            System.arraycopy(lines, 0, trimmed, 0, trimmed.length);
            lines = trimmed;
        }
        if (lines.length == 0) {
            throw new QrDecodingException(COMPONENT_ID, "ASCII artifact has no rows");
        }
        int lineWidth = lines[0].length();
        for (String line : lines) {
            if (line.length() != lineWidth) {
                throw new QrDecodingException(COMPONENT_ID, "ASCII artifact rows have different widths");
            }
        }
        for (int cellWidth = 1; cellWidth <= 4; cellWidth++) {
            if (lineWidth % cellWidth != 0) {
                continue;
            }
            int grid = lineWidth / cellWidth;
            for (int border = 0; border <= 32 && grid - 2 * border >= 21; border++) {
                int side = grid - 2 * border;
                if ((side - 17) % 4 != 0 || lines.length != grid) {
                    continue;
                }
                boolean[][] modules = new boolean[side][side];
                for (int y = 0; y < side; y++) {
                    for (int x = 0; x < side; x++) {
                        modules[y][x] = lines[y + border].charAt((x + border) * cellWidth) != ' ';
                    }
                }
                if (looksLikeQr(modules)) {
                    return decodeModules(modules);
                }
            }
        }
        throw new QrDecodingException(COMPONENT_ID, "Could not infer QR geometry from ASCII artifact");
    }

    private static boolean[][] sample(boolean[][] pixels, int side, int moduleSize, int quiet) {
        boolean[][] modules = new boolean[side][side];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int py = (quiet + y) * moduleSize + moduleSize / 2;
                int px = (quiet + x) * moduleSize + moduleSize / 2;
                modules[y][x] = pixels[py][px];
            }
        }
        return modules;
    }

    private static boolean looksLikeQr(boolean[][] modules) {
        int size = modules.length;
        if (size < 21 || (size - 17) % 4 != 0) {
            return false;
        }
        try {
            readFormat(modules, (size - 17) / 4);
            return finderMatches(modules, 3, 3)
                    && finderMatches(modules, size - 4, 3)
                    && finderMatches(modules, 3, size - 4);
        } catch (QrDecodingException | RuntimeException e) {
            return false;
        }
    }

    private static boolean finderMatches(boolean[][] modules, int centreX, int centreY) {
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                boolean expected = Math.max(Math.abs(dx), Math.abs(dy)) != 2;
                if (modules[centreY + dy][centreX + dx] != expected) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Format readFormat(boolean[][] modules, int version) throws QrDecodingException {
        int bits = 0;
        for (int i = 0; i <= 5; i++) {
            bits |= bool(modules[i][8]) << i;
        }
        bits |= bool(modules[7][8]) << 6;
        bits |= bool(modules[8][8]) << 7;
        bits |= bool(modules[8][7]) << 8;
        for (int i = 9; i < 15; i++) {
            bits |= bool(modules[8][14 - i]) << i;
        }
        int bestDistance = Integer.MAX_VALUE;
        QrErrorCorrection bestLevel = null;
        int bestMask = -1;
        for (QrErrorCorrection level : QrErrorCorrection.values()) {
            for (int mask = 0; mask < 8; mask++) {
                int candidate = formatBits(level, mask);
                int distance = Integer.bitCount(bits ^ candidate);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestLevel = level;
                    bestMask = mask;
                }
            }
        }
        if (bestDistance > 3 || bestLevel == null) {
            throw new QrDecodingException(COMPONENT_ID, "Format information is not a valid QR value");
        }
        return new Format(bestLevel, bestMask);
    }

    private static int formatBits(QrErrorCorrection level, int mask) {
        int data = level.getFormatBits() << 3 | mask;
        int remainder = data;
        for (int i = 0; i < 10; i++) {
            remainder = (remainder << 1) ^ ((remainder >>> 9) * 0x537);
        }
        return (data << 10 | remainder) ^ 0x5412;
    }

    private static boolean[][] functionModules(int version) {
        int size = QrTables.sizeForVersion(version);
        boolean[][] result = new boolean[size][size];
        for (int i = 0; i < size; i++) {
            result[6][i] = true;
            result[i][6] = true;
        }
        markFinder(result, 3, 3);
        markFinder(result, size - 4, 3);
        markFinder(result, 3, size - 4);
        int[] positions = QrTables.alignmentPatternPositions(version);
        for (int i = 0; i < positions.length; i++) {
            for (int j = 0; j < positions.length; j++) {
                boolean overlaps = (i == 0 && j == 0) || (i == 0 && j == positions.length - 1)
                        || (i == positions.length - 1 && j == 0);
                if (!overlaps) {
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            result[positions[j] + dy][positions[i] + dx] = true;
                        }
                    }
                }
            }
        }
        for (int i = 0; i <= 5; i++) result[i][8] = true;
        result[7][8] = true;
        result[8][8] = true;
        result[8][7] = true;
        for (int i = 9; i < 15; i++) result[8][14 - i] = true;
        for (int i = 0; i < 8; i++) result[8][size - 1 - i] = true;
        for (int i = 8; i < 15; i++) result[size - 15 + i][8] = true;
        result[size - 8][8] = true;
        if (version >= 7) {
            for (int i = 0; i < 18; i++) {
                result[i / 3][size - 11 + i % 3] = true;
                result[size - 11 + i % 3][i / 3] = true;
            }
        }
        return result;
    }

    private static void markFinder(boolean[][] result, int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int row = y + dy;
                int column = x + dx;
                if (row >= 0 && row < result.length && column >= 0 && column < result.length) {
                    result[row][column] = true;
                }
            }
        }
    }

    private static byte[] readCodewords(boolean[][] modules, boolean[][] function, int mask, int count) {
        byte[] result = new byte[count];
        int bit = 0;
        int size = modules.length;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vertical = 0; vertical < size; vertical++) {
                for (int offset = 0; offset < 2; offset++) {
                    int column = right - offset;
                    boolean upward = ((right + 1) & 2) == 0;
                    int row = upward ? size - 1 - vertical : vertical;
                    if (!function[row][column] && bit < count * 8) {
                        boolean value = modules[row][column] ^ maskBit(mask, column, row);
                        if (value) result[bit / 8] |= 1 << (7 - bit % 8);
                        bit++;
                    }
                }
            }
        }
        return result;
    }

    private byte[][] splitData(byte[] codewords, int version, QrErrorCorrection level)
            throws QrDecodingException {
        int blocks = QrTables.numErrorCorrectionBlocks(level, version);
        int eccLength = QrTables.eccCodewordsPerBlock(level, version);
        int raw = codewords.length;
        int shortLength = raw / blocks;
        int shortBlocks = blocks - raw % blocks;
        int shortData = shortLength - eccLength;
        int maxData = shortData + (shortBlocks < blocks ? 1 : 0);
        byte[][] data = new byte[blocks][];
        byte[][] ecc = new byte[blocks][];
        for (int block = 0; block < blocks; block++) {
            data[block] = new byte[shortData + (block < shortBlocks ? 0 : 1)];
            ecc[block] = new byte[eccLength];
        }
        int offset = 0;
        for (int column = 0; column < maxData; column++) {
            for (int block = 0; block < blocks; block++) {
                if (column < data[block].length) data[block][column] = codewords[offset++];
            }
        }
        for (int column = 0; column < eccLength; column++) {
            for (int block = 0; block < blocks; block++) ecc[block][column] = codewords[offset++];
        }
        if (verifyErrorCorrection(data, ecc)) {
            return data;
        }
        throw new QrDecodingException(COMPONENT_ID, "Reed-Solomon error correction rejected the symbol");
    }

    private static boolean verifyErrorCorrection(byte[][] data, byte[][] ecc) {
        for (int block = 0; block < data.length; block++) {
            if (!java.util.Arrays.equals(ecc[block], remainder(data[block], ecc[block].length))) return false;
        }
        return true;
    }

    private static byte[] join(byte[][] blocks) {
        int length = 0;
        for (byte[] block : blocks) length += block.length;
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] block : blocks) {
            System.arraycopy(block, 0, result, offset, block.length);
            offset += block.length;
        }
        return result;
    }

    private String parseSegments(BitReader bits, int version) throws QrDecodingException {
        StringBuilder result = new StringBuilder();
        Charset charset = StandardCharsets.ISO_8859_1;
        while (bits.remaining() >= 4) {
            int modeIndicator = bits.read(4);
            if (modeIndicator == 0) break;
            if (modeIndicator == QrEncodingMode.ECI.getModeIndicator()) {
                int first = bits.read(8);
                int assignment;
                if ((first & 0x80) == 0) assignment = first;
                else if ((first & 0xC0) == 0x80) assignment = (first & 0x3F) << 8 | bits.read(8);
                else if ((first & 0xE0) == 0xC0) assignment = (first & 0x1F) << 16 | bits.read(16);
                else throw new QrDecodingException(COMPONENT_ID, "Invalid ECI designator");
                if (assignment == 26) charset = StandardCharsets.UTF_8;
                else throw new QrDecodingException(COMPONENT_ID, "Unsupported ECI assignment " + assignment);
                continue;
            }
            QrEncodingMode mode = modeIndicator == 1 ? QrEncodingMode.NUMERIC
                    : modeIndicator == 2 ? QrEncodingMode.ALPHANUMERIC
                    : modeIndicator == 4 ? QrEncodingMode.BYTE
                    : modeIndicator == 8 ? QrEncodingMode.KANJI : null;
            if (mode == null) throw new QrDecodingException(COMPONENT_ID, "Unsupported mode indicator " + modeIndicator);
            int count = bits.read(mode.getCharacterCountBits(version));
            if (mode == QrEncodingMode.NUMERIC) readNumeric(bits, count, result);
            else if (mode == QrEncodingMode.ALPHANUMERIC) readAlphanumeric(bits, count, result);
            else if (mode == QrEncodingMode.BYTE) {
                byte[] bytes = new byte[count];
                for (int i = 0; i < count; i++) bytes[i] = (byte) bits.read(8);
                result.append(new String(bytes, charset));
            } else readKanji(bits, count, result);
        }
        return result.toString();
    }

    private static void readNumeric(BitReader bits, int count, StringBuilder result) {
        int remaining = count;
        while (remaining >= 3) { result.append(String.format("%03d", bits.read(10))); remaining -= 3; }
        if (remaining == 2) result.append(String.format("%02d", bits.read(7)));
        if (remaining == 1) result.append(bits.read(4));
    }

    private static void readAlphanumeric(BitReader bits, int count, StringBuilder result) {
        int remaining = count;
        while (remaining >= 2) { int value = bits.read(11); result.append(ALPHANUMERIC.charAt(value / 45));
            result.append(ALPHANUMERIC.charAt(value % 45)); remaining -= 2; }
        if (remaining == 1) result.append(ALPHANUMERIC.charAt(bits.read(6)));
    }

    private static void readKanji(BitReader bits, int count, StringBuilder result) {
        byte[] bytes = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            int value = bits.read(13);
            int intermediate = (value / 0xC0 << 8) | (value % 0xC0);
            int sjis = intermediate < 0x1F00 ? intermediate + 0x8140 : intermediate + 0xC140;
            bytes[i * 2] = (byte) (sjis >>> 8);
            bytes[i * 2 + 1] = (byte) sjis;
        }
        result.append(new String(bytes, SHIFT_JIS));
    }

    private static boolean maskBit(int mask, int x, int y) {
        switch (mask) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return (x / 3 + y / 2) % 2 == 0;
            case 5: return x * y % 2 + x * y % 3 == 0;
            case 6: return (x * y % 2 + x * y % 3) % 2 == 0;
            case 7: return ((x + y) % 2 + x * y % 3) % 2 == 0;
            default: throw new IllegalArgumentException("Mask must be 0..7");
        }
    }

    private static int bool(boolean value) { return value ? 1 : 0; }

    private static byte[] remainder(byte[] data, int degree) {
        int[] divisor = new int[degree];
        divisor[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < divisor.length; j++) {
                divisor[j] = multiply(divisor[j], root);
                if (j + 1 < divisor.length) divisor[j] ^= divisor[j + 1];
            }
            root = multiply(root, 2);
        }
        int[] remainder = new int[degree];
        for (byte value : data) {
            int factor = (value & 0xFF) ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, degree - 1);
            remainder[degree - 1] = 0;
            for (int i = 0; i < degree; i++) remainder[i] ^= multiply(divisor[i], factor);
        }
        byte[] result = new byte[degree];
        for (int i = 0; i < degree; i++) result[i] = (byte) remainder[i];
        return result;
    }

    private static int multiply(int left, int right) {
        int result = 0;
        while (right != 0) {
            if ((right & 1) != 0) result ^= left;
            left = (left << 1) ^ ((left >>> 7) * 0x11D);
            right >>>= 1;
        }
        return result;
    }

    private static final class Format {
        final QrErrorCorrection level;
        final int mask;
        Format(QrErrorCorrection level, int mask) { this.level = level; this.mask = mask; }
    }

    private static final class BitReader {
        private final byte[] bytes;
        private int position;
        BitReader(byte[] bytes) { this.bytes = bytes; }
        int read(int count) {
            if (count < 0 || position + count > bytes.length * 8) throw new IllegalArgumentException("Data ended early");
            int value = 0;
            for (int i = 0; i < count; i++) { value = value << 1 | ((bytes[position / 8] >>> (7 - position % 8)) & 1); position++; }
            return value;
        }
        int remaining() { return bytes.length * 8 - position; }
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        propertyChanges = new PropertyChangeSupport(this);
    }
}
