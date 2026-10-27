package ke.ku.sco310.qr.tools;

import ke.ku.sco310.qr.test.TestHarness;

import java.util.List;

/** Verifies the portable compiler and child-JVM settings used by the JDK-only build. */
public final class ProjectBuilderTest {

    private ProjectBuilderTest() {
    }

    public static void run(TestHarness harness) {
        List<String> options = ProjectBuilder.compilerOptions("scratch/classes", "test-classpath");
        harness.check("tooling", "compilation enables all lint categories portably",
                options.contains("-Xlint:all") && !options.contains("-Xlint:all,-this-escape"),
                options.toString());
        harness.check("tooling", "compilation targets Java 11 with the supplied output and classpath",
                options.get(options.indexOf("--release") + 1).equals("11")
                        && options.contains("scratch/classes") && options.contains("test-classpath"),
                options.toString());
        String previous = System.getProperty("java.awt.headless");
        try {
            System.setProperty("java.awt.headless", "true");
            List<String> command = ProjectBuilder.launchCommand("test.Main", "test-classpath", "evidence");
            harness.check("tooling", "verification child inherits explicit headless mode",
                    command.contains("-Djava.awt.headless=true") && command.contains("test.Main")
                            && command.get(command.size() - 1).equals("evidence"), command.toString());
            System.setProperty("java.awt.headless", "false");
            harness.check("tooling", "explicit graphical child mode is not silently overridden",
                    ProjectBuilder.launchCommand("test.Main", "test-classpath")
                            .contains("-Djava.awt.headless=false"), "explicit false propagated");
        } finally {
            if (previous == null) {
                System.clearProperty("java.awt.headless");
            } else {
                System.setProperty("java.awt.headless", previous);
            }
        }
    }
}
