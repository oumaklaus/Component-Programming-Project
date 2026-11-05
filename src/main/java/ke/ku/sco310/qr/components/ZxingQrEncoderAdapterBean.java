package ke.ku.sco310.qr.components;

import ke.ku.sco310.qr.api.BoundPropertySource;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Mode;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import ke.ku.sco310.qr.api.EncoderInfo;
import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrCodeEncoder;
import ke.ku.sco310.qr.api.QrEncodingException;
import ke.ku.sco310.qr.api.QrEncodingMode;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.engine.QrTables;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/** Adapter component that exposes the third-party ZXing engine through this suite's own QrCodeEncoder interface. */
public class ZxingQrEncoderAdapterBean implements QrCodeEncoder, Serializable, BoundPropertySource {

    private static final long serialVersionUID = 1L;

    /** Component instance identifier. */
    public static final String COMPONENT_ID = "encoder.zxing-adapter.v1";

    private transient PropertyChangeSupport propertyChanges = new PropertyChangeSupport(this);

    private String characterSet = "UTF-8";
    private QrErrorCorrection defaultErrorCorrection = QrErrorCorrection.MEDIUM;
    private boolean boostErrorCorrection = false;

    public ZxingQrEncoderAdapterBean() {
    }

    @Override
    public QrCode encode(String content, QrErrorCorrection level) throws QrEncodingException {
        QrErrorCorrection requested = level == null ? defaultErrorCorrection : level;
        ErrorCorrectionLevel zxingLevel = mapLevel(requested);
        try {
            if (boostErrorCorrection) {
                zxingLevel = ErrorCorrectionLevel.H;
            }
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, characterSet);
            QRCode encoded = Encoder.encode(content, zxingLevel, hints);
            return convert(encoded, content, requested);
        } catch (WriterException e) {
            throw new QrEncodingException(COMPONENT_ID,
                    "The ZXing engine refused the payload: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new QrEncodingException(COMPONENT_ID,
                    "Unexpected failure inside the ZXing engine: " + e, e);
        }
    }

    /** Translates the third-party result structure into this suite's value object. */
    private QrCode convert(QRCode encoded, String content, QrErrorCorrection requested) {
        ByteMatrix matrix = encoded.getMatrix();
        int size = matrix.getWidth();
        boolean[][] modules = new boolean[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                modules[y][x] = matrix.get(x, y) == 1;
            }
        }
        int version = encoded.getVersion().getVersionNumber();
        QrErrorCorrection actual = mapLevel(encoded.getECLevel());
        QrEncodingMode mode = mapMode(encoded.getMode());
        // Codeword counts are derived from the shared ISO/IEC 18004 tables (version and level
        // reported by ZXing), because ZXing 3.5.3 no longer exposes them on its result object.
        int dataCodewords = QrTables.numDataCodewords(version, actual);
        int eccCodewords = QrTables.totalCodewords(version) - dataCodewords;
        return new QrCode(content, requested, actual, mode, content.length(), version,
                encoded.getMaskPattern(), dataCodewords, eccCodewords, modules, COMPONENT_ID);
    }

    private static ErrorCorrectionLevel mapLevel(QrErrorCorrection level) {
        switch (level) {
            case LOW: return ErrorCorrectionLevel.L;
            case MEDIUM: return ErrorCorrectionLevel.M;
            case QUARTILE: return ErrorCorrectionLevel.Q;
            case HIGH: return ErrorCorrectionLevel.H;
            default: throw new IllegalArgumentException("Unmapped level: " + level);
        }
    }

    private static QrErrorCorrection mapLevel(ErrorCorrectionLevel level) {
        if (level == null) {
            return QrErrorCorrection.MEDIUM;
        }
        switch (level) {
            case L: return QrErrorCorrection.LOW;
            case M: return QrErrorCorrection.MEDIUM;
            case Q: return QrErrorCorrection.QUARTILE;
            case H: return QrErrorCorrection.HIGH;
            default: return QrErrorCorrection.MEDIUM;
        }
    }

    private static QrEncodingMode mapMode(Mode mode) {
        if (mode == Mode.NUMERIC) {
            return QrEncodingMode.NUMERIC;
        }
        if (mode == Mode.ALPHANUMERIC) {
            return QrEncodingMode.ALPHANUMERIC;
        }
        return QrEncodingMode.BYTE;
    }

    @Override
    public EncoderInfo getEncoderInfo() {
        EncoderInfo info = new EncoderInfo();
        info.setComponentId(COMPONENT_ID);
        info.setProviderName("ZXing (\"Zebra Crossing\") core");
        info.setProviderVersion("3.5.3");
        info.setProviderLicence("Apache License 2.0");
        info.setEngineDescription("Adapter around the third-party ZXing encoder; the foreign "
                + "ByteMatrix result is translated into this suite's QrCode value object.");
        info.setMinimumVersion(1);
        info.setMaximumVersion(40);
        info.setMaximumByteCapacity(2953);
        info.setThirdParty(true);
        info.setRequiresOptionalLibrary(true);
        info.setSupportedModes(Arrays.asList(QrEncodingMode.NUMERIC, QrEncodingMode.ALPHANUMERIC,
                QrEncodingMode.BYTE));
        return info;
    }

    @Override
    public String getComponentId() {
        return COMPONENT_ID;
    }

    public String getCharacterSet() {
        return characterSet;
    }

    /** Bound property: selects the character set handed to the third-party engine. */
    public void setCharacterSet(String characterSet) {
        String old = this.characterSet;
        this.characterSet = characterSet;
        propertyChanges.firePropertyChange("characterSet", old, characterSet);
    }

    public QrErrorCorrection getDefaultErrorCorrection() {
        return defaultErrorCorrection;
    }

    public void setDefaultErrorCorrection(QrErrorCorrection defaultErrorCorrection) {
        QrErrorCorrection old = this.defaultErrorCorrection;
        this.defaultErrorCorrection = defaultErrorCorrection;
        propertyChanges.firePropertyChange("defaultErrorCorrection", old, defaultErrorCorrection);
    }

    public boolean isBoostErrorCorrection() {
        return boostErrorCorrection;
    }

    public void setBoostErrorCorrection(boolean boostErrorCorrection) {
        this.boostErrorCorrection = boostErrorCorrection;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.removePropertyChangeListener(listener);
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        propertyChanges = new PropertyChangeSupport(this);
    }

    @Override
    public String toString() {
        return "ZxingQrEncoderAdapterBean{charset=" + characterSet
                + ", defaultEc=" + defaultErrorCorrection.getLabel() + "}";
    }
}
