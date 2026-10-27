package ke.ku.sco310.qr.engine;

import ke.ku.sco310.qr.api.QrEncodingMode;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One data segment of a QR Code symbol: a mode, a character count and the encoded bit stream.
 *
 * <p>The segment factory chooses the most compact combination of numeric, alphanumeric, byte
 * and kanji modes with a dynamic-programming pass over the payload. This is what keeps a mixed
 * payload at the lowest possible version, i.e. at the highest module size for a fixed print
 * size.</p>
 */
final class QrSegment {

    /** The 45 characters of the alphanumeric mode, indexed by their 6-bit value. */
    static final String ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

    private static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");
    /** ECI assignment number for UTF-8 (ISO/IEC 18004, Table 2). */
    static final int ECI_UTF8 = 26;

    private final QrEncodingMode mode;
    private final int characterCount;
    private final BitBuffer data;

    private QrSegment(QrEncodingMode mode, int characterCount, BitBuffer data) {
        this.mode = mode;
        this.characterCount = characterCount;
        this.data = data;
    }

    static QrSegment makeBytes(byte[] bytes) {
        BitBuffer buffer = new BitBuffer();
        buffer.appendBytes(bytes);
        return new QrSegment(QrEncodingMode.BYTE, bytes.length, buffer);
    }

    static QrSegment makeNumeric(String digits) {
        if (!isNumeric(digits)) {
            throw new IllegalArgumentException("Numeric segments require at least one digit and no other characters");
        }
        BitBuffer buffer = new BitBuffer();
        for (int i = 0; i < digits.length(); ) {
            int take = Math.min(3, digits.length() - i);
            int value = Integer.parseInt(digits.substring(i, i + take));
            buffer.appendBits(value, take == 3 ? 10 : (take == 2 ? 7 : 4));
            i += take;
        }
        return new QrSegment(QrEncodingMode.NUMERIC, digits.length(), buffer);
    }

    static QrSegment makeAlphanumeric(String text) {
        if (!isAlphanumeric(text)) {
            throw new IllegalArgumentException("Payload is outside the QR alphanumeric alphabet");
        }
        BitBuffer buffer = new BitBuffer();
        int i = 0;
        for (; i + 2 <= text.length(); i += 2) {
            int first = ALPHANUMERIC_CHARSET.indexOf(text.charAt(i));
            int second = ALPHANUMERIC_CHARSET.indexOf(text.charAt(i + 1));
            if (first < 0 || second < 0) {
                throw new IllegalArgumentException("Character outside the alphanumeric set: "
                        + text.charAt(first < 0 ? i : i + 1));
            }
            buffer.appendBits(first * 45 + second, 11);
        }
        if (i < text.length()) {
            buffer.appendBits(ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)), 6);
        }
        return new QrSegment(QrEncodingMode.ALPHANUMERIC, text.length(), buffer);
    }

    /**
     * Builds one Kanji-mode segment from a string whose characters are all encodable in the
     * Kanji Shift_JIS range (ISO/IEC 18004, 8.4.5).
     *
     * @param text payload restricted to Kanji-capable characters
     * @return a segment whose 13-bit values are the standard Kanji transformation
     */
    static QrSegment makeKanji(String text) {
        BitBuffer buffer = new BitBuffer();
        for (int i = 0; i < text.length(); i++) {
            buffer.appendBits(toKanjiValue(text.charAt(i)), 13);
        }
        return new QrSegment(QrEncodingMode.KANJI, text.length(), buffer);
    }

    /**
     * Builds an ECI designator segment for an assignment number in the ISO range 0..999999.
     * The segment carries the variable-length assignment value only; the
     * 4-bit mode indicator is emitted by the bit-stream assembler, so no character-count field
     * is ever written for this segment type.
     *
     * @param assignmentNumber ECI assignment number (0..999999)
     * @return an ECI segment whose payload is the assignment number
     */
    static QrSegment makeEci(int assignmentNumber) {
        if (assignmentNumber < 0 || assignmentNumber > 999999) {
            throw new IllegalArgumentException("ECI assignment must be 0..999999: " + assignmentNumber);
        }
        BitBuffer buffer = new BitBuffer();
        if (assignmentNumber < 128) {
            buffer.appendBits(assignmentNumber, 8);
        } else if (assignmentNumber < 16384) {
            buffer.appendBits(0x8000 | assignmentNumber, 16);
        } else {
            buffer.appendBits(0xC00000 | assignmentNumber, 24);
        }
        return new QrSegment(QrEncodingMode.ECI, 0, buffer);
    }

    /**
     * @param text payload
     * @return the most compact segmentation for this payload (computed for version 40)
     */
    static List<QrSegment> makeSegments(String text) {
        return makeSegments(text, 40);
    }

    /**
     * Chooses the optimal segmentation of {@code text} for a target version.
     *
     * <p>A forward-looking dynamic program minimizes the total number of bits: for every suffix
     * starting at position {@code i} it tries every valid leading segment and adds the optimal
     * cost of the remainder. The mode/end pair that realizes the minimum for the whole payload is
     * then walked forward to build the segment list.</p>
     *
     * @param text    payload to encode
     * @param version target symbol version, used for the character-count field widths
     * @return the segment list (empty when the payload is empty)
     */
    static List<QrSegment> makeSegments(String text, int version) {
        return optimise(text, version, false);
    }

    static List<QrSegment> makeMicroSegments(String text, int version) {
        return optimise(text, version, true);
    }

    private static List<QrSegment> optimise(String text, int version, boolean micro) {
        validateText(text);
        List<QrSegment> result = new ArrayList<>();
        if (text.isEmpty()) {
            return result;
        }
        int n = text.length();
        int[] utf8Prefix = new int[n + 1];
        int[] numericInvalid = new int[n + 1];
        int[] alphaInvalid = new int[n + 1];
        int[] kanjiInvalid = new int[n + 1];
        int[] latinInvalid = new int[n + 1];
        int[] nonAscii = new int[n + 1];
        boolean[] boundary = new boolean[n + 1];
        boundary[0] = true;
        boundary[n] = true;
        for (int k = 0; k < n; ) {
            int codePoint = text.codePointAt(k);
            int charCount = Character.charCount(codePoint);
            int bytes = utf8ByteCount(codePoint);
            for (int t = 0; t < charCount; t++) {
                int position = k + t;
                char c = text.charAt(position);
                utf8Prefix[position + 1] = utf8Prefix[k] + bytes;
                numericInvalid[position + 1] = numericInvalid[position] + (isDigit(c) ? 0 : 1);
                alphaInvalid[position + 1] = alphaInvalid[position]
                        + (ALPHANUMERIC_CHARSET.indexOf(c) >= 0 ? 0 : 1);
                kanjiInvalid[position + 1] = kanjiInvalid[position]
                        + (charCount == 1 && toKanjiValueOrMinusOne(c) >= 0 ? 0 : 1);
                latinInvalid[position + 1] = latinInvalid[position] + (c <= 255 ? 0 : 1);
                nonAscii[position + 1] = nonAscii[position] + (c > 127 ? 1 : 0);
            }
            k += charCount;
            boundary[k] = true;
        }

        long[][] cost = new long[n + 1][2];
        QrEncodingMode[][] modeAt = new QrEncodingMode[n][2];
        int[][] endAt = new int[n][2];
        for (long[] row : cost) {
            Arrays.fill(row, Long.MAX_VALUE / 4);
        }
        cost[n][0] = 0;
        cost[n][1] = 0;
        QrEncodingMode[] modes = {QrEncodingMode.NUMERIC, QrEncodingMode.ALPHANUMERIC,
                QrEncodingMode.KANJI, QrEncodingMode.BYTE};

        for (int i = n - 1; i >= 0; i--) {
            if (i > 0 && !boundary[i]) {
                continue;
            }
            for (int j = i + 1; j <= n; j++) {
                if (!boundary[j]) {
                    continue;
                }
                for (QrEncodingMode mode : modes) {
                    int countBits = micro ? MicroQrTables.characterCountBits(version, mode)
                            : mode.getCharacterCountBits(version);
                    if (countBits < 0) {
                        continue;
                    }
                    int count = mode == QrEncodingMode.BYTE && !micro
                            ? utf8Prefix[j] - utf8Prefix[i] : j - i;
                    if (count >= (1 << countBits)) {
                        continue;
                    }
                    int[] invalid = mode == QrEncodingMode.NUMERIC ? numericInvalid
                            : mode == QrEncodingMode.ALPHANUMERIC ? alphaInvalid
                            : mode == QrEncodingMode.KANJI ? kanjiInvalid : latinInvalid;
                    if ((mode != QrEncodingMode.BYTE || micro) && invalid[j] != invalid[i]) {
                        continue;
                    }
                    long bits = (micro ? version - 1 : 4) + countBits + dataBitLength(mode, count);
                    boolean needsEci = !micro && mode == QrEncodingMode.BYTE && nonAscii[j] != nonAscii[i];
                    for (int state = 0; state < 2; state++) {
                        int nextState = needsEci ? 1 : state;
                        long candidate = bits + cost[j][nextState] + (needsEci && state == 0 ? 12 : 0);
                        if (candidate < cost[i][state]) {
                            cost[i][state] = candidate;
                            modeAt[i][state] = mode;
                            endAt[i][state] = j;
                        }
                    }
                }
            }
        }

        int i = 0;
        int state = 0;
        while (i < n) {
            if (modeAt[i][state] == null) {
                throw new IllegalArgumentException("Payload uses a mode or character unavailable in M" + version);
            }
            int end = endAt[i][state];
            String part = text.substring(i, end);
            switch (modeAt[i][state]) {
                case NUMERIC:
                    result.add(makeNumeric(part));
                    break;
                case ALPHANUMERIC:
                    result.add(makeAlphanumeric(part));
                    break;
                case BYTE:
                    result.add(makeBytes(part.getBytes(micro ? StandardCharsets.ISO_8859_1
                            : StandardCharsets.UTF_8)));
                    if (!micro && nonAscii[end] != nonAscii[i]) {
                        state = 1;
                    }
                    break;
                case KANJI:
                    result.add(makeKanji(part));
                    break;
                default:
                    throw new IllegalStateException("Unreachable segmentation state");
            }
            i = end;
        }
        if (state == 1) {
            result.add(0, makeEci(ECI_UTF8));
        }
        return result;
    }

    private static long dataBitLength(QrEncodingMode mode, int length) {
        switch (mode) {
            case NUMERIC:
                return 10L * (length / 3)
                        + (length % 3 == 2 ? 7L : length % 3 == 1 ? 4L : 0L);
            case ALPHANUMERIC:
                return 11L * (length / 2) + 6L * (length % 2);
            case BYTE:
                return 8L * length;
            case KANJI:
                return 13L * length;
            default:
                throw new IllegalArgumentException("Unsupported DP mode: " + mode);
        }
    }

    static boolean isNumeric(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return !text.isEmpty();
    }

    static boolean isAlphanumeric(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (ALPHANUMERIC_CHARSET.indexOf(text.charAt(i)) < 0) {
                return false;
            }
        }
        return !text.isEmpty();
    }

    /** @return {@code true} when the payload contains any byte that is not plain ASCII. */
    static boolean needsEci(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return true;
            }
        }
        return false;
    }

    /** @return {@code true} when the payload contains at least one byte-mode segment. */
    static boolean usesByteMode(List<QrSegment> segments) {
        for (QrSegment segment : segments) {
            if (segment.mode == QrEncodingMode.BYTE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prepends a UTF-8 ECI designator when the payload is not plain ASCII and the segmentation
     * uses byte mode; plain-ASCII, numeric, alphanumeric and all-Kanji payloads need no ECI.
     *
     * @param segments segmentation produced by {@link #makeSegments(String, int)}
     * @param text     original payload
     * @return the same list, or a new list with the ECI segment in front
     */
    static List<QrSegment> withEciIfNeeded(List<QrSegment> segments, String text) {
        if (!segments.isEmpty() && segments.get(0).isEci()) {
            return segments;
        }
        if (needsEci(text) && usesByteMode(segments)) {
            List<QrSegment> prefixed = new ArrayList<>(segments.size() + 1);
            prefixed.add(makeEci(ECI_UTF8));
            prefixed.addAll(segments);
            return prefixed;
        }
        return segments;
    }

    QrEncodingMode getMode() {
        return mode;
    }

    int getCharacterCount() {
        return characterCount;
    }

    BitBuffer getData() {
        return data;
    }

    /** @return {@code true} for an ECI designator segment (it has no character-count field). */
    boolean isEci() {
        return mode == QrEncodingMode.ECI;
    }

    /** @param version target symbol version @return bits this segment occupies at that version. */
    int getTotalBits(int version) {
        if (isEci()) {
            return 4 + data.size();
        }
        return 4 + mode.getCharacterCountBits(version) + data.size();
    }

    @Override
    public String toString() {
        return mode + " segment (" + characterCount + " chars, " + data.size() + " bits)";
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int utf8ByteCount(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }


    /**
     * @param c Unicode character to encode
     * @return the 13-bit Kanji-mode value, or -1 when the character has no Kanji Shift_JIS form
     */
    private static int toKanjiValueOrMinusOne(char c) {
        if (!SHIFT_JIS.newEncoder().canEncode(c)) {
            return -1;
        }
        byte[] bytes = String.valueOf(c).getBytes(SHIFT_JIS);
        if (bytes.length != 2) {
            return -1;
        }
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        if (b0 >= 0x81 && b0 <= 0x9F && b1 >= 0x40 && b1 <= 0xFC && b1 != 0x7F) {
            return (b0 - 0x81) * 0xC0 + (b1 - 0x40);
        }
        if (b0 >= 0xE0 && b0 <= 0xEB && b1 >= 0x40 && b1 <= 0xFC && b1 != 0x7F) {
            return (b0 - 0xC1) * 0xC0 + (b1 - 0x40);
        }
        return -1;
    }

    static void validateText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Payload must not be null");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 == text.length() || !Character.isLowSurrogate(text.charAt(++i))) {
                    throw new IllegalArgumentException("Payload contains an unpaired high surrogate");
                }
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Payload contains an unpaired low surrogate");
            }
        }
    }

    private static int toKanjiValue(char c) {
        int value = toKanjiValueOrMinusOne(c);
        if (value < 0) {
            throw new IllegalArgumentException("Character cannot be encoded in Kanji mode: "
                    + String.format("U+%04X", (int) c));
        }
        return value;
    }
}
