package ke.ku.sco310.qr.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ke.ku.sco310.qr.api.QrCodeRenderer;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.api.QrGenerationResult;
import ke.ku.sco310.qr.facade.AssemblyProfile;
import ke.ku.sco310.qr.facade.ComponentRegistry;
import ke.ku.sco310.qr.facade.QrAssembly;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Web service assembly of the component suite (front end number five in the course material: a
 * component published over HTTP).
 *
 * <p>The service exposes the <em>same</em> facade component that the GUI uses; the only new code
 * is HTTP plumbing. Per request the assembly instantiates the renderer component that the query
 * string asks for and hands it to the facade, which proves in one deployment that renderer
 * components are interchangeable at run time.</p>
 *
 * <pre>
 * GET /                 HTML form
 * GET /qr?text=...      PNG by default; format=png|svg|ascii, ec=L|M|Q|H, moduleSize, quietZone
 * GET /stats            JSON capability and counter report of the running assembly
 * GET /components       JSON catalogue discovered through ServiceLoader
 * GET /history?limit=n  JSON of the persisted history records
 * GET /health           "OK"
 * </pre>
 */
public class QrHttpServiceComponent {

    private final QrAssembly assembly;
    private final ComponentRegistry registry = new ComponentRegistry();
    private final AtomicLong requestCount = new AtomicLong();
    private final Map<String, QrCodeRenderer> rendererCache = new ConcurrentHashMap<>();
    private volatile int maxPayloadBytes = 4096;
    private volatile String corsOrigin = "";
    private volatile HttpServer server;
    private volatile ExecutorService executor;

    public QrHttpServiceComponent(QrAssembly assembly) {
        this.assembly = assembly;
    }

    public static void main(String[] args) throws Exception {
        int port = 8099;
        int maxPayloadBytes = 4096;
        String corsOrigin = "";
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i])) {
                port = Integer.parseInt(args[++i]);
            } else if ("--max-payload-bytes".equals(args[i])) {
                maxPayloadBytes = Integer.parseInt(args[++i]);
            } else if ("--cors-origin".equals(args[i])) {
                corsOrigin = args[++i];
            }
        }
        AssemblyProfile profile = new AssemblyProfile();
        File profileFile = new File(AssemblyProfile.DEFAULT_RESOURCE);
        if (profileFile.isFile()) {
            profile = AssemblyProfile.load(profileFile);
        }
        QrAssembly assembly = new ComponentRegistry().build(profile);
        System.out.println(assembly.describeAssembly());
        QrHttpServiceComponent service = new QrHttpServiceComponent(assembly);
        service.setMaxPayloadBytes(maxPayloadBytes);
        service.setCorsOrigin(corsOrigin);
        service.start(port);
    }

    /**
     * Starts the HTTP endpoint.
     *
     * @param port TCP port to listen on
     * @throws IOException when the socket cannot be bound
     */
    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        server.setExecutor(pool);
        server.createContext("/", this::handleIndex);
        server.createContext("/qr", this::handleQr);
        server.createContext("/stats", this::handleStats);
        server.createContext("/history", this::handleHistory);
        server.createContext("/components", this::handleComponents);
        server.createContext("/health", this::handleHealth);
        this.server = server;
        this.executor = pool;
        server.start();
        System.out.println("[web] QR component service listening on http://localhost:" + port + "/");
        System.out.println("[web] try: curl -o code.png 'http://localhost:" + port
                + "/qr?text=HELLO&ec=M&format=png'");
    }

    /** Stops the running service; primarily useful to an embedding application or verification. */
    public void stop() {
        HttpServer running = server;
        if (running != null) {
            running.stop(0);
            server = null;
        }
        ExecutorService pool = executor;
        if (pool != null) {
            pool.shutdownNow();
            executor = null;
        }
    }

    /** @return the bound TCP port, or {@code -1} before {@link #start(int)}. */
    public int getPort() {
        HttpServer running = server;
        return running == null ? -1 : running.getAddress().getPort();
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        String html = "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<title>SCO310 QR component service</title></head><body>"
                + "<h1>QR Code generator - component service assembly</h1>"
                + "<p>Wired components: <code>" + htmlEscape(assembly.getFacade().describeWiring()) + "</code></p>"
                + "<form action=\"/qr\" method=\"get\">"
                + "<label>Payload <input name=\"text\" size=\"60\" value=\"https://www.ku.ac.ke\"></label><br>"
                + "<label>Level <select name=\"ec\"><option>L</option><option selected>M</option>"
                + "<option>Q</option><option>H</option></select></label> "
                + "<label>Format <select name=\"format\"><option>png</option><option>svg</option>"
                + "<option>ascii</option></select></label> "
                + "<label>Module size <input name=\"moduleSize\" value=\"8\" size=\"3\"></label> "
                + "<label>Quiet zone <input name=\"quietZone\" value=\"4\" size=\"3\"></label> "
                + "<button type=\"submit\">Generate</button></form>"
                + "<p>Machine endpoints: <a href=\"/stats\">/stats</a>, <a href=\"/history\">/history</a>, "
                + "<a href=\"/health\">/health</a></p>"
                + "</body></html>";
        respond(exchange, 200, "text/html; charset=utf-8", html);
    }

    private void handleQr(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        Map<String, String> query = parseQuery(exchange.getRequestURI());
        String text = query.get("text");
        if (text == null || text.isEmpty()) {
            respond(exchange, 400, "text/plain; charset=utf-8", "Missing 'text' parameter");
            return;
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > maxPayloadBytes) {
            respond(exchange, 413, "text/plain; charset=utf-8",
                    "Payload exceeds the configured limit of " + maxPayloadBytes + " UTF-8 bytes");
            return;
        }
        try {
            QrErrorCorrection level = QrErrorCorrection.fromLabel(
                    query.getOrDefault("ec", assembly.getFacade().getDefaultErrorCorrection().getLabel()));
            QrCodeRenderer renderer = rendererFor(query);
            QrGenerationResult result = assembly.getFacade().generate(text, level, renderer);
            byte[] body = result.getArtifact().getContent();
            exchange.getResponseHeaders().add("X-QR-Version", String.valueOf(result.getSymbol().getVersion()));
            exchange.getResponseHeaders().add("X-QR-Level", result.getSymbol().getErrorCorrection().getLabel());
            exchange.getResponseHeaders().add("X-QR-Mask", String.valueOf(result.getSymbol().getMask()));
            exchange.getResponseHeaders().add("X-QR-Renderer", renderer.getRendererId());
            exchange.getResponseHeaders().add("X-QR-Stored",
                    result.getStoredFile() == null ? "none" : result.getStoredFile().getName());
            respond(exchange, 200, renderer.getMimeType(), body);
        } catch (Exception e) {
            respond(exchange, 400, "text/plain; charset=utf-8", "Generation failed: " + e.getMessage());
        }
    }

    private QrCodeRenderer rendererFor(Map<String, String> query) {
        String format = query.getOrDefault("format", "png").toLowerCase(Locale.ROOT);
        int moduleSize = parseInt(query.get("moduleSize"), 8);
        int quietZone = parseInt(query.get("quietZone"), 4);
        String normalized = "text".equals(format) ? "ascii" : format;
        String id = normalized.contains(".") ? normalized : "renderer." + normalized + ".v1";
        if ("renderer.rounded.v1".equals(id)) id = "renderer.rounded-png.v1";
        final String rendererId = id;
        String key = rendererId + ":" + moduleSize + ":" + quietZone;
        return rendererCache.computeIfAbsent(key, ignored -> {
            try {
                QrCodeRenderer renderer = registry.createRenderer(rendererId);
                setProperty(renderer, "moduleSize", moduleSize);
                setProperty(renderer, "quietZone", quietZone);
                return renderer;
            } catch (Exception e) {
                throw new IllegalArgumentException("Unknown renderer '" + format + "': " + e.getMessage(), e);
            }
        });
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"service\": ").append(JsonUtil.quote("SCO310 QR component service")).append(",\n");
        json.append("  \"requests\": ").append(requestCount.get()).append(",\n");
        json.append("  \"generations\": ").append(assembly.getFacade().getGeneratedCount()).append(",\n");
        json.append("  \"auditEvents\": ").append(assembly.getListener().getProcessedEventCount()).append(",\n");
        json.append("  \"historyEntries\": ").append(assembly.getHistory().getEntryCount()).append(",\n");
        json.append("  \"wiring\": ").append(JsonUtil.quote(assembly.getFacade().describeWiring())).append(",\n");
        json.append("  \"components\": [\n");
        for (int i = 0; i < assembly.getComponents().size(); i++) {
            ke.ku.sco310.qr.facade.ComponentDescriptor d = assembly.getComponents().get(i);
            json.append("    {\"role\": ").append(JsonUtil.quote(d.getRole())).append(", \"id\": ")
                .append(JsonUtil.quote(d.getComponentId())).append(", \"available\": ").append(d.isAvailable())
                .append("}");
            json.append(i + 1 < assembly.getComponents().size() ? ",\n" : "\n");
        }
        json.append("  ]\n}\n");
        respond(exchange, 200, "application/json; charset=utf-8", json.toString());
    }

    private void handleHistory(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        Map<String, String> query = parseQuery(exchange.getRequestURI());
        int limit = parseInt(query.get("limit"), 10);
        StringBuilder json = new StringBuilder("[\n");
        java.util.List<ke.ku.sco310.qr.api.QrHistoryEntry> entries =
                assembly.getRepository().getHistorySnapshot();
        int from = Math.max(0, entries.size() - limit);
        for (int i = from; i < entries.size(); i++) {
            ke.ku.sco310.qr.api.QrHistoryEntry entry = entries.get(i);
            json.append("  {\"timestamp\": ").append(entry.getTimestamp())
                .append(", \"content\": ").append(JsonUtil.quote(entry.getContent()))
                .append(", \"version\": ").append(entry.getVersion())
                .append(", \"level\": ").append(JsonUtil.quote(entry.getErrorCorrectionLabel()))
                .append(", \"artifact\": ").append(JsonUtil.quote(entry.getArtifactFileName())).append("}");
            json.append(i + 1 < entries.size() ? ",\n" : "\n");
        }
        json.append("]\n");
        respond(exchange, 200, "application/json; charset=utf-8", json.toString());
    }

    private void handleComponents(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        StringBuilder json = new StringBuilder("[\n");
        java.util.List<ke.ku.sco310.qr.facade.ComponentDescriptor> components = registry.listComponents();
        for (int i = 0; i < components.size(); i++) {
            ke.ku.sco310.qr.facade.ComponentDescriptor component = components.get(i);
            json.append("  {\"role\": ").append(JsonUtil.quote(component.getRole()))
                    .append(", \"id\": ").append(JsonUtil.quote(component.getComponentId()))
                    .append(", \"implementation\": ").append(JsonUtil.quote(component.getImplementationClass()))
                    .append(", \"available\": ").append(component.isAvailable())
                    .append(", \"reason\": ").append(JsonUtil.quote(component.getUnavailableReason())).append("}");
            json.append(i + 1 < components.size() ? ",\n" : "\n");
        }
        json.append("]\n");
        respond(exchange, 200, "application/json; charset=utf-8", json.toString());
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        respond(exchange, 200, "text/plain", "OK");
    }

    static Map<String, String> parseQuery(URI uri) {
        Map<String, String> parameters = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return parameters;
        }
        for (String pair : raw.split("&")) {
            int equals = pair.indexOf('=');
            if (equals < 0) {
                parameters.put(decode(pair), "");
            } else {
                parameters.put(decode(pair.substring(0, equals)), decode(pair.substring(equals + 1)));
            }
        }
        return parameters;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String htmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void setProperty(Object bean, String name, int value) throws Exception {
        for (PropertyDescriptor descriptor : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
            if (name.equals(descriptor.getName()) && descriptor.getWriteMethod() != null) {
                Class<?> type = descriptor.getPropertyType();
                if (type == double.class) {
                    descriptor.getWriteMethod().invoke(bean, Double.valueOf(value));
                } else if (type == int.class) {
                    descriptor.getWriteMethod().invoke(bean, Integer.valueOf(value));
                }
                return;
            }
        }
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        respond(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private void respond(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        if (!corsOrigin.isEmpty()) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", corsOrigin);
        }
        System.out.println("[http] " + exchange.getRequestMethod() + " " + exchange.getRequestURI()
                + " -> " + status + " (" + body.length + " bytes)");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** @return the registry used by this service instance (exposed for the verification report). */
    public ComponentRegistry getRegistry() {
        return registry;
    }

    /** @return number of renderer configurations retained for reuse. */
    public int getCachedRendererCount() {
        return rendererCache.size();
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        if (maxPayloadBytes < 1 || maxPayloadBytes > 10_000_000) {
            throw new IllegalArgumentException("maxPayloadBytes must be 1..10000000");
        }
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public String getCorsOrigin() {
        return corsOrigin;
    }

    public void setCorsOrigin(String corsOrigin) {
        this.corsOrigin = corsOrigin == null ? "" : corsOrigin.trim();
    }
}
