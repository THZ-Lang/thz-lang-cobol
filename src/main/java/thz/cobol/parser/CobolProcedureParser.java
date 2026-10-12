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

        if (!dataPart.isBlank()) {
            int wsIdx = indexOfIgnoreCase(dataPart, "WORKING-STORAGE SECTION.");
            int lkIdx = indexOfIgnoreCase(dataPart, "LINKAGE SECTION.");

            if (wsIdx >= 0 && lkIdx > wsIdx) {
                String wsContent = dataPart.substring(wsIdx + "WORKING-STORAGE SECTION.".length(), lkIdx);
                String lkContent = dataPart.substring(lkIdx + "LINKAGE SECTION.".length());
                workingStorage = CopybookParser.parse(programId + "_WS", wsContent).getRootFields();
                linkageSection = CopybookParser.parse(programId + "_LK", lkContent).getRootFields();
            } else if (wsIdx >= 0) {
                String wsContent = dataPart.substring(wsIdx + "WORKING-STORAGE SECTION.".length());
                workingStorage = CopybookParser.parse(programId + "_WS", wsContent).getRootFields();
            } else if (lkIdx >= 0) {
                String lkContent = dataPart.substring(lkIdx + "LINKAGE SECTION.".length());
                linkageSection = CopybookParser.parse(programId + "_LK", lkContent).getRootFields();
            } else {
                workingStorage = CopybookParser.parse(programId + "_DATA", dataPart).getRootFields();
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
        return new CobolProgram(programId, workingStorage, linkageSection, paragraphs);
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
