package ke.ku.sco310.qr.api;

import java.io.File;
import java.util.List;

/**
 * <b>Required interface</b> (a context dependency) of the facade component.
 *
 * <p>Any component that wants to persist artifacts and history depends on this type and not
 * on a concrete storage technology. The suite ships a file-system implementation
 * ({@code ke.ku.sco310.qr.store.FileSystemRepositoryBean}); the chapter 2 deployment model
 * D = (C, E, P, L) is the reason the root directory is a configurable property: the same
 * component is deployed into a desktop environment and into a server environment by
 * changing the deployment parameters, not the code.</p>
 */
public interface QrCodeRepository extends java.io.Serializable {

    /** @return directory into which artifacts and the history file are written. */
    File getRootDirectory();

    /**
     * Writes one artifact to disk.
     *
     * @param artifact rendered artifact to store
     * @param baseName file name without extension
     * @return the file that was written
     * @throws QrStorageException when the file cannot be written
     */
    File storeArtifact(RenderedArtifact artifact, String baseName) throws QrStorageException;

    /**
     * Appends one record to the persisted history.
     *
     * @param entry record to append
     * @throws QrStorageException when the history file cannot be updated
     */
    void appendHistory(QrHistoryEntry entry) throws QrStorageException;

    /**
     * @return the persisted history, oldest first (never {@code null})
     * @throws QrStorageException when the history file cannot be read
     */
    List<QrHistoryEntry> loadHistory() throws QrStorageException;

    /**
     * @return number of records currently persisted
     * @throws QrStorageException when the history file cannot be read
     */
    int getHistoryCount() throws QrStorageException;

    /**
     * Removes every persisted record.
     *
     * @throws QrStorageException when the history file cannot be written
     */
    void clearHistory() throws QrStorageException;

    /** @return stable identifier of the repository component instance. */
    String getComponentId();

    /**
     * @return the persisted history without checked exceptions, for read-only front ends such as
     *         the application history; an unreadable history yields an empty list
     */
    List<QrHistoryEntry> getHistorySnapshot();
}
