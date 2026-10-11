package thz.cobol.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * PackedDecimalConverter — Conversão de alta performance e exatidão matemática
 * para campos COBOL COMP-3 (Binary Coded Decimal / Packed Decimal).
 *
 * Em conformidade com as regras clássicas de mainframe (IBM/GnuCOBOL):
 * - Cada byte armazena 2 dígitos (nibbles de 4 bits).
 * - O último nibble (mais à direita) armazena o sinal:
 *     0xC (12) = Positivo (+)
 *     0xD (13) = Negativo (-)
 *     0xF (15) = Não-assinado / Positivo sem sinal (+)
 */
public final class PackedDecimalConverter {

    private PackedDecimalConverter() {}

    /**
     * Decodifica um array de bytes COMP-3 para um {@link BigDecimal} com a escala indicada.
     */
    public static BigDecimal decode(byte[] bytes, int scale) {
        Objects.requireNonNull(bytes, "Bytes não podem ser nulos para decodificação COMP-3");
        if (bytes.length == 0) {
            return BigDecimal.ZERO.setScale(scale);
        }

        StringBuilder digits = new StringBuilder(bytes.length * 2);
        boolean isNegative = false;

        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            int highNibble = (b >> 4) & 0x0F;
            int lowNibble = b & 0x0F;

            if (i < bytes.length - 1) {
                if (highNibble > 9 || lowNibble > 9) {
                    throw new IllegalArgumentException("Byte inválido no Packed Decimal no índice " + i + ": 0x" + Integer.toHexString(b));
                }
                digits.append(highNibble).append(lowNibble);
            } else {
                // Último byte: highNibble é o último dígito, lowNibble é o sinal
                if (highNibble > 9) {
                    throw new IllegalArgumentException("Último dígito inválido no Packed Decimal: " + highNibble);
                }
                digits.append(highNibble);

                if (lowNibble == 0x0D || lowNibble == 0x0B) {
                    isNegative = true;
                } else if (lowNibble == 0x0C || lowNibble == 0x0F || lowNibble == 0x0A || lowNibble == 0x0E) {
                    isNegative = false;
                } else {
                    throw new IllegalArgumentException("Sinal inválido no Packed Decimal (último nibble): 0x" + Integer.toHexString(lowNibble));
                }
            }
        }

        String strVal = digits.toString();
        if (strVal.isEmpty()) strVal = "0";

        BigInteger unscaled = new BigInteger(strVal);
        if (isNegative) {
            unscaled = unscaled.negate();
        }

        return new BigDecimal(unscaled, scale);
    }

    /**
     * Codifica um {@link BigDecimal} para a representação de bytes COBOL COMP-3.
     */
    public static byte[] encode(BigDecimal value, int totalDigits, int scale) {
        Objects.requireNonNull(value, "Valor não pode ser nulo para codificação COMP-3");

        BigDecimal scaled = value.setScale(scale, java.math.RoundingMode.HALF_UP);
        BigInteger unscaled = scaled.unscaledValue();
        boolean isNegative = unscaled.signum() < 0;
        String digitsStr = unscaled.abs().toString();

        if (digitsStr.length() > totalDigits) {
            throw new IllegalArgumentException("Valor excede o total de dígitos (" + totalDigits + "): " + value);
        }

        // Padding com zeros à esquerda
        int pad = totalDigits - digitsStr.length();
        StringBuilder sb = new StringBuilder();
        sb.append("0".repeat(Math.max(0, pad)));
        sb.append(digitsStr);

        // Se totalDigits for par, precisa de um zero no nibble mais significativo
        // para acomodar (totalDigits + 1) nibbles nos bytes
        int totalNibbles = totalDigits + 1;
        if (totalNibbles % 2 != 0) {
            sb.insert(0, '0');
            totalNibbles++;
        }

        int byteCount = totalNibbles / 2;
        byte[] result = new byte[byteCount];

        int charIdx = 0;
        for (int i = 0; i < byteCount; i++) {
            int high = sb.charAt(charIdx++) - '0';
            int low;
            if (i == byteCount - 1) {
                // Último nibble é o sinal
                low = isNegative ? 0x0D : 0x0C;
            } else {
                low = sb.charAt(charIdx++) - '0';
            }
            result[i] = (byte) ((high << 4) | (low & 0x0F));
        }

        return result;
    }
}
