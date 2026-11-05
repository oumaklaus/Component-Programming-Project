package ke.ku.sco310.qr.api;

/** Provided interface of an encoder component. */
public interface QrCodeEncoder extends java.io.Serializable {

    /** Encodes content into a QR Code symbol. */
    QrCode encode(String content, QrErrorCorrection level) throws QrEncodingException;

    /** @return capability metadata of this encoder implementation. */
    EncoderInfo getEncoderInfo();

    /** @return stable identifier of the component instance, used in logs and saved results. */
    String getComponentId();
}
