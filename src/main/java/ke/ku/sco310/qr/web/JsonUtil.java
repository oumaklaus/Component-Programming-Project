package ke.ku.sco310.qr.web;

/** Small JSON string escaper shared by the HTTP response builders. */
final class JsonUtil {

    private JsonUtil() {
    }

    static String quote(String value) {
        StringBuilder result = new StringBuilder();
        result.append('"');
        String text = value == null ? "" : value;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\b': result.append("\\b"); break;
                case '\f': result.append("\\f"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (c < 0x20 || c > 0x7E) {
                        result.append(String.format(java.util.Locale.ROOT, "\\u%04X", (int) c));
                    } else {
                        result.append(c);
                    }
            }
        }
        return result.append('"').toString();
    }
}
