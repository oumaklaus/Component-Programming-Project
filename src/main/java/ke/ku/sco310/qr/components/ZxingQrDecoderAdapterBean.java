package ke.ku.sco310.qr.components;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import ke.ku.sco310.qr.api.BoundPropertySource;
import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrCodeDecoder;
import ke.ku.sco310.qr.api.QrDecodingException;
import ke.ku.sco310.qr.api.RenderedArtifact;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Optional decoder adapter backed by ZXing's QR reader.
 *
 * <p>The class is kept behind the same provided interface as the pure-Java decoder. Its only
 * runtime requirement is {@code com.google.zxing:core}; callers can discover availability through
 * the component catalogue and keep the in-house component when the optional library is absent.</p>
 */
public class ZxingQrDecoderAdapterBean implements QrCodeDecoder, Serializable, BoundPropertySource {

    private static final long serialVersionUID = 1L;

    /** Component instance identifier. */
    public static final String COMPONENT_ID = "decoder.zxing-adapter.v1";

    private transient PropertyChangeSupport propertyChanges = new PropertyChangeSupport(this);
    private boolean tryHarder;
    private String characterSet = "UTF-8";

    public ZxingQrDecoderAdapterBean() {
    }

    @Override
    public String decode(QrCode symbol) throws QrDecodingException {
        if (symbol == null) {
            throw new QrDecodingException(COMPONENT_ID, "No QR symbol was supplied");
        }
        int size = symbol.getSize();
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = symbol.isDark(x, y) ? 0x000000 : 0xFFFFFF;
            }
        }
        return decodePixels(new RGBLuminanceSource(size, size, pixels));
    }

    @Override
    public String decode(RenderedArtifact artifact) throws QrDecodingException {
        if (artifact == null || artifact.getContent().length == 0) {
            throw new QrDecodingException(COMPONENT_ID, "No rendered artifact was supplied");
        }
        if (!"PNG".equalsIgnoreCase(artifact.getFormatName())) {
            throw new QrDecodingException(COMPONENT_ID,
                    "ZXing adapter accepts PNG artifacts only, not " + artifact.getFormatName());
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(artifact.getContent()));
            if (image == null) {
                throw new QrDecodingException(COMPONENT_ID, "The artifact is not a readable PNG image");
            }
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0,
                    image.getWidth());
            return decodePixels(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels));
        } catch (QrDecodingException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new QrDecodingException(COMPONENT_ID, "ZXing could not decode the PNG artifact", e);
        }
    }

    private String decodePixels(LuminanceSource source) throws QrDecodingException {
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.CHARACTER_SET, characterSet);
            if (tryHarder) {
                hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            }
            Result result = new QRCodeReader().decode(bitmap, hints);
            return result.getText();
        } catch (Exception e) {
            throw new QrDecodingException(COMPONENT_ID, "ZXing rejected the QR symbol", e);
        }
    }

    @Override
    public String getDecoderId() {
        return COMPONENT_ID;
    }

    @Override
    public String getFormatName() {
        return "ZXing QR decoder";
    }

    public boolean isTryHarder() {
        return tryHarder;
    }

    public void setTryHarder(boolean tryHarder) {
        boolean old = this.tryHarder;
        this.tryHarder = tryHarder;
        propertyChanges.firePropertyChange("tryHarder", old, tryHarder);
    }

    public String getCharacterSet() {
        return characterSet;
    }

    public void setCharacterSet(String characterSet) {
        if (characterSet == null || characterSet.trim().isEmpty()) {
            throw new IllegalArgumentException("characterSet must not be blank");
        }
        String old = this.characterSet;
        this.characterSet = characterSet;
        propertyChanges.firePropertyChange("characterSet", old, characterSet);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.removePropertyChangeListener(listener);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        propertyChanges = new PropertyChangeSupport(this);
    }
}
