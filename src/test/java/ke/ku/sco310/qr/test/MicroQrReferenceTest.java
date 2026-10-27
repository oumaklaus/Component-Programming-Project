package ke.ku.sco310.qr.test;

import ke.ku.sco310.qr.api.MicroQrCode;
import ke.ku.sco310.qr.api.QrEncodingMode;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.components.MicroQrEncoderBean;
import ke.ku.sco310.qr.engine.MicroQrTables;

import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Dependency-free reference decoder for the Micro QR verification vectors.
 *
 * <p>ZXing 3.5.3 does not decode Micro QR, so this test is the independent in-repository oracle
 * for the separate M1-M4 path. It reverses the placement and mask rules, parses the published
 * segment formats and checks the Reed-Solomon remainder before comparing the recovered payload.</p>
 */
final class MicroQrReferenceTest {

    private static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

    private MicroQrReferenceTest() {
    }

    static void run(TestHarness harness) throws Exception {
        MicroQrCode m1 = fixed(1).encode("123", QrErrorCorrection.LOW);
        harness.checkEquals("micro", "M1 uses the compact numeric matrix", 11, m1.getSize());
        harness.checkEquals("micro", "M1 has detection-only error correction", null,
                m1.getErrorCorrection());
        harness.checkEquals("micro", "M1 reference decoder recovers the payload", "123", decode(m1));

        MicroQrCode m2 = fixed(2).encode("HELLO", QrErrorCorrection.MEDIUM);
        harness.checkEquals("micro", "M2 reference decoder recovers alphanumeric data", "HELLO",
                decode(m2));

        MicroQrCode m3 = fixed(3).encode("abc-123", QrErrorCorrection.LOW);
        harness.checkEquals("micro", "M3 reference decoder recovers byte data", "abc-123", decode(m3));

        MicroQrCode m4 = fixed(4).encode("\u6f22\u5b57", QrErrorCorrection.LOW);
        harness.checkEquals("micro", "M4 reference decoder recovers Kanji data", "\u6f22\u5b57",
                decode(m4));

        int masksDecoded = 0;
        for (int mask = 0; mask < 4; mask++) {
            MicroQrEncoderBean masked = fixed(2);
            masked.setForcedMaskPattern(mask);
            if ("HELLO".equals(decode(masked.encode("HELLO", QrErrorCorrection.LOW)))) {
                masksDecoded++;
            }
        }
        harness.check("micro", "all four Micro mask patterns pass the reference decoder",
                masksDecoded == 4, masksDecoded + "/4 masks decoded");

        harness.expectFailure("micro", "HIGH error correction is rejected",
                () -> fixed(1).encode("123", QrErrorCorrection.HIGH));
        MicroQrEncoderBean tooSmall = fixed(1);
        harness.expectFailure("micro", "a non-numeric payload is rejected by M1",
                () -> tooSmall.encode("ABC", QrErrorCorrection.LOW));
    }

    private static MicroQrEncoderBean fixed(int version) {
        MicroQrEncoderBean encoder = new MicroQrEncoderBean();
        encoder.setMinimumVersion(version);
        encoder.setMaximumVersion(version);
        return encoder;
    }

    private static String decode(MicroQrCode symbol) throws Exception {
        int version = symbol.getVersion();
        int size = symbol.getSize();
        boolean[][] function = functionModules(version);
        int dataBits = MicroQrTables.dataCapacityBits(version, symbol.getErrorCorrection());
        int dataCodewords = MicroQrTables.dataCodewords(version, symbol.getErrorCorrection());
        int eccCodewords = MicroQrTables.eccCodewords(version, symbol.getErrorCorrection());
        int messageBits = (version == 1 || version == 3)
                ? (dataCodewords * 8 - 4 + eccCodewords * 8)
                : (dataCodewords * 8 + eccCodewords * 8);
        BitReader bits = new BitReader(readMessage(symbol, function, messageBits));
        byte[] data = new byte[dataCodewords];
        for (int i = 0; i < dataBits; i++) {
            if (bits.read(1) != 0) {
                data[i / 8] |= 1 << (7 - i % 8);
            }
        }
        byte[] ecc = new byte[eccCodewords];
        for (int i = 0; i < ecc.length * 8; i++) {
            if (bits.read(1) != 0) {
                ecc[i / 8] |= 1 << (7 - i % 8);
            }
        }
        if (!Arrays.equals(ecc, computeRemainder(data, ecc.length))) {
            throw new IllegalArgumentException("Micro QR Reed-Solomon remainder mismatch");
        }
        BitReader payload = new BitReader(data, dataBits);
        StringBuilder result = new StringBuilder();
        while (payload.remaining() > 0) {
            if (payload.remaining() <= version * 2 + 1) {
                break;
            }
            QrEncodingMode mode;
            if (version == 1) {
                mode = QrEncodingMode.NUMERIC;
            } else {
                int indicator = payload.read(version - 1);
                mode = indicator == 0 ? QrEncodingMode.NUMERIC
                        : indicator == 1 ? QrEncodingMode.ALPHANUMERIC
                        : indicator == 2 ? QrEncodingMode.BYTE : QrEncodingMode.KANJI;
            }
            int count = payload.read(MicroQrTables.characterCountBits(version, mode));
            if (count == 0) {
                break;
            }
            if (mode == QrEncodingMode.NUMERIC) {
                readNumeric(payload, count, result);
            } else if (mode == QrEncodingMode.ALPHANUMERIC) {
                readAlphanumeric(payload, count, result);
            } else if (mode == QrEncodingMode.BYTE) {
                byte[] bytes = new byte[count];
                for (int i = 0; i < count; i++) {
                    bytes[i] = (byte) payload.read(8);
                }
                result.append(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1));
            } else {
                readKanji(payload, count, result);
            }
        }
        return result.toString();
    }

    private static byte[] readMessage(MicroQrCode symbol, boolean[][] function, int bits) {
        int size = symbol.getSize();
        byte[] result = new byte[(bits + 7) / 8];
        int bit = 0;
        int directionOffset = symbol.getVersion() == 1 || symbol.getVersion() == 3 ? 2 : 0;
        for (int right = size - 1; right > 0; right -= 2) {
            for (int vertical = 0; vertical < size; vertical++) {
                for (int offset = 0; offset < 2; offset++) {
                    int x = right - offset;
                    boolean upward = ((right + directionOffset) & 2) == 0;
                    int y = upward ? size - 1 - vertical : vertical;
                    if (!function[y][x] && bit < bits) {
                        boolean value = symbol.isDark(x, y) ^ maskBit(symbol.getMask(), x, y);
                        if (value) {
                            result[bit / 8] |= 1 << (7 - bit % 8);
                        }
                        bit++;
                    }
                }
            }
        }
        if (bit != bits) {
            throw new IllegalArgumentException("Reference decoder read " + bit + " of " + bits + " bits");
        }
        return result;
    }

    private static boolean[][] functionModules(int version) {
        int size = 9 + version * 2;
        boolean[][] result = new boolean[size][size];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                result[y][x] = true;
            }
        }
        for (int i = 8; i < size; i++) {
            result[0][i] = true;
            result[i][0] = true;
        }
        for (int i = 1; i <= 8; i++) {
            result[i][8] = true;
            result[8][i] = true;
        }
        return result;
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

    private static void readNumeric(BitReader bits, int count, StringBuilder result) {
        int remaining = count;
        while (remaining >= 3) {
            result.append(String.format("%03d", bits.read(10)));
            remaining -= 3;
        }
        if (remaining == 2) {
            result.append(String.format("%02d", bits.read(7)));
        } else if (remaining == 1) {
            result.append(bits.read(4));
        }
    }

    private static void readAlphanumeric(BitReader bits, int count, StringBuilder result) {
        int remaining = count;
        while (remaining >= 2) {
            int value = bits.read(11);
            result.append(ALPHANUMERIC.charAt(value / 45));
            result.append(ALPHANUMERIC.charAt(value % 45));
            remaining -= 2;
        }
        if (remaining == 1) {
            result.append(ALPHANUMERIC.charAt(bits.read(6)));
        }
    }

    private static void readKanji(BitReader bits, int count, StringBuilder result) {
        byte[] bytes = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            int value = bits.read(13);
            int intermediate = (value / 0xC0 << 8) | (value % 0xC0);
            int adjusted = intermediate < 0x1F00 ? intermediate + 0x8140 : intermediate + 0xC140;
            bytes[i * 2] = (byte) (adjusted >>> 8);
            bytes[i * 2 + 1] = (byte) adjusted;
        }
        result.append(new String(bytes, SHIFT_JIS));
    }

    /** Small independent GF(256) remainder used to check the encoder's correction bytes. */
    private static byte[] computeRemainder(byte[] data, int degree) {
        int[] divisor = new int[degree];
        divisor[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < divisor.length; j++) {
                divisor[j] = multiply(divisor[j], root);
                if (j + 1 < divisor.length) {
                    divisor[j] ^= divisor[j + 1];
                }
            }
            root = multiply(root, 2);
        }
        int[] remainder = new int[degree];
        for (byte value : data) {
            int factor = (value & 0xFF) ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, degree - 1);
            remainder[degree - 1] = 0;
            for (int i = 0; i < degree; i++) {
                remainder[i] ^= multiply(divisor[i], factor);
            }
        }
        byte[] result = new byte[degree];
        for (int i = 0; i < degree; i++) {
            result[i] = (byte) remainder[i];
        }
        return result;
    }

    private static int multiply(int left, int right) {
        int result = 0;
        while (right != 0) {
            if ((right & 1) != 0) {
                result ^= left;
            }
            left = (left << 1) ^ ((left >>> 7) * 0x11D);
            right >>>= 1;
        }
        return result;
    }

    private static final class BitReader {
        private final byte[] bytes;
        private final int limit;
        private int position;

        BitReader(byte[] bytes) {
            this(bytes, bytes.length * 8);
        }

        BitReader(byte[] bytes, int limit) {
            this.bytes = bytes;
            this.limit = limit;
        }

        int read(int count) {
            if (count < 0 || position + count > limit) {
                throw new IllegalArgumentException("Reference decoder ran out of bits");
            }
            int value = 0;
            for (int i = 0; i < count; i++) {
                value = value << 1 | ((bytes[position / 8] >>> (7 - position % 8)) & 1);
                position++;
            }
            return value;
        }

        int remaining() {
            return limit - position;
        }
    }
}
