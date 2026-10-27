package ke.ku.sco310.qr.engine;

import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrEncodingException;
import ke.ku.sco310.qr.api.QrEncodingMode;
import ke.ku.sco310.qr.api.QrErrorCorrection;

import java.util.List;

/**
 * The complete QR Code encoding pipeline, written from ISO/IEC 18004 without any third-party
 * dependency. This is the hidden behaviour ("B") behind the encoder component.
 *
 * <p>Pipeline stages, in order:</p>
 * <ol>
 *   <li>analyse the payload and build the most compact segment (numeric / alphanumeric / byte);</li>
 *   <li>select the smallest symbol version at which the payload fits the requested level;</li>
 *   <li>assemble the bit stream: mode indicator, character count, data, terminator, pad bits
 *       and the alternating pad codewords {@code 0xEC} / {@code 0x11};</li>
 *   <li>split into Reed-Solomon blocks, generate error-correction codewords and interleave;</li>
 *   <li>draw function patterns, place the codewords in the zig-zag order, choose the mask with
 *       the lowest penalty score and write the format information.</li>
 * </ol>
 */
public final class QrEngine {

    private static final int PENALTY_N1 = 3;  // adjacent modules of the same colour
    private static final int PENALTY_N2 = 3;  // 2x2 blocks of the same colour
    private static final int PENALTY_N3 = 40; // finder-like 1:1:3:1:1 patterns
    private static final int PENALTY_N4 = 10; // deviation from a 50% dark ratio
    private static final int PAD_CODEWORD_0 = 0xEC;
    private static final int PAD_CODEWORD_1 = 0x11;

    private final boolean[][] modules;
    private final boolean[][] isFunction;
    private final int size;
    private final int version;

    private QrEngine(int version) {
        this.version = version;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];
    }

    /**
     * Encodes a payload into a symbol.
     *
     * @param content               payload text
     * @param requestedLevel        error-correction level asked for by the caller
     * @param boostErrorCorrection  when {@code true} the level is raised while the payload still
     *                              fits, which makes the printed symbol more robust
     * @param minimumVersion        lowest version the encoder may choose (1..40)
     * @param maximumVersion        highest version the encoder may choose (1..40)
     * @param forcedMask            mask pattern 0..7, or a negative value to auto-select
     * @param producerId            identifier of the calling component, recorded in the symbol
     * @return the immutable symbol description
     * @throws QrEncodingException when the payload is empty, unrepresentable or too long
     */
    public static QrCode encode(String content, QrErrorCorrection requestedLevel,
                                boolean boostErrorCorrection, int minimumVersion,
                                int maximumVersion, int forcedMask, String producerId)
            throws QrEncodingException {

        if (content == null || content.isEmpty()) {
            throw new QrEncodingException(producerId, "The payload is empty; a QR Code must carry at least one byte");
        }
        if (requestedLevel == null) {
            throw new QrEncodingException(producerId, "An error-correction level is required");
        }
        try {
            QrSegment.validateText(content);
        } catch (IllegalArgumentException e) {
            throw new QrEncodingException(producerId, e.getMessage(), e);
        }
        if (content.codePointCount(0, content.length()) > 7089) {
            throw new QrEncodingException(producerId, "Payload exceeds the maximum QR numeric capacity of 7089 characters");
        }
        if (minimumVersion < 1 || maximumVersion > 40 || minimumVersion > maximumVersion) {
            throw new QrEncodingException(producerId, "Invalid version window "
                    + minimumVersion + ".." + maximumVersion);
        }
        if (forcedMask < -1 || forcedMask > 7) {
            throw new QrEncodingException(producerId, "Mask pattern must be 0..7 or -1 for automatic selection");
        }

        int version = minimumVersion;
        QrErrorCorrection level = requestedLevel;
        List<QrSegment> segments = new java.util.ArrayList<>();
        int usedBits = 0;
        int previousRange = -1;
        while (true) {
            int range = version <= 9 ? 0 : version <= 26 ? 1 : 2;
            if (range != previousRange) {
                segments = QrSegment.makeSegments(content, version);
                previousRange = range;
            }
            usedBits = totalBits(segments, version);
            int capacityBits = QrTables.numDataCodewords(version, level) * 8;
            if (usedBits <= capacityBits) {
                break;
            }
            if (version >= maximumVersion) {
                throw new QrEncodingException(producerId, "Payload of " + content.length()
                        + " characters does not fit version " + maximumVersion
                        + " at level " + level.getLabel() + " (needs " + usedBits + " bits, capacity "
                        + capacityBits + " bits). Shorten the payload or lower the error-correction level.");
            }
            version++;
        }

        if (boostErrorCorrection) {
            QrErrorCorrection[] levels = QrErrorCorrection.values();
            while (level.ordinal() < levels.length - 1) {
                QrErrorCorrection next = levels[level.ordinal() + 1];
                int capacityBits = QrTables.numDataCodewords(version, next) * 8;
                if (usedBits <= capacityBits) {
                    level = next;
                } else {
                    break;
                }
            }
        }

        int dataCapacityBits = QrTables.numDataCodewords(version, level) * 8;
        BitBuffer dataBits = new BitBuffer();
        for (QrSegment segment : segments) {
            dataBits.appendBits(segment.getMode().getModeIndicator(), 4);
            if (!segment.isEci()) {
                dataBits.appendBits(segment.getCharacterCount(), segment.getMode().getCharacterCountBits(version));
            }
            dataBits.append(segment.getData());
        }

        // Terminator: up to four zero bits, then zero padding to the next codeword boundary.
        dataBits.appendBits(0, Math.min(4, dataCapacityBits - dataBits.size()));
        dataBits.appendBits(0, (8 - dataBits.size() % 8) % 8);

        // Pad codewords 11101100 / 00010001 alternate until the data capacity is filled.
        for (int pad = PAD_CODEWORD_0; dataBits.size() < dataCapacityBits; pad ^= PAD_CODEWORD_0 ^ PAD_CODEWORD_1) {
            dataBits.appendBits(pad, 8);
        }

        byte[] dataCodewords = dataBits.toByteArray();
        byte[] allCodewords = addEccAndInterleave(dataCodewords, version, level);

        QrEngine engine = new QrEngine(version);
        engine.drawFunctionPatterns();
        engine.drawCodewords(allCodewords);

        int mask = forcedMask;
        if (mask < 0) {
            int bestPenalty = Integer.MAX_VALUE;
            for (int candidate = 0; candidate < 8; candidate++) {
                engine.applyMask(candidate);
                engine.drawFormatBits(candidate, level);
                int penalty = engine.getPenaltyScore();
                if (penalty < bestPenalty) {
                    bestPenalty = penalty;
                    mask = candidate;
                }
                engine.applyMask(candidate); // XOR is its own inverse: undo the candidate mask
            }
        }
        engine.applyMask(mask);
        engine.drawFormatBits(mask, level);

        QrEncodingMode mode = QrEncodingMode.BYTE;
        int characterCount = 0;
        for (QrSegment segment : segments) {
            if (segment.isEci()) {
                continue;
            }
            if (characterCount == 0) {
                mode = segment.getMode();
            }
            characterCount += segment.getCharacterCount();
        }
        int eccCodewords = QrTables.eccCodewordsPerBlock(level, version)
                * QrTables.numErrorCorrectionBlocks(level, version);

        return new QrCode(content, requestedLevel, level, mode, characterCount, version, mask,
                QrTables.numDataCodewords(version, level), eccCodewords, engine.modules, producerId);
    }

    /** @return number of bits the segments occupy at a given version (mode+counter+payload). */
    private static int totalBits(List<QrSegment> segments, int version) {
        int total = 0;
        for (QrSegment segment : segments) {
            total += segment.getTotalBits(version);
        }
        return total;
    }

    /**
     * Splits the data codewords into blocks, appends the Reed-Solomon codewords of every block
     * and interleaves the result as required by ISO/IEC 18004, 8.6.
     */
    static byte[] addEccAndInterleave(byte[] data, int version, QrErrorCorrection level) {
        int numBlocks = QrTables.numErrorCorrectionBlocks(level, version);
        int blockEccLen = QrTables.eccCodewordsPerBlock(level, version);
        int rawCodewords = QrTables.totalCodewords(version);
        // Blocks are either "short" (shortBlockLen codewords) or "long" (shortBlockLen + 1).
        int shortBlockLen = rawCodewords / numBlocks;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortDataLen = shortBlockLen - blockEccLen;
        int maxDataLen = shortDataLen + (numShortBlocks < numBlocks ? 1 : 0);

        // Step 1: cut the data codeword sequence into blocks and compute the ECC codewords of
        // every block with the Reed-Solomon encoder.
        byte[][] dataParts = new byte[numBlocks][];
        byte[][] eccParts = new byte[numBlocks][];
        int offset = 0;
        for (int i = 0; i < numBlocks; i++) {
            int dataLen = shortDataLen + (i < numShortBlocks ? 0 : 1);
            byte[] dataPart = new byte[dataLen];
            System.arraycopy(data, offset, dataPart, 0, dataLen);
            offset += dataLen;
            dataParts[i] = dataPart;
            // The Reed-Solomon register already accounts for the x^degree shift, so only the
            // data codewords of this block are fed in - never zero-valued ECC placeholders.
            eccParts[i] = ReedSolomon.computeRemainder(dataPart, blockEccLen);
        }

        // Step 2: interleave (ISO/IEC 18004, 8.6). First every data codeword column by column,
        // then every error-correction codeword column by column. Short blocks contribute no
        // codeword in the last data column, which is why the two phases are kept apart.
        byte[] result = new byte[rawCodewords];
        int k = 0;
        for (int column = 0; column < maxDataLen; column++) {
            for (int j = 0; j < numBlocks; j++) {
                if (column < dataParts[j].length) {
                    result[k++] = dataParts[j][column];
                }
            }
        }
        for (int column = 0; column < blockEccLen; column++) {
            for (int j = 0; j < numBlocks; j++) {
                result[k++] = eccParts[j][column];
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ matrix construction

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            setFunctionModule(6, i, i % 2 == 0);
            setFunctionModule(i, 6, i % 2 == 0);
        }
        drawFinderPattern(3, 3);
        drawFinderPattern(size - 4, 3);
        drawFinderPattern(3, size - 4);

        int[] alignPositions = QrTables.alignmentPatternPositions(version);
        int numAlign = alignPositions.length;
        for (int i = 0; i < numAlign; i++) {
            for (int j = 0; j < numAlign; j++) {
                boolean overlapsFinder = (i == 0 && j == 0)
                        || (i == 0 && j == numAlign - 1)
                        || (i == numAlign - 1 && j == 0);
                if (!overlapsFinder) {
                    drawAlignmentPattern(alignPositions[i], alignPositions[j]);
                }
            }
        }
        drawFormatBits(0, QrErrorCorrection.LOW); // placeholder, rewritten once the mask is known
        drawVersionBits();
    }

    private void drawFinderPattern(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx;
                int yy = y + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) {
                    setFunctionModule(xx, yy, distance != 2 && distance != 4);
                }
            }
        }
    }

    private void drawAlignmentPattern(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunctionModule(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private void drawFormatBits(int mask, QrErrorCorrection level) {
        int data = level.getFormatBits() << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = (data << 10 | rem) ^ 0x5412;

        for (int i = 0; i <= 5; i++) {
            setFunctionModule(8, i, getBit(bits, i));
        }
        setFunctionModule(8, 7, getBit(bits, 6));
        setFunctionModule(8, 8, getBit(bits, 7));
        setFunctionModule(7, 8, getBit(bits, 8));
        for (int i = 9; i < 15; i++) {
            setFunctionModule(14 - i, 8, getBit(bits, i));
        }

        for (int i = 0; i < 8; i++) {
            setFunctionModule(size - 1 - i, 8, getBit(bits, i));
        }
        for (int i = 8; i < 15; i++) {
            setFunctionModule(8, size - 15 + i, getBit(bits, i));
        }
        setFunctionModule(8, size - 8, true); // the module that is always dark
    }

    private void drawVersionBits() {
        if (version < 7) {
            return;
        }
        int rem = version;
        for (int i = 0; i < 12; i++) {
            rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        }
        int bits = version << 12 | rem;
        for (int i = 0; i < 18; i++) {
            boolean bit = getBit(bits, i);
            int a = size - 11 + i % 3;
            int b = i / 3;
            setFunctionModule(a, b, bit);
            setFunctionModule(b, a, bit);
        }
    }

    /** Places the interleaved codewords in the two-module wide zig-zag order of ISO/IEC 18004, 8.7. */
    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5; // the vertical timing pattern column is skipped
            }
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = getBit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (mask) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    case 7: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                    default: throw new IllegalArgumentException("Mask must be 0..7: " + mask);
                }
                if (invert && !isFunction[y][x]) {
                    modules[y][x] = !modules[y][x];
                }
            }
        }
    }

    private void setFunctionModule(int x, int y, boolean isDark) {
        modules[y][x] = isDark;
        isFunction[y][x] = true;
    }

    private static boolean getBit(int value, int index) {
        return ((value >>> index) & 1) != 0;
    }

    // ------------------------------------------------------------------ mask penalty score

    /** @return the four-part penalty score of the current mask (lower is better). */
    private int getPenaltyScore() {
        int result = 0;

        for (int y = 0; y < size; y++) { // rows
            boolean runColour = false;
            int runLength = 0;
            int[] runHistory = new int[7];
            for (int x = 0; x < size; x++) {
                if (modules[y][x] == runColour) {
                    runLength++;
                    if (runLength == 5) {
                        result += PENALTY_N1;
                    } else if (runLength > 5) {
                        result++;
                    }
                } else {
                    finderPenaltyAddHistory(runLength, runHistory);
                    if (!runColour) {
                        result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3;
                    }
                    runColour = modules[y][x];
                    runLength = 1;
                }
            }
            result += finderPenaltyTerminateAndCount(runColour, runLength, runHistory) * PENALTY_N3;
        }

        for (int x = 0; x < size; x++) { // columns
            boolean runColour = false;
            int runLength = 0;
            int[] runHistory = new int[7];
            for (int y = 0; y < size; y++) {
                if (modules[y][x] == runColour) {
                    runLength++;
                    if (runLength == 5) {
                        result += PENALTY_N1;
                    } else if (runLength > 5) {
                        result++;
                    }
                } else {
                    finderPenaltyAddHistory(runLength, runHistory);
                    if (!runColour) {
                        result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3;
                    }
                    runColour = modules[y][x];
                    runLength = 1;
                }
            }
            result += finderPenaltyTerminateAndCount(runColour, runLength, runHistory) * PENALTY_N3;
        }

        for (int y = 0; y < size - 1; y++) { // 2x2 blocks
            for (int x = 0; x < size - 1; x++) {
                boolean colour = modules[y][x];
                if (colour == modules[y][x + 1] && colour == modules[y + 1][x] && colour == modules[y + 1][x + 1]) {
                    result += PENALTY_N2;
                }
            }
        }

        int darkModules = 0;
        for (boolean[] row : modules) {
            for (boolean cell : row) {
                if (cell) {
                    darkModules++;
                }
            }
        }
        int totalModules = size * size;
        int k = (Math.abs(darkModules * 20 - totalModules * 10) + totalModules - 1) / totalModules - 1;
        result += k * PENALTY_N4;
        return result;
    }

    private int finderPenaltyCountPatterns(int[] runHistory) {
        int n = runHistory[1];
        boolean core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3
                && runHistory[4] == n && runHistory[5] == n;
        return (core && runHistory[0] >= n * 4 && runHistory[6] >= n ? 1 : 0)
                + (core && runHistory[6] >= n * 4 && runHistory[0] >= n ? 1 : 0);
    }

    private int finderPenaltyTerminateAndCount(boolean currentRunColour, int currentRunLength, int[] runHistory) {
        if (currentRunColour) {
            finderPenaltyAddHistory(currentRunLength, runHistory);
            currentRunLength = 0;
        }
        currentRunLength += size; // light border after the last run
        finderPenaltyAddHistory(currentRunLength, runHistory);
        return finderPenaltyCountPatterns(runHistory);
    }

    private void finderPenaltyAddHistory(int currentRunLength, int[] runHistory) {
        if (runHistory[0] == 0) {
            currentRunLength += size; // light border before the first run
        }
        System.arraycopy(runHistory, 0, runHistory, 1, runHistory.length - 1);
        runHistory[0] = currentRunLength;
    }
}
