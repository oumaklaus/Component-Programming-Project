package ke.ku.sco310.qr.tools;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * The project's build tool, written in Java so that building, verifying and packaging this
 * component suite needs nothing but the JDK.
 *
 * <p>This is the equivalent of the shell scripts a hobby project would use, except that it is a
 * normal Java program: it drives {@link javax.tools.JavaCompiler} to compile the sources, launches
 * the verification suite in a child JVM, and writes the deployable JAR with
 * {@link java.util.jar.JarOutputStream}. No build tool, script interpreter or third-party
 * dependency is involved.</p>
 *
 * <pre>
 * javac -d out/classes src/main/java/ke/ku/sco310/qr/*&#47;*.java     (bootstrap compile, once)
 * java  -cp out/classes ke.ku.sco310.qr.tools.ProjectBuilder all
 * </pre>
 *
 * <p>Targets: {@code clean}, {@code compile}, {@code test}, {@code compare}, {@code jar},
 * {@code all} (the default).</p>
 */
public final class ProjectBuilder {

    private static final String OUT = "out";
    private static final String CLASSES = "out/classes";
    private static final String TEST_CLASSES = "out/test-classes";
    private static final String DIST = "out/dist";
    private static final String JAR_NAME = "qr-component-suite-1.0.0.jar";
    private static final String APP_JAR_NAME = "QrStudio.jar";
    private static final String RELEASE = "11";
    private static final String ZXING_CORE = "lib/core-3.5.3.jar";
    private static final String ZXING_JAVASE = "lib/javase-3.5.3.jar";

    private ProjectBuilder() {
    }

    public static void main(String[] args) throws Exception {
        String target = args.length == 0 ? "all" : args[0].toLowerCase(Locale.ROOT);
        switch (target) {
            case "clean":
                clean();
                break;
            case "compile":
                compileMain();
                break;
            case "test":
                runVerification();
                break;
            case "compare":
                runMaskComparison();
                break;
            case "jar":
                packageJar();
                break;
            case "all":
                clean();
                compileMain();
                runVerification();
                runMaskComparison();
                packageJar();
                break;
            default:
                System.out.println("unknown target: " + target);
                System.out.println("usage: ProjectBuilder [clean|compile|test|compare|jar|all]");
                System.exit(2);
        }
        info("done");
    }

    // ------------------------------------------------------------------ targets

    /** Removes the whole {@code out/} tree and recreates the directories the build needs. */
    static void clean() throws IOException {
        info("removing build output");
        deleteRecursively(Paths.get(OUT));
        Files.deleteIfExists(Paths.get(APP_JAR_NAME));
        Files.createDirectories(Paths.get(CLASSES));
        Files.createDirectories(Paths.get(TEST_CLASSES));
        Files.createDirectories(Paths.get(DIST));
    }

    /** Compiles the component suite (the main source tree) into {@code out/classes}. */
    static void compileMain() throws IOException {
        info("compiling main sources with the JavaCompiler API (--release " + RELEASE + ")");
        List<File> sources = javaSourcesUnder("src/main/java");
        if (!compile(sources, CLASSES, ZXING_CORE)) {
            System.exit(1);
        }
        copyResources(CLASSES);
        info("main classes: " + countFiles(Paths.get(CLASSES), ".class"));
    }

    /** Compiles the verification suite and runs it, rewriting the evidence folder. */
    static void runVerification() throws IOException, InterruptedException {
        File evidence = evidenceDirectory();
        info("compiling the verification suite");
        List<File> sources = javaSourcesUnder("src/main/java");
        sources.addAll(javaSourcesUnder("src/test/java"));
        if (!compile(sources, TEST_CLASSES, ZXING_CORE + File.pathSeparator + ZXING_JAVASE)) {
            System.exit(1);
        }
        copyResources(TEST_CLASSES);
        info("running the automated verification suite");
        String classPath = TEST_CLASSES + File.pathSeparator + CLASSES + File.pathSeparator
                + ZXING_CORE + File.pathSeparator + ZXING_JAVASE;
        int status = launch("ke.ku.sco310.qr.test.VerificationRunner", classPath, evidence.getAbsolutePath());
        if (status != 0) {
            System.out.println("verification failed with exit status " + status);
            System.exit(status);
        }
    }

    /** Runs the mask-selection comparison against the independent ZXing encoder. */
    static void runMaskComparison() throws IOException, InterruptedException {
        File evidence = evidenceDirectory();
        info("comparing the mask-selection stage with the independent ZXing encoder");
        List<File> sources = javaSourcesUnder("src/main/java");
        sources.addAll(javaSourcesUnder("src/test/java"));
        if (!compile(sources, TEST_CLASSES, ZXING_CORE + File.pathSeparator + ZXING_JAVASE)) {
            System.exit(1);
        }
        String classPath = TEST_CLASSES + File.pathSeparator + CLASSES + File.pathSeparator + ZXING_CORE;
        launch("ke.ku.sco310.qr.test.MaskComparisonTool", classPath,
                new File(evidence, "mask-selection-comparison.csv").getAbsolutePath());
    }

    /** Builds the deployable JAR: compiled components plus the manifest that marks them as beans. */
    static void packageJar() throws IOException {
        compileMain();
        info("packaging the components into " + JAR_NAME + " (manifest: manifest/MANIFEST.MF)");
        Files.createDirectories(Paths.get(DIST));
        // The default deployment descriptor travels inside the archive so that a deployment can
        // start from the packaged defaults; the manifest declares it as an ordinary resource.
        Files.copy(Paths.get("config/assembly.xml"), Paths.get(CLASSES, "assembly-defaults.xml"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Manifest manifest;
        try (InputStream in = new FileInputStream("manifest/MANIFEST.MF")) {
            manifest = new Manifest(in);
        }
        File jarFile = new File(DIST, JAR_NAME);
        int entries = 0;
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarFile), manifest);
             Stream<Path> tree = Files.walk(Paths.get(CLASSES))) {
            List<Path> files = new ArrayList<>();
            tree.filter(Files::isRegularFile).forEach(files::add);
            files.sort(Comparator.comparing(Path::toString));
            for (Path file : files) {
                String name = Paths.get(CLASSES).relativize(file).toString().replace(File.separatorChar, '/');
                if (name.equals("META-INF/MANIFEST.MF")) {
                    continue;   // written from the manifest object already
                }
                JarEntry entry = new JarEntry(name);
                entry.setTime(file.toFile().lastModified());
                jar.putNextEntry(entry);
                Files.copy(file, jar);
                jar.closeEntry();
                entries++;
            }
        }
        info("jar written: " + jarFile.getPath() + " (" + jarFile.length() + " bytes, " + entries
                + " entries)");
        Files.createDirectories(Paths.get(DIST, "lib"));
        for (String library : new String[]{ZXING_CORE, ZXING_JAVASE}) {
            Files.copy(Paths.get(library), Paths.get(DIST, "lib", new File(library).getName()),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        Manifest appManifest;
        try (InputStream in = new FileInputStream("manifest/MANIFEST.MF")) {
            appManifest = new Manifest(in);
        }
        appManifest.getMainAttributes().remove(Attributes.Name.CLASS_PATH);
        File appJar = new File(APP_JAR_NAME);
        Set<String> written = new HashSet<>();
        int appEntries = 0;
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(appJar), appManifest);
             Stream<Path> tree = Files.walk(Paths.get(CLASSES))) {
            List<Path> files = new ArrayList<>();
            tree.filter(Files::isRegularFile).forEach(files::add);
            files.sort(Comparator.comparing(Path::toString));
            for (Path file : files) {
                String name = Paths.get(CLASSES).relativize(file).toString().replace(File.separatorChar, '/');
                if (name.equals("META-INF/MANIFEST.MF")) {
                    continue;
                }
                JarEntry entry = new JarEntry(name);
                entry.setTime(file.toFile().lastModified());
                jar.putNextEntry(entry);
                Files.copy(file, jar);
                jar.closeEntry();
                written.add(name);
                appEntries++;
            }
            for (String library : new String[]{ZXING_CORE, ZXING_JAVASE}) {
                try (JarFile source = new JarFile(library)) {
                    List<JarEntry> dependencies = new ArrayList<>();
                    source.stream()
                            .filter(entry -> !entry.isDirectory())
                            .filter(entry -> !isJarMetadata(entry.getName()))
                            .forEach(dependencies::add);
                    dependencies.sort(Comparator.comparing(JarEntry::getName));
                    for (JarEntry entry : dependencies) {
                        if (!written.add(entry.getName())) {
                            continue;
                        }
                        JarEntry output = new JarEntry(entry.getName());
                        output.setTime(entry.getTime());
                        jar.putNextEntry(output);
                        try (InputStream input = source.getInputStream(entry)) {
                            input.transferTo(jar);
                        }
                        jar.closeEntry();
                        appEntries++;
                    }
                }
            }
        }
        info("standalone app written: " + appJar.getPath() + " (" + appJar.length() + " bytes, "
                + appEntries + " entries)");
    }

    private static boolean isJarMetadata(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.equals("META-INF/MANIFEST.MF")
                || upper.equals("META-INF/INDEX.LIST")
                || upper.endsWith(".SF")
                || upper.endsWith(".RSA")
                || upper.endsWith(".DSA");
    }

    // ------------------------------------------------------------------ helpers

    private static File evidenceDirectory() {
        String env = System.getenv("QR_EVIDENCE_DIR");
        if (env != null && !env.trim().isEmpty()) {
            return new File(env.trim());
        }
        String property = System.getProperty("qr.evidence.dir");
        if (property != null && !property.trim().isEmpty()) {
            return new File(property.trim());
        }
        return new File("docs/evidence");
    }

    private static boolean compile(List<File> sources, String outputDirectory, String classPath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.out.println("no system Java compiler found: run this tool with a JDK, not a JRE");
            return false;
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null,
                StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(sources);
            List<String> options = compilerOptions(outputDirectory, classPath);
            boolean success = compiler.getTask(null, files, diagnostics, options, null, units).call();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                String where = diagnostic.getSource() == null ? ""
                        : new File(diagnostic.getSource().toUri()).getName() + ":"
                          + diagnostic.getLineNumber() + " ";
                System.out.println("  " + diagnostic.getKind() + " " + where
                        + diagnostic.getMessage(Locale.ROOT));
            }
            return success;
        } catch (IOException e) {
            System.out.println("compilation failed: " + e);
            return false;
        }
    }

    private static int launch(String mainClass, String classPath, String... args) throws IOException,
            InterruptedException {
        Process process = new ProcessBuilder(launchCommand(mainClass, classPath, args)).inheritIO().start();
        return process.waitFor();
    }

    static List<String> compilerOptions(String outputDirectory, String classPath) {
        return Arrays.asList("--release", RELEASE, "-Xlint:all", "-d", outputDirectory,
                "-cp", classPath);
    }

    static List<String> launchCommand(String mainClass, String classPath, String... args) {
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Djava.awt.headless=" + System.getProperty("java.awt.headless", "true"));
        command.add("-cp");
        command.add(classPath);
        command.add(mainClass);
        command.addAll(Arrays.asList(args));
        return command;
    }

    private static List<File> javaSourcesUnder(String root) throws IOException {
        List<File> sources = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(Paths.get(root))) {
            tree.filter(path -> path.toString().endsWith(".java"))
                .filter(Files::isRegularFile)
                .sorted()
                .forEach(path -> sources.add(path.toFile()));
        }
        return sources;
    }

    /** Copies classpath service descriptors and other resources into a compiler output tree. */
    private static void copyResources(String outputDirectory) throws IOException {
        Path root = Paths.get("src/main/resources");
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(root)) {
            tree.filter(Files::isRegularFile).forEach(source -> {
                try {
                    Path target = Paths.get(outputDirectory).resolve(root.relativize(source));
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new ResourceCopyException(e);
                }
            });
        } catch (ResourceCopyException e) {
            throw e.cause;
        }
    }

    private static long countFiles(Path root, String suffix) throws IOException {
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(path -> path.toString().endsWith(suffix)).count();
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(root)) {
            List<Path> paths = new ArrayList<>();
            tree.forEach(paths::add);
            paths.sort(Comparator.reverseOrder());
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void info(String message) {
        System.out.println("[build] " + message);
    }

    private static final class ResourceCopyException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final IOException cause;

        ResourceCopyException(IOException cause) {
            this.cause = cause;
        }
    }

    /** Keeps the unused-import warning away while documenting the stream-based traversal above. */
    static OutputStream unusedDocumentationHook() {
        return null;
    }
}
