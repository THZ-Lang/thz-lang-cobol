package thz.cobol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thz.cobol.standalone.CobolStandaloneCompiler;
import thz.cobol.standalone.CobolStandaloneRunner;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CobolStandaloneTest {

    private static final String PROGRAMA_COBOL_PURO = """
        IDENTIFICATION DIVISION.
        PROGRAM-ID. FOLHA-PAGAMENTO-STANDALONE.
        ENVIRONMENT DIVISION.
        DATA DIVISION.
        WORKING-STORAGE SECTION.
        01  DADOS-FOLHA.
            05  SALARIO-BASE       PIC S9(9)V99 COMP-3.
            05  PERCENTUAL-INSS    PIC S9(3)V99 COMP-3.
            05  DESCONTO-INSS      PIC S9(9)V99 COMP-3.
            05  SALARIO-LIQUIDO    PIC S9(9)V99 COMP-3.
            05  NOME-FUNCIONARIO   PIC X(30).
        PROCEDURE DIVISION.
        PROCESSAR-FOLHA.
            MOVE "CARLOS SILVA" TO NOME-FUNCIONARIO.
            MOVE 8000.00 TO SALARIO-BASE.
            MOVE 14.00 TO PERCENTUAL-INSS.
            COMPUTE DESCONTO-INSS = SALARIO-BASE * (PERCENTUAL-INSS / 100).
            COMPUTE SALARIO-LIQUIDO = SALARIO-BASE - DESCONTO-INSS.
            DISPLAY "Funcionario: " NOME-FUNCIONARIO.
            DISPLAY "Salario Liquido Calculado: " SALARIO-LIQUIDO.
            GOBACK.
        """;

    @Test
    @DisplayName("Variação 3: Deve executar programa COBOL 100% puro diretamente sob o runtime THZ")
    void testExecutarProgramaCobolPuro() {
        var resultado = CobolBridge.executarCobolPuro(PROGRAMA_COBOL_PURO);

        assertNotNull(resultado);
        assertNotNull(resultado.variaveis());

        // Verificações matemáticas de exatidão decimal
        assertEquals("CARLOS SILVA", resultado.variaveis().get("NOME-FUNCIONARIO"));
        assertEquals(new BigDecimal("8000.00"), resultado.variaveis().get("SALARIO-BASE"));
        assertEquals(new BigDecimal("14.00"), resultado.variaveis().get("PERCENTUAL-INSS"));
        assertEquals(new BigDecimal("1120.00"), resultado.variaveis().get("DESCONTO-INSS"));
        assertEquals(new BigDecimal("6880.00"), resultado.variaveis().get("SALARIO-LIQUIDO"));

        // Verificações de saída do DISPLAY
        assertEquals(2, resultado.logs().size());
        assertTrue(resultado.logs().get(0).contains("Funcionario: CARLOS SILVA"));
        assertTrue(resultado.logs().get(1).contains("Salario Liquido Calculado: 6880.00"));
    }

    @Test
    @DisplayName("Variação 3: Deve compilar programa COBOL 100% puro para LLVM IR nativo executável com @main (Zero JVM)")
    void testCompilarCobolPuroParaLlvmAot() {
        var resultado = CobolBridge.compilarCobolPuroParaLlvm(PROGRAMA_COBOL_PURO);

        assertNotNull(resultado);
        assertEquals("FOLHA-PAGAMENTO-STANDALONE", resultado.programId());
        assertTrue(resultado.totalVariaveis() > 0);

        String llvmIr = resultado.llvmIrAot();

        // 1. Contém @main executável do SO
        assertTrue(llvmIr.contains("define i32 @main(i32 %argc, ptr %argv)"));

        // 2. Utiliza Arena O(1) do runtime nativo THZ
        assertTrue(llvmIr.contains("@thz_arena_alloc"));
        assertTrue(llvmIr.contains("@thz_arena_free_all"));

        // 3. Invoca a rotina nativa
        assertTrue(llvmIr.contains("@thz_chamar_cobol_FOLHA_PAGAMENTO_STANDALONE"));

        // 4. Gera também código equivalente em THZ-LANG para auditoria
        assertNotNull(resultado.thzEquivalente());
        assertTrue(resultado.thzEquivalente().contains("PROGRAMA FolhaPagamentoStandalone"));
        assertTrue(resultado.thzEquivalente().contains("REGRA_NEGOCIO RegraFolhaPagamentoStandalone"));
    }

    @Test
    @DisplayName("Variação 3: Deve executar CALC_JUROS.cbl com precisão matemática em expressões com parênteses")
    void testExecutarProgramaCalcJuros() throws Exception {
        String codigo = java.nio.file.Files.readString(java.nio.file.Path.of("exemplos/programas/CALC_JUROS.cbl"));
        var resultado = CobolBridge.executarCobolPuro(codigo);

        assertNotNull(resultado);
        assertEquals(new BigDecimal("50000.00"), resultado.variaveis().get("WS-CAPITAL"));
        assertEquals(new BigDecimal("1.75"), resultado.variaveis().get("WS-TAXA-MENSAL"));
        assertEquals(new BigDecimal("50875.00"), resultado.variaveis().get("WS-TOTAL-ACUMULADO"));
        assertEquals(1, resultado.logs().size());
        assertEquals("Calculo Concluido. Total: 50875.00", resultado.logs().get(0));
    }
}
