package ke.ku.sco310.qr.api;

/**
 * Raised when a symbol or artifact cannot be decoded (malformed modules, unreadable format
 * information, data codewords that exceed the correction capability of the symbol, or a
 * payload mode the decoder does not support).
 *
 * <p>This is the mirror image of {@link QrEncodingException}: the encoder and the decoder
 * publish the same failure vocabulary so that a front end written against
 * {@link QrCodeDecoder} does not have to know which implementation produced the failure.</p>
 */
public class QrDecodingException extends QrComponentException {

    private static final long serialVersionUID = 1L;

    public QrDecodingException(String componentId, String message) {
        super(componentId, message);
    }

    public QrDecodingException(String componentId, String message, Throwable cause) {
        super(componentId, message, cause);
    }
}