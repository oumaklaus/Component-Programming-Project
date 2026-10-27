package ke.ku.sco310.qr.render;

import ke.ku.sco310.qr.api.BoundPropertySource;
import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrCodeRenderer;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.api.QrRenderingException;
import ke.ku.sco310.qr.api.RenderedArtifact;
import ke.ku.sco310.qr.editor.HexColorPropertyEditor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * PNG renderer that draws modules as rounded squares and can place a centre logo.
 *
 * <p>The logo policy is deliberately conservative: its square coverage is compared with the
 * nominal recovery percentage of the selected error-correction level. The default behaviour
 * refuses an unsafe logo; setting {@link #setWarnOnly(boolean)} to {@code true} permits it and
 * publishes the warning through {@link #getLastWarning()}.</p>
 */
public class RoundedModuleRendererBean implements QrCodeRenderer, Serializable, BoundPropertySource {

    private static final long serialVersionUID = 1L;

    /** Component instance identifier. */
    public static final String COMPONENT_ID = "renderer.rounded-png.v1";

    private transient PropertyChangeSupport propertyChanges = new PropertyChangeSupport(this);
    private int moduleSize = 10;
    private int quietZone = 4;
    private Color foregroundColor = Color.BLACK;
    private Color backgroundColor = Color.WHITE;
    private double cornerRadius = 0.25;
    private byte[] logoImage = new byte[0];
    private double logoScale = 0.20;
    private boolean warnOnly;
    private String lastWarning = "";

    public RoundedModuleRendererBean() {
    }

    @Override
    public RenderedArtifact render(QrCode symbol) throws QrRenderingException {
        BufferedImage image = renderToImage(symbol);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw new QrRenderingException(COMPONENT_ID, "No PNG writer is registered in this JVM");
            }
            RenderedArtifact artifact = new RenderedArtifact();
            artifact.setContent(output.toByteArray());
            artifact.setFormatName("PNG");
            artifact.setFileExtension("png");
            artifact.setMimeType("image/png");
            artifact.setWidth(image.getWidth());
            artifact.setHeight(image.getHeight());
            artifact.setTextual(false);
            artifact.setRendererId(COMPONENT_ID);
            return artifact;
        } catch (IOException e) {
            throw new QrRenderingException(COMPONENT_ID, "Could not encode the rounded PNG", e);
        }
    }

    /** @return an in-memory rounded image, after applying the configured logo policy. */
    public BufferedImage renderToImage(QrCode symbol) throws QrRenderingException {
        if (symbol == null) {
            throw new QrRenderingException(COMPONENT_ID, "No QR symbol was supplied");
        }
        validateLogoCoverage(symbol);
        int pixelSize = getPixelSize(symbol);
        BufferedImage image = new BufferedImage(pixelSize, pixelSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(backgroundColor);
            graphics.fillRect(0, 0, pixelSize, pixelSize);
            graphics.setColor(foregroundColor);
            int diameter = Math.max(1, (int) Math.round(moduleSize * cornerRadius * 2));
            for (int y = 0; y < symbol.getSize(); y++) {
                for (int x = 0; x < symbol.getSize(); x++) {
                    if (symbol.isDark(x, y)) {
                        int px = (quietZone + x) * moduleSize;
                        int py = (quietZone + y) * moduleSize;
                        graphics.fillRoundRect(px, py, moduleSize, moduleSize, diameter, diameter);
                    }
                }
            }
            if (logoImage.length > 0) {
                try {
                    BufferedImage logo = ImageIO.read(new ByteArrayInputStream(logoImage));
                    if (logo == null) {
                        throw new IOException("logo bytes are not a readable image");
                    }
                    int logoPixels = Math.max(moduleSize, (int) Math.round(symbol.getSize()
                            * moduleSize * logoScale));
                    int x = (pixelSize - logoPixels) / 2;
                    int y = (pixelSize - logoPixels) / 2;
                    graphics.drawImage(logo.getScaledInstance(logoPixels, logoPixels, Image.SCALE_SMOOTH),
                            x, y, null);
                } catch (IOException e) {
                    throw new QrRenderingException(COMPONENT_ID, "Logo bytes are not a readable image", e);
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void validateLogoCoverage(QrCode symbol) throws QrRenderingException {
        lastWarning = "";
        if (logoImage.length == 0) {
            return;
        }
        double coveredPercent = logoScale * logoScale * 100.0;
        QrErrorCorrection level = symbol.getErrorCorrection();
        double recoverablePercent = level == null ? 0 : level.getNominalRecoveryPercent();
        if (coveredPercent > recoverablePercent) {
            lastWarning = String.format(Locale.ROOT,
                    "logo covers %.1f%% of the symbol but level %s nominally recovers %d%%",
                    coveredPercent, level == null ? "?" : level.getLabel(),
                    (int) recoverablePercent);
            if (!warnOnly) {
                throw new QrRenderingException(COMPONENT_ID, lastWarning);
            }
        }
    }

    public int getPixelSize(QrCode symbol) {
        return (symbol.getSize() + 2 * quietZone) * moduleSize;
    }

    @Override
    public String getRendererId() { return COMPONENT_ID; }

    @Override
    public String getFormatName() { return "PNG"; }

    @Override
    public String getFileExtension() { return "png"; }

    @Override
    public String getMimeType() { return "image/png"; }

    @Override
    public String describeConfiguration() {
        return String.format(Locale.ROOT, "Rounded PNG: moduleSize=%dpx, quietZone=%d, radius=%.2f, logo=%.2f",
                moduleSize, quietZone, cornerRadius, logoImage.length == 0 ? 0 : logoScale);
    }

    public int getModuleSize() { return moduleSize; }
    public void setModuleSize(int value) {
        if (value < 1 || value > 64) throw new IllegalArgumentException("moduleSize must be 1..64");
        int old = moduleSize; moduleSize = value; propertyChanges.firePropertyChange("moduleSize", old, value);
    }
    public int getQuietZone() { return quietZone; }
    public void setQuietZone(int value) {
        if (value < 0 || value > 32) throw new IllegalArgumentException("quietZone must be 0..32");
        int old = quietZone; quietZone = value; propertyChanges.firePropertyChange("quietZone", old, value);
    }
    public Color getForegroundColor() { return foregroundColor; }
    public void setForegroundColor(Color value) {
        if (value == null) throw new IllegalArgumentException("foregroundColor must not be null");
        Color old = foregroundColor; foregroundColor = value; propertyChanges.firePropertyChange("foregroundColor", old, value);
    }
    public Color getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(Color value) {
        if (value == null) throw new IllegalArgumentException("backgroundColor must not be null");
        Color old = backgroundColor; backgroundColor = value; propertyChanges.firePropertyChange("backgroundColor", old, value);
    }
    public double getCornerRadius() { return cornerRadius; }
    public void setCornerRadius(double value) {
        if (Double.isNaN(value) || value < 0 || value > 0.5) throw new IllegalArgumentException("cornerRadius must be 0..0.5");
        double old = cornerRadius; cornerRadius = value; propertyChanges.firePropertyChange("cornerRadius", old, value);
    }
    public byte[] getLogoImage() { return logoImage.clone(); }
    public void setLogoImage(byte[] value) {
        byte[] old = logoImage; logoImage = value == null ? new byte[0] : value.clone();
        propertyChanges.firePropertyChange("logoImage", old, logoImage.clone());
    }
    public double getLogoScale() { return logoScale; }
    public void setLogoScale(double value) {
        if (Double.isNaN(value) || value <= 0 || value > 1) throw new IllegalArgumentException("logoScale must be >0..1");
        double old = logoScale; logoScale = value; propertyChanges.firePropertyChange("logoScale", old, value);
    }
    public boolean isWarnOnly() { return warnOnly; }
    public void setWarnOnly(boolean value) {
        boolean old = warnOnly; warnOnly = value; propertyChanges.firePropertyChange("warnOnly", old, value);
    }
    public String getLastWarning() { return lastWarning; }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) { propertyChanges.addPropertyChangeListener(listener); }
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) { propertyChanges.removePropertyChangeListener(listener); }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        propertyChanges = new PropertyChangeSupport(this);
    }
}
