package ke.ku.sco310.qr.api;

/**
 * <b>Provided interface</b> of a renderer component.
 *
 * <p>Implementations differ only in the artifact they produce (raster PNG, vector SVG,
 * console text art). Because all of them return the same {@link RenderedArtifact} type, the
 * assembly can exchange one renderer for another without touching the caller - the
 * canonical demonstration of replaceability used in the project report.</p>
 */
public interface QrCodeRenderer extends java.io.Serializable {

    /**
     * Renders one symbol into a transferable artifact.
     *
     * <p>Pre-condition: {@code symbol} is non-null and produced by a {@link QrCodeEncoder}
     * of this suite. Post-condition: the returned artifact carries a non-empty body whose
     * format matches {@link #getFormatName()}.</p>
     *
     * @param symbol encoded symbol to draw
     * @return the rendered artifact
     * @throws QrRenderingException when the image cannot be produced
     */
    RenderedArtifact render(QrCode symbol) throws QrRenderingException;

    /** @return stable identifier of the renderer component instance. */
    String getRendererId();

    /** @return human readable format name, e.g. {@code PNG}. */
    String getFormatName();

    /** @return file extension without the dot, e.g. {@code png}. */
    String getFileExtension();

    /** @return MIME type of the generated file, e.g. {@code image/png}. */
    String getMimeType();

    /** @return one-line description of the current property configuration (used in reports). */
    String describeConfiguration();
}
