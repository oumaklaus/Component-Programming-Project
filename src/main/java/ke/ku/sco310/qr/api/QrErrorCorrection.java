package ke.ku.sco310.qr.api;

/**
 * Error-correction level of a QR Code symbol (ISO/IEC 18004, Table 12).
 *
 * <p>The constant declaration order is significant: it matches the row order of the
 * error-correction block tables used by the encoder
 * ({@code ECC_CODEWORDS_PER_BLOCK[ecl.ordinal()][version]}).</p>
 *
 * <p>This type is part of the <em>provided interface</em> of every encoder component in the
 * assembly, so it is deliberately independent of any encoder implementation.</p>
 */
public enum QrErrorCorrection {

    /** Level L - recovers nominally 7% of the codewords. Format bits 0b01. */
    LOW("L", 1, 7),
    /** Level M - recovers nominally 15% of the codewords. Format bits 0b00. */
    MEDIUM("M", 0, 15),
    /** Level Q - recovers nominally 25% of the codewords. Format bits 0b11. */
    QUARTILE("Q", 3, 25),
    /** Level H - recovers nominally 30% of the codewords. Format bits 0b10. */
    HIGH("H", 2, 30);

    private final String label;
    private final int formatBits;
    private final int nominalRecoveryPercent;

    QrErrorCorrection(String label, int formatBits, int nominalRecoveryPercent) {
        this.label = label;
        this.formatBits = formatBits;
        this.nominalRecoveryPercent = nominalRecoveryPercent;
    }

    /** @return single-letter label used by the QR specification. */
    public String getLabel() {
        return label;
    }

    /** @return the 2-bit value written into the 15-bit format information area. */
    public int getFormatBits() {
        return formatBits;
    }

    /** @return nominal percentage of codewords that can be restored. */
    public int getNominalRecoveryPercent() {
        return nominalRecoveryPercent;
    }

    /**
     * Parses a level from a user supplied label.
     *
     * @param text one of {@code L, M, Q, H} (case insensitive) or {@code low, medium, quartile, high}
     * @return the matching level
     * @throws IllegalArgumentException if the label is not recognised
     */
    public static QrErrorCorrection fromLabel(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Error-correction level label must not be null");
        }
        String t = text.trim().toUpperCase(java.util.Locale.ROOT);
        for (QrErrorCorrection level : values()) {
            if (level.label.equals(t) || level.name().equals(t)) {
                return level;
            }
        }
        if (t.startsWith("LOW")) return LOW;
        if (t.startsWith("MED")) return MEDIUM;
        if (t.startsWith("Q") || t.startsWith("QUART")) return QUARTILE;
        if (t.startsWith("H") || t.startsWith("HIGH")) return HIGH;
        throw new IllegalArgumentException(
                "Unknown error-correction level '" + text + "'. Expected one of L, M, Q, H");
    }

    @Override
    public String toString() {
        return label + " (" + nominalRecoveryPercent + "% recovery)";
    }
}
