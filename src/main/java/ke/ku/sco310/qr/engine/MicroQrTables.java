package ke.ku.sco310.qr.engine;

import ke.ku.sco310.qr.api.QrEncodingMode;
import ke.ku.sco310.qr.api.QrErrorCorrection;

/** ISO/IEC 18004:2015 Tables 2, 3, 7 and 9 for the separate Micro QR M1-M4 path. */
public final class MicroQrTables {

    private static final int[][] DATA_BITS = {{}, {20}, {40, 32}, {84, 68}, {128, 112, 80}};
    private static final int[][] ECC_CODEWORDS = {{}, {2}, {5, 6}, {6, 8}, {8, 10, 14}};

    private MicroQrTables() {
    }

    /**
     * @param version Micro version 1..4
     * @param level L/M for M2-M3, L/M/Q for M4; null means M1 error detection only
     * @return usable data bits, including the final four-bit codeword of M1/M3
     * @throws IllegalArgumentException for an unsupported version/level pair
     */
    public static int dataCapacityBits(int version, QrErrorCorrection level) {
        return DATA_BITS[versionIndex(version)][levelIndex(version, level)];
    }

    public static int dataCodewords(int version, QrErrorCorrection level) {
        return (dataCapacityBits(version, level) + 7) / 8;
    }

    public static int eccCodewords(int version, QrErrorCorrection level) {
        return ECC_CODEWORDS[versionIndex(version)][levelIndex(version, level)];
    }

    public static int formatType(int version, QrErrorCorrection level) {
        int index = levelIndex(version, level);
        return version == 1 ? 0 : version == 2 ? 1 + index : version == 3 ? 3 + index : 5 + index;
    }

    /** @return count-field width, or -1 if the mode is unavailable in this Micro version. */
    public static int characterCountBits(int version, QrEncodingMode mode) {
        versionIndex(version);
        switch (mode) {
            case NUMERIC: return version + 2;
            case ALPHANUMERIC: return version >= 2 ? version + 1 : -1;
            case BYTE: return version >= 3 ? version + 1 : -1;
            case KANJI: return version >= 3 ? version : -1;
            default: return -1;
        }
    }

    public static int modeIndicator(QrEncodingMode mode) {
        switch (mode) {
            case NUMERIC: return 0;
            case ALPHANUMERIC: return 1;
            case BYTE: return 2;
            case KANJI: return 3;
            default: throw new IllegalArgumentException("Micro QR does not support " + mode);
        }
    }

    private static int versionIndex(int version) {
        if (version < 1 || version > 4) {
            throw new IllegalArgumentException("Micro version must be 1..4");
        }
        return version;
    }

    private static int levelIndex(int version, QrErrorCorrection level) {
        versionIndex(version);
        if (version == 1 && level == null) {
            return 0;
        }
        if (version > 1 && level != null && level != QrErrorCorrection.HIGH
                && (version == 4 || level != QrErrorCorrection.QUARTILE)) {
            return level.ordinal();
        }
        throw new IllegalArgumentException("Unsupported Micro QR version/level: M" + version + "/" + level);
    }
}
