package ke.ku.sco310.qr.api;

import java.io.Serializable;

/** Provided interface for the separate ISO/IEC 18004 Micro QR M1-M4 encoding path. */
public interface QrMicroEncoder extends Serializable {

    /**
     * Encodes a compact payload as Micro QR.
     *
     * <p>Pre-conditions: {@code content} is non-null and non-empty; {@code level} is L, M or Q.
     * Level L may select M1 when its numeric detection-only capacity is sufficient. Post-condition:
     * the returned matrix is 11, 13, 15 or 17 modules square and contains one finder pattern.</p>
     *
     * @param content payload to encode
     * @param level requested level (L, M or Q)
     * @return immutable Micro QR symbol
     * @throws QrEncodingException when no permitted Micro version can represent the payload
     */
    MicroQrCode encode(String content, QrErrorCorrection level) throws QrEncodingException;

    /** @return stable identifier of the component implementation. */
    String getComponentId();
}
