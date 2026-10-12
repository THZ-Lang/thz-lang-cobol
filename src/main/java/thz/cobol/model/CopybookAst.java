package thz.cobol.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa a Árvore de Sintaxe Abstrata (AST) de um Copybook ou da DATA DIVISION do COBOL.
 */
public final class CopybookAst {

    private final String name;
    private final List<CobolField> rootFields;

    public CopybookAst(String name) {
        this(name, new ArrayList<>());
    }

    public CopybookAst(String name, List<CobolField> rootFields) {
        this.name = name;
        this.rootFields = rootFields != null ? rootFields : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public List<CobolField> getRootFields() {
        return Collections.unmodifiableList(rootFields);
    }

    public void addRootField(CobolField field) {
        this.rootFields.add(field);
    }

    /**
     * Calcula o tamanho total em bytes do registro.
     */
    public int getTotalByteLength() {
        int total = 0;
        for (CobolField f : rootFields) {
            total += calculateFieldLength(f);
        }
        return total;
    }

    private int calculateFieldLength(CobolField field) {
        if (field.isGroup()) {
            int subTotal = 0;
            for (CobolField child : field.getChildren()) {
                subTotal += calculateFieldLength(child);
            }
            int occurs = field.getOccurs() > 0 ? field.getOccurs() : 1;
            return subTotal * occurs;
        }
        int len = field.getPicture() != null ? field.getPicture().getByteLength() : 0;
        int occurs = field.getOccurs() > 0 ? field.getOccurs() : 1;
        return len * occurs;
    }
}
