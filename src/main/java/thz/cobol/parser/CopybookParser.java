package thz.cobol.parser;

import thz.cobol.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CopybookParser — Analisador sintático de arquivos Copybook (.cpy, .cbl) e seções de dados COBOL.
 * Suporta formato fixo tradicional (coluna 7) e formato livre (moderno).
 */
public final class CopybookParser {

    private static final Pattern LEVEL_NAME_PATTERN = Pattern.compile("^(\\d{1,2})\\s+([A-Z0-9_-]+)(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIC_PATTERN = Pattern.compile("(?:PIC|PICTURE)\\s+IS\\s+([^\\s.]+)|(?:PIC|PICTURE)\\s+([^\\s.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern USAGE_PATTERN = Pattern.compile("(?:USAGE\\s+IS\\s+|USAGE\\s+)?(COMP-3|COMP-4|COMP-5|COMP|BINARY|PACKED-DECIMAL|DISPLAY)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OCCURS_PATTERN = Pattern.compile("OCCURS\\s+(\\d+)\\s+TIMES?", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE_PATTERN = Pattern.compile("VALUE\\s+(?:IS\\s+)?(?:'([^']*)'|\"([^\"]*)\"|([^\\s.]+))", Pattern.CASE_INSENSITIVE);

    /**
     * Faz o parsing do conteúdo textual de um copybook COBOL.
     */
    public static CopybookAst parse(String copybookName, String content) {
        Objects.requireNonNull(content, "Conteúdo do Copybook não pode ser nulo");
        List<String> rawStatements = extractStatements(content);

        CopybookAst ast = new CopybookAst(copybookName);
        Deque<CobolField> stack = new ArrayDeque<>();

        for (String stmt : rawStatements) {
            CobolField field = parseFieldStatement(stmt);
            if (field == null) continue;

            if (field.getLevel() == 88) {
                // Nível 88 é condição do campo pai
                if (!stack.isEmpty()) {
                    stack.peek().addChild(field);
                }
                continue;
            }

            // Desempilha campos de nível igual ou superior ao atual
            while (!stack.isEmpty() && stack.peek().getLevel() >= field.getLevel()) {
                stack.pop();
            }

            if (stack.isEmpty()) {
                ast.addRootField(field);
            } else {
                stack.peek().addChild(field);
            }

            stack.push(field);
        }

        return ast;
    }

    private static List<String> extractStatements(String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStmt = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = cleanCobolLine(line);
                if (line.isBlank()) continue;

                currentStmt.append(" ").append(line.trim());
                if (currentStmt.toString().trim().endsWith(".")) {
                    String stmtStr = currentStmt.toString().trim();
                    // Remove ponto final
                    stmtStr = stmtStr.substring(0, stmtStr.length() - 1).trim();
                    statements.add(stmtStr);
                    currentStmt.setLength(0);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler conteúdo do Copybook", e);
        }

        if (!currentStmt.toString().isBlank()) {
            statements.add(currentStmt.toString().trim());
        }

        return statements;
    }

    private static String cleanCobolLine(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) return "";

        String trimmed = rawLine.trim();
        // Comentários de formato livre ou iniciados por * /
        if (trimmed.startsWith("*") || trimmed.startsWith("/")) {
            return "";
        }

        // Formato clássico de 80 colunas com número de sequência (colunas 1-6 com dígitos)
        String line = rawLine;
        if (line.length() >= 7) {
            boolean isSequence = true;
            for (int i = 0; i < 6; i++) {
                if (!Character.isDigit(line.charAt(i))) {
                    isSequence = false;
                    break;
                }
            }
            if (isSequence) {
                char col7 = line.charAt(6);
                if (col7 == '*' || col7 == '/') {
                    return ""; // Comentário clássico na coluna 7
                }
                line = line.substring(7);
            }
        }

        // Remove comentários estilo modern cobol (*> ...)
        int idxModernComment = line.indexOf("*>");
        if (idxModernComment >= 0) {
            line = line.substring(0, idxModernComment);
        }

        return line.trim();
    }

    private static CobolField parseFieldStatement(String stmt) {
        Matcher m = LEVEL_NAME_PATTERN.matcher(stmt);
        if (!m.find()) return null;

        int level = Integer.parseInt(m.group(1));
        String name = m.group(2).toUpperCase();
        String rest = m.group(3);

        String picRaw = null;
        Matcher picMatcher = PIC_PATTERN.matcher(rest);
        if (picMatcher.find()) {
            picRaw = picMatcher.group(1) != null ? picMatcher.group(1) : picMatcher.group(2);
        }

        String usage = "DISPLAY";
        Matcher usageMatcher = USAGE_PATTERN.matcher(rest);
        if (usageMatcher.find()) {
            usage = usageMatcher.group(1).toUpperCase();
        }

        int occurs = 0;
        Matcher occursMatcher = OCCURS_PATTERN.matcher(rest);
        if (occursMatcher.find()) {
            occurs = Integer.parseInt(occursMatcher.group(1));
        }

        String val = null;
        Matcher valMatcher = VALUE_PATTERN.matcher(rest);
        if (valMatcher.find()) {
            if (valMatcher.group(1) != null) val = valMatcher.group(1);
            else if (valMatcher.group(2) != null) val = valMatcher.group(2);
            else val = valMatcher.group(3);
        }

        CobolDataType dataType;
        CobolPicture picture = null;

        if (level == 88) {
            dataType = CobolDataType.CONDITION;
        } else if (picRaw == null || picRaw.isBlank()) {
            dataType = CobolDataType.GROUP;
        } else {
            picture = CobolPicture.parse(picRaw, usage);
            if (picture.isAlphanumeric()) {
                dataType = CobolDataType.ALPHANUMERIC;
            } else if (usage.contains("COMP-3") || usage.contains("PACKED-DECIMAL")) {
                dataType = CobolDataType.PACKED_DECIMAL;
            } else if (usage.contains("COMP") || usage.contains("BINARY")) {
                dataType = CobolDataType.BINARY;
            } else {
                dataType = CobolDataType.NUMERIC_DISPLAY;
            }
        }

        return new CobolField(level, name, dataType, picture, usage, occurs, val);
    }
}
