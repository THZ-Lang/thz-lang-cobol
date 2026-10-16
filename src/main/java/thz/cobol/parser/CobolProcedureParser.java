package thz.cobol.parser;

import thz.cobol.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CobolProcedureParser — Analisador da PROCEDURE DIVISION e orquestrador de programas COBOL.
 */
public final class CobolProcedureParser {

    private static final Pattern PROGRAM_ID_PATTERN = Pattern.compile("PROGRAM-ID\\.\\s*([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("^([A-Z0-9_-]+)\\.\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern MOVE_PATTERN = Pattern.compile("^MOVE\\s+(.+?)\\s+TO\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPUTE_PATTERN = Pattern.compile("^COMPUTE\\s+([A-Z0-9_-]+)\\s*=\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_PATTERN = Pattern.compile("^ADD\\s+(.+?)\\s+TO\\s+(.+?)(?:\\s+GIVING\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBTRACT_PATTERN = Pattern.compile("^SUBTRACT\\s+(.+?)\\s+FROM\\s+(.+?)(?:\\s+GIVING\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERFORM_PATTERN = Pattern.compile("^PERFORM\\s+([A-Z0-9_-]+)(?:\\s+(\\d+)\\s+TIMES)?(?:\\s+UNTIL\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISPLAY_PATTERN = Pattern.compile("^DISPLAY\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CALL_PATTERN = Pattern.compile("^CALL\\s+(?:'([^']*)'|\"([^\"]*)\"|([A-Z0-9_-]+))(?:\\s+USING\\s+(.+))?$", Pattern.CASE_INSENSITIVE);

    /**
     * Faz o parsing completo de um programa COBOL (Data e Procedure Division).
     */
    public static CobolProgram parseProgram(String sourceCode) {
        Objects.requireNonNull(sourceCode, "Código-fonte COBOL não pode ser nulo");

        String programId = "PROGRAMA_COBOL";
        Matcher pidM = PROGRAM_ID_PATTERN.matcher(sourceCode);
        if (pidM.find()) {
            programId = pidM.group(1).toUpperCase();
        }

        // Separa seções DATA DIVISION e PROCEDURE DIVISION
        String dataPart = "";
        String procPart;

        int dataIdx = indexOfIgnoreCase(sourceCode, "DATA DIVISION.");
        int procIdx = indexOfIgnoreCase(sourceCode, "PROCEDURE DIVISION");

        if (dataIdx >= 0 && procIdx > dataIdx) {
            dataPart = sourceCode.substring(dataIdx + "DATA DIVISION.".length(), procIdx);
            procPart = sourceCode.substring(procIdx);
        } else if (procIdx >= 0) {
            procPart = sourceCode.substring(procIdx);
        } else {
            procPart = sourceCode;
        }

        // Analisa DATA DIVISION se presente
        List<CobolField> workingStorage = List.of();
        List<CobolField> linkageSection = List.of();
        List<CobolScreenItem> screenSection = List.of();

        if (!dataPart.isBlank()) {
            record SecaoEncontrada(String nome, int start, int headerLen) {}
            List<SecaoEncontrada> secoes = new ArrayList<>();

            int wsIdx = indexOfIgnoreCase(dataPart, "WORKING-STORAGE SECTION.");
            if (wsIdx >= 0) secoes.add(new SecaoEncontrada("WS", wsIdx, "WORKING-STORAGE SECTION.".length()));

            int lkIdx = indexOfIgnoreCase(dataPart, "LINKAGE SECTION.");
            if (lkIdx >= 0) secoes.add(new SecaoEncontrada("LK", lkIdx, "LINKAGE SECTION.".length()));

            int scIdx = indexOfIgnoreCase(dataPart, "SCREEN SECTION.");
            if (scIdx >= 0) secoes.add(new SecaoEncontrada("SC", scIdx, "SCREEN SECTION.".length()));

            secoes.sort(Comparator.comparingInt(SecaoEncontrada::start));

            if (secoes.isEmpty()) {
                workingStorage = CopybookParser.parse(programId + "_DATA", dataPart).getRootFields();
            } else {
                for (int i = 0; i < secoes.size(); i++) {
                    SecaoEncontrada atual = secoes.get(i);
                    int inicioConteudo = atual.start + atual.headerLen;
                    int fimConteudo = (i + 1 < secoes.size()) ? secoes.get(i + 1).start : dataPart.length();
                    String conteudo = dataPart.substring(inicioConteudo, fimConteudo);

                    switch (atual.nome) {
                        case "WS" -> workingStorage = CopybookParser.parse(programId + "_WS", conteudo).getRootFields();
                        case "LK" -> linkageSection = CopybookParser.parse(programId + "_LK", conteudo).getRootFields();
                        case "SC" -> screenSection = parseScreenSection(conteudo);
                    }
                }
            }
        }

        // Remove cabeçalho 'PROCEDURE DIVISION ... .'
        if (procIdx >= 0) {
            int dotIdx = procPart.indexOf('.');
            if (dotIdx >= 0) {
                procPart = procPart.substring(dotIdx + 1);
            }
        }

        List<CobolParagraph> paragraphs = parseParagraphs(procPart);
        return new CobolProgram(programId, workingStorage, linkageSection, paragraphs, screenSection);
    }

    private static List<CobolScreenItem> parseScreenSection(String scContent) {
        if (scContent == null || scContent.isBlank()) return List.of();
        List<CobolScreenItem> roots = new ArrayList<>();
        CobolScreenItem.Builder currentRoot = null;
        List<CobolScreenItem> currentChildren = new ArrayList<>();

        // Normaliza linhas e divide por declarações de nível (ex: 01 ..., 05 ...)
        String[] lines = scContent.split("\\R");
        StringBuilder currentStmt = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("*")) continue;

            if (trimmed.matches("^\\d{2}\\b.*")) {
                if (!currentStmt.isEmpty()) {
                    processScreenStatement(currentStmt.toString(), roots, currentChildren);
                    currentStmt.setLength(0);
                }
            }
            if (!currentStmt.isEmpty()) currentStmt.append(" ");
            currentStmt.append(trimmed);
        }
        if (!currentStmt.isEmpty()) {
            processScreenStatement(currentStmt.toString(), roots, currentChildren);
        }

        // Consolida último root se houver
        if (!currentChildren.isEmpty() && !roots.isEmpty()) {
            int lastIdx = roots.size() - 1;
            CobolScreenItem last = roots.get(lastIdx);
            roots.set(lastIdx, new CobolScreenItem(
                    last.name(), last.level(), last.line(), last.column(), last.value(),
                    last.foregroundColor(), last.backgroundColor(),
                    last.highlight(), last.reverseVideo(), last.blink(), last.blankScreen(),
                    last.fromField(), last.toField(), last.usingField(),
                    List.copyOf(currentChildren)
            ));
        }

        return List.copyOf(roots);
    }

    private static void processScreenStatement(String stmt, List<CobolScreenItem> roots, List<CobolScreenItem> currentChildren) {
        String clean = stmt.trim();
        if (clean.endsWith(".")) clean = clean.substring(0, clean.length() - 1).trim();

        Matcher levelM = Pattern.compile("^(\\d{2})(?:\\s+([A-Z0-9_-]+))?", Pattern.CASE_INSENSITIVE).matcher(clean);
        if (!levelM.find()) return;

        int level = Integer.parseInt(levelM.group(1));
        String name = levelM.group(2) != null ? levelM.group(2).toUpperCase() : "";

        CobolScreenItem.Builder builder = CobolScreenItem.builder(name, level);

        if (Pattern.compile("\\bBLANK\\s+SCREEN\\b", Pattern.CASE_INSENSITIVE).matcher(clean).find()) {
            builder.blankScreen(true);
        }
        if (Pattern.compile("\\bHIGHLIGHT\\b", Pattern.CASE_INSENSITIVE).matcher(clean).find()) {
            builder.highlight(true);
        }
        if (Pattern.compile("\\bREVERSE-VIDEO\\b", Pattern.CASE_INSENSITIVE).matcher(clean).find()) {
            builder.reverseVideo(true);
        }
        if (Pattern.compile("\\bBLINK\\b", Pattern.CASE_INSENSITIVE).matcher(clean).find()) {
            builder.blink(true);
        }

        Matcher lineM = Pattern.compile("\\bLINE(?:\\s+IS)?\\s+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(clean);
        if (lineM.find()) builder.line(Integer.parseInt(lineM.group(1)));

        Matcher colM = Pattern.compile("\\b(?:COL|COLUMN)(?:\\s+IS)?\\s+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(clean);
        if (colM.find()) builder.column(Integer.parseInt(colM.group(1)));

        Matcher fgM = Pattern.compile("\\bFOREGROUND-COLOU?R(?:\\s+IS)?\\s+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(clean);
        if (fgM.find()) builder.foregroundColor(Integer.parseInt(fgM.group(1)));

        Matcher bgM = Pattern.compile("\\bBACKGROUND-COLOU?R(?:\\s+IS)?\\s+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(clean);
        if (bgM.find()) builder.backgroundColor(Integer.parseInt(bgM.group(1)));

        Matcher valM = Pattern.compile("\\bVALUE(?:\\s+IS)?\\s+(\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE).matcher(clean);
        if (valM.find()) {
            String v = valM.group(2) != null ? valM.group(2) : valM.group(3);
            builder.value(v);
        }

        Matcher fromM = Pattern.compile("\\bFROM\\s+([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE).matcher(clean);
        if (fromM.find()) builder.fromField(fromM.group(1).toUpperCase());

        Matcher toM = Pattern.compile("\\bTO\\s+([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE).matcher(clean);
        if (toM.find()) builder.toField(toM.group(1).toUpperCase());

        Matcher usingM = Pattern.compile("\\bUSING\\s+([A-Z0-9_-]+)", Pattern.CASE_INSENSITIVE).matcher(clean);
        if (usingM.find()) builder.usingField(usingM.group(1).toUpperCase());

        CobolScreenItem item = builder.build();

        if (level == 1) {
            // Se já tínhamos um root anterior com filhos, consolida-o
            if (!currentChildren.isEmpty() && !roots.isEmpty()) {
                int lastIdx = roots.size() - 1;
                CobolScreenItem last = roots.get(lastIdx);
                roots.set(lastIdx, new CobolScreenItem(
                        last.name(), last.level(), last.line(), last.column(), last.value(),
                        last.foregroundColor(), last.backgroundColor(),
                        last.highlight(), last.reverseVideo(), last.blink(), last.blankScreen(),
                        last.fromField(), last.toField(), last.usingField(),
                        List.copyOf(currentChildren)
                ));
                currentChildren.clear();
            }
            roots.add(item);
        } else {
            currentChildren.add(item);
        }
    }

    private static List<CobolParagraph> parseParagraphs(String procCode) {
        List<CobolParagraph> paragraphs = new ArrayList<>();
        String currentParName = "MAIN_LOGIC";
        List<CobolStatement> currentStmts = new ArrayList<>();
        StringBuilder pendingSentence = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new StringReader(procCode))) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                String line = rawLine.trim();
                if (line.isBlank() || line.startsWith("*") || line.startsWith("/>")) continue;

                Matcher parM = PARAGRAPH_PATTERN.matcher(line);
                if (parM.matches()) {
                    if (pendingSentence.length() > 0) {
                        for (String s : splitSentences(pendingSentence.toString(), null)) {
                            CobolStatement stmt = parseStatement(s.trim());
                            if (stmt != null) currentStmts.add(stmt);
                        }
                        pendingSentence.setLength(0);
                    }
                    if (!currentStmts.isEmpty()) {
                        paragraphs.add(new CobolParagraph(currentParName, currentStmts));
                        currentStmts = new ArrayList<>();
                    }
                    currentParName = parM.group(1).toUpperCase();
                    continue;
                }

                if (pendingSentence.length() > 0) {
                    pendingSentence.append(" ");
                }
                pendingSentence.append(line);

                List<String> sentences = splitSentences(pendingSentence.toString(), pendingSentence);
                for (String s : sentences) {
                    s = s.trim();
                    if (!s.isBlank()) {
                        CobolStatement stmt = parseStatement(s);
                        if (stmt != null) {
                            currentStmts.add(stmt);
                        }
                    }
                }
            }

            if (pendingSentence.length() > 0) {
                for (String s : splitSentences(pendingSentence.toString(), null)) {
                    s = s.trim();
                    if (!s.isBlank()) {
                        CobolStatement stmt = parseStatement(s);
                        if (stmt != null) {
                            currentStmts.add(stmt);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao processar parágrafos COBOL", e);
        }

        if (!currentStmts.isEmpty()) {
            paragraphs.add(new CobolParagraph(currentParName, currentStmts));
        }

        return paragraphs;
    }

    private static List<String> splitSentences(String text) {
        return splitSentences(text, null);
    }

    private static List<String> splitSentences(String line, StringBuilder leftover) {
        List<String> sentences = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inDoubleQuote = false;
        boolean inSingleQuote = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                sb.append(c);
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                sb.append(c);
            } else if (c == '.' && !inDoubleQuote && !inSingleQuote) {
                boolean isEndOfSentence = (i + 1 == line.length()) || Character.isWhitespace(line.charAt(i + 1));
                if (isEndOfSentence) {
                    String s = sb.toString().trim();
                    if (!s.isBlank()) {
                        sentences.add(s);
                    }
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        String rem = sb.toString().trim();
        if (leftover != null) {
            leftover.setLength(0);
            leftover.append(rem);
        } else if (!rem.isBlank()) {
            sentences.add(rem);
        }
        return sentences;
    }

    public static CobolStatement parseStatement(String text) {
        String s = text.trim();
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1).trim();

        if (s.equalsIgnoreCase("GOBACK") || s.equalsIgnoreCase("STOP RUN")) {
            return new CobolStatement.Goback();
        }

        Matcher mm = MOVE_PATTERN.matcher(s);
        if (mm.matches()) {
            return new CobolStatement.Move(mm.group(1).trim(), mm.group(2).trim());
        }

        Matcher cm = COMPUTE_PATTERN.matcher(s);
        if (cm.matches()) {
            return new CobolStatement.Compute(cm.group(1).trim(), cm.group(2).trim());
        }

        Matcher am = ADD_PATTERN.matcher(s);
        if (am.matches()) {
            return new CobolStatement.Add(am.group(1).trim(), am.group(2).trim(), am.group(3) != null ? am.group(3).trim() : null);
        }

        Matcher sm = SUBTRACT_PATTERN.matcher(s);
        if (sm.matches()) {
            return new CobolStatement.Subtract(sm.group(1).trim(), sm.group(2).trim(), sm.group(3) != null ? sm.group(3).trim() : null);
        }

        Matcher pm = PERFORM_PATTERN.matcher(s);
        if (pm.matches()) {
            Integer times = pm.group(2) != null ? Integer.valueOf(pm.group(2)) : null;
            return new CobolStatement.Perform(pm.group(1).trim(), times, pm.group(3));
        }

        Matcher dm = DISPLAY_PATTERN.matcher(s);
        if (dm.matches()) {
            List<String> items = new ArrayList<>();
            Matcher itemM = Pattern.compile("\"[^\"]*\"|'[^']*'|[^\\s\"']+").matcher(dm.group(1));
            while (itemM.find()) {
                items.add(itemM.group());
            }
            return new CobolStatement.Display(items);
        }

        Matcher callM = CALL_PATTERN.matcher(s);
        if (callM.matches()) {
            String prog = callM.group(1) != null ? callM.group(1) : (callM.group(2) != null ? callM.group(2) : callM.group(3));
            List<String> params = List.of();
            if (callM.group(4) != null) {
                params = Arrays.stream(callM.group(4).split("\\s+")).map(String::trim).toList();
            }
            return new CobolStatement.Call(prog, params);
        }

        return null;
    }

    private static int indexOfIgnoreCase(String str, String search) {
        return str.toLowerCase().indexOf(search.toLowerCase());
    }
}
