package thz.cobol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thz.cobol.ir.CobolLlvmEmitter;
import thz.cobol.model.CobolProgram;
import thz.cobol.model.CopybookAst;
import thz.cobol.parser.CobolProcedureParser;
import thz.cobol.parser.CopybookParser;

import static org.junit.jupiter.api.Assertions.*;

class CobolLlvmEmitterTest {

    @Test
    @DisplayName("Deve emitir LLVM IR nativo com C-ABI, structs e conversores COMP-3 sem dependencia JVM")
    void testEmissaoLlvmIrNativo() {
        String copybook = """
            01  REGISTRO-CONTA.
                05  NUMERO-CONTA     PIC 9(8).
                05  TITULAR-NOME     PIC X(30).
                05  SALDO-DISPONIVEL PIC S9(11)V99 COMP-3.
            """;

        String programCobol = """
            IDENTIFICATION DIVISION.
            PROGRAM-ID. ROTINA-CONTA.
            PROCEDURE DIVISION.
            PROCESSAR.
                DISPLAY "Iniciando Processamento de Conta".
                GOBACK.
            """;

        CopybookAst copyAst = CopybookParser.parse("REGISTRO-CONTA", copybook);
        CobolProgram prog = CobolProcedureParser.parseProgram(programCobol);

        String llvmIr = CobolLlvmEmitter.emitirLlvmIr(prog, copyAst);

        assertNotNull(llvmIr);
        // Target Triple e DataLayout
        assertTrue(llvmIr.contains("target triple ="));
        assertTrue(llvmIr.contains("target datalayout ="));

        // Runtime THZ e C-ABI
        assertTrue(llvmIr.contains("@thz_arena_alloc"));
        assertTrue(llvmIr.contains("@cob_call"));

        // Struct nativa LLVM
        assertTrue(llvmIr.contains("%struct.Cobol_REGISTRO_CONTA = type {"));

        // Conversores de precisão COMP-3 <-> i128
        assertTrue(llvmIr.contains("@thz_cobol_comp3_to_i128"));
        assertTrue(llvmIr.contains("@thz_cobol_i128_to_comp3"));

        // Função wrapper AOT
        assertTrue(llvmIr.contains("@thz_chamar_cobol_ROTINA_CONTA"));
    }
}
