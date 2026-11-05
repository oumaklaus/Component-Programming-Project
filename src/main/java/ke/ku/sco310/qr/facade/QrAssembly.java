package ke.ku.sco310.qr.facade;

import ke.ku.sco310.qr.api.QrCodeEncoder;
import ke.ku.sco310.qr.api.QrCodeRepository;
import ke.ku.sco310.qr.api.QrCodeRenderer;
import ke.ku.sco310.qr.api.QrEncodingException;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.api.QrGenerationResult;
import ke.ku.sco310.qr.api.QrStorageException;
import ke.ku.sco310.qr.api.QrRenderingException;
import ke.ku.sco310.qr.log.AuditLogListenerBean;
import ke.ku.sco310.qr.store.QrHistoryBean;

import java.util.ArrayList;
import java.util.List;

/**
 * A concrete, wired assembly of components - the "application" in component-oriented terms.
 *
 * <p>An assembly is data, not code: the same class can hold different desktop component selections,
 * and the difference between them lives in the {@link AssemblyProfile} that was used to build
 * it.</p>
 */
public class QrAssembly {

    private static final long serialVersionUID = 1L;

    private final AssemblyProfile profile;
    private QrCodeEncoder encoder;
    private QrCodeRenderer renderer;
    private QrCodeRepository repository;
    private QrHistoryBean history;
    private AuditLogListenerBean listener;
    private QrStudioFacadeBean facade;
    private final List<ComponentDescriptor> components = new ArrayList<>();
    private List<String> configurationNotes = new ArrayList<>();

    public QrAssembly(AssemblyProfile profile) {
        this.profile = profile;
    }

    public AssemblyProfile getProfile() {
        return profile;
    }

    public QrCodeEncoder getEncoder() {
        return encoder;
    }

    public void setEncoder(QrCodeEncoder encoder) {
        this.encoder = encoder;
    }

    public QrCodeRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(QrCodeRenderer renderer) {
        this.renderer = renderer;
    }

    public QrCodeRepository getRepository() {
        return repository;
    }

    public void setRepository(QrCodeRepository repository) {
        this.repository = repository;
    }

    public QrHistoryBean getHistory() {
        return history;
    }

    public void setHistory(QrHistoryBean history) {
        this.history = history;
    }

    public AuditLogListenerBean getListener() {
        return listener;
    }

    public void setListener(AuditLogListenerBean listener) {
        this.listener = listener;
    }

    public QrStudioFacadeBean getFacade() {
        return facade;
    }

    public void setFacade(QrStudioFacadeBean facade) {
        this.facade = facade;
    }

    public List<ComponentDescriptor> getComponents() {
        return new ArrayList<>(components);
    }

    public List<String> getConfigurationNotes() {
        return new ArrayList<>(configurationNotes);
    }

    public void setConfigurationNotes(List<String> notes) {
        this.configurationNotes = new ArrayList<>(notes);
    }

    public void setComponents(List<ComponentDescriptor> descriptors) {
        components.clear();
        components.addAll(descriptors);
    }

    /**
     * Swaps the renderer component of the running assembly.
     *
     * @param replacement new renderer implementation
     */
    public void replaceRenderer(QrCodeRenderer replacement) {
        this.renderer = replacement;
        facade.setRenderer(replacement);
        profile.setComponentId("renderer", replacement.getRendererId());
    }

    /**
     * Swaps the encoder component of the running assembly.
     *
     * @param replacement new encoder implementation
     */
    public void replaceEncoder(QrCodeEncoder replacement) {
        this.encoder = replacement;
        facade.setEncoder(replacement);
        profile.setComponentId("encoder", replacement.getComponentId());
    }

    /** Convenience delegation so the front ends only need the assembly. */
    public QrGenerationResult generate(String content, QrErrorCorrection level)
            throws QrEncodingException, QrRenderingException, QrStorageException {
        return facade.generate(content, level);
    }

    /** @return a multi-line component table of this assembly, used in the report. */
    public String describeAssembly() {
        StringBuilder sb = new StringBuilder();
        sb.append(profile.describe());
        sb.append(String.format("%-11s %-30s %-14s %-46s %s%n",
                "ROLE", "COMPONENT ID", "STATE", "PROVIDED INTERFACE", "REQUIRED INTERFACE"));
        sb.append("-".repeat(140)).append('\n');
        for (ComponentDescriptor descriptor : components) {
            sb.append(descriptor.toTableRow()).append('\n');
        }
        sb.append('\n').append(facade.describeWiring()).append('\n');
        for (String note : configurationNotes) {
            sb.append("  note: ").append(note).append('\n');
        }
        return sb.toString();
    }
}
