package ke.ku.sco310.qr.facade;

import ke.ku.sco310.qr.api.QrErrorCorrection;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declarative description of one assembly: which component implementation plays which role and
 * with which design-time property values.
 *
 * <p>This is the project's deployment descriptor. The same compiled components are deployed as a
 * desktop GUI with a different component selection - deployment model
 * D = (C, E, P, L), where only the parameters P change.</p>
 */
public class AssemblyProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Name of the profile file loaded by default. */
    public static final String DEFAULT_RESOURCE = "config/assembly.xml";

    private String profileName = "default";
    private final LinkedHashMap<String, String> componentIds = new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<String, String>> roleProperties = new LinkedHashMap<>();

    public AssemblyProfile() {
        componentIds.put("encoder", "encoder.pure-java.v1");
        componentIds.put("renderer", "renderer.png.v1");
        componentIds.put("repository", "repository.file-system.v1");
        componentIds.put("history", "history.bounded-memory.v1");
        componentIds.put("listener", "listener.audit-log.v1");
        componentIds.put("facade", "facade.qr-studio.v1");
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    /** @return the component id chosen for a role ({@code encoder}, {@code renderer}, ...). */
    public String getComponentId(String role) {
        return componentIds.get(role);
    }

    public void setComponentId(String role, String componentId) {
        componentIds.put(role, componentId);
    }

    public Map<String, String> getComponentIds() {
        return new LinkedHashMap<>(componentIds);
    }

    /** @return the property overrides declared for one role, possibly empty. */
    public Map<String, String> getProperties(String role) {
        LinkedHashMap<String, String> properties = roleProperties.get(role);
        return properties == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<>(properties);
    }

    public void setProperty(String role, String name, String value) {
        roleProperties.computeIfAbsent(role, key -> new LinkedHashMap<>()).put(name, value);
    }

    /** @return the declared roles in declaration order. */
    public List<String> getRoles() {
        return new ArrayList<>(componentIds.keySet());
    }

    /** @return the profile name, the component selection and every override as readable text. */
    public String describe() {
        StringBuilder sb = new StringBuilder("Assembly profile '" + profileName + "':\n");
        for (Map.Entry<String, String> entry : componentIds.entrySet()) {
            sb.append(String.format("  %-11s -> %s%n", entry.getKey(), entry.getValue()));
            for (Map.Entry<String, String> property : getProperties(entry.getKey()).entrySet()) {
                sb.append(String.format("                 %s = %s%n", property.getKey(), property.getValue()));
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ convenience accessors

    /** @return the default error-correction level declared for the facade. */
    public QrErrorCorrection getDefaultErrorCorrection() {
        String value = getProperties("facade").get("defaultErrorCorrection");
        return value == null ? QrErrorCorrection.MEDIUM : QrErrorCorrection.fromLabel(value);
    }

    /** @return the storage directory declared for the repository. */
    public File getStorageRoot() {
        String value = getProperties("repository").get("rootDirectory");
        return new File(value == null ? "out/generated" : value);
    }

    /** @return the history capacity declared for the history component. */
    public int getHistoryCapacity() {
        String value = getProperties("history").get("maxEntries");
        return value == null ? 50 : Integer.parseInt(value);
    }

    // ------------------------------------------------------------------ persistence

    /**
     * Reads a profile from an XML descriptor.
     *
     * @param file descriptor file
     * @return the parsed profile
     * @throws Exception when the file is missing or malformed
     */
    public static AssemblyProfile load(File file) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        org.w3c.dom.Document document = factory.newDocumentBuilder().parse(file);
        document.getDocumentElement().normalize();

        AssemblyProfile profile = new AssemblyProfile();
        String name = document.getDocumentElement().getAttribute("name");
        if (name != null && !name.isEmpty()) {
            profile.setProfileName(name);
        }
        org.w3c.dom.NodeList components = document.getElementsByTagName("component");
        for (int i = 0; i < components.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) components.item(i);
            profile.setComponentId(element.getAttribute("role"), element.getAttribute("id"));
        }
        org.w3c.dom.NodeList properties = document.getElementsByTagName("property");
        for (int i = 0; i < properties.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) properties.item(i);
            profile.setProperty(element.getAttribute("role"), element.getAttribute("name"),
                    element.getAttribute("value"));
        }
        return profile;
    }

    /**
     * Writes this profile back to an XML descriptor.
     *
     * @param file destination file
     * @throws Exception when the document cannot be produced
     */
    public void save(File file) throws Exception {
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().newDocument();
        org.w3c.dom.Element root = document.createElement("qr-assembly");
        root.setAttribute("name", profileName);
        document.appendChild(root);
        for (Map.Entry<String, String> entry : componentIds.entrySet()) {
            org.w3c.dom.Element component = document.createElement("component");
            component.setAttribute("role", entry.getKey());
            component.setAttribute("id", entry.getValue());
            root.appendChild(component);
        }
        for (Map.Entry<String, LinkedHashMap<String, String>> role : roleProperties.entrySet()) {
            for (Map.Entry<String, String> property : role.getValue().entrySet()) {
                org.w3c.dom.Element element = document.createElement("property");
                element.setAttribute("role", role.getKey());
                element.setAttribute("name", property.getKey());
                element.setAttribute("value", property.getValue());
                root.appendChild(element);
            }
        }
        javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory
                .newInstance().newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        transformer.transform(new javax.xml.transform.dom.DOMSource(document),
                new javax.xml.transform.stream.StreamResult(file));
    }

    @Override
    public String toString() {
        return "AssemblyProfile[" + profileName + " " + componentIds + "]";
    }
}
