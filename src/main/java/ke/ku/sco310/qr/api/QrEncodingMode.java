package ke.ku.sco310.qr.api;

/**
 * QR Code data-encoding modes supported by this component suite (ISO/IEC 18004, 8.4).
 *
 * <p>Each mode carries the two facts an implementation needs: the 4-bit mode indicator
 * written in the data stream and the width (in bits) of the character-count field for
 * each of the three version ranges (1-9, 10-26, 27-40).</p>
 */
public enum QrEncodingMode {

    /** Digits 0-9 only: 10 bits per 3 characters. */
    NUMERIC(0x1, new int[]{10, 12, 14}),
    /** 45-character alphabet: 11 bits per 2 characters. */
    ALPHANUMERIC(0x2, new int[]{9, 11, 13}),
    /** Arbitrary 8-bit data: 8 bits per byte. */
    BYTE(0x4, new int[]{8, 16, 16}),
    /** Kanji mode: 13 bits per Shift_JIS double-byte character (ISO/IEC 18004, 8.4.5). */
    KANJI(0x8, new int[]{8, 10, 12}),
    /** ECI designator: flags a character set for the following byte segments (ISO/IEC 18004, 7.4.9). */
    ECI(0x7, new int[]{0, 0, 0});

    private final int modeIndicator;
    private final int[] charCountBits; // index 0 => versions 1-9, 1 => 10-26, 2 => 27-40

    QrEncodingMode(int modeIndicator, int[] charCountBits) {
        this.modeIndicator = modeIndicator;
        this.charCountBits = charCountBits;
    }

    /** @return the 4-bit mode indicator placed before the character-count field. */
    public int getModeIndicator() {
        return modeIndicator;
    }

    /**
     * @param version symbol version in the range 1..40
     * @return width in bits of the character-count field for this mode and version
     */
    public int getCharacterCountBits(int version) {
        if (version < 1 || version > 40) {
            throw new IllegalArgumentException("Version out of range: " + version);
        }
        int index = version <= 9 ? 0 : (version <= 26 ? 1 : 2);
        return charCountBits[index];
    }

    @Override
    public String toString() {
        return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
