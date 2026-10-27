package ke.ku.sco310.qr.engine;

import ke.ku.sco310.qr.api.MicroQrCode;
import ke.ku.sco310.qr.api.QrEncodingException;
import ke.ku.sco310.qr.api.QrEncodingMode;
import ke.ku.sco310.qr.api.QrErrorCorrection;

import java.util.List;

/** Dependency-free ISO/IEC 18004 Micro QR encoder, kept separate from the standard QR path. */
public final class MicroQrEngine {

    private static final int PAD_0 = 0xEC;
    private static final int PAD_1 = 0x11;

    private final int version;
    private final int size;
    private final boolean[][] modules;
    private final boolean[][] function;

    private MicroQrEngine(int version) {
        this.version = version;
        size = 9 + version * 2;
        modules = new boolean[size][size];
        function = new boolean[size][size];
    }

    public static MicroQrCode encode(String content, QrErrorCorrection requestedLevel,
                                     boolean boostErrorCorrection, int minimumVersion,
                                     int maximumVersion, int forcedMask, String producerId)
            throws QrEncodingException {
        validate(content, requestedLevel, minimumVersion, maximumVersion, forcedMask, producerId);

        int version = -1;
        QrErrorCorrection level = null;
        List<QrSegment> segments = null;
        for (int candidate = minimumVersion; candidate <= maximumVersion; candidate++) {
            QrErrorCorrection candidateLevel = levelFor(candidate, requestedLevel);
            if (candidateLevel == QrErrorCorrection.HIGH) {
                continue;
            }
            try {
                List<QrSegment> candidateSegments = QrSegment.makeMicroSegments(content, candidate);
                if (totalBits(candidateSegments, candidate) <= MicroQrTables.dataCapacityBits(
                        candidate, candidateLevel)) {
                    version = candidate;
                    level = candidateLevel;
                    segments = candidateSegments;
                    break;
                }
            } catch (IllegalArgumentException ignored) {
                // This version does not publish a mode needed by the payload; try the next M-version.
            }
        }
        if (version < 0 || segments == null) {
            throw new QrEncodingException(producerId, "Payload does not fit M" + minimumVersion
                    + "..M" + maximumVersion + " at level " + requestedLevel.getLabel());
        }

        if (boostErrorCorrection && level != null) {
            for (QrErrorCorrection stronger : new QrErrorCorrection[]{QrErrorCorrection.MEDIUM,
                    QrErrorCorrection.QUARTILE}) {
                if (stronger.ordinal() > level.ordinal() && supports(version, stronger)
                        && totalBits(segments, version) <= MicroQrTables.dataCapacityBits(version, stronger)) {
                    level = stronger;
                }
            }
        }

        int capacity = MicroQrTables.dataCapacityBits(version, level);
        BitBuffer data = makeDataBits(segments, version, capacity);
        byte[] dataCodewords = data.toByteArray();
        byte[] ecc = ReedSolomon.computeRemainder(dataCodewords,
                MicroQrTables.eccCodewords(version, level));
        BitBuffer message = finalMessage(dataCodewords, ecc, version);

        MicroQrEngine engine = new MicroQrEngine(version);
        engine.drawFunctionPatterns();
        engine.drawCodewords(message);
        int mask = forcedMask;
        if (mask < 0) {
            int bestScore = -1;
            for (int candidate = 0; candidate < 4; candidate++) {
                engine.applyMask(candidate);
                int score = engine.maskScore();
                if (score > bestScore) {
                    bestScore = score;
                    mask = candidate;
                }
                engine.applyMask(candidate);
            }
        }
        engine.applyMask(mask);
        engine.drawFormatBits(mask, level);

        QrEncodingMode firstMode = segments.get(0).getMode();
        return new MicroQrCode(content, requestedLevel, level, firstMode,
                content.length(), version, mask, dataCodewords.length, ecc.length,
                engine.modules, producerId);
    }

    static byte[] makeDataCodewords(String content, int version, QrErrorCorrection level) {
        List<QrSegment> segments = QrSegment.makeMicroSegments(content, version);
        return makeDataBits(segments, version,
                MicroQrTables.dataCapacityBits(version, level)).toByteArray();
    }

    private static BitBuffer makeDataBits(List<QrSegment> segments, int version, int capacity) {
        BitBuffer result = new BitBuffer();
        for (QrSegment segment : segments) {
            if (version > 1) {
                result.appendBits(MicroQrTables.modeIndicator(segment.getMode()), version - 1);
            }
            result.appendBits(segment.getCharacterCount(),
                    MicroQrTables.characterCountBits(version, segment.getMode()));
            result.append(segment.getData());
        }
        int terminator = Math.min(capacity - result.size(), version * 2 + 1);
        result.appendBits(0, terminator);
        if (version == 1 || version == 3) {
            result.appendBits(0, capacity - result.size());
        } else {
            result.appendBits(0, Math.min(capacity - result.size(),
                    (8 - result.size() % 8) % 8));
            for (int pad = PAD_0; result.size() < capacity; pad ^= PAD_0 ^ PAD_1) {
                result.appendBits(pad, Math.min(8, capacity - result.size()));
            }
        }
        if (result.size() != capacity) {
            throw new IllegalStateException("Micro data stream is " + result.size()
                    + " bits but capacity is " + capacity);
        }
        return result;
    }

    private static BitBuffer finalMessage(byte[] data, byte[] ecc, int version) {
        BitBuffer result = new BitBuffer();
        int fullDataBytes = data.length;
        if (version == 1 || version == 3) {
            fullDataBytes--;
        }
        for (int i = 0; i < fullDataBytes; i++) {
            result.appendBits(data[i] & 0xFF, 8);
        }
        if (fullDataBytes < data.length) {
            result.appendBits((data[data.length - 1] & 0xFF) >>> 4, 4);
        }
        result.appendBytes(ecc);
        return result;
    }

    private static int totalBits(List<QrSegment> segments, int version) {
        int total = 0;
        for (QrSegment segment : segments) {
            total += version - 1 + MicroQrTables.characterCountBits(version, segment.getMode())
                    + segment.getData().size();
        }
        return total;
    }

    private void drawFunctionPatterns() {
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                boolean dark = x < 7 && y < 7
                        && (x == 0 || x == 6 || y == 0 || y == 6
                        || (x >= 2 && x <= 4 && y >= 2 && y <= 4));
                setFunction(x, y, dark);
            }
        }
        for (int i = 8; i < size; i++) {
            setFunction(i, 0, (i & 1) == 0);
            setFunction(0, i, (i & 1) == 0);
        }
        for (int i = 1; i <= 8; i++) {
            setFunction(8, i, false);
            setFunction(i, 8, false);
        }
    }

    private void drawCodewords(BitBuffer data) {
        int bit = 0;
        int directionOffset = version == 1 || version == 3 ? 2 : 0;
        for (int right = size - 1; right > 0; right -= 2) {
            for (int vertical = 0; vertical < size; vertical++) {
                for (int offset = 0; offset < 2; offset++) {
                    int x = right - offset;
                    boolean upward = ((right + directionOffset) & 2) == 0;
                    int y = upward ? size - 1 - vertical : vertical;
                    if (!function[y][x] && bit < data.size()) {
                        modules[y][x] = data.get(bit++);
                    }
                }
            }
        }
        if (bit != data.size()) {
            throw new IllegalStateException("Placed " + bit + " of " + data.size() + " Micro QR bits");
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!function[y][x] && maskBit(mask, x, y)) {
                    modules[y][x] = !modules[y][x];
                }
            }
        }
    }

    private static boolean maskBit(int mask, int x, int y) {
        switch (mask) {
            case 0: return y % 2 == 0;
            case 1: return (y / 2 + x / 3) % 2 == 0;
            case 2: return (x * y % 2 + x * y % 3) % 2 == 0;
            case 3: return ((x + y) % 2 + x * y % 3) % 2 == 0;
            default: throw new IllegalArgumentException("Micro mask must be 0..3");
        }
    }

    private int maskScore() {
        int right = 0;
        int bottom = 0;
        for (int i = 1; i < size; i++) {
            if (modules[i][size - 1]) {
                right++;
            }
            if (modules[size - 1][i]) {
                bottom++;
            }
        }
        return Math.min(right, bottom) * 16 + Math.max(right, bottom);
    }

    private void drawFormatBits(int mask, QrErrorCorrection level) {
        int data = MicroQrTables.formatType(version, level) << 2 | mask;
        int remainder = data;
        for (int i = 0; i < 10; i++) {
            remainder = (remainder << 1) ^ ((remainder >>> 9) * 0x537);
        }
        int bits = (data << 10 | remainder) ^ 0x4445;
        for (int i = 0; i < 8; i++) {
            setFunction(8, i + 1, ((bits >>> i) & 1) != 0);
            setFunction(i + 1, 8, ((bits >>> (14 - i)) & 1) != 0);
        }
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        function[y][x] = true;
    }

    private static QrErrorCorrection levelFor(int version, QrErrorCorrection requested) {
        if (version == 1) {
            return requested == QrErrorCorrection.LOW ? null : QrErrorCorrection.HIGH;
        }
        return supports(version, requested) ? requested : QrErrorCorrection.HIGH;
    }

    private static boolean supports(int version, QrErrorCorrection level) {
        return level == QrErrorCorrection.LOW || level == QrErrorCorrection.MEDIUM
                || version == 4 && level == QrErrorCorrection.QUARTILE;
    }

    private static void validate(String content, QrErrorCorrection level, int minimumVersion,
                                 int maximumVersion, int forcedMask, String producerId)
            throws QrEncodingException {
        if (content == null || content.isEmpty()) {
            throw new QrEncodingException(producerId, "The Micro QR payload is empty");
        }
        try {
            QrSegment.validateText(content);
        } catch (IllegalArgumentException e) {
            throw new QrEncodingException(producerId, e.getMessage(), e);
        }
        if (level == null || level == QrErrorCorrection.HIGH) {
            throw new QrEncodingException(producerId, "Micro QR supports requested levels L, M and Q only");
        }
        if (minimumVersion < 1 || maximumVersion > 4 || minimumVersion > maximumVersion) {
            throw new QrEncodingException(producerId, "Invalid Micro version window M"
                    + minimumVersion + "..M" + maximumVersion);
        }
        if (forcedMask < -1 || forcedMask > 3) {
            throw new QrEncodingException(producerId, "Micro mask must be 0..3 or -1 for automatic selection");
        }
    }
}
