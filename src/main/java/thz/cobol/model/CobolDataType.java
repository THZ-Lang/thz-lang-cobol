package thz.cobol.model;

/**
 * Tipos de dados fundamentais do COBOL e seu mapeamento para o ecossistema THZ-LANG.
 */
public enum CobolDataType {
    /** Alfanumérico (PIC X / PIC A) -> THZ TEXTO */
    ALPHANUMERIC("TEXTO", "ptr"),

    /** Numérico Display (PIC 9 sem COMP) -> THZ INTEIRO ou DECIMAL */
    NUMERIC_DISPLAY("INTEIRO", "i64"),

    /** Decimal Empacotado (COMP-3 / BCD) -> THZ DECIMAL (i128 de alta precisão ISO 10967) */
    PACKED_DECIMAL("DECIMAL", "i128"),

    /** Binário (COMP / COMP-4 / BINARY) -> THZ INTEIRO (i32/i64) */
    BINARY("INTEIRO", "i64"),

    /** Grupo / Estrutura aninhada -> THZ ESTRUTURA */
    GROUP("ESTRUTURA", "ptr"),

    /** Condição / Flag de Nível 88 -> THZ LOGICO */
    CONDITION("LOGICO", "i1");

    private final String tipoThz;
    private final String tipoLlvm;

    CobolDataType(String tipoThz, String tipoLlvm) {
        this.tipoThz = tipoThz;
        this.tipoLlvm = tipoLlvm;
    }

    public String getTipoThz() {
        return tipoThz;
    }

    public String getTipoLlvm() {
        return tipoLlvm;
    }
}
