package thz.cobol.transpiler;

import thz.cobol.model.*;

import java.util.List;

/**
 * CopybookToThzTranspiler — Transpila definições de dados de Copybooks COBOL (.cpy)
 * diretamente para código fonte nativo THZ-LANG (ESTRUTURA).
 */
public final class CopybookToThzTranspiler {

    private CopybookToThzTranspiler() {}

    /**
     * Transpila uma AST de Copybook para o código THZ-LANG correspondente.
     */
    public static String transpile(CopybookAst copybook) {
        StringBuilder sb = new StringBuilder();

        sb.append("# ==============================================================================\n");
        sb.append("# Gerado automaticamente pelo THZ-LANG COBOL Bridge v0.1.0\n");
        sb.append("# Copybook Fonte: ").append(copybook.getName()).append("\n");
        sb.append("# Tamanho Total em Memória: ").append(copybook.getTotalByteLength()).append(" bytes\n");
        sb.append("# ==============================================================================\n\n");

        for (CobolField root : copybook.getRootFields()) {
            emitEstrutura(root, sb, 0);
        }

        return sb.toString();
    }

    private static void emitEstrutura(CobolField field, StringBuilder sb, int indentLevel) {
        String indent = "    ".repeat(indentLevel);
        String structName = toPascalCase(field.getName());

        if (field.isGroup()) {
            sb.append(indent).append("ESTRUTURA ").append(structName).append("\n");

            for (CobolField child : field.getChildren()) {
                if (child.isGroup()) {
                    // Estrutura aninhada
                    emitEstrutura(child, sb, indentLevel + 1);
                    sb.append(indent).append("    ").append(child.getNomeThz()).append(" : ")
                            .append(toPascalCase(child.getName())).append("\n");
                } else if (child.getLevel() == 88) {
                    // Anotação de condição 88
                    sb.append(indent).append("    # CONDICAO 88 ").append(child.getName())
                            .append(" = ").append(child.getDefaultValue() != null ? child.getDefaultValue() : "TRUE")
                            .append("\n");
                } else {
                    String tipoThz = mapFieldToThzType(child);
                    sb.append(indent).append("    ").append(child.getNomeThz()).append(" : ").append(tipoThz);

                    // Anotação com PIC original
                    if (child.getPicture() != null) {
                        sb.append(" # COBOL: PIC ").append(child.getPicture().getRawPicture());
                        if (child.getUsage() != null && !child.getUsage().equals("DISPLAY")) {
                            sb.append(" ").append(child.getUsage());
                        }
                    }
                    sb.append("\n");
                }
            }

            sb.append(indent).append("FIM_ESTRUTURA\n\n");
        } else {
            // Campo raiz único
            sb.append(indent).append("ESTRUTURA ").append(structName).append("\n");
            sb.append(indent).append("    ").append(field.getNomeThz()).append(" : ")
                    .append(mapFieldToThzType(field)).append("\n");
            sb.append(indent).append("FIM_ESTRUTURA\n\n");
        }
    }

    private static String mapFieldToThzType(CobolField field) {
        CobolPicture pic = field.getPicture();
        if (pic == null) return "TEXTO";

        if (pic.isAlphanumeric()) {
            return "TEXTO";
        }

        if (field.getDataType() == CobolDataType.PACKED_DECIMAL || pic.getDecimalScale() > 0) {
            return "DECIMAL";
        }

        if (pic.getTotalDigits() <= 9) {
            return "INTEIRO32";
        }

        return "INTEIRO";
    }

    private static String toPascalCase(String cobolName) {
        if (cobolName == null || cobolName.isBlank()) return "Registro";
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
