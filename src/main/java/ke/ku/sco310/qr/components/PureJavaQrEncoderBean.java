package ke.ku.sco310.qr.components;

import ke.ku.sco310.qr.api.BoundPropertySource;
import ke.ku.sco310.qr.api.EncoderInfo;
import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrCodeEncoder;
import ke.ku.sco310.qr.api.QrEncodingException;
import ke.ku.sco310.qr.api.QrEncodingMode;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.engine.QrEngine;
import ke.ku.sco310.qr.engine.QrTables;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.io.Serializable;
import java.util.Arrays;

/**
 * In-house encoder component: a JavaBean wrapping the pure-Java engine of this project.
 *
 * <p>Property design demonstrates all four property kinds required by the JavaBeans
 * component model (chapter 3):</p>
 * <ul>
 *   <li><b>simple</b> - {@code maximumVersion}, {@code forcedMaskPattern};</li>
 *   <li><b>bound</b> - {@code defaultErrorCorrection} and {@code boostErrorCorrection} notify
 *       interested listeners whenever they change;</li>
 *   <li><b>constrained</b> - {@code minimumVersion} may be vetoed by a listener, and the bean
 *       itself vetoes a change that would make the version window inconsistent;</li>
 *   <li><b>indexed</b> - not applicable to this component, so the indexed demonstration lives in
 *       {@link ke.ku.sco310.qr.store.QrHistoryBean}.</li>
 * </ul>
 */
public class PureJavaQrEncoderBean implements QrCodeEncoder, Serializable, BoundPropertySource {

    private static final long serialVersionUID = 1L;

    /** Component instance identifier (also recorded inside every symbol this bean produces). */
    public static final String COMPONENT_ID = "encoder.pure-java.v1";

    private transient PropertyChangeSupport propertyChanges = new PropertyChangeSupport(this);
    private transient VetoableChangeSupport vetoableChanges = new VetoableChangeSupport(this);

    private QrErrorCorrection defaultErrorCorrection = QrErrorCorrection.MEDIUM;
    private boolean boostErrorCorrection = false;
    private int minimumVersion = 1;
    private int maximumVersion = 40;
    private int forcedMaskPattern = -1;

    /** Mandatory JavaBeans no-argument constructor. */
    public PureJavaQrEncoderBean() {
    }

    // ------------------------------------------------------------------ provided interface

    @Override
    public QrCode encode(String content, QrErrorCorrection level) throws QrEncodingException {
        QrErrorCorrection effective = level == null ? defaultErrorCorrection : level;
        return QrEngine.encode(content, effective, boostErrorCorrection, minimumVersion,
                maximumVersion, forcedMaskPattern, COMPONENT_ID);
    }

    /** Convenience overload used by the GUI and HTTP assemblies: uses the default level property. */
    public QrCode encode(String content) throws QrEncodingException {
        return encode(content, defaultErrorCorrection);
    }

    @Override
    public EncoderInfo getEncoderInfo() {
        EncoderInfo info = new EncoderInfo();
        info.setComponentId(COMPONENT_ID);
        info.setProviderName("SCO310 in-house QR engine");
        info.setProviderVersion("1.0.0");
        info.setProviderLicence("Student project - course work");
        info.setEngineDescription("Pure-Java ISO/IEC 18004 encoder: numeric, alphanumeric, byte and "
                + "kanji modes with a mixed-mode dynamic-programming segmenter, UTF-8 ECI designators, "
                + "Reed-Solomon over GF(2^8)/0x11D, all eight mask patterns scored with the four-part "
                + "penalty function, versions 1-40.");
        info.setMinimumVersion(1);
        info.setMaximumVersion(40);
        info.setMaximumByteCapacity(QrTables.maximumByteCapacity(QrErrorCorrection.LOW));
        info.setThirdParty(false);
        info.setRequiresOptionalLibrary(false);
        info.setSupportedModes(Arrays.asList(QrEncodingMode.NUMERIC, QrEncodingMode.ALPHANUMERIC,
                QrEncodingMode.BYTE, QrEncodingMode.KANJI, QrEncodingMode.ECI));
        return info;
    }

    @Override
    public String getComponentId() {
        return COMPONENT_ID;
    }

    // ------------------------------------------------------------------ properties

    public QrErrorCorrection getDefaultErrorCorrection() {
        return defaultErrorCorrection;
    }

    /** Bound property: changing it fires a {@code PropertyChangeEvent("defaultErrorCorrection")}. */
    public void setDefaultErrorCorrection(QrErrorCorrection defaultErrorCorrection) {
        if (defaultErrorCorrection == null) {
            throw new IllegalArgumentException("defaultErrorCorrection must not be null");
        }
        QrErrorCorrection old = this.defaultErrorCorrection;
        this.defaultErrorCorrection = defaultErrorCorrection;
        propertyChanges.firePropertyChange("defaultErrorCorrection", old, defaultErrorCorrection);
    }

    public boolean isBoostErrorCorrection() {
        return boostErrorCorrection;
    }

    /** Bound property: when enabled the encoder climbs to the strongest level that still fits. */
    public void setBoostErrorCorrection(boolean boostErrorCorrection) {
        boolean old = this.boostErrorCorrection;
        this.boostErrorCorrection = boostErrorCorrection;
        propertyChanges.firePropertyChange("boostErrorCorrection", old, boostErrorCorrection);
    }

    public int getMinimumVersion() {
        return minimumVersion;
    }

    /**
     * Constrained property: a registered {@link VetoableChangeListener} may reject the new value,
     * and this bean vetoes inconsistent windows itself.
     *
     * @param minimumVersion lowest version the encoder may select (1..40)
     * @throws java.beans.PropertyVetoException when a listener vetoes the change
     */
    public void setMinimumVersion(int minimumVersion) throws java.beans.PropertyVetoException {
        if (minimumVersion < 1 || minimumVersion > 40) {
            throw new IllegalArgumentException("minimumVersion must be 1..40");
        }
        if (minimumVersion > maximumVersion) {
            throw new java.beans.PropertyVetoException(
                    "minimumVersion " + minimumVersion + " would exceed maximumVersion " + maximumVersion, null);
        }
        int old = this.minimumVersion;
        vetoableChanges.fireVetoableChange("minimumVersion", old, minimumVersion);
        this.minimumVersion = minimumVersion;
        propertyChanges.firePropertyChange("minimumVersion", old, minimumVersion);
    }

    public int getMaximumVersion() {
        return maximumVersion;
    }

    public void setMaximumVersion(int maximumVersion) {
        if (maximumVersion < 1 || maximumVersion > 40) {
            throw new IllegalArgumentException("maximumVersion must be 1..40");
        }
        if (maximumVersion < minimumVersion) {
            maximumVersion = minimumVersion;
        }
        int old = this.maximumVersion;
        this.maximumVersion = maximumVersion;
        propertyChanges.firePropertyChange("maximumVersion", old, maximumVersion);
    }

    public int getForcedMaskPattern() {
        return forcedMaskPattern;
    }

    /** Simple property: {@code -1} keeps the automatic penalty-based mask selection. */
    public void setForcedMaskPattern(int forcedMaskPattern) {
        if (forcedMaskPattern < -1 || forcedMaskPattern > 7) {
            throw new IllegalArgumentException("forcedMaskPattern must be -1 or 0..7");
        }
        int old = this.forcedMaskPattern;
        this.forcedMaskPattern = forcedMaskPattern;
        propertyChanges.firePropertyChange("forcedMaskPattern", old, forcedMaskPattern);
    }

    // ------------------------------------------------------------------ bound property plumbing

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.removePropertyChangeListener(listener);
    }

    public void addVetoableChangeListener(VetoableChangeListener listener) {
        vetoableChanges.addVetoableChangeListener(listener);
    }

    public void removeVetoableChangeListener(VetoableChangeListener listener) {
        vetoableChanges.removeVetoableChangeListener(listener);
    }

    /** Restores the transient listener lists after deserialization. */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        propertyChanges = new PropertyChangeSupport(this);
        vetoableChanges = new VetoableChangeSupport(this);
    }

    @Override
    public String toString() {
        return "PureJavaQrEncoderBean{defaultEc=" + defaultErrorCorrection.getLabel()
                + ", versions=" + minimumVersion + ".." + maximumVersion
                + ", boost=" + boostErrorCorrection + ", forcedMask=" + forcedMaskPattern + "}";
    }
}
