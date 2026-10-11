package thz.cobol;

import thz.cobol.ir.CobolLlvmEmitter;
import thz.cobol.model.CobolProgram;
import thz.cobol.model.CopybookAst;
import thz.cobol.parser.CobolProcedureParser;
import thz.cobol.parser.CopybookParser;
import thz.cobol.transpiler.CobolToThzTranspiler;
import thz.cobol.transpiler.CopybookToThzTranspiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CobolBridge — Ponto central de integração entre THZ-LANG e COBOL.
 * Oferece parsing de Copybooks, transpilação para THZ, compilação AOT para LLVM IR
 * e interoperabilidade em tempo de execução.
 */
public final class CobolBridge {

    private CobolBridge() {}

    /**
     * Faz o parsing de um Copybook a partir de uma String.
     */
    public static CopybookAst parseCopybook(String nome, String conteudo) {
        return CopybookParser.parse(nome, conteudo);
    }

    /**
     * Faz o parsing de um Copybook a partir de um arquivo em disco.
     */
    public static CopybookAst parseCopybookArquivo(Path caminho) throws IOException {
        String nome = caminho.getFileName().toString();
        String conteudo = Files.readString(caminho);
        return parseCopybook(nome, conteudo);
    }

    /**
     * Converte um Copybook (.cpy) diretamente em definições de ESTRUTURA THZ-LANG.
     */
    public static String copybookParaThz(String nome, String conteudo) {
        CopybookAst ast = parseCopybook(nome, conteudo);
        return CopybookToThzTranspiler.transpile(ast);
    }

    /**
     * Converte um programa ou bloco de código COBOL para código-fonte THZ-LANG.
     */
    public static String cobolParaThz(String codigoCobol) {
        CobolProgram program = CobolProcedureParser.parseProgram(codigoCobol);
        return CobolToThzTranspiler.transpile(program);
    }

    /**
     * Emite a representação LLVM IR nativa (Stage 2 AOT) para o programa COBOL.
     */
    public static String emitirLlvmIr(String codigoCobol) {
        CobolProgram program = CobolProcedureParser.parseProgram(codigoCobol);
        CopybookAst dataAst = new CopybookAst(program.programId() + "_DATA", program.workingStorage());
        return CobolLlvmEmitter.emitirLlvmIr(program, dataAst);
    }
}
