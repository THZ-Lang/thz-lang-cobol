package thz.cobol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thz.cobol.model.CobolProgram;
import thz.cobol.model.CopybookAst;
import thz.cobol.parser.CobolProcedureParser;
import thz.cobol.parser.CopybookParser;
import thz.cobol.transpiler.CobolToThzTranspiler;
import thz.cobol.transpiler.CopybookToThzTranspiler;

import static org.junit.jupiter.api.Assertions.*;

class CobolTranspilerTest {

    @Test
    @DisplayName("Deve transpilar Copybook para ESTRUTURA THZ-LANG com tipos mapeados")
    void testTranspilarCopybookParaEstruturaThz() {
        String copybook = """
            01  FOLHA-PAGAMENTO.
                05  FUNC-ID          PIC 9(6).
                05  FUNC-NOME        PIC X(40).
                05  SALARIO-BASE     PIC S9(9)V99 COMP-3.
                05  DESCONTO-INSS    PIC S9(7)V99 COMP-3.
            """;

        CopybookAst ast = CopybookParser.parse("FOLHA-PAGAMENTO", copybook);
        String thzCode = CopybookToThzTranspiler.transpile(ast);

        assertNotNull(thzCode);
        assertTrue(thzCode.contains("ESTRUTURA FolhaPagamento"));
        assertTrue(thzCode.contains("func_id : INTEIRO32"));
        assertTrue(thzCode.contains("func_nome : TEXTO"));
        assertTrue(thzCode.contains("salario_base : DECIMAL"));
        assertTrue(thzCode.contains("desconto_inss : DECIMAL"));
        assertTrue(thzCode.contains("FIM_ESTRUTURA"));
    }

    @Test
    @DisplayName("Deve transpilar lógica de PROCEDURE DIVISION para REGRA_NEGOCIO e OPERACAO THZ")
    void testTranspilarProcedureDivisionParaThz() {
        String cobolCode = """
            IDENTIFICATION DIVISION.
            PROGRAM-ID. CALC-JUROS.
            DATA DIVISION.
            WORKING-STORAGE SECTION.
            01  DADOS-CALCULO.
                05  VALOR-PRINCIPAL   PIC S9(9)V99 COMP-3.
                05  TAXA-MENSAL       PIC S9(3)V99 COMP-3.
                05  TOTAL-JUROS       PIC S9(9)V99 COMP-3.
            PROCEDURE DIVISION.
            CALCULAR.
                MOVE 1000.00 TO VALOR-PRINCIPAL.
                MOVE 5.00 TO TAXA-MENSAL.
                COMPUTE TOTAL-JUROS = VALOR-PRINCIPAL * (TAXA-MENSAL / 100).
                DISPLAY "Total Juros Calculado: " TOTAL-JUROS.
                GOBACK.
            """;

        CobolProgram program = CobolProcedureParser.parseProgram(cobolCode);
        String thzCode = CobolToThzTranspiler.transpile(program);

        assertNotNull(thzCode);
        assertTrue(thzCode.contains("PROGRAMA CalcJuros"));
        assertTrue(thzCode.contains("REGRA_NEGOCIO RegraCalcJuros"));
        assertTrue(thzCode.contains("OPERACAO Calcular() : LOGICO"));
        assertTrue(thzCode.contains("valor_principal <- 1000.00"));
        assertTrue(thzCode.contains("taxa_mensal <- 5.00"));
        assertTrue(thzCode.contains("total_juros <-"));
        assertTrue(thzCode.contains("EXIBA \"Total Juros Calculado: \""));
        assertTrue(thzCode.contains("RETORNE VERDADEIRO"));
    }
}
