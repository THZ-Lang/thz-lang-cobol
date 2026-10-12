package thz.cobol.runtime;

import thz.cobol.model.CobolField;
import thz.cobol.model.CobolPicture;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.math.BigDecimal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * CobolSharedMemorySegment — Buffer contíguo de memória compartilhada entre THZ-LANG e COBOL.
 *
 * Utiliza a Foreign Function & Memory API (Project Panama) do Java 25 para alocar
 * memória nativa contígua sem JNI, compartilhada transparentemente entre:
 * - O código de execução do THZ-LANG;
 * - As rotinas da PROCEDURE DIVISION do COBOL;
 * - O runtime nativo de arenas O(1) e o backend LLVM IR.
 */
public final class CobolSharedMemorySegment implements AutoCloseable {

    private final CobolMemoryLayout layout;
    private final Arena arena;
    private final MemorySegment segment;
    private final byte[] backingBytes;

    private CobolSharedMemorySegment(CobolMemoryLayout layout, Arena arena, MemorySegment segment, byte[] backingBytes) {
        this.layout = Objects.requireNonNull(layout, "Layout de memória não pode ser nulo");
        this.arena = arena;
        this.segment = segment;
        this.backingBytes = backingBytes;
    }

    /**
     * Aloca um segmento de memória nativa contígua compartilhada para o layout indicado.
     */
    public static CobolSharedMemorySegment alocar(CobolMemoryLayout layout) {
        int size = Math.max(1, layout.getTotalByteLength());
        byte[] bytes = new byte[size];
        // Preenche inicialmente com espaços em branco (padrão mainframe COBOL)
        Arrays.fill(bytes, (byte) ' ');

        Arena arena = Arena.ofConfined();
        MemorySegment nativeSegment = arena.allocate(size);
        MemorySegment.copy(MemorySegment.ofArray(bytes), 0, nativeSegment, 0, size);

        return new CobolSharedMemorySegment(layout, arena, nativeSegment, bytes);
    }

    public CobolMemoryLayout getLayout() {
        return layout;
    }

    public int getByteLength() {
        return layout.getTotalByteLength();
    }

    /**
     * Escreve uma string alfanumérica (PIC X) no offset exato do campo, aplicando padding de espaços.
     */
    public void escreverAlfanumerico(String nomeCampo, String texto) {
        var fo = layout.getOffset(nomeCampo);
        byte[] strBytes = texto != null ? texto.getBytes(StandardCharsets.ISO_8859_1) : new byte[0];
        int fieldLen = fo.length();

        for (int i = 0; i < fieldLen; i++) {
            byte b = (i < strBytes.length) ? strBytes[i] : (byte) ' ';
            segment.set(ValueLayout.JAVA_BYTE, fo.offset() + i, b);
            backingBytes[fo.offset() + i] = b;
        }
    }

    /**
     * Lê uma string alfanumérica (PIC X) do offset exato do campo.
     */
    public String lerAlfanumerico(String nomeCampo) {
        var fo = layout.getOffset(nomeCampo);
        byte[] dst = new byte[fo.length()];
        MemorySegment.copy(segment, fo.offset(), MemorySegment.ofArray(dst), 0, fo.length());
        return new String(dst, StandardCharsets.ISO_8859_1).stripTrailing();
    }

    /**
     * Escreve um valor Decimal em formato COMP-3 (Packed Decimal BCD) in-place no buffer compartilhado.
     */
    public void escreverDecimalComp3(String nomeCampo, BigDecimal valor) {
        var fo = layout.getOffset(nomeCampo);
        CobolField field = fo.field();
        CobolPicture pic = field.getPicture();

        int digits = pic != null ? pic.getTotalDigits() : 9;
        int scale = pic != null ? pic.getDecimalScale() : 2;

        byte[] comp3 = PackedDecimalConverter.encode(valor, digits, scale);
        // Copia os bytes empacotados diretamente para o offset de memória compartilhada
        for (int i = 0; i < comp3.length && i < fo.length(); i++) {
            segment.set(ValueLayout.JAVA_BYTE, fo.offset() + i, comp3[i]);
            backingBytes[fo.offset() + i] = comp3[i];
        }
    }

    /**
     * Lê um valor Decimal em formato COMP-3 (Packed Decimal BCD) do buffer compartilhado.
     */
    public BigDecimal lerDecimalComp3(String nomeCampo) {
        var fo = layout.getOffset(nomeCampo);
        CobolField field = fo.field();
        CobolPicture pic = field.getPicture();
        int scale = pic != null ? pic.getDecimalScale() : 2;

        byte[] dst = new byte[fo.length()];
        MemorySegment.copy(segment, fo.offset(), MemorySegment.ofArray(dst), 0, fo.length());
        return PackedDecimalConverter.decode(dst, scale);
    }

    /**
     * Escreve um número inteiro numérico display (PIC 9) no offset exato com padding de zeros.
     */
    public void escreverInteiroDisplay(String nomeCampo, long valor) {
        var fo = layout.getOffset(nomeCampo);
        String formatted = String.format("%0" + fo.length() + "d", Math.abs(valor));
        byte[] strBytes = formatted.getBytes(StandardCharsets.ISO_8859_1);

        for (int i = 0; i < fo.length(); i++) {
            byte b = (i < strBytes.length) ? strBytes[i] : (byte) '0';
            segment.set(ValueLayout.JAVA_BYTE, fo.offset() + i, b);
            backingBytes[fo.offset() + i] = b;
        }
    }

    /**
     * Lê um número inteiro numérico display (PIC 9) do buffer compartilhado.
     */
    public long lerInteiroDisplay(String nomeCampo) {
        var fo = layout.getOffset(nomeCampo);
        byte[] dst = new byte[fo.length()];
        MemorySegment.copy(segment, fo.offset(), MemorySegment.ofArray(dst), 0, fo.length());
        String s = new String(dst, StandardCharsets.ISO_8859_1).trim();
        return s.isEmpty() ? 0L : Long.parseLong(s);
    }

    /**
     * Retorna o segmento de memória nativa direto (para chamadas FFM / FFI).
     */
    public MemorySegment getNativeSegment() {
        return segment;
    }

    /**
     * Retorna o snapshot dos bytes físicos contíguos de memória.
     */
    public byte[] getBackingBytes() {
        return backingBytes.clone();
    }

    @Override
    public void close() {
        if (arena != null) {
            arena.close();
        }
    }
}
