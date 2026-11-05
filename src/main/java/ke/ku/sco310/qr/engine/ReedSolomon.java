package ke.ku.sco310.qr.engine;

/** Reed-Solomon error-correction codeword generator for QR Code symbols. */
final class ReedSolomon {

    private ReedSolomon() {
    }

    /**
     * @param data   data codewords
     * @param degree number of error-correction codewords to generate
     * @return the error-correction codewords (the remainder polynomial)
     */
    static byte[] computeRemainder(byte[] data, int degree) {
        if (degree < 0) {
            throw new IllegalArgumentException("Negative degree: " + degree);
        }
        int[] divisor = computeDivisor(degree);
        int[] remainder = new int[degree];
        for (byte b : data) {
            int factor = (b & 0xFF) ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, degree - 1);
            remainder[degree - 1] = 0;
            for (int i = 0; i < degree; i++) {
                remainder[i] ^= GaloisField.multiply(divisor[i], factor);
            }
        }
        byte[] result = new byte[degree];
        for (int i = 0; i < degree; i++) {
            result[i] = (byte) remainder[i];
        }
        return result;
    }

    /**
     * @param degree degree of the wanted divisor polynomial
     * @return coefficients of the divisor, highest order term omitted (it is always 1)
     */
    static int[] computeDivisor(int degree) {
        if (degree < 1 || degree > 255) {
            throw new IllegalArgumentException("Invalid divisor degree: " + degree);
        }
        int[] result = new int[degree];
        result[degree - 1] = 1; // start with the monomial x^0
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < result.length; j++) {
                result[j] = GaloisField.multiply(result[j], root);
                if (j + 1 < result.length) {
                    result[j] ^= result[j + 1];
                }
            }
            root = GaloisField.multiply(root, 0x02);
        }
        return result;
    }

    /** @return the divisor polynomial of {@code degree}, as readable hexadecimal coefficients. */
    static String describeDivisor(int degree) {
        StringBuilder sb = new StringBuilder("x^").append(degree);
        int[] divisor = computeDivisor(degree);
        for (int i = 0; i < divisor.length; i++) {
            sb.append(" + ").append(Integer.toHexString(divisor[i]).toUpperCase(java.util.Locale.ROOT))
              .append("x^").append(degree - 1 - i);
        }
        return sb.toString();
    }
}
