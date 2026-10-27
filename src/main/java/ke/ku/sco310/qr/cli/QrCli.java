package ke.ku.sco310.qr.cli;

import ke.ku.sco310.qr.api.QrCodeDecoder;
import ke.ku.sco310.qr.api.QrCodeRenderer;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.api.QrGenerationResult;
import ke.ku.sco310.qr.api.RenderedArtifact;
import ke.ku.sco310.qr.facade.AssemblyProfile;
import ke.ku.sco310.qr.facade.ComponentDescriptor;
import ke.ku.sco310.qr.facade.ComponentRegistry;
import ke.ku.sco310.qr.facade.QrAssembly;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command-line front end for the component assembly.
 *
 * <pre>
 * QrCli generate --text HELLO --renderer svg --ec M --out hello.svg
 * QrCli decode --in hello.svg
 * QrCli batch --csv jobs.csv --out-dir out
 * QrCli list-components
 * </pre>
 *
 * <p>The CLI contains option and file handling only. Encoding and rendering always flow through
 * {@link QrAssembly}, so changing the assembly profile or an SPI provider changes the CLI without
 * duplicating QR logic.</p>
 */
public final class QrCli {

    private static final int OK = 0;
    private static final int USAGE_ERROR = 2;
    private static final int INPUT_ERROR = 3;
    private static final int OPERATION_ERROR = 4;
    private static final int IO_ERROR = 5;
    private static final int BATCH_ERROR = 10;

    private QrCli() {
    }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != OK) {
            System.exit(status);
        }
    }

    /** Runs the CLI and returns a stable process exit code without terminating the caller. */
    public static int run(String[] args, java.io.PrintStream out, java.io.PrintStream err) {
        if (args == null || args.length == 0) {
            usage(err);
            return USAGE_ERROR;
        }
        String command = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (command) {
                case "generate": return generate(parse(args, 1), out, err);
                case "decode": return decode(parse(args, 1), out, err);
                case "batch": return batch(parse(args, 1), out, err);
                case "list-components": return listComponents(out);
                case "help": usage(out); return OK;
                default:
                    err.println("Unknown command: " + args[0]);
                    usage(err);
                    return USAGE_ERROR;
            }
        } catch (IllegalArgumentException e) {
            err.println("Input error: " + e.getMessage());
            return INPUT_ERROR;
        } catch (IOException e) {
            err.println("I/O error: " + e.getMessage());
            return IO_ERROR;
        } catch (Exception e) {
            err.println("Operation failed: " + e.getMessage());
            return OPERATION_ERROR;
        }
    }

    private static int generate(Map<String, String> options, java.io.PrintStream out,
                                java.io.PrintStream err) throws Exception {
        String text = required(options, "text");
        QrErrorCorrection level = level(options.getOrDefault("ec", "M"));
        ComponentRegistry registry = new ComponentRegistry();
        QrCodeRenderer renderer = renderer(registry, options.getOrDefault("renderer", "png"));
        QrAssembly assembly = assembly(registry);
        QrGenerationResult result = assembly.getFacade().generate(text, level, renderer);
        Path output = outputPath(options.get("out"), result.getArtifact().getFileExtension());
        if (output == null && result.getArtifact().isTextual()) {
            out.print(result.getArtifact().getContentAsText());
        } else {
            if (output == null) output = Path.of("qr-code." + result.getArtifact().getFileExtension());
            writeArtifact(output, result.getArtifact());
            out.println("wrote " + output.toAbsolutePath() + " (" + result.getArtifact().getSizeInBytes()
                    + " bytes, v" + result.getSymbol().getVersion() + ")");
        }
        return OK;
    }

    private static int decode(Map<String, String> options, java.io.PrintStream out,
                              java.io.PrintStream err) throws Exception {
        String input = required(options, "in");
        Path path = Path.of(input);
        if (!Files.isRegularFile(path)) throw new IOException("input file does not exist: " + input);
        String format = formatFromName(path.getFileName().toString());
        RenderedArtifact artifact = new RenderedArtifact();
        artifact.setContent(Files.readAllBytes(path));
        artifact.setFormatName(format);
        artifact.setFileExtension(pathExtension(path));
        QrCodeDecoder decoder = new ComponentRegistry().createDecoder(
                options.getOrDefault("decoder", "decoder.pure-java.v1"));
        out.println(decoder.decode(artifact));
        return OK;
    }

    private static int batch(Map<String, String> options, java.io.PrintStream out,
                             java.io.PrintStream err) throws Exception {
        Path csv = Path.of(required(options, "csv"));
        Path outputDirectory = Path.of(options.getOrDefault("out-dir", "out/batch"));
        Path outputRoot = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(outputRoot);
        List<String> report = new ArrayList<>();
        List<String> rows = Files.readAllLines(csv, StandardCharsets.UTF_8);
        int succeeded = 0;
        int failed = 0;
        int rowNumber = 0;
        for (String row : rows) {
            rowNumber++;
            if (row.trim().isEmpty()) continue;
            List<String> fields = csvFields(row);
            if (rowNumber == 1 && !fields.isEmpty() && "text".equalsIgnoreCase(fields.get(0).trim())) continue;
            try {
                if (fields.size() < 4) throw new IllegalArgumentException("expected text,filename,ec,format");
                String text = fields.get(0);
                String filename = fields.get(1);
                if (text.isEmpty() || filename.isEmpty()) throw new IllegalArgumentException("text and filename are required");
                ComponentRegistry registry = new ComponentRegistry();
                QrAssembly assembly = assembly(registry);
                QrCodeRenderer renderer = renderer(registry, fields.get(3));
                QrGenerationResult result = assembly.getFacade().generate(text, level(fields.get(2)), renderer);
                Path destination = outputRoot.resolve(filename).normalize();
                if (!destination.startsWith(outputRoot)) {
                    throw new IllegalArgumentException("filename escapes the output directory");
                }
                writeArtifact(destination, result.getArtifact());
                succeeded++;
                report.add("OK row " + rowNumber + ": " + destination + " (" + result.getArtifact().getFormatName() + ")");
            } catch (Exception e) {
                failed++;
                report.add("ERROR row " + rowNumber + ": " + e.getMessage());
            }
        }
        report.add(0, "total=" + (succeeded + failed) + " succeeded=" + succeeded + " failed=" + failed);
        Path summary = outputRoot.resolve("batch-summary.txt");
        Files.write(summary, report, StandardCharsets.UTF_8);
        out.println(String.join(System.lineSeparator(), report));
        return failed == 0 ? OK : BATCH_ERROR;
    }

    private static int listComponents(java.io.PrintStream out) {
        for (ComponentDescriptor descriptor : new ComponentRegistry().listComponents()) {
            out.println(descriptor.toTableRow());
        }
        return OK;
    }

    private static QrAssembly assembly(ComponentRegistry registry) throws Exception {
        AssemblyProfile profile = new AssemblyProfile();
        profile.setProperty("facade", "autoSaveArtifacts", "false");
        profile.setProperty("facade", "autoSaveHistory", "false");
        return registry.build(profile);
    }

    private static QrCodeRenderer renderer(ComponentRegistry registry, String requested) throws Exception {
        String value = requested == null || requested.trim().isEmpty() ? "png" : requested.trim().toLowerCase(Locale.ROOT);
        String id = value.contains(".") ? value : "renderer." + value + ".v1";
        if ("renderer.rounded.v1".equals(id)) id = "renderer.rounded-png.v1";
        QrCodeRenderer renderer = registry.createRenderer(id);
        setProperty(renderer, "moduleSize", "8");
        setProperty(renderer, "quietZone", "4");
        return renderer;
    }

    private static void setProperty(Object bean, String name, String value) throws Exception {
        for (PropertyDescriptor descriptor : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
            if (name.equals(descriptor.getName()) && descriptor.getWriteMethod() != null) {
                Class<?> type = descriptor.getPropertyType();
                Object converted = type == int.class ? Integer.valueOf(value)
                        : type == double.class ? Double.valueOf(value) : value;
                descriptor.getWriteMethod().invoke(bean, converted);
                return;
            }
        }
    }

    private static void writeArtifact(Path destination, RenderedArtifact artifact) throws IOException {
        if (destination.getParent() != null) Files.createDirectories(destination.getParent());
        Files.write(destination, artifact.getContent());
    }

    private static Path outputPath(String value, String extension) {
        return value == null || value.trim().isEmpty() ? null : Path.of(value);
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("--" + name + " is required");
        return value;
    }

    private static QrErrorCorrection level(String value) {
        return QrErrorCorrection.fromLabel(value);
    }

    private static String formatFromName(String name) {
        String extension = pathExtension(Path.of(name));
        if ("svg".equals(extension)) return "SVG";
        if ("txt".equals(extension) || "ascii".equals(extension)) return "ASCII";
        if ("png".equals(extension)) return "PNG";
        throw new IllegalArgumentException("cannot infer format from extension: " + extension);
    }

    private static String pathExtension(Path path) {
        String name = path.toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> parse(String[] args, int start) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = start; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) throw new IllegalArgumentException("unexpected argument: " + arg);
            String name = arg.substring(2);
            if (name.isEmpty() || i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("option --" + name + " needs a value");
            }
            options.put(name, args[++i]);
        }
        return options;
    }

    private static List<String> csvFields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) { fields.add(field.toString()); field.setLength(0); }
            else field.append(c);
        }
        if (quoted) throw new IllegalArgumentException("unterminated CSV quote");
        fields.add(field.toString());
        return fields;
    }

    private static void usage(java.io.PrintStream out) {
        out.println("Usage: QrCli <generate|decode|batch|list-components> [options]");
        out.println("  generate --text TEXT [--renderer png|svg|ascii|pdf] [--ec L|M|Q|H] [--out FILE]");
        out.println("  decode --in FILE [--decoder decoder.pure-java.v1]");
        out.println("  batch --csv FILE [--out-dir DIRECTORY]");
        out.println("  list-components");
    }
}
