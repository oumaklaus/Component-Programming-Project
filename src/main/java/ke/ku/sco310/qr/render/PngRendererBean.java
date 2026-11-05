package ke.ku.sco310.qr.render;

import ke.ku.sco310.qr.api.BoundPropertySource;
import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrCodeRenderer;
import ke.ku.sco310.qr.api.QrRenderingException;
import ke.ku.sco310.qr.api.RenderedArtifact;
import ke.ku.sco310.qr.editor.HexColorPropertyEditor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Raster renderer component: draws the symbol as a square PNG image. */
public class PngRendererBean implements QrCodeRenderer, Serializable, BoundPropertySource {

    private static final long serialVersionUID = 1L;

    /** Component instance identifier. */
    public static final String COMPONENT_ID = "renderer.png.v1";

    private transient PropertyChangeSupport propertyChanges = new PropertyChangeSupport(this);

    private int moduleSize = 8;
    private int quietZone = 4;
    private Color foregroundColor = Color.BLACK;
    private Color backgroundColor = Color.WHITE;

    public PngRendererBean() {
    }

    @Override
    public RenderedArtifact render(QrCode symbol) throws QrRenderingException {
        BufferedImage image = renderToImage(symbol);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw new QrRenderingException(COMPONENT_ID, "No PNG writer is registered in this JVM");
            }
            RenderedArtifact artifact = new RenderedArtifact();
            artifact.setContent(out.toByteArray());
            artifact.setFormatName("PNG");
            artifact.setFileExtension("png");
            artifact.setMimeType("image/png");
            artifact.setWidth(image.getWidth());
            artifact.setHeight(image.getHeight());
            artifact.setTextual(false);
            artifact.setRendererId(COMPONENT_ID);
            return artifact;
        } catch (IOException e) {
            throw new QrRenderingException(COMPONENT_ID, "Could not encode the PNG image", e);
        }
    }

    /** Extra provided method used by the Swing assembly (and by the automated tests) so that the in-memory image is available without a round trip through... */
    public BufferedImage renderToImage(QrCode symbol) {
        int pixelSize = getPixelSize(symbol);
        BufferedImage image = new BufferedImage(pixelSize, pixelSize,
                backgroundColor.getAlpha() == 255 && foregroundColor.getAlpha() == 255
                        ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(backgroundColor);
            g.fillRect(0, 0, pixelSize, pixelSize);
            g.setColor(foregroundColor);
            int size = symbol.getSize();
            for (int y = 0; y < size; y++) {
                int runStart = -1;
                for (int x = 0; x <= size; x++) {
                    boolean dark = x < size && symbol.isDark(x, y);
                    if (dark && runStart < 0) {
                        runStart = x;
                    } else if (!dark && runStart >= 0) {
                        // One rectangle per horizontal run keeps the drawing cost linear.
                        g.fillRect((quietZone + runStart) * moduleSize, (quietZone + y) * moduleSize,
                                (x - runStart) * moduleSize, moduleSize);
                        runStart = -1;
                    }
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /** @param symbol encoded symbol @return side length of the produced image in pixels. */
    public int getPixelSize(QrCode symbol) {
        return (symbol.getSize() + 2 * quietZone) * moduleSize;
    }

    @Override
    public String getRendererId() {
        return COMPONENT_ID;
    }

    @Override
    public String getFormatName() {
        return "PNG";
    }

    @Override
    public String getFileExtension() {
        return "png";
    }

    @Override
    public String getMimeType() {
        return "image/png";
    }

    @Override
    public String describeConfiguration() {
        return String.format(Locale.ROOT, "PNG: moduleSize=%dpx, quietZone=%d modules, fg=%s, bg=%s",
                moduleSize, quietZone, HexColorPropertyEditor.toHex(foregroundColor),
                HexColorPropertyEditor.toHex(backgroundColor));
    }

    // ------------------------------------------------------------------ properties

    public int getModuleSize() {
        return moduleSize;
    }

    /** Bound property: pixel size of one module (1..64). */
    public void setModuleSize(int moduleSize) {
        if (moduleSize < 1 || moduleSize > 64) {
            throw new IllegalArgumentException("moduleSize must be 1..64");
        }
        int old = this.moduleSize;
        this.moduleSize = moduleSize;
        propertyChanges.firePropertyChange("moduleSize", old, moduleSize);
    }

    public int getQuietZone() {
        return quietZone;
    }

    /** Bound property: quiet zone width in modules; the specification requires at least 4. */
    public void setQuietZone(int quietZone) {
        if (quietZone < 0 || quietZone > 32) {
            throw new IllegalArgumentException("quietZone must be 0..32");
        }
        int old = this.quietZone;
        this.quietZone = quietZone;
        propertyChanges.firePropertyChange("quietZone", old, quietZone);
    }

    public Color getForegroundColor() {
        return foregroundColor;
    }

    /** Bound property edited in builder tools by {@link HexColorPropertyEditor}. */
    public void setForegroundColor(Color foregroundColor) {
        Color old = this.foregroundColor;
        this.foregroundColor = foregroundColor;
        propertyChanges.firePropertyChange("foregroundColor", old, foregroundColor);
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color backgroundColor) {
        Color old = this.backgroundColor;
        this.backgroundColor = backgroundColor;
        propertyChanges.firePropertyChange("backgroundColor", old, backgroundColor);
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
        return describeConfiguration();
    }
}
