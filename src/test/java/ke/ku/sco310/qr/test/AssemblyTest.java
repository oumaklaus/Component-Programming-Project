package ke.ku.sco310.qr.test;

import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.api.QrGenerationResult;
import ke.ku.sco310.qr.components.OptionalComponentSupport;
import ke.ku.sco310.qr.components.PureJavaQrEncoderBean;
import ke.ku.sco310.qr.components.ZxingQrEncoderAdapterBean;
import ke.ku.sco310.qr.facade.AssemblyProfile;
import ke.ku.sco310.qr.facade.ComponentDescriptor;
import ke.ku.sco310.qr.facade.ComponentRegistry;
import ke.ku.sco310.qr.facade.QrAssembly;
import ke.ku.sco310.qr.render.AsciiRendererBean;
import ke.ku.sco310.qr.render.PngRendererBean;
import ke.ku.sco310.qr.render.SvgRendererBean;

import java.io.File;
import java.util.List;

/**
 * End-to-end verification of the assembly: catalogue, wiring, dependency validation, generation,
 * history and audit bookkeeping, component replacement at run time and the negative controls that
 * prove the checks above are not vacuous.
 */
final class AssemblyTest {

    private AssemblyTest() {
    }

    static void run(TestHarness harness, File workDirectory) throws Exception {
        // A unique storage directory per run keeps the checks independent of previous runs.
        File storage = new File(workDirectory, "assembly-test-" + System.currentTimeMillis());
        storage.mkdirs();
        ComponentRegistry registry = new ComponentRegistry();

        // ---------------------------------------------------------------- catalogue
        List<ComponentDescriptor> catalogue = registry.listComponents();
        harness.check("assembly.catalogue", "every role has at least one implementation",
                registry.listByRole("encoder").size() >= 1
                        && registry.listByRole("renderer").size() >= 5
                        && registry.listByRole("repository").size() >= 1,
                "encoders=" + registry.listByRole("encoder").size()
                        + " renderers=" + registry.listByRole("renderer").size()
                        + " repositories=" + registry.listByRole("repository").size());
        boolean thirdPartyDeclared = catalogue.stream()
                .anyMatch(d -> d.getComponentId().equals("encoder.zxing-adapter.v1")
                        && (!d.isAvailable() || d.getRequiredInterfaces().size() > 0));
        harness.check("assembly.catalogue", "the optional component declares its context dependency",
                thirdPartyDeclared, "adapter declared with an explicit required interface");

        // ---------------------------------------------------------------- build and validate
        AssemblyProfile profile = new AssemblyProfile();
        profile.setProperty("repository", "rootDirectory", storage.getPath());
        profile.setProperty("renderer", "moduleSize", "7");
        QrAssembly assembly = registry.build(profile);
        List<String> validation = registry.validate(assembly);
        boolean allSatisfied = validation.stream().noneMatch(line -> line.contains("MISSING"));
        harness.check("assembly.wiring", "every declared dependency of the assembly is satisfied",
                allSatisfied, String.join(" | ", validation));
        harness.check("assembly.wiring", "profile properties reached the component instances",
                ((PngRendererBean) assembly.getRenderer()).getModuleSize() == 7,
                "moduleSize=" + ((PngRendererBean) assembly.getRenderer()).getModuleSize());

        assembly.getRepository().clearHistory();

        // ---------------------------------------------------------------- end to end
        String payload = "https://www.ku.ac.ke/sco310/component-programming";
        QrGenerationResult result = assembly.generate(payload, QrErrorCorrection.QUARTILE);
        harness.check("assembly.end-to-end", "generation stores an artifact and returns the path",
                result.getStoredFile() != null && result.getStoredFile().isFile()
                        && result.getStoredFile().length() > 0,
                String.valueOf(result.getStoredFile()));
        harness.checkEquals("assembly.end-to-end", "the stored PNG decodes back to the payload",
                payload, ZxingVerify.decodePng(java.nio.file.Files.readAllBytes(result.getStoredFile().toPath())));
        harness.check("assembly.end-to-end", "history and audit counters were updated",
                assembly.getHistory().getEntryCount() == 1
                        && assembly.getListener().getProcessedEventCount() == 1
                        && assembly.getFacade().getGeneratedCount() == 1,
                "history=" + assembly.getHistory().getEntryCount()
                        + " audit=" + assembly.getListener().getProcessedEventCount()
                        + " facade=" + assembly.getFacade().getGeneratedCount());
        harness.checkEquals("assembly.end-to-end", "the repository persisted the history record",
                1, assembly.getRepository().getHistoryCount());

        // ---------------------------------------------------------------- renderer replacement
        assembly.replaceRenderer(new SvgRendererBean());
        QrGenerationResult svgResult = assembly.generate(payload, QrErrorCorrection.QUARTILE);
        harness.checkEquals("assembly.replace", "the assembly runs with a different renderer component",
                "SVG", svgResult.getArtifact().getFormatName());
        harness.check("assembly.replace", "the choice is recorded in the profile",
                "renderer.svg.v1".equals(assembly.getProfile().getComponentId("renderer")),
                assembly.getProfile().getComponentId("renderer"));
        assembly.replaceRenderer(new AsciiRendererBean());
        QrGenerationResult asciiResult = assembly.generate(payload, QrErrorCorrection.QUARTILE);
        harness.checkEquals("assembly.replace", "a third renderer drops in without recompiling",
                "ASCII", asciiResult.getArtifact().getFormatName());
        harness.check("assembly.replace", "all three renderers produced the same symbol",
                svgResult.getSymbol().equals(asciiResult.getSymbol())
                        && result.getSymbol().equals(svgResult.getSymbol()),
                "version " + result.getSymbol().getVersion() + ", mask " + result.getSymbol().getMask()
                        + " for PNG, SVG and ASCII");

        // ---------------------------------------------------------------- encoder replacement
        if (OptionalComponentSupport.isZxingAvailable()) {
            assembly.replaceEncoder(new ZxingQrEncoderAdapterBean());
            QrGenerationResult zxingResult = assembly.generate(payload, QrErrorCorrection.QUARTILE);
            harness.checkEquals("assembly.replace", "the third-party encoder satisfies the same contract",
                    payload, ZxingVerify.decodeModules(zxingResult.getSymbol()));
            harness.check("assembly.replace", "the facade now reports the adapter as its encoder",
                    zxingResult.getSymbol().getProducerId().equals(ZxingQrEncoderAdapterBean.COMPONENT_ID),
                    zxingResult.getSymbol().getProducerId());
            assembly.replaceEncoder(new PureJavaQrEncoderBean());
        }

        // ---------------------------------------------------------------- dependency failure
        QrAssembly incomplete = registry.build(profile);
        incomplete.getFacade().setEncoder(null);
        harness.expectFailure("assembly.negative", "generating without an encoder fails loudly",
                () -> incomplete.getFacade().generate("no encoder", QrErrorCorrection.LOW));

        // ---------------------------------------------------------------- profile errors
        AssemblyProfile unknownComponent = new AssemblyProfile();
        unknownComponent.setComponentId("renderer", "renderer.does-not-exist.v1");
        harness.expectFailure("assembly.negative", "an unknown component id is rejected at assembly time",
                () -> registry.build(unknownComponent));

        AssemblyProfile wrongProperty = new AssemblyProfile();
        wrongProperty.setComponentId("renderer", "renderer.ascii.v1");
        wrongProperty.setProperty("renderer", "moduleSize", "9");
        QrAssembly tolerant = registry.build(wrongProperty);
        harness.check("assembly.negative",
                "a profile property the selected component does not declare is reported, not fatal",
                tolerant.getConfigurationNotes().size() == 1
                        && tolerant.getConfigurationNotes().get(0).contains("moduleSize"),
                tolerant.getConfigurationNotes().toString());

        // ---------------------------------------------------------------- repository
        assembly.getRepository().clearHistory();
        harness.checkEquals("assembly.repository", "clearHistory empties the persisted history",
                0, assembly.getRepository().getHistoryCount());
        assembly.getRepository().appendHistory(new ke.ku.sco310.qr.api.QrHistoryEntry(
                new PureJavaQrEncoderBean().encode("persisted entry", QrErrorCorrection.LOW), "x.png"));
        harness.checkEquals("assembly.repository", "a new record is persisted immediately",
                1, assembly.getRepository().getHistoryCount());
        harness.check("assembly.repository",
                "reinstantiating the repository component reads the same history file",
                new ke.ku.sco310.qr.store.FileSystemRepositoryBean(storage).getHistoryCount() == 1,
                "history file " + new File(storage, "history.ser").getAbsolutePath());

        // ---------------------------------------------------------------- capacity + veto end to end
        assembly.getHistory().setMaxEntries(2);
        assembly.generate("entry one", QrErrorCorrection.LOW);
        assembly.generate("entry two", QrErrorCorrection.LOW);
        assembly.generate("entry three", QrErrorCorrection.LOW);
        harness.check("assembly.history", "the in-memory history honours its capacity policy",
                assembly.getHistory().getEntryCount() == 2
                        && assembly.getHistory().getDiscardedEntryCount() >= 1,
                "entries=" + assembly.getHistory().getEntryCount()
                        + " discarded=" + assembly.getHistory().getDiscardedEntryCount());

        harness.note("assembly storage directory: " + storage.getAbsolutePath());
    }
}
