package ke.ku.sco310.qr.render;

import ke.ku.sco310.qr.api.BoundPropertySource;
import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrCodeRenderer;
import ke.ku.sco310.qr.api.QrRenderingException;
import ke.ku.sco310.qr.api.RenderedArtifact;
import ke.ku.sco310.qr.editor.HexColorPropertyEditor;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dependency-free single-page vector PDF renderer.
 *
 * <p>The PDF syntax is written directly: a catalog, pages tree, page dictionary and one content
 * stream containing rectangle fill operators. No PDF library or runtime dependency is involved;
 * the output is suitable for a print workflow and remains a vector document at any zoom level.</p>
 */
public class PdfRendererBean implements QrCodeRenderer, Serializable, BoundPropertySource {

    private static final long serialVersionUID = 1L;
    public static final String COMPONENT_ID = "renderer.pdf.v1";

    private transient PropertyChangeSupport propertyChanges = new PropertyChangeSupport(this);
    private double moduleSize = 8.0;
    private int quietZone = 4;
    private String foregroundColor = "#000000";
    private String backgroundColor = "#FFFFFF";

    public PdfRendererBean() {
    }

    @Override
    public RenderedArtifact render(QrCode symbol) throws QrRenderingException {
        if (symbol == null) throw new QrRenderingException(COMPONENT_ID, "No QR symbol was supplied");
        double pageSize = (symbol.getSize() + quietZone * 2) * moduleSize;
        String stream = contentStream(symbol, pageSize);
        byte[] pdf = writePdf(pageSize, stream);
        RenderedArtifact artifact = new RenderedArtifact();
        artifact.setContent(pdf);
        artifact.setFormatName("PDF");
        artifact.setFileExtension("pdf");
        artifact.setMimeType("application/pdf");
        artifact.setWidth((int) Math.ceil(pageSize));
        artifact.setHeight((int) Math.ceil(pageSize));
        artifact.setTextual(false);
        artifact.setRendererId(COMPONENT_ID);
        return artifact;
    }

    private String contentStream(QrCode symbol, double pageSize) {
        float[] background = rgb(backgroundColor);
        float[] foreground = rgb(foregroundColor);
        StringBuilder stream = new StringBuilder(4096);
        stream.append("q\n").append(format(background[0])).append(' ').append(format(background[1]))
                .append(' ').append(format(background[2])).append(" rg\n0 0 ")
                .append(format(pageSize)).append(' ').append(format(pageSize)).append(" re f\n");
        stream.append(format(foreground[0])).append(' ').append(format(foreground[1])).append(' ')
                .append(format(foreground[2])).append(" rg\n");
        for (int y = 0; y < symbol.getSize(); y++) {
            int runStart = -1;
            for (int x = 0; x <= symbol.getSize(); x++) {
                boolean dark = x < symbol.getSize() && symbol.isDark(x, y);
                if (dark && runStart < 0) runStart = x;
                if (!dark && runStart >= 0) {
                    double px = (quietZone + runStart) * moduleSize;
                    double py = (quietZone + symbol.getSize() - y - 1) * moduleSize;
                    stream.append(format(px)).append(' ').append(format(py)).append(' ')
                            .append(format((x - runStart) * moduleSize)).append(' ')
                            .append(format(moduleSize)).append(" re f\n");
                    runStart = -1;
                }
            }
        }
        return stream.append("Q\n").toString();
    }

    private static byte[] writePdf(double pageSize, String stream) {
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + format(pageSize) + " "
                + format(pageSize) + "] /Resources << >> /Contents 4 0 R >>");
        byte[] streamBytes = stream.getBytes(StandardCharsets.US_ASCII);
        objects.add("<< /Length " + streamBytes.length + " >>\nstream\n" + stream + "endstream");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(output.size());
            write(output, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = output.size();
        write(output, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) {
            write(output, String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
        }
        write(output, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xref + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, String value) {
        try { output.write(value.getBytes(StandardCharsets.ISO_8859_1)); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }

    private static float[] rgb(String hex) {
        java.awt.Color color = HexColorPropertyEditor.fromHex(hex);
        return new float[]{color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f};
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.3f", value); }

    @Override public String getRendererId() { return COMPONENT_ID; }
    @Override public String getFormatName() { return "PDF"; }
    @Override public String getFileExtension() { return "pdf"; }
    @Override public String getMimeType() { return "application/pdf"; }
    @Override public String describeConfiguration() { return "PDF: moduleSize=" + moduleSize + ", quietZone=" + quietZone; }

    public double getModuleSize() { return moduleSize; }
    public void setModuleSize(double value) { if (!(value > 0) || value > 100) throw new IllegalArgumentException("moduleSize must be >0..100"); double old = moduleSize; moduleSize = value; propertyChanges.firePropertyChange("moduleSize", old, value); }
    public int getQuietZone() { return quietZone; }
    public void setQuietZone(int value) { if (value < 0 || value > 32) throw new IllegalArgumentException("quietZone must be 0..32"); int old = quietZone; quietZone = value; propertyChanges.firePropertyChange("quietZone", old, value); }
    public String getForegroundColor() { return foregroundColor; }
    public void setForegroundColor(String value) { String old = foregroundColor; foregroundColor = HexColorPropertyEditor.toHex(HexColorPropertyEditor.fromHex(value)); propertyChanges.firePropertyChange("foregroundColor", old, foregroundColor); }
    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String value) { String old = backgroundColor; backgroundColor = HexColorPropertyEditor.toHex(HexColorPropertyEditor.fromHex(value)); propertyChanges.firePropertyChange("backgroundColor", old, backgroundColor); }
    @Override public void addPropertyChangeListener(PropertyChangeListener listener) { propertyChanges.addPropertyChangeListener(listener); }
    @Override public void removePropertyChangeListener(PropertyChangeListener listener) { propertyChanges.removePropertyChangeListener(listener); }
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException { in.defaultReadObject(); propertyChanges = new PropertyChangeSupport(this); }
}
