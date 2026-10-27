package ke.ku.sco310.qr.components;

import ke.ku.sco310.qr.api.BoundPropertySource;
import ke.ku.sco310.qr.api.MicroQrCode;
import ke.ku.sco310.qr.api.QrEncodingException;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.api.QrMicroEncoder;
import ke.ku.sco310.qr.engine.MicroQrEngine;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;

/** Serializable JavaBean exposing the suite's separate Micro QR M1-M4 encoder. */
public class MicroQrEncoderBean implements QrMicroEncoder, BoundPropertySource {

    private static final long serialVersionUID = 1L;
    public static final String COMPONENT_ID = "encoder.micro-qr.v1";

    private transient PropertyChangeSupport propertyChanges = new PropertyChangeSupport(this);
    private QrErrorCorrection defaultErrorCorrection = QrErrorCorrection.LOW;
    private boolean boostErrorCorrection;
    private int minimumVersion = 1;
    private int maximumVersion = 4;
    private int forcedMaskPattern = -1;

    public MicroQrEncoderBean() {
    }

    @Override
    public MicroQrCode encode(String content, QrErrorCorrection level) throws QrEncodingException {
        return MicroQrEngine.encode(content, level == null ? defaultErrorCorrection : level,
                boostErrorCorrection, minimumVersion, maximumVersion, forcedMaskPattern, COMPONENT_ID);
    }

    public MicroQrCode encode(String content) throws QrEncodingException {
        return encode(content, defaultErrorCorrection);
    }

    @Override
    public String getComponentId() {
        return COMPONENT_ID;
    }

    public QrErrorCorrection getDefaultErrorCorrection() {
        return defaultErrorCorrection;
    }

    public void setDefaultErrorCorrection(QrErrorCorrection value) {
        if (value == null || value == QrErrorCorrection.HIGH) {
            throw new IllegalArgumentException("Micro QR default level must be L, M or Q");
        }
        QrErrorCorrection old = defaultErrorCorrection;
        defaultErrorCorrection = value;
        propertyChanges.firePropertyChange("defaultErrorCorrection", old, value);
    }

    public boolean isBoostErrorCorrection() {
        return boostErrorCorrection;
    }

    public void setBoostErrorCorrection(boolean value) {
        boolean old = boostErrorCorrection;
        boostErrorCorrection = value;
        propertyChanges.firePropertyChange("boostErrorCorrection", old, value);
    }

    public int getMinimumVersion() {
        return minimumVersion;
    }

    public void setMinimumVersion(int value) {
        if (value < 1 || value > maximumVersion) {
            throw new IllegalArgumentException("minimumVersion must be 1.." + maximumVersion);
        }
        int old = minimumVersion;
        minimumVersion = value;
        propertyChanges.firePropertyChange("minimumVersion", old, value);
    }

    public int getMaximumVersion() {
        return maximumVersion;
    }

    public void setMaximumVersion(int value) {
        if (value < minimumVersion || value > 4) {
            throw new IllegalArgumentException("maximumVersion must be " + minimumVersion + "..4");
        }
        int old = maximumVersion;
        maximumVersion = value;
        propertyChanges.firePropertyChange("maximumVersion", old, value);
    }

    public int getForcedMaskPattern() {
        return forcedMaskPattern;
    }

    public void setForcedMaskPattern(int value) {
        if (value < -1 || value > 3) {
            throw new IllegalArgumentException("forcedMaskPattern must be -1 or 0..3");
        }
        int old = forcedMaskPattern;
        forcedMaskPattern = value;
        propertyChanges.firePropertyChange("forcedMaskPattern", old, value);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.removePropertyChangeListener(listener);
    }

    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        propertyChanges = new PropertyChangeSupport(this);
    }
}
