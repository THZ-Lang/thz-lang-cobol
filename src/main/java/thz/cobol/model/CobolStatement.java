package thz.cobol.model;

import java.util.List;

/**
 * Declarações e instruções da PROCEDURE DIVISION do COBOL.
 */
public sealed interface CobolStatement permits
        CobolStatement.Move,
        CobolStatement.Compute,
        CobolStatement.Add,
        CobolStatement.Subtract,
        CobolStatement.IfStatement,
        CobolStatement.Perform,
        CobolStatement.Display,
        CobolStatement.Call,
        CobolStatement.Goback {

    /** MOVE source TO target */
    record Move(String source, String target) implements CobolStatement {}

    /** COMPUTE target = expression */
    record Compute(String target, String expression) implements CobolStatement {}

    /** ADD source TO target (ou ADD a TO b GIVING c) */
    record Add(String source, String target, String giving) implements CobolStatement {}

    /** SUBTRACT source FROM target (ou SUBTRACT a FROM b GIVING c) */
    record Subtract(String source, String target, String giving) implements CobolStatement {}

    /** IF condition THEN ... ELSE ... END-IF */
    record IfStatement(String condition, List<CobolStatement> thenBranch, List<CobolStatement> elseBranch) implements CobolStatement {}

    /** PERFORM targetParagraph [VARYING / UNTIL / N TIMES] */
    record Perform(String targetParagraph, Integer times, String untilCondition) implements CobolStatement {}

    /** DISPLAY item1 item2 ... */
    record Display(List<String> items) implements CobolStatement {}

    /** CALL "PROG" USING arg1 arg2 ... */
    record Call(String programName, List<String> parameters) implements CobolStatement {}

    /** GOBACK / STOP RUN */
    record Goback() implements CobolStatement {}
}
