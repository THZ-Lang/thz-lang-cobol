package thz.cobol.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa um campo ou registro de dados no COBOL (ex: 01 CLIENTE-REGISTRO, 05 CLI-NOME PIC X(30)).
 */
public final class CobolField {

    private final int level;
    private final String name;
    private final CobolDataType dataType;
    private final CobolPicture picture;
    private final String usage;
    private final int occurs;
    private final String defaultValue;
    private final List<CobolField> children;

    public CobolField(int level, String name, CobolDataType dataType, CobolPicture picture, String usage, int occurs, String defaultValue) {
        this(level, name, dataType, picture, usage, occurs, defaultValue, new ArrayList<>());
    }

    public CobolField(int level, String name, CobolDataType dataType, CobolPicture picture, String usage, int occurs, String defaultValue, List<CobolField> children) {
        this.level = level;
        this.name = name;
        this.dataType = dataType;
        this.picture = picture;
        this.usage = usage != null ? usage : "DISPLAY";
        this.occurs = occurs;
        this.defaultValue = defaultValue;
        this.children = children != null ? children : new ArrayList<>();
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public CobolDataType getDataType() {
        return dataType;
    }

    public CobolPicture getPicture() {
        return picture;
    }

    public String getUsage() {
        return usage;
    }

    public int getOccurs() {
        return occurs;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public List<CobolField> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(CobolField child) {
        this.children.add(child);
    }

    public boolean isGroup() {
        return !children.isEmpty() || dataType == CobolDataType.GROUP;
    }

    /**
     * Retorna o nome formatado no padrão snake_case para o THZ-LANG.
     * Exemplo: 'CLI-SALDO-DEVEDOR' -> 'cli_saldo_devedor'.
     */
    public String getNomeThz() {
        if (name == null) return "campo";
        return name.toLowerCase().replace("-", "_").trim();
    }

    @Override
    public String toString() {
        return String.format("%02d %s %s [%s]", level, name, picture != null ? picture.getRawPicture() : "GROUP", dataType);
    }
}
