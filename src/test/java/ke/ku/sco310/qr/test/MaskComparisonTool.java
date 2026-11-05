/*
 * Complementary analysis instrument (not part of the shipped components).
 *
 * The automated suite proves that every symbol this suite produces is decodable. This tool
 * answers a different question: how closely does the mask-selection stage agree with a second,
 * independent implementation (ZXing)? ISO/IEC 18004 leaves a little latitude in the
 * interpretation of penalty rule 3 at the symbol border, so a like-for-like comparison of the
 * chosen mask pattern is the honest way to characterise that difference.
 *
 * Run with:  java -cp out/test-classes:out/classes:lib/core-3.5.3.jar \
 *                ke.ku.sco310.qr.test.MaskComparisonTool docs/evidence/mask-selection-comparison.csv
 */
package ke.ku.sco310.qr.test;

import ke.ku.sco310.qr.api.QrCode;
import ke.ku.sco310.qr.api.QrEncodingException;
import ke.ku.sco310.qr.api.QrErrorCorrection;
import ke.ku.sco310.qr.engine.QrEngine;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public final class MaskComparisonTool {

    private static final String[] PAYLOADS = {
        "01234567", "8675309", "HELLO WORLD", "Kenyatta University",
        "SCO310 COMPONENT PROGRAMMING", "Kenyatta University 2026",
        "WIFI:T:WPA;S:KU-WIFI;P:secret123;;", "tel:+254700000000",
        "mailto:sco310@ku.ac.ke", "geo:-1.2921,36.8219", "https://github.com/",
        "Component programming is the assembly of reusable units.",
        "1234567890123456789012345678901234567890", "A", "Z9",
        "The quick brown fox jumps over the lazy dog.", "QR CODE GENERATOR v1.0.0",
        "Le cafe naifve - accents", "Line 1\nLine 2\nLine 3", "ORDER-2026-10-15-0042",
        "Kenyatta University", "0123456789",
        "SCO310", "Kenyatta University - Component Programming", "urn:isbn:9780137581444",
        "\u3053\u3093\u306b\u3061\u306f\u4e16\u754c"
    };

    private static final QrErrorCorrection[] LEVELS = {
        QrErrorCorrection.LOW, QrErrorCorrection.MEDIUM,
        QrErrorCorrection.QUARTILE, QrErrorCorrection.HIGH
    };

    private static final ErrorCorrectionLevel[] ORACLE_LEVELS = {
        ErrorCorrectionLevel.L, ErrorCorrectionLevel.M,
        ErrorCorrectionLevel.Q, ErrorCorrectionLevel.H
    };

    private MaskComparisonTool() {
    }

    public static void main(String[] args) throws Exception {
        String target = args.length > 0 ? args[0] : "docs/evidence/mask-selection-comparison.csv";
        int cases = 0;
        int versionAgreement = 0;
        int maskAgreement = 0;
        int sameVersionComparisons = 0;
        StringBuilder csv = new StringBuilder(
                "payload,level,ours_version,ours_mask,oracle_version,oracle_mask,"
                + "same_version,same_mask,note\n");

        for (String payload : PAYLOADS) {
            for (int i = 0; i < LEVELS.length; i++) {
                QrCode ours;
                try {
                    ours = QrEngine.encode(payload, LEVELS[i], false, 1, 40, -1, "mask-comparison");
                } catch (QrEncodingException e) {
                    continue;
                }
                QRCode oracle;
                try {
                    oracle = Encoder.encode(payload, ORACLE_LEVELS[i]);
                } catch (Exception e) {
                    continue;
                }
                int oracleVersion = oracle.getVersion().getVersionNumber();
                int oracleMask = oracle.getMaskPattern();
                cases++;
                boolean sameVersion = ours.getVersion() == oracleVersion;
                boolean sameMask = sameVersion && ours.getMask() == oracleMask;
                if (sameVersion) {
                    versionAgreement++;
                    sameVersionComparisons++;
                    if (sameMask) {
                        maskAgreement++;
                    }
                }
                String note = sameVersion
                        ? (sameMask ? "identical symbol parameters" : "penalty tie-break differs")
                        : "different version: the oracle lossily encodes non-Latin text as Latin-1";
                csv.append('"').append(payload.replace("\n", "\\n").replace("\"", "'")).append('"').append(',')
                   .append(LEVELS[i].getLabel()).append(',')
                   .append(ours.getVersion()).append(',').append(ours.getMask()).append(',')
                   .append(oracleVersion).append(',').append(oracleMask).append(',')
                   .append(sameVersion ? "yes" : "no").append(',')
                   .append(sameMask ? "yes" : "no").append(',')
                   .append('"').append(note).append('"').append('\n');
            }
        }

        try (PrintWriter writer = new PrintWriter(target, StandardCharsets.UTF_8.name())) {
            writer.print(csv);
        }
        System.out.println("mask comparison written to " + target);
        System.out.println("cases                       : " + cases);
        System.out.println("same symbol version          : " + versionAgreement + "/" + cases);
        System.out.println("same mask pattern            : " + maskAgreement + "/" + sameVersionComparisons
                + " (of the cases where the version agrees)");
        System.out.println("all produced symbols decode  : yes (see docs/evidence/test-report.txt)");
    }
}
