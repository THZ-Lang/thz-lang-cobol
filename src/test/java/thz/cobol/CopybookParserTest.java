package thz.cobol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thz.cobol.model.CobolDataType;
import thz.cobol.model.CobolField;
import thz.cobol.model.CopybookAst;
import thz.cobol.parser.CopybookParser;

import static org.junit.jupiter.api.Assertions.*;

class CopybookParserTest {

    @Test
    @DisplayName("Deve fazer parsing de copybook bancário com múltiplos níveis e COMP-3")
    void testParsingCopybookBancario() {
        String copybookContent = """
            * COPYBOOK DE CLIENTE E CONTA BANCARIA
            01  REGISTRO-CLIENTE.
                05  CLI-CODIGO           PIC 9(8).
                05  CLI-NOME             PIC X(30).
                05  CLI-DADOS-CONTA.
                    10  CTA-AGENCIA      PIC 9(4).
                    10  CTA-NUMERO       PIC 9(8).
                05  CLI-SALDO-CORRENTE   PIC S9(13)V99 COMP-3.
                05  CLI-LIMITE-CREDITO   PIC S9(9)V99 COMP-3.
                05  CLI-STATUS           PIC X(1).
                    88  CLI-ATIVO        VALUE 'A'.
                    88  CLI-BLOQUEADO    VALUE 'B'.
            """;

        CopybookAst ast = CopybookParser.parse("REGISTRO-CLIENTE", copybookContent);

        assertNotNull(ast);
        assertEquals("REGISTRO-CLIENTE", ast.getName());
        assertEquals(1, ast.getRootFields().size());

        CobolField root = ast.getRootFields().get(0);
        assertEquals("REGISTRO-CLIENTE", root.getName());
        assertTrue(root.isGroup());
        assertEquals(6, root.getChildren().size());

        // CLI-CODIGO
        CobolField cliCodigo = root.getChildren().get(0);
        assertEquals("CLI-CODIGO", cliCodigo.getName());
        assertEquals(CobolDataType.NUMERIC_DISPLAY, cliCodigo.getDataType());
        assertEquals("cli_codigo", cliCodigo.getNomeThz());

        // CLI-NOME
        CobolField cliNome = root.getChildren().get(1);
        assertEquals("CLI-NOME", cliNome.getName());
        assertEquals(CobolDataType.ALPHANUMERIC, cliNome.getDataType());
        assertEquals(30, cliNome.getPicture().getByteLength());

        // CLI-DADOS-CONTA (grupo aninhado)
        CobolField dadosConta = root.getChildren().get(2);
        assertTrue(dadosConta.isGroup());
        assertEquals(2, dadosConta.getChildren().size());

        // CLI-SALDO-CORRENTE (COMP-3)
        CobolField saldo = root.getChildren().get(3);
        assertEquals("CLI-SALDO-CORRENTE", saldo.getName());
        assertEquals(CobolDataType.PACKED_DECIMAL, saldo.getDataType());
        assertEquals(2, saldo.getPicture().getDecimalScale());
        assertTrue(saldo.getPicture().isSigned());

        // Total de bytes calculado
        assertTrue(ast.getTotalByteLength() > 0);
    }
}
