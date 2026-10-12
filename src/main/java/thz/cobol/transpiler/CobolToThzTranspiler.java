package thz.cobol.transpiler;

import thz.cobol.model.*;

import java.util.List;

/**
 * CobolToThzTranspiler — Transpila a lógica completa de um programa COBOL
 * (Data Division e Procedure Division) para um módulo nativo THZ-LANG.
 */
public final class CobolToThzTranspiler {

    private CobolToThzTranspiler() {}

    /**
     * Transpila um programa COBOL em código-fonte THZ-LANG idiomático.
     */
    public static String transpile(CobolProgram program) {
        StringBuilder sb = new StringBuilder();

        String moduleName = toPascalCase(program.programId());

        sb.append("PROGRAMA ").append(moduleName).append("\n\n");

        sb.append("METADADOS_ARQUITETURA\n");
        sb.append("    DOMINIO: \"ModernizacaoLegado\"\n");
        sb.append("    SUBDOMINIO: \"ProcessamentoCOBOL\"\n");
        sb.append("    CAMADA: \"Dominio\"\n");
        sb.append("    VERSAO: \"0.1.0\"\n");
        sb.append("    AUTOR: \"THZ-LANG COBOL Transpiler\"\n");
        sb.append("    SLO_LATENCIA_MAXIMA: \"5ms\"\n");
        sb.append("    CONFORMIDADE: \"ISO-IEC-10967\"\n");
        sb.append("FIM_METADADOS\n\n");

        // Estruturas de dados (Working Storage e Linkage)
        if (!program.workingStorage().isEmpty()) {
            sb.append("# --- DADOS DA WORKING-STORAGE ---\n");
            for (CobolField f : program.workingStorage()) {
                sb.append(CopybookToThzTranspiler.transpile(new CopybookAst(f.getName(), List.of(f))));
            }
        }

        if (!program.linkageSection().isEmpty()) {
            sb.append("# --- DADOS DA LINKAGE SECTION ---\n");
            for (CobolField f : program.linkageSection()) {
                sb.append(CopybookToThzTranspiler.transpile(new CopybookAst(f.getName(), List.of(f))));
            }
        }

        // Regra de Negócio principal
        sb.append("REGRA_NEGOCIO Regra").append(moduleName).append("\n");
        sb.append("    IDENTIFICADOR_REGRA: \"RN-CBL-").append(program.programId()).append("\"\n");
        sb.append("    RASTREIO_REQUISITO: \"REQ-CBL-MIGRATION\"\n");
        sb.append("    DESCRICAO: \"Lógica transpilada da PROCEDURE DIVISION do programa ").append(program.programId()).append("\"\n\n");

        for (CobolParagraph paragraph : program.paragraphs()) {
            emitOperacao(paragraph, sb);
        }

        sb.append("FIM_REGRA_NEGOCIO\n\n");
        sb.append("FIM_PROGRAMA\n");

        return sb.toString();
    }

    private static void emitOperacao(CobolParagraph paragraph, StringBuilder sb) {
        String opName = toPascalCase(paragraph.name());

        sb.append("    OPERACAO ").append(opName).append("() : LOGICO\n");
        sb.append("    INICIO\n");

        for (CobolStatement stmt : paragraph.statements()) {
            emitStatement(stmt, sb);
        }

        sb.append("        RETORNE VERDADEIRO\n");
        sb.append("    FIM\n\n");
    }

    private static void emitStatement(CobolStatement stmt, StringBuilder sb) {
        switch (stmt) {
            case CobolStatement.Move(String src, String dst) -> {
                String varDst = toSnakeCase(dst);
                String valSrc = formatValue(src);
                sb.append("        ").append(varDst).append(" <- ").append(valSrc).append("\n");
            }
            case CobolStatement.Compute(String target, String expr) -> {
                String varDst = toSnakeCase(target);
                String thzExpr = translateExpression(expr);
                sb.append("        ").append(varDst).append(" <- ").append(thzExpr).append("\n");
            }
            case CobolStatement.Add(String src, String dst, String giving) -> {
                String target = giving != null ? toSnakeCase(giving) : toSnakeCase(dst);
                sb.append("        ").append(target).append(" <- ").append(toSnakeCase(dst)).append(" + ").append(formatValue(src)).append("\n");
            }
            case CobolStatement.Subtract(String src, String dst, String giving) -> {
                String target = giving != null ? toSnakeCase(giving) : toSnakeCase(dst);
                sb.append("        ").append(target).append(" <- ").append(toSnakeCase(dst)).append(" - ").append(formatValue(src)).append("\n");
            }
            case CobolStatement.Display(List<String> items) -> {
                sb.append("        EXIBA ");
                StringBuilder concat = new StringBuilder();
                for (int i = 0; i < items.size(); i++) {
                    concat.append(formatValue(items.get(i)));
                    if (i + 1 < items.size()) concat.append(" + \" \" + ");
                }
                sb.append(concat).append("\n");
            }
            case CobolStatement.Perform(String target, Integer times, String until) -> {
                String targetOp = toPascalCase(target);
                if (times != null) {
                    sb.append("        PARA i DE 1 ATE ").append(times).append(" PASSO 1 FACA\n");
                    sb.append("            ").append(targetOp).append("()\n");
                    sb.append("        FIM_PARA\n");
                } else if (until != null && !until.isBlank()) {
                    sb.append("        ENQUANTO NAO (").append(formatValue(until)).append(") FACA\n");
                    sb.append("            ").append(targetOp).append("()\n");
                    sb.append("        FIM_ENQUANTO\n");
                } else {
                    sb.append("        ").append(targetOp).append("()\n");
                }
            }
            case CobolStatement.Call(String prog, List<String> params) -> {
                sb.append("        COBOL.chamar(\"").append(prog).append("\"");
                for (String p : params) {
                    sb.append(", ").append(toSnakeCase(p));
                }
                sb.append(")\n");
            }
            case CobolStatement.Goback() -> {
                sb.append("        RETORNE VERDADEIRO\n");
            }
            case CobolStatement.IfStatement(String cond, List<CobolStatement> thenB, List<CobolStatement> elseB) -> {
                sb.append("        SE ").append(translateExpression(cond)).append("\n");
                for (CobolStatement s : thenB) {
                    emitStatement(s, sb);
                }
                if (!elseB.isEmpty()) {
                    sb.append("        SENAO\n");
                    for (CobolStatement s : elseB) {
                        emitStatement(s, sb);
                    }
                }
                sb.append("        FIM_SE\n");
            }
        }
    }

    private static String formatValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return "\"\"";
        }
        String s = raw.trim();
        if (s.equalsIgnoreCase("ZERO") || s.equalsIgnoreCase("ZEROS") || s.equalsIgnoreCase("ZEROES")) {
            return "0";
        }
        if (s.equalsIgnoreCase("SPACE") || s.equalsIgnoreCase("SPACES")) {
            return "\"\"";
        }
        if (s.length() >= 2 && ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\"")))) {
            return "\"" + s.substring(1, s.length() - 1) + "\"";
        }
        if (Character.isDigit(s.charAt(0)) || s.startsWith("-") || s.startsWith("+")) {
            return s;
        }
        return toSnakeCase(s);
    }

    private static String translateExpression(String expr) {
        // Converte operadores e identificadores COBOL para THZ
        return expr.replace("=", "==")
                .replaceAll("(?i)\\bEQUAL TO\\b", "==")
                .replaceAll("(?i)\\bGREATER THAN\\b", ">")
                .replaceAll("(?i)\\bLESS THAN\\b", "<");
    }

    private static String toSnakeCase(String cobolName) {
        if (cobolName == null) return "var";
        return cobolName.toLowerCase().replace("-", "_").trim();
    }

    private static String toPascalCase(String cobolName) {
        if (cobolName == null || cobolName.isBlank()) return "Op";
        String[] parts = cobolName.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
