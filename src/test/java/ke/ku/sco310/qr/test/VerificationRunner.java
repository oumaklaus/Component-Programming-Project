package ke.ku.sco310.qr.test;

import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.api.RenderedArtifact;
import ke.ku.sco310.qr.components.PureJavaQrEncoderBean;
import ke.ku.sco310.qr.components.ZxingQrEncoderAdapterBean;
import ke.ku.sco310.qr.facade.AssemblyProfile;
import ke.ku.sco310.qr.facade.ComponentRegistry;
import ke.ku.sco310.qr.facade.QrAssembly;
import ke.ku.sco310.qr.render.AsciiRendererBean;
import ke.ku.sco310.qr.render.PngRendererBean;
import ke.ku.sco310.qr.render.SvgRendererBean;
import ke.ku.sco310.qr.tools.ProjectBuilderTest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Verification entry point of the project.
 *
 * <pre>
 * java -cp out/classes:out/test-classes:lib/core-3.5.3.jar:lib/javase-3.5.3.jar \
 *      ke.ku.sco310.qr.test.VerificationRunner
 * </pre>
 *
 * <p>It runs every automated check, then writes the evidence folder used by the report: the raw
 * transcript, a CSV and a Markdown table of the results, the ISO/IEC 18004 table cross-check, the
 * 160-combination capacity matrix, and a gallery of real artifacts with their SHA-256 digests and
 * the payload recovered from each one by the independent decoder.</p>
 */
public final class VerificationRunner {

    private VerificationRunner() {
    }

    public static void main(String[] args) throws Exception {
        File evidence = evidenceDirectory(args);
        File work = new File(evidence, "work");
        File gallery = new File(evidence, "gallery");
        work.mkdirs();
        gallery.mkdirs();

        TestHarness harness = new TestHarness();
        List<String> tableCsv = new ArrayList<>();
        List<String> capacityCsv = new ArrayList<>();
        List<String> rendererCsv = new ArrayList<>();
        List<String> artifactCsv = new ArrayList<>();

        System.out.println("=== SCO310 QR component suite - verification run ===");
        System.out.println("evidence directory: " + evidence.getAbsolutePath());

        EngineReferenceTest.run(harness, tableCsv, capacityCsv);
        MicroQrReferenceTest.run(harness);
        ExtensionComponentTest.run(harness);
        FrontEndTest.run(harness, new File(evidence, "work/frontends"));
        ProjectBuilderTest.run(harness);
        RendererTest.run(harness, rendererCsv);
        BeanContractTest.run(harness, work);
        AssemblyTest.run(harness, work);
        buildGallery(harness, gallery, artifactCsv);

        String transcript = harness.toTextReport();
        System.out.println(transcript);

        write(new File(evidence, "test-report.txt"),
                "SCO310 QR component suite - automated verification transcript\n"
                        + "generated: " + new java.util.Date() + "\n"
                        + "java: " + System.getProperty("java.version") + " ("
                        + System.getProperty("java.vendor") + ")\n"
                        + "host: " + System.getProperty("os.name") + " " + System.getProperty("os.arch") + "\n\n"
                        + transcript + "\n" + summariseTableCheck(tableCsv));
        write(new File(evidence, "test-results.csv"), harness.toCsv());
        write(new File(evidence, "test-results.md"), "# Automated verification results\n\n"
                + harness.toMarkdown() + "\n\nSummary: " + harness.getTotal() + " checks, "
                + (harness.getTotal() - harness.getFailures()) + " passed, "
                + harness.getFailures() + " failed.\n");
        write(new File(evidence, "table-cross-check.csv"),
                "version,level,zxing_ecc_per_block,ours_ecc_per_block,zxing_blocks,ours_blocks,"
                        + "zxing_data_codewords,ours_data_codewords,zxing_total_codewords,"
                        + "ours_total_codewords,verdict\n" + String.join("\n", tableCsv) + "\n");
        write(new File(evidence, "capacity-matrix.csv"),
                "version,level,payload_bytes,symbol_version,modules,data_codewords,ecc_codewords,verdict\n"
                        + String.join("\n", capacityCsv) + "\n");
        write(new File(evidence, "renderer-matrix.csv"),
                "renderer,configuration,size,bytes,reconstructed\n" + String.join("\n", rendererCsv) + "\n");
        write(new File(evidence, "artifact-gallery.csv"),
                "file,kind,bytes,sha256,payload,decoded,sha256_matches_decoded\n"
                        + String.join("\n", artifactCsv) + "\n");

        System.out.println("evidence written to " + evidence.getAbsolutePath());
        System.out.println("checks: " + harness.getTotal() + ", failures: " + harness.getFailures());
        if (harness.getFailures() > 0) {
            System.exit(1);
        }
    }

    static File evidenceDirectory(String[] args) {
        if (args.length > 0 && !args[0].trim().isEmpty()) {
            return new File(args[0]);
        }
        String environment = System.getenv("QR_EVIDENCE_DIR");
        if (environment != null && !environment.trim().isEmpty()) {
            return new File(environment.trim());
        }
        String property = System.getProperty("qr.evidence.dir");
        return new File(property == null || property.trim().isEmpty()
                ? "docs/evidence" : property.trim());
    }

    /**
     * Produces real artifacts with every renderer, records their digests and asks the independent
     * decoder to recover the payload from each of them.
     */
    private static void buildGallery(TestHarness harness, File gallery, List<String> artifactCsv)
            throws Exception {
        PureJavaQrEncoderBean encoder = new PureJavaQrEncoderBean();
        String[] payloads = {
            "SCO310",
            "https://www.ku.ac.ke/sco310",
            "Kenyatta University - Component Programming",
            "1234567890",
        };

        int pngFiles = 0;
        for (int i = 0; i < payloads.length; i++) {
            String payload = payloads[i];
            QrCode symbol = encoder.encode(payload, QrErrorCorrection.MEDIUM);

            PngRendererBean png = new PngRendererBean();
            png.setModuleSize(6);
            RenderedArtifact pngArtifact = png.render(symbol);
            File pngFile = new File(gallery, "gallery-" + (i + 1) + ".png");
            Files.write(pngFile.toPath(), pngArtifact.getContent());
            record(artifactCsv, pngFile, pngArtifact, payload);
            pngFiles++;

            SvgRendererBean svg = new SvgRendererBean();
            svg.setModuleSize(8);
            RenderedArtifact svgArtifact = svg.render(symbol);
            File svgFile = new File(gallery, "gallery-" + (i + 1) + ".svg");
            Files.write(svgFile.toPath(), svgArtifact.getContent());
            record(artifactCsv, svgFile, svgArtifact, payload);

            AsciiRendererBean ascii = new AsciiRendererBean();
            RenderedArtifact asciiArtifact = ascii.render(symbol);
            File textFile = new File(gallery, "gallery-" + (i + 1) + ".txt");
            Files.write(textFile.toPath(), asciiArtifact.getContent());
            record(artifactCsv, textFile, asciiArtifact, payload);
        }

        // One artifact per error-correction level for the same payload.
        String payload = "Level comparison";
        for (QrErrorCorrection level : QrErrorCorrection.values()) {
            QrCode symbol = encoder.encode(payload, level);
            PngRendererBean png = new PngRendererBean();
            png.setModuleSize(5);
            RenderedArtifact artifact = png.render(symbol);
            File file = new File(gallery, "level-" + level.getLabel() + ".png");
            Files.write(file.toPath(), artifact.getContent());
            record(artifactCsv, file, artifact, payload);
        }

        // The version ladder: identical payload, rising minimum version.
        StringBuilder ladderCsv = new StringBuilder("version,modules,data_codewords,ecc_codewords,png,decoded\n");
        for (int version = 1; version <= 10; version++) {
            String ladderPayload = String.join("", java.util.Collections.nCopies(version * 6, "L"));
            PureJavaQrEncoderBean forced = new PureJavaQrEncoderBean();
            forced.setMinimumVersion(version);
            forced.setMaximumVersion(version);
            QrCode symbol = forced.encode(ladderPayload, QrErrorCorrection.MEDIUM);
            PngRendererBean png = new PngRendererBean();
            png.setModuleSize(5);
            RenderedArtifact artifact = png.render(symbol);
            File file = new File(gallery, "ladder-v" + version + ".png");
            Files.write(file.toPath(), artifact.getContent());
            String decoded = ZxingVerify.decodePng(artifact.getContent());
            ladderCsv.append(version).append(',').append(symbol.getSize()).append(',')
                     .append(symbol.getDataCodewordCount()).append(',')
                     .append(symbol.getEccCodewordCount()).append(',')
                     .append(file.getName()).append(',')
                     .append(ladderPayload.equals(decoded) ? "yes" : "no").append('\n');
            artifactCsv.add(file.getName() + ",PNG ladder v" + version + "," + artifact.getSizeInBytes()
                    + "," + sha256(artifact.getContent()) + ",\"" + ladderPayload + "\"," 
                    + (ladderPayload.equals(decoded) ? "yes" : "no"));
        }
        write(new File(gallery, "version-ladder.csv"), ladderCsv.toString());

        // The two encoder components side by side: same payload, different implementations.
        if (ke.ku.sco310.qr.components.OptionalComponentSupport.isZxingAvailable()) {
            ZxingQrEncoderAdapterBean zxing = new ZxingQrEncoderAdapterBean();
            QrCode zxingSymbol = zxing.encode(payloads[1], QrErrorCorrection.MEDIUM);
            PngRendererBean png = new PngRendererBean();
            png.setModuleSize(6);
            RenderedArtifact artifact = png.render(zxingSymbol);
            File file = new File(gallery, "encoder-zxing-adapter.png");
            Files.write(file.toPath(), artifact.getContent());
            String decoded = ZxingVerify.decodePng(artifact.getContent());
            artifactCsv.add(file.getName() + ",PNG produced by the ZXing adapter component,"
                    + artifact.getSizeInBytes() + "," + sha256(artifact.getContent()) + ",\""
                    + payloads[1] + "\"," + (payloads[1].equals(decoded) ? "yes" : "no"));
            harness.check("gallery", "the third-party adapter component produces decodable artifacts",
                    payloads[1].equals(decoded),
                    "v" + zxingSymbol.getVersion() + " mask " + zxingSymbol.getMask()
                            + " by " + zxingSymbol.getProducerId());
        }

        // The whole application assembly writing through the repository component. The artifact is
        // copied to a stable gallery name so repeated verification runs do not add timestamped files.
        AssemblyProfile profile = new AssemblyProfile();
        File work = new File(gallery.getParentFile(), "work");
        profile.setProperty("repository", "rootDirectory", work.getPath());
        profile.setProperty("facade", "fileBaseName", "assembled-app");
        QrAssembly assembly = new ComponentRegistry().build(profile);
        ke.ku.sco310.qr.api.QrGenerationResult result =
                assembly.generate("Generated by the assembled application", QrErrorCorrection.HIGH);
        File assembled = new File(gallery, "assembled-app.png");
        Files.copy(result.getStoredFile().toPath(), assembled.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        record(artifactCsv, assembled, result.getArtifact(), "Generated by the assembled application");
        harness.check("gallery", "the assembled application writes gallery artifacts",
                result.getStoredFile() != null && result.getStoredFile().isFile() && assembled.isFile(),
                assembled.getPath());
        harness.check("gallery", "gallery contains PNG, SVG and ASCII artifacts",
                pngFiles == 4 && new File(gallery, "gallery-1.svg").isFile()
                        && new File(gallery, "gallery-1.txt").isFile(),
                gallery.getAbsolutePath());
    }

    private static void record(List<String> csv, File file, RenderedArtifact artifact, String payload)
            throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String decoded = "n/a";
        try {
            if ("PNG".equals(artifact.getFormatName())) {
                decoded = ZxingVerify.decodePng(bytes);
            } else {
                decoded = "(non-raster artifact - verified by parsing in the renderer checks)";
            }
        } catch (Exception e) {
            decoded = "DECODE FAILED: " + e.getMessage();
        }
        csv.add(file.getName() + "," + artifact.getFormatName() + "," + artifact.getSizeInBytes()
                + "," + sha256(bytes) + ",\"" + payload + "\",\""
                + (payload.equals(decoded) ? "yes" : decoded) + "\"");
    }

    private static String summariseTableCheck(List<String> tableCsv) {
        long mismatches = tableCsv.stream().filter(line -> line.endsWith("MISMATCH")).count();
        return "\nISO/IEC 18004 parameter table cross-check: " + tableCsv.size()
                + " version/level combinations compared with the ZXing tables, "
                + mismatches + " mismatches.\n";
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void write(File file, String content) throws Exception {
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        System.out.println("[evidence] wrote " + file.getPath() + " (" + content.length() + " chars)");
    }
}
