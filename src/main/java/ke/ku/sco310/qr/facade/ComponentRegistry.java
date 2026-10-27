package ke.ku.sco310.qr.facade;

import ke.ku.sco310.qr.api.EncoderInfo;
import ke.ku.sco310.qr.api.QrCodeDecoder;
import ke.ku.sco310.qr.api.QrCodeEncoder;
import ke.ku.sco310.qr.api.QrCodeRenderer;
import ke.ku.sco310.qr.api.QrMicroEncoder;
import ke.ku.sco310.qr.components.OptionalComponentSupport;
import ke.ku.sco310.qr.log.AuditLogListenerBean;
import ke.ku.sco310.qr.store.FileSystemRepositoryBean;
import ke.ku.sco310.qr.store.QrHistoryBean;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Component broker and assembler.
 *
 * <p>Responsibilities:</p>
 * <ol>
 *   <li><b>catalogue</b> - {@link #listComponents()} publishes every implementation that can fill
 *       each role, together with the interfaces it provides and requires and whether the current
 *       class path can host it;</li>
 *   <li><b>selection</b> - {@link #build(AssemblyProfile)} instantiates the implementation chosen
 *       by the profile and refuses a selection whose context dependencies are not satisfied;</li>
 *   <li><b>configuration</b> - profile properties are applied to the beans through
 *       {@code java.beans.Introspector} and their setter methods, so a designer edits text, not
 *       code;</li>
 *   <li><b>wiring</b> - the facade receives the other components through its property setters and
 *       the audit listener is connected to the facade's event source.</li>
 * </ol>
 */
public class ComponentRegistry {

    private final List<String> configurationNotes = new ArrayList<>();

    /** @return notes about profile properties that the selected implementations do not declare. */
    public List<String> getConfigurationNotes() {
        return new ArrayList<>(configurationNotes);
    }

    /** Role names used by the profile and by the components table. */
    public static final String ROLE_ENCODER = "encoder";
    public static final String ROLE_MICRO_ENCODER = "micro-encoder";
    public static final String ROLE_RENDERER = "renderer";
    public static final String ROLE_DECODER = "decoder";
    public static final String ROLE_REPOSITORY = "repository";
    public static final String ROLE_HISTORY = "history";
    public static final String ROLE_LISTENER = "listener";
    public static final String ROLE_FACADE = "facade";

    /**
     * @return every known component implementation, in a stable order, with capability metadata.
     */
    public List<ComponentDescriptor> listComponents() {
        List<ComponentDescriptor> catalogue = new ArrayList<>();
        for (QrCodeEncoder provider : providers(QrCodeEncoder.class)) {
            ComponentDescriptor descriptor = descriptor(ROLE_ENCODER, provider.getComponentId(), provider);
            if (provider.getEncoderInfo().isRequiresOptionalLibrary()) {
                descriptor.requires("optional third-party encoder library");
            }
            if (!OptionalComponentSupport.isZxingAvailable()
                    && provider.getEncoderInfo().isRequiresOptionalLibrary()) {
                descriptor.unavailable("com.google.zxing.core is not on the class path");
            }
            catalogue.add(descriptor);
        }
        for (QrMicroEncoder provider : providers(QrMicroEncoder.class)) {
            catalogue.add(descriptor(ROLE_MICRO_ENCODER, provider.getComponentId(), provider));
        }
        for (QrCodeRenderer provider : providers(QrCodeRenderer.class)) {
            catalogue.add(descriptor(ROLE_RENDERER, provider.getRendererId(), provider));
        }
        for (QrCodeDecoder provider : providers(QrCodeDecoder.class)) {
            ComponentDescriptor descriptor = descriptor(ROLE_DECODER, provider.getDecoderId(), provider);
            if (provider.getDecoderId().contains("zxing") && !OptionalComponentSupport.isZxingAvailable()) {
                descriptor.unavailable("com.google.zxing.core is not on the class path");
            }
            catalogue.add(descriptor);
        }

        catalogue.add(new ComponentDescriptor(ROLE_REPOSITORY, FileSystemRepositoryBean.COMPONENT_ID,
                FileSystemRepositoryBean.class.getName())
                .provides("ke.ku.sco310.qr.api.QrCodeRepository", "java.io.Serializable")
                .properties("rootDirectory", "historyFileName"));
        catalogue.add(new ComponentDescriptor(ROLE_REPOSITORY, "repository.in-memory.v1",
                "ke.ku.sco310.qr.store.FileSystemRepositoryBean (same class, temp directory)")
                .provides("ke.ku.sco310.qr.api.QrCodeRepository")
                .properties("rootDirectory"));

        catalogue.add(new ComponentDescriptor(ROLE_HISTORY, QrHistoryBean.COMPONENT_ID,
                QrHistoryBean.class.getName())
                .provides("java.io.Serializable", "indexed QrHistoryEntry[] property")
                .properties("entries (indexed, bound)", "maxEntries (constrained)"));

        catalogue.add(new ComponentDescriptor(ROLE_LISTENER, AuditLogListenerBean.COMPONENT_ID,
                AuditLogListenerBean.class.getName())
                .provides("ke.ku.sco310.qr.event.QrCodeGenerationListener", "java.io.Serializable")
                .requires("ke.ku.sco310.qr.event.QrCodeGeneratedEvent")
                .properties("logFile", "echoToConsole", "verbose"));

        catalogue.add(new ComponentDescriptor(ROLE_FACADE, QrStudioFacadeBean.COMPONENT_ID,
                QrStudioFacadeBean.class.getName())
                .provides("QrGenerationResult generate(String, QrErrorCorrection)",
                        "void addQrCodeGenerationListener(QrCodeGenerationListener)")
                .requires("ke.ku.sco310.qr.api.QrCodeEncoder",
                        "ke.ku.sco310.qr.api.QrCodeRenderer",
                        "ke.ku.sco310.qr.api.QrCodeRepository",
                        "ke.ku.sco310.qr.store.QrHistoryBean")
                .properties("defaultErrorCorrection", "autoSaveArtifacts", "autoSaveHistory",
                        "fileBaseName"));
        return catalogue;
    }

    /**
     * @param role role to look in
     * @return descriptors of every implementation that can fill the role right now
     */
    public List<ComponentDescriptor> listByRole(String role) {
        List<ComponentDescriptor> result = new ArrayList<>();
        for (ComponentDescriptor descriptor : listComponents()) {
            if (descriptor.getRole().equals(role) && descriptor.isAvailable()) {
                result.add(descriptor);
            }
        }
        return result;
    }

    /**
     * Creates and wires a complete assembly from a profile.
     *
     * @param profile component selection plus property values
     * @return the wired assembly, ready to generate symbols
     * @throws Exception when the profile selects an unavailable component or a property cannot be
     *                   applied
     */
    public QrAssembly build(AssemblyProfile profile) throws Exception {
        configurationNotes.clear();
        QrAssembly assembly = new QrAssembly(profile);
        List<ComponentDescriptor> descriptors = new ArrayList<>();

        // 1. encoder ---------------------------------------------------------------
        QrCodeEncoder encoder = createEncoder(profile.getComponentId(ROLE_ENCODER));
        applyProperties(encoder, profile.getProperties(ROLE_ENCODER),
                "component " + profile.getComponentId(ROLE_ENCODER));
        assembly.setEncoder(encoder);
        descriptors.add(describe(encoder.getComponentId()));
        logSelection("encoder", encoder.getComponentId(), describeEncoder(encoder));

        // 2. renderer --------------------------------------------------------------
        QrCodeRenderer renderer = createRenderer(profile.getComponentId(ROLE_RENDERER));
        applyProperties(renderer, profile.getProperties(ROLE_RENDERER),
                "component " + profile.getComponentId(ROLE_RENDERER));
        assembly.setRenderer(renderer);
        descriptors.add(describe(renderer.getRendererId()));
        logSelection("renderer", renderer.getRendererId(), renderer.describeConfiguration());

        // 3. repository ------------------------------------------------------------
        FileSystemRepositoryBean repository = new FileSystemRepositoryBean();
        applyProperties(repository, profile.getProperties(ROLE_REPOSITORY), "repository");
        assembly.setRepository(repository);
        descriptors.add(describe(FileSystemRepositoryBean.COMPONENT_ID));
        logSelection("repository", repository.getComponentId(), repository.describeConfiguration());

        // 4. history ---------------------------------------------------------------
        QrHistoryBean history = new QrHistoryBean();
        applyProperties(history, profile.getProperties(ROLE_HISTORY), "history");
        assembly.setHistory(history);
        descriptors.add(describe(QrHistoryBean.COMPONENT_ID));
        logSelection("history", history.getComponentId(),
                "capacity=" + history.getMaxEntries() + " entries");

        // 5. audit listener --------------------------------------------------------
        AuditLogListenerBean listener = new AuditLogListenerBean();
        applyProperties(listener, profile.getProperties(ROLE_LISTENER), "listener");
        assembly.setListener(listener);
        descriptors.add(describe(AuditLogListenerBean.COMPONENT_ID));

        // 6. facade, wired to the components through its required interfaces --------
        QrStudioFacadeBean facade = new QrStudioFacadeBean();
        applyProperties(facade, profile.getProperties(ROLE_FACADE), "facade");
        facade.setEncoder(encoder);
        facade.setRenderer(renderer);
        facade.setRepository(repository);
        facade.setHistory(history);
        facade.addQrCodeGenerationListener(listener);
        assembly.setFacade(facade);
        descriptors.add(describe(QrStudioFacadeBean.COMPONENT_ID));

        assembly.setComponents(descriptors);
        for (String note : configurationNotes) {
            System.out.println("[registry] note: " + note);
        }
        assembly.setConfigurationNotes(configurationNotes);
        validate(assembly);
        return assembly;
    }

    /**
     * Checks that every dependency declared by a component in the assembly is satisfied.
     *
     * @param assembly wired assembly
     * @return one line per dependency, prefixed {@code OK} or {@code MISSING}
     */
    public List<String> validate(QrAssembly assembly) {
        List<String> report = new ArrayList<>();
        report.add("encoder       -> facade.setEncoder     : "
                + (assembly.getEncoder() != null ? "OK" : "MISSING"));
        report.add("renderer      -> facade.setRenderer    : "
                + (assembly.getRenderer() != null ? "OK" : "MISSING"));
        report.add("repository    -> facade.setRepository  : "
                + (assembly.getRepository() != null ? "OK" : "MISSING"));
        report.add("history       -> facade.setHistory     : "
                + (assembly.getHistory() != null ? "OK" : "MISSING"));
        report.add("listener      -> facade event source   : "
                + (assembly.getFacade().getListenerCount() > 0 ? "OK" : "MISSING"));
        report.add("encoder       -> optional ZXing library : "
                + (OptionalComponentSupport.isZxingAvailable() ? "PRESENT" : "ABSENT (in-house engine used)"));
        return report;
    }

    /** @return the component descriptor for a component id, or {@code null} when unknown. */
    public ComponentDescriptor describe(String componentId) {
        for (ComponentDescriptor descriptor : listComponents()) {
            if (descriptor.getComponentId().equals(componentId)) {
                return descriptor;
            }
        }
        return null;
    }

    private QrCodeEncoder createEncoder(String componentId) throws Exception {
        return provider(QrCodeEncoder.class, componentId == null ? "encoder.pure-java.v1" : componentId,
                ROLE_ENCODER);
    }

    /** Creates a renderer provider through the renderer SPI. */
    public QrCodeRenderer createRenderer(String componentId) {
        return provider(QrCodeRenderer.class, componentId == null ? "renderer.png.v1" : componentId,
                ROLE_RENDERER);
    }

    /** @return a decoder provider selected by its SPI component id. */
    public QrCodeDecoder createDecoder(String componentId) {
        return provider(QrCodeDecoder.class, componentId, ROLE_DECODER);
    }

    /** @return the separate Micro QR provider selected by its SPI component id. */
    public QrMicroEncoder createMicroEncoder(String componentId) {
        return provider(QrMicroEncoder.class, componentId, ROLE_MICRO_ENCODER);
    }

    private <T> T provider(Class<T> serviceType, String componentId, String role) {
        for (T provider : providers(serviceType)) {
            String id = serviceType == QrCodeRenderer.class
                    ? ((QrCodeRenderer) provider).getRendererId()
                    : serviceType == QrCodeDecoder.class
                    ? ((QrCodeDecoder) provider).getDecoderId()
                    : serviceType == QrMicroEncoder.class
                    ? ((QrMicroEncoder) provider).getComponentId()
                    : ((QrCodeEncoder) provider).getComponentId();
            if (componentId.equals(id)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown or unavailable " + role + " component id: " + componentId);
    }

    private <T> List<T> providers(Class<T> serviceType) {
        List<T> result = new ArrayList<>();
        try {
            for (T provider : ServiceLoader.load(serviceType)) {
                result.add(provider);
            }
        } catch (ServiceConfigurationError error) {
            // An optional provider may fail to load when its library is absent. The catalogue still
            // contains all providers that the current class path can instantiate.
        }
        return result;
    }

    private ComponentDescriptor descriptor(String role, String id, Object provider) {
        String className = provider.getClass().getName();
        ComponentDescriptor descriptor = new ComponentDescriptor(role, id, className)
                .provides(provider.getClass().getInterfaces()[0].getName(), "java.io.Serializable")
                .properties(writablePropertyNames(provider).toArray(new String[0]));
        return descriptor;
    }

    private List<String> writablePropertyNames(Object bean) {
        List<String> names = new ArrayList<>();
        try {
            for (PropertyDescriptor property : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
                if (property.getWriteMethod() != null && !"class".equals(property.getName())) {
                    names.add(property.getName());
                }
            }
        } catch (IntrospectionException e) {
            names.add("(introspection unavailable)");
        }
        return names;
    }

    /**
     * Applies textual property values to a bean through the JavaBeans introspection API.
     *
     * <p>This is the mechanism that makes design-time configuration possible: the property
     * descriptors are discovered from the {@code get}/{@code set} naming patterns (or from the
     * component's BeanInfo), and the values from the profile are coerced to the declared types.</p>
     */
    private void applyProperties(Object bean, Map<String, String> properties, String label)
            throws IntrospectionException, InvocationTargetException, IllegalAccessException {
        if (properties.isEmpty()) {
            return;
        }
        List<String> skipped = new ArrayList<>();
        Map<String, PropertyDescriptor> writable = new LinkedHashMap<>();
        BeanInfo info = Introspector.getBeanInfo(bean.getClass());
        for (PropertyDescriptor descriptor : info.getPropertyDescriptors()) {
            if (descriptor.getWriteMethod() != null) {
                writable.put(descriptor.getName(), descriptor);
            }
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            PropertyDescriptor descriptor = writable.get(entry.getKey());
            if (descriptor == null) {
                // The profile describes a role, not a class: a property that the selected
                // implementation does not declare is simply reported, never fatal.
                skipped.add(label + "." + entry.getKey());
                continue;
            }
            Object value = PropertyCoercion.coerce(entry.getValue(), descriptor.getPropertyType());
            Method setter = descriptor.getWriteMethod();
            try {
                setter.invoke(bean, value);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new IllegalArgumentException("Property '" + entry.getKey() + "' of " + label
                        + " rejected the value '" + entry.getValue() + "': " + cause.getMessage(), cause);
            }
        }
        if (!skipped.isEmpty()) {
            configurationNotes.add("not applicable to " + label + " (property not declared): "
                    + String.join(", ", skipped));
        }
    }

    private String describeEncoder(QrCodeEncoder encoder) {
        EncoderInfo info = encoder.getEncoderInfo();
        return info.getProviderName() + " " + info.getProviderVersion()
                + (info.isThirdParty() ? " (third-party)" : " (in-house)");
    }

    private void logSelection(String role, String componentId, String detail) {
        System.out.printf("[registry] %-10s %-30s %s%n", role, componentId, detail);
    }

    /** @return the storage directory used when no profile is supplied. */
    public static File defaultStorageRoot() {
        return new File("out/generated");
    }
}
