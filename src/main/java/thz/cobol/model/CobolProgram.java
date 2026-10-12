package thz.cobol.model;

import java.util.List;

/**
 * Representa um programa COBOL completo ou bloco embutido analisado.
 */
public record CobolProgram(
        String programId,
        List<CobolField> workingStorage,
        List<CobolField> linkageSection,
        List<CobolParagraph> paragraphs
) {
    public CobolProgram {
        workingStorage = workingStorage != null ? List.copyOf(workingStorage) : List.of();
        linkageSection = linkageSection != null ? List.copyOf(linkageSection) : List.of();
        paragraphs = paragraphs != null ? List.copyOf(paragraphs) : List.of();
    }
}
