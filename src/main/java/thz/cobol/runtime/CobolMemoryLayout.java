package thz.cobol.runtime;

import thz.cobol.model.*;

import java.util.*;

/**
 * CobolMemoryLayout — Mapeamento de layout contíguo de memória para registros COBOL e THZ-LANG.
 * Calcula offsets exatos em bytes para garantir compartilhamento transparente de memória (Zero-Copy).
 */
public final class CobolMemoryLayout {

    private final String recordName;
    private final Map<String, FieldOffset> fieldOffsets;
    private final int totalByteLength;

    public CobolMemoryLayout(String recordName, Map<String, FieldOffset> fieldOffsets, int totalByteLength) {
        this.recordName = recordName;
        this.fieldOffsets = Collections.unmodifiableMap(fieldOffsets);
        this.totalByteLength = totalByteLength;
    }

    /**
     * Constrói o layout de memória contíguo a partir de um Copybook ou registro COBOL.
     */
    public static CobolMemoryLayout calcularLayout(CopybookAst copybook) {
        Map<String, FieldOffset> offsets = new LinkedHashMap<>();
        int currentOffset = 0;

        for (CobolField root : copybook.getRootFields()) {
            currentOffset = mapearCampos(root, currentOffset, offsets);
        }

        return new CobolMemoryLayout(copybook.getName(), offsets, currentOffset);
    }

    private static int mapearCampos(CobolField field, int startOffset, Map<String, FieldOffset> offsets) {
        int current = startOffset;

        if (field.isGroup()) {
            for (CobolField child : field.getChildren()) {
                if (child.getLevel() == 88) continue; // Condições 88 não ocupam espaço físico
                current = mapearCampos(child, current, offsets);
            }
            int length = current - startOffset;
            offsets.put(field.getName().toUpperCase(), new FieldOffset(field, startOffset, length));
            return current;
        }

        int length = field.getPicture() != null ? field.getPicture().getByteLength() : 0;
        int occurs = field.getOccurs() > 0 ? field.getOccurs() : 1;
        int totalFieldLen = length * occurs;

        offsets.put(field.getName().toUpperCase(), new FieldOffset(field, current, totalFieldLen));
        return current + totalFieldLen;
    }

    public String getRecordName() {
        return recordName;
    }

    public int getTotalByteLength() {
        return totalByteLength;
    }

    public FieldOffset getOffset(String fieldName) {
        FieldOffset fo = fieldOffsets.get(fieldName.toUpperCase());
        if (fo == null) {
            throw new IllegalArgumentException("Campo não encontrado no layout de memória: " + fieldName);
        }
        return fo;
    }

    public Map<String, FieldOffset> getAllOffsets() {
        return fieldOffsets;
    }

    public record FieldOffset(CobolField field, int offset, int length) {}
}
