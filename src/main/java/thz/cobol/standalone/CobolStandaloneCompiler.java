package thz.cobol.standalone;

import thz.cobol.ir.CobolLlvmEmitter;
import thz.cobol.model.CobolProgram;
import thz.cobol.model.CopybookAst;
import thz.cobol.parser.CobolProcedureParser;
import thz.cobol.transpiler.CobolToThzTranspiler;

import java.util.Objects;

/**
 * CobolStandaloneCompiler — Compilador autônomo de programas COBOL puros para LLVM IR AOT e THZ-IR.
 * Permite que arquivos .cbl sejam transformados diretamente em binários executáveis nativos (.exe/.elf).
 */
public final class CobolStandaloneCompiler {

    private CobolStandaloneCompiler() {}

    /**
     * Compila um código COBOL puro gerando LLVM IR autônomo (com função @main) e representação THZ.
     */
    public static CompilacaoResultado compilar(String codigoCobol) {
        Objects.requireNonNull(codigoCobol, "Código COBOL não pode ser nulo");

        CobolProgram program = CobolProcedureParser.parseProgram(codigoCobol);
        CopybookAst wsAst = new CopybookAst(program.programId() + "_WS", program.workingStorage());

        // 1. Gera LLVM IR nativo AOT
        StringBuilder llvmSb = new StringBuilder();
        llvmSb.append(CobolLlvmEmitter.emitirLlvmIr(program, wsAst));

        // Emite o ponto de entrada executável @main para o Sistema Operacional
        llvmSb.append("; ==============================================================================\n");
        llvmSb.append("; Ponto de Entrada Executavel Nativo (@main) para o Programa COBOL Puro: ").append(program.programId()).append("\n");
        llvmSb.append("; ==============================================================================\n\n");
        llvmSb.append("define i32 @main(i32 %argc, ptr %argv) {\n");
        llvmSb.append("entry:\n");
        llvmSb.append("    ; Inicializa arena de memoria O(1) do runtime THZ-LANG\n");
        llvmSb.append("    %arena = call ptr @thz_arena_alloc(i64 1048576)\n");
        llvmSb.append("    ; Invoca a rotina principal do programa COBOL\n");
        llvmSb.append("    %ret = call i32 @thz_chamar_cobol_").append(program.programId().replace("-", "_")).append("(%arena)\n");
        llvmSb.append("    ; Libera arena de memoria\n");
        llvmSb.append("    call void @thz_arena_free_all(ptr %arena)\n");
        llvmSb.append("    ret i32 %ret\n");
        llvmSb.append("}\n");

        // 2. Gera representação equivalente em THZ-LANG
        String thzSource = CobolToThzTranspiler.transpile(program);

        return new CompilacaoResultado(
                program.programId(),
                llvmSb.toString(),
                thzSource,
                program.workingStorage().size(),
                program.paragraphs().size()
        );
    }

    public record CompilacaoResultado(
            String programId,
            String llvmIrAot,
            String thzEquivalente,
            int totalVariaveis,
            int totalParagrafos
    ) {}
}
