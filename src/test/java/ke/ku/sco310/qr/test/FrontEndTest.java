package ke.ku.sco310.qr.test;

import ke.ku.sco310.qr.cli.QrCli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Checks the command-line front end and batch processing. */
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

    }
}
