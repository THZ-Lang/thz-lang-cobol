package thz.cobol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thz.cobol.runtime.PackedDecimalConverter;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PackedDecimalTest {

    @Test
    @DisplayName("Deve decodificar valor COMP-3 positivo sem casas decimais")
    void testDecodificarPositivo() {
        // +12345: bytes 0x12, 0x34, 0x5C
        byte[] bytes = new byte[]{(byte) 0x12, (byte) 0x34, (byte) 0x5C};
        BigDecimal result = PackedDecimalConverter.decode(bytes, 0);
        assertEquals(new BigDecimal("12345"), result);
    }

    @Test
    @DisplayName("Deve decodificar valor COMP-3 negativo com 2 casas decimais")
    void testDecodificarNegativoComDecimais() {
        // -50.25: unscaled = 5025, 4 digitos -> 0x05, 0x02, 0x5D
        byte[] bytes = new byte[]{(byte) 0x05, (byte) 0x02, (byte) 0x5D};
        BigDecimal result = PackedDecimalConverter.decode(bytes, 2);
        assertEquals(new BigDecimal("-50.25"), result);
    }

    @Test
    @DisplayName("Deve codificar BigDecimal para COMP-3 e validar roundtrip")
    void testRoundtripCodificacaoEDecodificacao() {
        BigDecimal original = new BigDecimal("1500.75");
        // PIC S9(7)V99 COMP-3 -> 9 digitos totais, escala 2
        byte[] encoded = PackedDecimalConverter.encode(original, 9, 2);
        assertNotNull(encoded);

        BigDecimal decodificado = PackedDecimalConverter.decode(encoded, 2);
        assertEquals(original, decodificado);
    }
}
