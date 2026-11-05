package ke.ku.sco310.qr.render;

import ke.ku.sco310.qr.editor.HexColorPropertyEditor;

import java.beans.BeanDescriptor;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

/** Explicit introspection description of PngRendererBean (chapter 3, "5 Steps to Creating a BeanInfo Class"). */
public class PngRendererBeanBeanInfo extends SimpleBeanInfo {

    @Override
    public BeanDescriptor getBeanDescriptor() {
        BeanDescriptor descriptor = new BeanDescriptor(PngRendererBean.class);
        descriptor.setDisplayName("PNG Renderer Bean");
        descriptor.setShortDescription("Renders an encoded QR Code symbol as a PNG image.");
        return descriptor;
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            PropertyDescriptor moduleSize = new PropertyDescriptor("moduleSize", PngRendererBean.class);
            moduleSize.setDisplayName("Module size (px)");
            moduleSize.setShortDescription("Pixel size of a single QR module; 8 px gives a 200 dpi friendly symbol.");

            PropertyDescriptor quietZone = new PropertyDescriptor("quietZone", PngRendererBean.class);
            quietZone.setDisplayName("Quiet zone (modules)");
            quietZone.setShortDescription("Light margin around the symbol; 4 modules is the ISO minimum.");
            quietZone.setExpert(true);

            PropertyDescriptor foreground = new PropertyDescriptor("foregroundColor", PngRendererBean.class);
            foreground.setDisplayName("Module colour");
            foreground.setShortDescription("Colour of the dark modules, edited as #RRGGBB.");
            foreground.setPropertyEditorClass(HexColorPropertyEditor.class);

            PropertyDescriptor background = new PropertyDescriptor("backgroundColor", PngRendererBean.class);
            background.setDisplayName("Background colour");
            background.setShortDescription("Colour of the light modules, edited as #RRGGBB.");
            background.setPropertyEditorClass(HexColorPropertyEditor.class);

            return new PropertyDescriptor[]{moduleSize, quietZone, foreground, background};
        } catch (IntrospectionException e) {
            return null; // let the Introspector fall back to the default reflection based search
        }
    }
}
