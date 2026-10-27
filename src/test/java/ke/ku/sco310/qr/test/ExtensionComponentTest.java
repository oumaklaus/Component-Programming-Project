package ke.ku.sco310.qr.test;

import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrCodeDecoder;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.api.RenderedArtifact;
import ke.ku.sco310.qr.components.PureJavaQrDecoderBean;
import ke.ku.sco310.qr.components.PureJavaQrEncoderBean;
import ke.ku.sco310.qr.components.ZxingQrDecoderAdapterBean;
import ke.ku.sco310.qr.facade.ComponentDescriptor;
import ke.ku.sco310.qr.facade.ComponentRegistry;
import ke.ku.sco310.qr.render.AsciiRendererBean;
import ke.ku.sco310.qr.render.PdfRendererBean;
import ke.ku.sco310.qr.render.PngRendererBean;
import ke.ku.sco310.qr.render.RoundedModuleRendererBean;
import ke.ku.sco310.qr.render.SvgRendererBean;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import javax.imageio.ImageIO;

/** Verification of the decoder, rounded/logo renderer, PDF renderer and SPI catalogue. */
final class ExtensionComponentTest {

    private ExtensionComponentTest() {
    }

    static void run(TestHarness harness) throws Exception {
        checkBeanContract(harness, "decoder", PureJavaQrDecoderBean.class);
        checkBeanContract(harness, "renderer", RoundedModuleRendererBean.class);
        checkBeanContract(harness, "renderer", PdfRendererBean.class);
        checkBeanContract(harness, "encoder", ke.ku.sco310.qr.components.MicroQrEncoderBean.class);
        if (ke.ku.sco310.qr.components.OptionalComponentSupport.isZxingAvailable()) {
            checkBeanContract(harness, "decoder", ZxingQrDecoderAdapterBean.class);
        }
        String payload = "decoder round trip: é 漢字";
        QrCode symbol = new PureJavaQrEncoderBean().encode(payload, QrErrorCorrection.HIGH);
        PureJavaQrDecoderBean pure = new PureJavaQrDecoderBean();
        harness.checkEquals("extensions.decoder", "pure decoder reads a symbol matrix", payload,
                pure.decode(symbol));
        QrCode versionOne = new PureJavaQrEncoderBean().encode("A", QrErrorCorrection.MEDIUM);
        harness.checkEquals("extensions.decoder", "pure decoder reads version-one format bits", "A",
                pure.decode(versionOne));

        RenderedArtifact png = new PngRendererBean().render(symbol);
        RenderedArtifact svg = new SvgRendererBean().render(symbol);
        RenderedArtifact ascii = new AsciiRendererBean().render(symbol);
        harness.checkEquals("extensions.decoder", "pure decoder reads PNG output", payload, pure.decode(png));
        harness.checkEquals("extensions.decoder", "pure decoder reads SVG output", payload, pure.decode(svg));
        harness.checkEquals("extensions.decoder", "pure decoder reads ASCII output", payload, pure.decode(ascii));

        boolean[][] damaged = symbol.getModules();
        damaged[10][10] = !damaged[10][10];
        QrCode damagedSymbol = new QrCode(symbol.getContent(), symbol.getRequestedErrorCorrection(),
                symbol.getErrorCorrection(), symbol.getEncodingMode(), symbol.getCharacterCount(),
                symbol.getVersion(), symbol.getMask(), symbol.getDataCodewordCount(),
                symbol.getEccCodewordCount(), damaged, symbol.getProducerId());
        harness.expectFailure("extensions.decoder", "pure decoder rejects a damaged matrix",
                () -> pure.decode(damagedSymbol));

        if (ke.ku.sco310.qr.components.OptionalComponentSupport.isZxingAvailable()) {
            QrCodeDecoder zxing = new ZxingQrDecoderAdapterBean();
            harness.checkEquals("extensions.decoder", "ZXing decoder adapter reads PNG output",
                    payload, zxing.decode(png));
            harness.expectFailure("extensions.decoder", "ZXing adapter rejects a non-PNG artifact",
                    () -> zxing.decode(svg));
        }

        RoundedModuleRendererBean rounded = new RoundedModuleRendererBean();
        rounded.setCornerRadius(0.4);
        RenderedArtifact roundedArtifact = rounded.render(symbol);
        harness.check("extensions.rounded", "rounded renderer produces PNG output",
                roundedArtifact.getContent().length > 8 && roundedArtifact.getRendererId().contains("rounded"),
                roundedArtifact.toString());
        BufferedImage logo = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D logoGraphics = logo.createGraphics();
        logoGraphics.setColor(Color.RED);
        logoGraphics.fillRect(0, 0, 4, 4);
        logoGraphics.dispose();
        ByteArrayOutputStream logoBytes = new ByteArrayOutputStream();
        ImageIO.write(logo, "png", logoBytes);
        rounded.setLogoImage(logoBytes.toByteArray());
        rounded.setLogoScale(0.8);
        harness.expectFailure("extensions.rounded", "unsafe logo coverage is refused",
                () -> rounded.render(symbol));
        rounded.setWarnOnly(true);
        rounded.render(symbol);
        harness.check("extensions.rounded", "unsafe logo coverage can be explicitly warned",
                !rounded.getLastWarning().isEmpty(), rounded.getLastWarning());

        PdfRendererBean pdf = new PdfRendererBean();
        RenderedArtifact pdfArtifact = pdf.render(symbol);
        String pdfText = new String(pdfArtifact.getContent(), java.nio.charset.StandardCharsets.ISO_8859_1);
        harness.check("extensions.pdf", "PDF is a single-page vector document",
                pdfText.startsWith("%PDF-1.4") && pdfText.contains("/Count 1")
                        && pdfText.contains("/Length") && pdfText.contains(" re f"),
                pdfArtifact.getSizeInBytes() + " bytes, rectangle operators present");
        harness.expectFailure("extensions.pdf", "pure decoder rejects unsupported PDF input",
                () -> pure.decode(pdfArtifact));

        ComponentRegistry registry = new ComponentRegistry();
        List<ComponentDescriptor> catalogue = registry.listComponents();
        harness.check("extensions.spi", "ServiceLoader catalogue contains new providers",
                contains(catalogue, "decoder.pure-java.v1")
                        && contains(catalogue, "decoder.zxing-adapter.v1")
                        && contains(catalogue, "renderer.rounded-png.v1")
                        && contains(catalogue, "renderer.pdf.v1")
                        && contains(catalogue, "encoder.micro-qr.v1"),
                "catalogue entries=" + catalogue.size());
        harness.check("extensions.spi", "ServiceLoader creates providers by stable id",
                registry.createDecoder("decoder.pure-java.v1") instanceof PureJavaQrDecoderBean
                        && registry.createRenderer("renderer.pdf.v1") instanceof PdfRendererBean,
                "decoder and PDF renderer loaded through SPI");
        harness.expectFailure("extensions.spi", "unknown SPI component id is rejected",
                () -> registry.createDecoder("decoder.missing.v1"));
    }

    private static boolean contains(List<ComponentDescriptor> descriptors, String id) {
        return descriptors.stream().anyMatch(descriptor -> id.equals(descriptor.getComponentId()));
    }

    private static void checkBeanContract(TestHarness harness, String group, Class<?> type) {
        boolean noArg = false;
        for (Constructor<?> constructor : type.getConstructors()) {
            noArg |= constructor.getParameterCount() == 0;
        }
        boolean bean = Modifier.isPublic(type.getModifiers()) && noArg
                && Serializable.class.isAssignableFrom(type)
                && ke.ku.sco310.qr.api.BoundPropertySource.class.isAssignableFrom(type);
        harness.check("extensions." + group, type.getSimpleName() + " is a serializable bound JavaBean",
                bean, "public=" + Modifier.isPublic(type.getModifiers()) + " noArg=" + noArg);
    }
}
