package ke.ku.sco310.qr.api;

/**
 * <b>Provided interface</b> of a decoder component.
 *
 * <p>This suite's encoder components produce symbols and rendered artifacts; the decoder
 * components read them back. Two components implement this interface:
 * <ul>
 *   <li>{@code ke.ku.sco310.qr.components.PureJavaQrDecoderBean} - an in-house decoder that
 *       parses a {@link QrCode} module matrix (and the textual/vector artifacts of this suite)
 *       entirely in pure Java;</li>
 *   <li>{@code ke.ku.sco310.qr.components.ZxingQrDecoderAdapterBean} - an adapter that hands a
 *       raster artifact to the independent third-party ZXing decoder.</li>
 * </ul>
 * A caller holding this type cannot tell the two apart except through {@link #getDecoderId()},
 * which is exactly the property that component-oriented programming asks for.</p>
 *
 * <p>Pre-conditions: the input symbol/artifact is non-null and was produced by an encoder of
 * this suite (for {@link #decode(QrCode)}) or is a readable raster (for
 * {@link #decode(RenderedArtifact)}).<br>
 * Post-conditions: the returned string is the payload that the symbol carried.<br>
 * Error conditions: {@link QrDecodingException} when the modules are malformed, the format
 * information cannot be recovered, or the payload exceeds the symbol's correction capability.</p>
 */
public interface QrCodeDecoder extends java.io.Serializable {

    /**
     * Decodes a symbol back to the payload it carried.
     *
     * <p>This works on the module matrix directly, so it needs no image pipeline. The in-house
     * implementation reads the format information, unmasks, de-interleaves, corrects the
     * Reed-Solomon codewords and parses the data segments.</p>
     *
     * @param symbol encoded symbol to decode
     * @return the payload recovered from the symbol
     * @throws QrDecodingException when the symbol cannot be decoded
     */
    String decode(QrCode symbol) throws QrDecodingException;

    /**
     * Decodes a rendered artifact back to the payload it carried.
     *
     * <p>Raster artifacts (PNG) go through an image pipeline; textual and vector artifacts
     * (ASCII, SVG) are parsed geometrically. The in-house implementation decodes the non-raster
     * artifacts itself and delegates raster artifacts to ZXing when it is available.</p>
     *
     * @param artifact rendered artifact to decode
     * @return the payload recovered from the artifact
     * @throws QrDecodingException when the artifact cannot be decoded
     */
    String decode(RenderedArtifact artifact) throws QrDecodingException;

    /** @return stable identifier of the decoder component instance. */
    String getDecoderId();

    /** @return human readable name of the decoder implementation, e.g. {@code Pure-Java}. */
    String getFormatName();
}