package ke.ku.sco310.qr.api;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable data contract for a Micro QR symbol (M1-M4).
 *
 * <p>Micro QR has a different version, format-information and placement model from a standard
 * QR Code, so it deliberately does not overload {@link QrCode}. The M1 symbol has error-detection
 * codewords but no selectable error-correction level; consequently {@link #getErrorCorrection()}
 * returns {@code null} for M1.</p>
 */
public final class MicroQrCode implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String content;
    private final QrErrorCorrection requestedErrorCorrection;
    private final QrErrorCorrection errorCorrection;
    private final QrEncodingMode encodingMode;
    private final int characterCount;
    private final int version;
    private final int mask;
    private final int dataCodewordCount;
    private final int eccCodewordCount;
    private final boolean[][] modules;
    private final String producerId;

    public MicroQrCode(String content, QrErrorCorrection requestedErrorCorrection,
                       QrErrorCorrection errorCorrection, QrEncodingMode encodingMode,
                       int characterCount, int version, int mask, int dataCodewordCount,
                       int eccCodewordCount, boolean[][] modules, String producerId) {
        this.content = content;
        this.requestedErrorCorrection = requestedErrorCorrection;
        this.errorCorrection = errorCorrection;
        this.encodingMode = encodingMode;
        this.characterCount = characterCount;
        this.version = version;
        this.mask = mask;
        this.dataCodewordCount = dataCodewordCount;
        this.eccCodewordCount = eccCodewordCount;
        this.modules = copy(modules);
        this.producerId = producerId;
    }

    public String getContent() {
        return content;
    }

    public QrErrorCorrection getRequestedErrorCorrection() {
        return requestedErrorCorrection;
    }

    public QrErrorCorrection getErrorCorrection() {
        return errorCorrection;
    }

    public QrEncodingMode getEncodingMode() {
        return encodingMode;
    }

    public int getCharacterCount() {
        return characterCount;
    }

    public int getVersion() {
        return version;
    }

    public String getVersionName() {
        return "M" + version;
    }

    public int getSize() {
        return 9 + version * 2;
    }

    public int getMask() {
        return mask;
    }

    public int getDataCodewordCount() {
        return dataCodewordCount;
    }

    public int getEccCodewordCount() {
        return eccCodewordCount;
    }

    public String getProducerId() {
        return producerId;
    }

    public boolean isDark(int x, int y) {
        if (x < 0 || y < 0 || x >= getSize() || y >= getSize()) {
            throw new IndexOutOfBoundsException("Module (" + x + "," + y + ") outside "
                    + getSize() + "x" + getSize());
        }
        return modules[y][x];
    }

    public boolean[][] getModules() {
        return copy(modules);
    }

    private static boolean[][] copy(boolean[][] source) {
        boolean[][] result = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = Arrays.copyOf(source[i], source[i].length);
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MicroQrCode)) {
            return false;
        }
        MicroQrCode that = (MicroQrCode) other;
        return version == that.version && mask == that.mask
                && Objects.equals(content, that.content)
                && errorCorrection == that.errorCorrection
                && encodingMode == that.encodingMode
                && Arrays.deepEquals(modules, that.modules);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(content, errorCorrection, encodingMode, version, mask)
                + Arrays.deepHashCode(modules);
    }

    @Override
    public String toString() {
        return "MicroQrCode{version=M" + version + ", size=" + getSize() + ", ec="
                + (errorCorrection == null ? "detection-only" : errorCorrection.getLabel())
                + ", mode=" + encodingMode + ", mask=" + mask + ", chars=" + characterCount
                + ", producer='" + producerId + "'}";
    }
}
