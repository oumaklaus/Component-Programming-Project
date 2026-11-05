package ke.ku.sco310.qr.ui;

import ke.ku.sco310.qr.facade.AssemblyProfile;
import ke.ku.sco310.qr.facade.ComponentRegistry;
import ke.ku.sco310.qr.facade.QrAssembly;

import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point of the desktop assembly (front end number one).
 *
 * <pre>
 * java -cp out/classes:lib/core-3.5.3.jar ke.ku.sco310.qr.ui.QrStudioGuiAssembly
 * java -cp ... ke.ku.sco310.qr.ui.QrStudioGuiAssembly --screenshot docs/screenshots/gui.png
 * </pre>
 *
 * <p>{@code --screenshot} is used by the project's documentation build: the frame is generated,
 * filled with a demonstration payload, painted into an image and written to disk, so the report
 * always contains a screenshot of the real application rather than a mock-up.</p>
 */
public final class QrStudioGuiAssembly {

    private QrStudioGuiAssembly() {
    }

    public static void main(String[] args) throws Exception {
        String screenshot = null;
        String format = "PNG";
        for (int i = 0; i < args.length; i++) {
            if ("--screenshot".equals(args[i])) {
                screenshot = args[++i];
            } else if ("--format".equals(args[i])) {
                format = args[++i];
            }
        }

        AssemblyProfile profile = loadProfile();
        QrAssembly assembly = new ComponentRegistry().build(profile);
        System.out.println(assembly.describeAssembly());

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        String target = screenshot;
        String chosenFormat = format;
        final QrStudioFrame[] holder = new QrStudioFrame[1];
        // Phase 1: build the frame on the event dispatch thread. The demonstration payload is
        // filled in only for the documentation capture; a normal launch starts with an empty form.
        SwingUtilities.invokeAndWait(() -> {
            QrStudioFrame frame = new QrStudioFrame(assembly);
            frame.setVisible(true);
            if (target != null) {
                frame.selectRenderer(chosenFormat);
                frame.setPayload("Kenyatta University");
                frame.generateNow();
            }
            holder[0] = frame;
            System.out.println("[gui] wired: " + frame.describeWiring());
        });
        if (target == null) {
            return;
        }
        // Phase 2: wait *off* the event dispatch thread. Sleeping inside invokeAndWait would block
        // the queue and the frame would still be painted with the pre-generation layout.
        Thread.sleep(600);
        // Phase 3: paint the settled window into an image, again on the event dispatch thread.
        final String path = target;
        SwingUtilities.invokeAndWait(() -> {
            try {
                File file = new File(path);
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                ImageIO.write(holder[0].captureWindow(), "png", file);
                System.out.println("[gui] screenshot written to " + file.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("[gui] screenshot failed: " + e);
                System.exit(3);
            }
        });
        System.exit(0);
    }

    private static AssemblyProfile loadProfile() {
        File file = new File(AssemblyProfile.DEFAULT_RESOURCE);
        if (!file.isFile()) {
            return new AssemblyProfile();
        }
        try {
            return AssemblyProfile.load(file);
        } catch (Exception e) {
            System.out.println("[gui] could not read the profile (" + e.getMessage() + "), using defaults");
            return new AssemblyProfile();
        }
    }
}
