package thz.cobol.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Representa a cláusula PICTURE (PIC) de uma variável COBOL.
 * Suporta formatos numéricos, alfanuméricos, decimais com escala (V) e com sinal (S).
 */
public final class CobolPicture {

    private final String rawPicture;
    private final boolean alphanumeric;
    private final boolean signed;
    private final int totalDigits;
    private final int decimalScale;
    private final int byteLength;

    public CobolPicture(String rawPicture, boolean alphanumeric, boolean signed, int totalDigits, int decimalScale, int byteLength) {
        this.rawPicture = rawPicture;
        this.alphanumeric = alphanumeric;
        this.signed = signed;
        this.totalDigits = totalDigits;
        this.decimalScale = decimalScale;
        this.byteLength = byteLength;
    }

    /**
     * Faz o parsing de uma string PICTURE do COBOL, ex: 'X(30)', '9(7)V99', 'S9(13)V99'.
     */
    public static CobolPicture parse(String picString, String usage) {
        if (picString == null || picString.isBlank()) {
            return new CobolPicture("", false, false, 0, 0, 0);
        }

        String normalized = picString.trim().toUpperCase().replace("PICTURE", "").replace("PIC", "").trim();
        // Remove ponto final do COBOL se presente
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        boolean isAlpha = normalized.contains("X") || normalized.contains("A");
        boolean isSigned = normalized.startsWith("S");

        if (isAlpha) {
            int len = expandLength(normalized, 'X');
            if (len == 0) len = expandLength(normalized, 'A');
            return new CobolPicture(normalized, true, false, 0, 0, len);
        }

        // Numérico
        int vIndex = normalized.indexOf('V');
        int intDigits;
        int decDigits = 0;

        if (vIndex >= 0) {
            String intPart = normalized.substring(0, vIndex);
            if (intPart.startsWith("S")) intPart = intPart.substring(1);
            String decPart = normalized.substring(vIndex + 1);

            intDigits = countDigits(intPart);
            decDigits = countDigits(decPart);
        } else {
            String numPart = isSigned ? normalized.substring(1) : normalized;
            intDigits = countDigits(numPart);
        }

        int totalDig = intDigits + decDigits;
        int bytes = totalDig;

        boolean isComp3 = usage != null && usage.toUpperCase().contains("COMP-3");
        boolean isComp = usage != null && (usage.toUpperCase().contains("COMP") && !isComp3);

        if (isComp3) {
            // COMP-3 (Packed Decimal): cada byte armazena 2 dígitos, e o último nibble é o sinal.
            // Ex: total de dígitos N -> (N + 1) / 2 bytes (arredondado para cima)
            bytes = (totalDig + 1) / 2;
            if ((totalDig + 1) % 2 != 0) {
                bytes += 1;
            }
        } else if (isComp) {
            if (totalDig <= 4) bytes = 2; // 16-bit
            else if (totalDig <= 9) bytes = 4; // 32-bit
            else bytes = 8; // 64-bit
        }

        return new CobolPicture(normalized, false, isSigned, totalDig, decDigits, bytes);
    }

    private static int expandLength(String s, char targetChar) {
        Pattern p = Pattern.compile(targetChar + "\\((\\d+)\\)");
        Matcher m = p.matcher(s);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        int count = 0;
        for (char c : s.toCharArray()) {
            if (c == targetChar) count++;
        }
        return count;
    }

    private static int countDigits(String s) {
        Pattern p = Pattern.compile("9\\((\\d+)\\)");
        Matcher m = p.matcher(s);
        int total = 0;
        int lastEnd = 0;
        while (m.find()) {
            // soma ocorrências literais de '9' antes do grupo
            for (int i = lastEnd; i < m.start(); i++) {
                if (s.charAt(i) == '9') total++;
            }
            total += Integer.parseInt(m.group(1));
            lastEnd = m.end();
        }
        for (int i = lastEnd; i < s.length(); i++) {
            if (s.charAt(i) == '9') total++;
        }
        return total;
    }

    public String getRawPicture() {
        return rawPicture;
    }

    public boolean isAlphanumeric() {
        return alphanumeric;
    }

    public boolean isSigned() {
        return signed;
    }

    public int getTotalDigits() {
        return totalDigits;
    }

    public int getDecimalScale() {
        return decimalScale;
    }

    public int getByteLength() {
        return byteLength;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CobolPicture that = (CobolPicture) o;
        return alphanumeric == that.alphanumeric && signed == that.signed && totalDigits == that.totalDigits && decimalScale == that.decimalScale && byteLength == that.byteLength && Objects.equals(rawPicture, that.rawPicture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawPicture, alphanumeric, signed, totalDigits, decimalScale, byteLength);
    }

    @Override
    public String toString() {
        return "CobolPicture{" + rawPicture + ", alpha=" + alphanumeric + ", signed=" + signed + ", dig=" + totalDigits + ", scale=" + decimalScale + ", bytes=" + byteLength + '}';
    }
}
