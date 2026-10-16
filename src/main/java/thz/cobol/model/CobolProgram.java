package thz.cobol.model;

import java.util.List;

/**
 * Representa um programa COBOL completo ou bloco embutido analisado.
 */
public record CobolProgram(
        String programId,
        List<CobolField> workingStorage,
        List<CobolField> linkageSection,
        List<CobolParagraph> paragraphs,
        List<CobolScreenItem> screenSection
) {
    public CobolProgram(
            String programId,
            List<CobolField> workingStorage,
            List<CobolField> linkageSection,
            List<CobolParagraph> paragraphs
    ) {
        this(programId, workingStorage, linkageSection, paragraphs, List.of());
    }

    public CobolProgram {
        workingStorage = workingStorage != null ? List.copyOf(workingStorage) : List.of();
        linkageSection = linkageSection != null ? List.copyOf(linkageSection) : List.of();
        paragraphs = paragraphs != null ? List.copyOf(paragraphs) : List.of();
        screenSection = screenSection != null ? List.copyOf(screenSection) : List.of();
    }
}
