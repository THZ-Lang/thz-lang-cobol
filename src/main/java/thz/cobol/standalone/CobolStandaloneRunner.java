package thz.cobol.standalone;

import thz.cobol.model.*;
import thz.cobol.parser.CobolProcedureParser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CobolStandaloneRunner — Motor de execução de programas COBOL 100% puros sob o THZ-LANG.
 * Executa arquivos .cbl sem misturar código THZ (semelhante ao Jython/IronPython).
 */
public final class CobolStandaloneRunner {

    private final CobolProgram program;
    private final Map<String, Object> storage = new LinkedHashMap<>();
    private final Map<String, CobolScreenItem> screenItems = new LinkedHashMap<>();
    private final List<String> outputLogs = new ArrayList<>();

    public CobolStandaloneRunner(CobolProgram program) {
        this.program = Objects.requireNonNull(program, "Programa COBOL não pode ser nulo");
        inicializarVariaveis();
    }

    public static CobolStandaloneRunner carregar(String codigoCobol) {
        CobolProgram prog = CobolProcedureParser.parseProgram(codigoCobol);
        return new CobolStandaloneRunner(prog);
    }

    private void inicializarVariaveis() {
        for (CobolField f : program.workingStorage()) {
            cadastrarCampos(f);
        }
        for (CobolScreenItem s : program.screenSection()) {
            cadastrarScreenItem(s);
        }
    }

    private void cadastrarScreenItem(CobolScreenItem item) {
        if (item.name() != null && !item.name().isBlank()) {
            screenItems.put(item.name().toUpperCase(), item);
        }
        for (CobolScreenItem child : item.children()) {
            cadastrarScreenItem(child);
        }
    }

    private void cadastrarCampos(CobolField field) {
        if (field.isGroup()) {
            for (CobolField child : field.getChildren()) {
                cadastrarCampos(child);
            }
        } else {
            Object valorInicial = switch (field.getDataType()) {
                case ALPHANUMERIC -> field.getDefaultValue() != null ? field.getDefaultValue() : "";
                case PACKED_DECIMAL, NUMERIC_DISPLAY, BINARY -> {
                    if (field.getDefaultValue() != null && !field.getDefaultValue().isBlank()) {
                        yield new BigDecimal(field.getDefaultValue());
                    }
                    yield BigDecimal.ZERO;
                }
                case CONDITION, GROUP -> null;
            };
            storage.put(field.getName().toUpperCase(), valorInicial);
        }
    }

    /**
     * Executa a lógica da PROCEDURE DIVISION do programa COBOL puro.
     */
    public ExecucaoResultado executar() {
        outputLogs.clear();

        for (CobolParagraph par : program.paragraphs()) {
            boolean finalizar = executarParagrafo(par);
            if (finalizar) break;
        }

        return new ExecucaoResultado(Collections.unmodifiableMap(storage), Collections.unmodifiableList(outputLogs));
    }

    /**
     * Executa a lógica da PROCEDURE DIVISION operando in-place sobre o buffer de memória compartilhada.
     * THZ-LANG e COBOL compartilham o mesmo espaço contíguo de bytes físicos sem cópia.
     */
    public ExecucaoResultado executarSobreMemoriaCompartilhada(thz.cobol.runtime.CobolSharedMemorySegment sharedMem) {
        Objects.requireNonNull(sharedMem, "Segmento de memória compartilhada não pode ser nulo");
        var layout = sharedMem.getLayout();

        // 1. Sincroniza o estado inicial lendo os bytes físicos contíguos
        for (var entry : layout.getAllOffsets().entrySet()) {
            String fieldName = entry.getKey();
            var fo = entry.getValue();
            var f = fo.field();
            if (f.isGroup()) continue;

            Object val = switch (f.getDataType()) {
                case ALPHANUMERIC -> sharedMem.lerAlfanumerico(fieldName);
                case PACKED_DECIMAL -> sharedMem.lerDecimalComp3(fieldName);
                case NUMERIC_DISPLAY, BINARY -> new BigDecimal(sharedMem.lerInteiroDisplay(fieldName));
                default -> null;
            };
            if (val != null) {
                storage.put(fieldName, val);
            }
        }

        // 2. Executa as instruções da PROCEDURE DIVISION
        ExecucaoResultado res = executar();

        // 3. Escreve in-place de volta na memória compartilhada contígua
        for (var entry : layout.getAllOffsets().entrySet()) {
            String fieldName = entry.getKey();
            var fo = entry.getValue();
            var f = fo.field();
            if (f.isGroup()) continue;

            Object val = storage.get(fieldName);
            if (val == null) continue;

            switch (f.getDataType()) {
                case ALPHANUMERIC -> sharedMem.escreverAlfanumerico(fieldName, String.valueOf(val));
                case PACKED_DECIMAL -> sharedMem.escreverDecimalComp3(fieldName, paraBigDecimal(val));
                case NUMERIC_DISPLAY, BINARY -> sharedMem.escreverInteiroDisplay(fieldName, paraBigDecimal(val).longValue());
                default -> {}
            }
        }

        return res;
    }

    private boolean executarParagrafo(CobolParagraph par) {
        for (CobolStatement stmt : par.statements()) {
            switch (stmt) {
                case CobolStatement.Move(String src, String dst) -> {
                    Object val = avaliarValor(src);
                    storage.put(dst.toUpperCase(), val);
                }
                case CobolStatement.Compute(String target, String expr) -> {
                    BigDecimal res = avaliarExpressaoAritmetica(expr);
                    storage.put(target.toUpperCase(), res);
                }
                case CobolStatement.Add(String src, String dst, String giving) -> {
                    BigDecimal vSrc = paraBigDecimal(avaliarValor(src));
                    BigDecimal vDst = paraBigDecimal(storage.getOrDefault(dst.toUpperCase(), BigDecimal.ZERO));
                    BigDecimal soma = vDst.add(vSrc);
                    String target = giving != null ? giving.toUpperCase() : dst.toUpperCase();
                    storage.put(target, soma);
                }
                case CobolStatement.Subtract(String src, String dst, String giving) -> {
                    BigDecimal vSrc = paraBigDecimal(avaliarValor(src));
                    BigDecimal vDst = paraBigDecimal(storage.getOrDefault(dst.toUpperCase(), BigDecimal.ZERO));
                    BigDecimal sub = vDst.subtract(vSrc);
                    String target = giving != null ? giving.toUpperCase() : dst.toUpperCase();
                    storage.put(target, sub);
                }
                case CobolStatement.Display(List<String> items) -> {
                    if (items.size() == 1 && screenItems.containsKey(items.get(0).toUpperCase())) {
                        renderizarItemTela(screenItems.get(items.get(0).toUpperCase()));
                    } else {
                        StringBuilder line = new StringBuilder();
                        for (int i = 0; i < items.size(); i++) {
                            String it = items.get(i);
                            String valor;
                            if ((it.startsWith("\"") && it.endsWith("\"")) || (it.startsWith("'") && it.endsWith("'"))) {
                                valor = it.substring(1, it.length() - 1);
                            } else if (storage.containsKey(it.toUpperCase())) {
                                valor = String.valueOf(storage.get(it.toUpperCase()));
                            } else {
                                valor = it;
                            }
                            if (!line.isEmpty() && !line.toString().endsWith(" ") && !valor.startsWith(" ")) {
                                line.append(" ");
                            }
                            line.append(valor);
                        }
                        outputLogs.add(line.toString().trim());
                    }
                }
                case CobolStatement.Goback() -> {
                    return true; // Encerra execução (GOBACK / STOP RUN)
                }
                default -> {}
            }
        }
        return false;
    }

    private void renderizarItemTela(CobolScreenItem screen) {
        if (screen.blankScreen()) {
            outputLogs.add("\033[2J\033[H");
        }
        renderizarLinhaTela(screen);
        for (CobolScreenItem child : screen.children()) {
            if (child.blankScreen()) {
                outputLogs.add("\033[2J\033[H");
            }
            renderizarLinhaTela(child);
        }
    }

    private void renderizarLinhaTela(CobolScreenItem item) {
        String texto = "";
        if (item.value() != null && !item.value().isBlank()) {
            texto = item.value();
        } else if (item.usingField() != null && storage.containsKey(item.usingField().toUpperCase())) {
            texto = String.valueOf(storage.get(item.usingField().toUpperCase()));
        } else if (item.fromField() != null && storage.containsKey(item.fromField().toUpperCase())) {
            texto = String.valueOf(storage.get(item.fromField().toUpperCase()));
        } else if (item.toField() != null && storage.containsKey(item.toField().toUpperCase())) {
            texto = String.valueOf(storage.get(item.toField().toUpperCase()));
        }

        if (texto.isEmpty() && !item.blankScreen() && item.line() == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (item.line() != null && item.column() != null) {
            sb.append(String.format("\033[%d;%dH", item.line(), item.column()));
        }

        List<String> sgr = new ArrayList<>();
        if (item.highlight()) sgr.add("1");
        if (item.reverseVideo()) sgr.add("7");
        if (item.blink()) sgr.add("5");

        if (item.foregroundColor() != null) {
            sgr.add(String.valueOf(cobolParaAnsiFg(item.foregroundColor())));
        }
        if (item.backgroundColor() != null) {
            sgr.add(String.valueOf(cobolParaAnsiBg(item.backgroundColor())));
        }

        if (!sgr.isEmpty()) {
            sb.append("\033[").append(String.join(";", sgr)).append("m");
        }

        sb.append(texto);

        if (!sgr.isEmpty()) {
            sb.append("\033[0m");
        }

        outputLogs.add(sb.toString());
    }

    private static int cobolParaAnsiFg(int cobolColor) {
        return switch (cobolColor % 8) {
            case 0 -> 30; // Preto
            case 1 -> 34; // Azul
            case 2 -> 32; // Verde
            case 3 -> 36; // Ciano
            case 4 -> 31; // Vermelho
            case 5 -> 35; // Magenta
            case 6 -> 33; // Marrom / Amarelo
            case 7 -> 37; // Branco
            default -> 37;
        };
    }

    private static int cobolParaAnsiBg(int cobolColor) {
        return cobolParaAnsiFg(cobolColor) + 10;
    }

    private Object avaliarValor(String raw) {
        String s = raw.trim();
        if (s.equalsIgnoreCase("ZERO") || s.equalsIgnoreCase("ZEROS")) return BigDecimal.ZERO;
        if (s.equalsIgnoreCase("SPACE") || s.equalsIgnoreCase("SPACES")) return "";

        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }

        if (storage.containsKey(s.toUpperCase())) {
            return storage.get(s.toUpperCase());
        }

        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return s;
        }
    }

    private BigDecimal paraBigDecimal(Object obj) {
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number num) return new BigDecimal(num.toString());
        if (obj != null) {
            try {
                return new BigDecimal(obj.toString());
            } catch (Exception ignored) {}
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal avaliarExpressaoAritmetica(String expr) {
        // Substitui variáveis pelo seu valor numérico
        String processada = expr;
        for (Map.Entry<String, Object> entry : storage.entrySet()) {
            if (entry.getValue() instanceof BigDecimal bd) {
                processada = processada.replaceAll("(?i)\\b" + Pattern.quote(entry.getKey()) + "\\b", bd.toPlainString());
            }
        }

        return avaliarExpressaoInterna(processada).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal avaliarExpressaoInterna(String expr) {
        // Suporte para expressões aritméticas financeiras básicas: a * (1 + b / 100)
        String s = expr.trim();

        // Parênteses
        Pattern parenPattern = Pattern.compile("\\(([^()]+)\\)");
        Matcher m = parenPattern.matcher(s);
        while (m.find()) {
            BigDecimal sub = avaliarExpressaoInterna(m.group(1));
            s = s.substring(0, m.start()) + " " + sub.toPlainString() + " " + s.substring(m.end());
            m = parenPattern.matcher(s);
        }

        // Garante espaçamento ao redor de operadores
        s = s.replaceAll("(?<=\\d)\\s*([*+/])\\s*(?=\\d)", " $1 ");
        s = s.replaceAll("(?<=\\d)\\s*-\\s*(?=\\d)", " - ");

        // Divisão e Multiplicação
        String[] tokens = s.trim().split("\\s+");
        List<String> list = new ArrayList<>(Arrays.asList(tokens));

        for (int i = 0; i < list.size(); i++) {
            String op = list.get(i);
            if (op.equals("*") || op.equals("/")) {
                if (i == 0 || i + 1 >= list.size()) {
                    throw new IllegalArgumentException("Expressão aritmética malformatada ao redor do operador: " + op);
                }
                BigDecimal a = new BigDecimal(list.get(i - 1));
                BigDecimal b = new BigDecimal(list.get(i + 1));
                BigDecimal r = op.equals("*") ? a.multiply(b) : a.divide(b, 8, RoundingMode.HALF_UP);

                list.set(i - 1, r.stripTrailingZeros().toPlainString());
                list.remove(i);
                list.remove(i);
                i--;
            }
        }

        // Adição e Subtração
        for (int i = 0; i < list.size(); i++) {
            String op = list.get(i);
            if (op.equals("+") || op.equals("-")) {
                if (i == 0 || i + 1 >= list.size()) {
                    throw new IllegalArgumentException("Expressão aritmética malformatada ao redor do operador: " + op);
                }
                BigDecimal a = new BigDecimal(list.get(i - 1));
                BigDecimal b = new BigDecimal(list.get(i + 1));
                BigDecimal r = op.equals("+") ? a.add(b) : a.subtract(b);

                list.set(i - 1, r.stripTrailingZeros().toPlainString());
                list.remove(i);
                list.remove(i);
                i--;
            }
        }

        if (list.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(list.get(0));
    }

    public record ExecucaoResultado(Map<String, Object> variaveis, List<String> logs) {}
}
