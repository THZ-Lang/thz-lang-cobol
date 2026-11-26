package thz.cobol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thz.cobol.parser.CobolProcedureParser;
import thz.cobol.parser.CopybookParser;
import thz.cobol.runtime.CobolMemoryLayout;
import thz.cobol.runtime.CobolSharedMemorySegment;
import thz.cobol.standalone.CobolStandaloneRunner;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CobolMemoryInteropTest {

    private static final String COPYBOOK_CLIENTE = """
        01  REGISTRO-CLIENTE-CONTA.
            05  CLI-NOME            PIC X(30).
            05  CLI-CONTA           PIC 9(8).
            05  CLI-SALDO           PIC S9(11)V99 COMP-3.
            05  CLI-TARIFA          PIC S9(7)V99 COMP-3.
        """;

    private static final String PROGRAMA_COBOL_MUTACAO = """
        IDENTIFICATION DIVISION.
        PROGRAM-ID. CALC-TARIFA-MUTACAO.
        DATA DIVISION.
        WORKING-STORAGE SECTION.
        01  REGISTRO-CLIENTE-CONTA.
            05  CLI-NOME            PIC X(30).
            05  CLI-CONTA           PIC 9(8).
            05  CLI-SALDO           PIC S9(11)V99 COMP-3.
            05  CLI-TARIFA          PIC S9(7)V99 COMP-3.
        PROCEDURE DIVISION.
        PROCESSAR.
            COMPUTE CLI-TARIFA = CLI-SALDO * (2 / 100).
            MOVE "PROCESSADO COM SUCESSO" TO CLI-NOME.
            GOBACK.
        """;

    @Test
    @DisplayName("Memória Compartilhada Zero-Copy: THZ escreve, COBOL calcula in-place, THZ lê do mesmo buffer")
    void testCompartilhamentoMemoriaZeroCopyBidirecional() {
        var copybookAst = CopybookParser.parse("REGISTRO-CLIENTE-CONTA", COPYBOOK_CLIENTE);
        var layout = CobolMemoryLayout.calcularLayout(copybookAst);

        try (var sharedMem = CobolSharedMemorySegment.alocar(layout)) {
            // 1. O THZ-LANG escreve nos campos da memória física
            sharedMem.escreverAlfanumerico("CLI-NOME", "CLIENTE EMPRESA S/A");
            sharedMem.escreverInteiroDisplay("CLI-CONTA", 98765432L);
            sharedMem.escreverDecimalComp3("CLI-SALDO", new BigDecimal("50000.00"));
            sharedMem.escreverDecimalComp3("CLI-TARIFA", BigDecimal.ZERO);

            // Validação preliminar: buffer físico reflete o que o THZ escreveu
            assertEquals("CLIENTE EMPRESA S/A", sharedMem.lerAlfanumerico("CLI-NOME"));
            assertEquals(98765432L, sharedMem.lerInteiroDisplay("CLI-CONTA"));
            assertEquals(new BigDecimal("50000.00"), sharedMem.lerDecimalComp3("CLI-SALDO"));

            // 2. A rotina COBOL é executada in-place sobre o MESMO buffer de memória
            var runner = CobolStandaloneRunner.carregar(PROGRAMA_COBOL_MUTACAO);
            runner.executarSobreMemoriaCompartilhada(sharedMem);

            // 3. O THZ-LANG lê do MESMO segmento compartilhado os valores mutados pelo COBOL
            // A tarifa de 2% sobre 50.000,00 deve ser exatamente 1.000,00
            assertEquals(new BigDecimal("1000.00"), sharedMem.lerDecimalComp3("CLI-TARIFA"));
            // O nome foi atualizado in-place pelo MOVE do COBOL
            assertEquals("PROCESSADO COM SUCESSO", sharedMem.lerAlfanumerico("CLI-NOME"));
            // O número da conta original permaneceu intacto no seu offset
            assertEquals(98765432L, sharedMem.lerInteiroDisplay("CLI-CONTA"));
        }
    }

    @Test
    @DisplayName("Continuidade de Memória: Verifica que os offsets de bytes são estritamente contíguos sem gaps ocultos")
    void testOffsetsEContinuumDeMemoria() {
        var copybookAst = CopybookParser.parse("REGISTRO-CLIENTE-CONTA", COPYBOOK_CLIENTE);
        var layout = CobolMemoryLayout.calcularLayout(copybookAst);

        var offNome = layout.getOffset("CLI-NOME");
        var offConta = layout.getOffset("CLI-CONTA");
        var offSaldo = layout.getOffset("CLI-SALDO");
        var offTarifa = layout.getOffset("CLI-TARIFA");

        // CLI-NOME: offset 0, tamanho 30
        assertEquals(0, offNome.offset());
        assertEquals(30, offNome.length());

        // CLI-CONTA: começa exatamente no byte 30, tamanho 8
        assertEquals(30, offConta.offset());
        assertEquals(8, offConta.length());

        // CLI-SALDO (COMP-3): começa exatamente no byte 38
        assertEquals(38, offSaldo.offset());
        // S9(11)V99 = 13 digitos -> (13+1)/2 = 7 bytes
        assertEquals(7, offSaldo.length());

        // CLI-TARIFA (COMP-3): começa exatamente no byte 45
        assertEquals(45, offTarifa.offset());
        // S9(7)V99 = 9 digitos -> (9+1)/2 = 5 bytes
        assertEquals(5, offTarifa.length());

        // Tamanho total contíguo: 30 + 8 + 7 + 5 = 50 bytes exatos
        assertEquals(50, layout.getTotalByteLength());
    }

    @Test
    @DisplayName("Project Panama FFM: Valida alocação nativa direta de segmento de memória compartilhada")
    void testProjectPanamaFfmNativeSegment() {
        var copybookAst = CopybookParser.parse("REGISTRO-CLIENTE-CONTA", COPYBOOK_CLIENTE);
        var layout = CobolMemoryLayout.calcularLayout(copybookAst);

        try (var sharedMem = CobolSharedMemorySegment.alocar(layout)) {
            assertNotNull(sharedMem.getNativeSegment());
            assertEquals(50, sharedMem.getNativeSegment().byteSize());
            assertTrue(sharedMem.getNativeSegment().isNative());
        }
    }

    @Test
    @DisplayName("LLVM IR Memória Compartilhada: Emite getelementptr inbounds provando unificação nativa para a máquina")
    void testLlvmIrEmiteAcessoMemoriaCompartilhada() {
        assertNotNull(CopybookParser.parse("REGISTRO-CLIENTE-CONTA", COPYBOOK_CLIENTE));
        assertNotNull(CobolProcedureParser.parseProgram(PROGRAMA_COBOL_MUTACAO));

        String llvmIr = CobolBridge.emitirLlvmIr(PROGRAMA_COBOL_MUTACAO);

        assertNotNull(llvmIr);
        // Contém o ponteiro de acesso de memória compartilhada unificado
        assertTrue(llvmIr.contains("define ptr @thz_cobol_campo_ptr(ptr %bufferCompartilhado, i64 %offsetByte)"));
        assertTrue(llvmIr.contains("getelementptr inbounds i8, ptr %bufferCompartilhado, i64 %offsetByte"));
    }
}
