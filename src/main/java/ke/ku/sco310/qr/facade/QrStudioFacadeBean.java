package ke.ku.sco310.qr.facade;

import ke.ku.sco310.qr.api.BoundPropertySource;
import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrCodeEncoder;
import ke.ku.sco310.qr.api.QrCodeRenderer;
import ke.ku.sco310.qr.api.QrCodeRepository;
import ke.ku.sco310.qr.api.QrEncodingException;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.api.QrGenerationResult;
import ke.ku.sco310.qr.api.QrHistoryEntry;
import ke.ku.sco310.qr.api.QrRenderingException;
import ke.ku.sco310.qr.api.QrStorageException;
import ke.ku.sco310.qr.api.RenderedArtifact;
import ke.ku.sco310.qr.event.QrCodeGeneratedEvent;
import ke.ku.sco310.qr.event.QrCodeGenerationListener;
import ke.ku.sco310.qr.store.QrHistoryBean;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** Composite (facade) component that assembles the encoder, the renderer, the repository, the history and the listener components into the process "ge... */
public class QrStudioFacadeBean implements Serializable, BoundPropertySource {

    private static final long serialVersionUID = 1L;

    /** Component instance identifier. */
    public static final String COMPONENT_ID = "facade.qr-studio.v1";

    private transient PropertyChangeSupport propertyChanges = new PropertyChangeSupport(this);
    private final CopyOnWriteArrayList<QrCodeGenerationListener> listeners = new CopyOnWriteArrayList<>();

    // --- required interfaces (context dependencies) -------------------------------
    private QrCodeEncoder encoder;
    private QrCodeRenderer renderer;
    private QrCodeRepository repository;
    private QrHistoryBean history;

    // --- configuration properties -------------------------------------------------
    private QrErrorCorrection defaultErrorCorrection = QrErrorCorrection.MEDIUM;
    private boolean autoSaveArtifacts = true;
    private boolean autoSaveHistory = true;
    private String fileBaseName = "qr-code";
    private int generatedCount;

    public QrStudioFacadeBean() {
    }

    public String getComponentId() {
        return COMPONENT_ID;
    }

    // ------------------------------------------------------------------ provided interface

    /** Encodes, renders and (when configured) stores one payload using the injected components. */
    public QrGenerationResult generate(String content, QrErrorCorrection level)
            throws QrEncodingException, QrRenderingException, QrStorageException {
        return generate(content, level, null);
    }

    /** Variant of #generate(String, that temporarily uses a caller supplied renderer component instead of the injected one. */
    public QrGenerationResult generate(String content, QrErrorCorrection level,
                                       QrCodeRenderer rendererOverride)
            throws QrEncodingException, QrRenderingException, QrStorageException {
        requireWired();
        long start = System.nanoTime();
        QrCodeRenderer effectiveRenderer = rendererOverride == null ? renderer : rendererOverride;

        QrCode symbol = encoder.encode(content, level == null ? defaultErrorCorrection : level);
        RenderedArtifact artifact = effectiveRenderer.render(symbol);

        File stored = null;
        if (autoSaveArtifacts && repository != null) {
            String baseName = buildBaseName(symbol);
            stored = repository.storeArtifact(artifact, baseName);
        }

        QrHistoryEntry entry = new QrHistoryEntry(symbol,
                stored == null ? "(not stored)" : stored.getName());
        entry.setArtifactFormat(artifact.getFormatName());
        if (history != null) {
            history.addEntry(entry);
        }
        if (autoSaveHistory && repository != null) {
            repository.appendHistory(entry);
        }

        generatedCount++;
        long durationMillis = Math.round((System.nanoTime() - start) / 1_000_000.0);
        QrGenerationResult result = new QrGenerationResult(symbol, artifact, stored, entry, durationMillis);

        fireQrCodeGenerated(new QrCodeGeneratedEvent(this, symbol, artifact, stored,
                effectiveRenderer.getRendererId(), durationMillis));
        propertyChanges.firePropertyChange("generatedCount", generatedCount - 1, generatedCount);
        return result;
    }

    /** Convenience overload using the {@code defaultErrorCorrection} property. */
    public QrGenerationResult generate(String content)
            throws QrEncodingException, QrRenderingException, QrStorageException {
        return generate(content, defaultErrorCorrection);
    }

    /** Re-renders an already encoded symbol with a different renderer component. */
    public RenderedArtifact renderWith(QrCode symbol, QrCodeRenderer renderer) throws QrRenderingException {
        if (renderer == null) {
            throw new QrRenderingException(COMPONENT_ID, "No renderer was supplied");
        }
        return renderer.render(symbol);
    }

    /** @return a one-line description of what is currently wired into the facade. */
    public String describeWiring() {
        return "facade[encoder=" + (encoder == null ? "<missing>" : encoder.getComponentId())
                + ", renderer=" + (renderer == null ? "<missing>" : renderer.getRendererId())
                + ", repository=" + (repository == null ? "<missing>" : repository.getComponentId())
                + ", history=" + (history == null ? "<missing>" : history.getComponentId())
                + ", listeners=" + listeners.size() + "]";
    }

    private void requireWired() throws QrEncodingException {
        if (encoder == null) {
            throw new QrEncodingException(COMPONENT_ID,
                    "No encoder component is connected: call setEncoder(...) before generating");
        }
        if (renderer == null) {
            throw new QrEncodingException(COMPONENT_ID,
                    "No renderer component is connected: call setRenderer(...) before generating");
        }
    }

    private String buildBaseName(QrCode symbol) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        return fileBaseName + "-v" + symbol.getVersion() + "-"
                + symbol.getErrorCorrection().getLabel() + "-" + stamp;
    }

    // ------------------------------------------------------------------ listener plumbing

    public void addQrCodeGenerationListener(QrCodeGenerationListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeQrCodeGenerationListener(QrCodeGenerationListener listener) {
        listeners.remove(listener);
    }

    /** @return number of currently connected listeners. */
    public int getListenerCount() {
        return listeners.size();
    }

    private void fireQrCodeGenerated(QrCodeGeneratedEvent event) {
        for (QrCodeGenerationListener listener : listeners) {
            try {
                listener.qrCodeGenerated(event);
            } catch (RuntimeException e) {
                System.err.println("[facade] listener " + listener.getClass().getSimpleName()
                        + " failed: " + e);
            }
        }
    }

    // ------------------------------------------------------------------ required interfaces

    public QrCodeEncoder getEncoder() {
        return encoder;
    }

    public void setEncoder(QrCodeEncoder encoder) {
        QrCodeEncoder old = this.encoder;
        this.encoder = encoder;
        propertyChanges.firePropertyChange("encoder", old, encoder);
    }

    public QrCodeRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(QrCodeRenderer renderer) {
        QrCodeRenderer old = this.renderer;
        this.renderer = renderer;
        propertyChanges.firePropertyChange("renderer", old, renderer);
    }

    public QrCodeRepository getRepository() {
        return repository;
    }

    public void setRepository(QrCodeRepository repository) {
        QrCodeRepository old = this.repository;
        this.repository = repository;
        propertyChanges.firePropertyChange("repository", old, repository);
    }

    public QrHistoryBean getHistory() {
        return history;
    }

    public void setHistory(QrHistoryBean history) {
        QrHistoryBean old = this.history;
        this.history = history;
        propertyChanges.firePropertyChange("history", old, history);
    }

    // ------------------------------------------------------------------ configuration

    public QrErrorCorrection getDefaultErrorCorrection() {
        return defaultErrorCorrection;
    }

    public void setDefaultErrorCorrection(QrErrorCorrection defaultErrorCorrection) {
        QrErrorCorrection old = this.defaultErrorCorrection;
        this.defaultErrorCorrection = defaultErrorCorrection;
        propertyChanges.firePropertyChange("defaultErrorCorrection", old, defaultErrorCorrection);
    }

    public boolean isAutoSaveArtifacts() {
        return autoSaveArtifacts;
    }

    public void setAutoSaveArtifacts(boolean autoSaveArtifacts) {
        boolean old = this.autoSaveArtifacts;
        this.autoSaveArtifacts = autoSaveArtifacts;
        propertyChanges.firePropertyChange("autoSaveArtifacts", old, autoSaveArtifacts);
    }

    public boolean isAutoSaveHistory() {
        return autoSaveHistory;
    }

    public void setAutoSaveHistory(boolean autoSaveHistory) {
        boolean old = this.autoSaveHistory;
        this.autoSaveHistory = autoSaveHistory;
        propertyChanges.firePropertyChange("autoSaveHistory", old, autoSaveHistory);
    }

    public String getFileBaseName() {
        return fileBaseName;
    }

    public void setFileBaseName(String fileBaseName) {
        String old = this.fileBaseName;
        this.fileBaseName = fileBaseName;
        propertyChanges.firePropertyChange("fileBaseName", old, fileBaseName);
    }

    /** @return how many generations this facade instance has performed (read-only simple property). */
    public int getGeneratedCount() {
        return generatedCount;
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
        return describeWiring();
    }
}
