package ke.ku.sco310.qr.api;

import java.io.File;
import java.io.Serializable;

/**
 * Immutable outcome of one end-to-end generation performed by the facade component.
 *
 * <p>The facade returns this single object so that callers never have to
 * assemble the pieces themselves; the symbol, the artifact, the stored file and the timing all
 * travel together.</p>
 */
public final class QrGenerationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final QrCode symbol;
    private final RenderedArtifact artifact;
    private final File storedFile;
    private final QrHistoryEntry historyEntry;
    private final long durationMillis;

    public QrGenerationResult(QrCode symbol, RenderedArtifact artifact, File storedFile,
                              QrHistoryEntry historyEntry, long durationMillis) {
        this.symbol = symbol;
        this.artifact = artifact;
        this.storedFile = storedFile;
        this.historyEntry = historyEntry;
        this.durationMillis = durationMillis;
    }

    public QrCode getSymbol() {
        return symbol;
    }

    public RenderedArtifact getArtifact() {
        return artifact;
    }

    /** @return the file the artifact was written to, or {@code null} when storage was disabled. */
    public File getStoredFile() {
        return storedFile;
    }

    public QrHistoryEntry getHistoryEntry() {
        return historyEntry;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    @Override
    public String toString() {
        return "QrGenerationResult{v" + symbol.getVersion() + "-"
                + symbol.getErrorCorrection().getLabel() + ", " + symbol.getCharacterCount()
                + " chars, " + (artifact == null ? "no artifact" : artifact.getFormatName())
                + ", stored=" + (storedFile == null ? "no" : storedFile.getName())
                + ", " + durationMillis + " ms}";
    }
}
