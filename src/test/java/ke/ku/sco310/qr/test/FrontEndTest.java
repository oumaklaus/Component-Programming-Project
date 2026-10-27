package ke.ku.sco310.qr.test;

import ke.ku.sco310.qr.cli.QrCli;
import ke.ku.sco310.qr.facade.AssemblyProfile;
import ke.ku.sco310.qr.facade.ComponentRegistry;
import ke.ku.sco310.qr.facade.QrAssembly;
import ke.ku.sco310.qr.web.QrHttpServiceComponent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** End-to-end checks for the CLI commands, batch reporting and hardened HTTP service. */
final class FrontEndTest {

    private FrontEndTest() {
    }

    static void run(TestHarness harness, File workDirectory) throws Exception {
        Path cliDirectory = new File(workDirectory, "cli").toPath();
        Files.createDirectories(cliDirectory);
        ByteArrayOutputStream cliOutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream cliErrBytes = new ByteArrayOutputStream();
        PrintStream cliOut = new PrintStream(cliOutBytes, true, StandardCharsets.UTF_8.name());
        PrintStream cliErr = new PrintStream(cliErrBytes, true, StandardCharsets.UTF_8.name());
        Path svg = cliDirectory.resolve("hello.svg");
        int generateStatus = QrCli.run(new String[]{"generate", "--text", "CLI hello", "--renderer", "svg",
                "--ec", "M", "--out", svg.toString()}, cliOut, cliErr);
        harness.check("frontends.cli", "generate writes the requested renderer artifact",
                generateStatus == 0 && Files.isRegularFile(svg) && Files.size(svg) > 100,
                "status=" + generateStatus + " bytes=" + Files.size(svg));
        int decodeStatus = QrCli.run(new String[]{"decode", "--in", svg.toString()}, cliOut, cliErr);
        harness.check("frontends.cli", "decode recovers a generated artifact",
                decodeStatus == 0 && cliOutBytes.toString(StandardCharsets.UTF_8.name()).contains("CLI hello"),
                cliOutBytes.toString(StandardCharsets.UTF_8.name()));
        int listStatus = QrCli.run(new String[]{"list-components"}, cliOut, cliErr);
        harness.check("frontends.cli", "list-components publishes the SPI catalogue",
                listStatus == 0 && cliOutBytes.toString(StandardCharsets.UTF_8.name()).contains("renderer.pdf.v1"),
                "status=" + listStatus);

        Path csv = cliDirectory.resolve("jobs.csv");
        Files.write(csv, java.util.Arrays.asList(
                "text,filename,ec,format",
                "batch one,one.svg,M,svg",
                "broken,two.png,Z,png"), StandardCharsets.UTF_8);
        Path batchOut = cliDirectory.resolve("batch");
        int batchStatus = QrCli.run(new String[]{"batch", "--csv", csv.toString(), "--out-dir", batchOut.toString()},
                cliOut, cliErr);
        String summary = Files.readString(batchOut.resolve("batch-summary.txt"));
        harness.check("frontends.batch", "batch writes successes and reports row errors",
                batchStatus == 10 && Files.isRegularFile(batchOut.resolve("one.svg"))
                        && summary.contains("succeeded=1") && summary.contains("failed=1"), summary);

        AssemblyProfile profile = new AssemblyProfile();
        profile.setProperty("repository", "rootDirectory", new File(workDirectory, "http").getPath());
        profile.setProperty("facade", "autoSaveArtifacts", "false");
        profile.setProperty("facade", "autoSaveHistory", "true");
        QrAssembly assembly = new ComponentRegistry().build(profile);
        QrHttpServiceComponent service = new QrHttpServiceComponent(assembly);
        service.setMaxPayloadBytes(12);
        service.setCorsOrigin("https://client.example");
        service.start(0);
        HttpClient client = HttpClient.newHttpClient();
        try {
            String base = "http://localhost:" + service.getPort();
            HttpResponse<String> generated = get(client, base + "/qr?text=hello%20http&format=svg");
            harness.check("frontends.http", "QR endpoint returns a cached SVG renderer artifact",
                    generated.statusCode() == 200 && generated.headers().firstValue("Access-Control-Allow-Origin")
                            .orElse("").equals("https://client.example")
                            && generated.body().contains("<svg"),
                    generated.statusCode() + " " + generated.body());
            get(client, base + "/qr?text=hello%20http&format=svg");
            harness.checkEquals("frontends.http", "identical requests reuse one renderer instance", 1,
                    service.getCachedRendererCount());

            get(client, base + "/qr?text=line1%0A%22%C3%A9%22&format=svg");
            HttpResponse<String> history = get(client, base + "/history");
            harness.check("frontends.http", "JSON escaping protects control and unicode characters",
                    history.statusCode() == 200 && history.body().contains("line1\\n\\\"\\u00E9\\\""), history.body());
            HttpResponse<String> components = get(client, base + "/components");
            harness.check("frontends.http", "components endpoint lists the discovered catalogue",
                    components.statusCode() == 200 && components.body().contains("renderer.pdf.v1"),
                    components.body());
            HttpResponse<String> tooLarge = get(client, base + "/qr?text=1234567890123&format=svg");
            harness.checkEquals("frontends.http", "payload limit returns HTTP 413", 413, tooLarge.statusCode());
            HttpResponse<String> missing = get(client, base + "/qr");
            harness.checkEquals("frontends.http", "missing text returns HTTP 400", 400, missing.statusCode());
            HttpResponse<String> stats = get(client, base + "/stats");
            harness.check("frontends.http", "request log counter includes all endpoint calls",
                    stats.statusCode() == 200 && stats.body().contains("\"requests\""), stats.body());
        } finally {
            service.stop();
        }
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
