package thz.cobol.model;

import java.util.Collections;
import java.util.List;

/**
 * Representa um parágrafo da PROCEDURE DIVISION.
 */
public record CobolParagraph(String name, List<CobolStatement> statements) {
    public CobolParagraph {
        statements = statements != null ? List.copyOf(statements) : List.of();
    }
}
