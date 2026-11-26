package thz.cobol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thz.cobol.model.CobolProgram;
import thz.cobol.model.CobolScreenItem;
import thz.cobol.parser.CobolProcedureParser;
import thz.cobol.standalone.CobolStandaloneRunner;

import static org.junit.jupiter.api.Assertions.*;

public class CobolScreenSectionTest {

    @Test
    @DisplayName("Deve fazer parsing de SCREEN SECTION com cores, atributos e coordenadas")
    void deveParsearScreenSectionComCores() {
        String codigo = """
                IDENTIFICATION DIVISION.
                PROGRAM-ID. TELA-DEMO.
                DATA DIVISION.
                WORKING-STORAGE SECTION.
                01 WS-USUARIO PIC X(20) VALUE "LUCAS".
                SCREEN SECTION.
                01 TELA-PRINCIPAL.
                   05 BLANK SCREEN.
                   05 LINE 2 COL 10 VALUE "=== SISTEMA THZ ===" FOREGROUND-COLOR 2 HIGHLIGHT.
                   05 LINE 4 COL 5 VALUE "BEM-VINDO:" FOREGROUND-COLOR 7.
                   05 SCR-USER LINE 4 COL 16 PIC X(20) USING WS-USUARIO FOREGROUND-COLOR 3.
                PROCEDURE DIVISION.
                    DISPLAY TELA-PRINCIPAL.
                    GOBACK.
                """;

        CobolProgram program = CobolProcedureParser.parseProgram(codigo);
        assertNotNull(program);
        assertEquals(1, program.screenSection().size(), "Deve conter 1 tela raiz");

        CobolScreenItem root = program.screenSection().get(0);
        assertEquals("TELA-PRINCIPAL", root.name());
        assertEquals(4, root.children().size(), "Deve conter 4 campos de tela");

        // 1. BLANK SCREEN
        assertTrue(root.children().get(0).blankScreen());

        // 2. Título verde highlight
        CobolScreenItem titulo = root.children().get(1);
        assertEquals(Integer.valueOf(2), titulo.line());
        assertEquals(Integer.valueOf(10), titulo.column());
        assertEquals("=== SISTEMA THZ ===", titulo.value());
        assertEquals(Integer.valueOf(2), titulo.foregroundColor());
        assertTrue(titulo.highlight());

        // 3. Label branco
        CobolScreenItem label = root.children().get(2);
        assertEquals(Integer.valueOf(4), label.line());
        assertEquals(Integer.valueOf(5), label.column());
        assertEquals("BEM-VINDO:", label.value());
        assertEquals(Integer.valueOf(7), label.foregroundColor());

        // 4. Campo associado USING WS-USUARIO com ciano
        CobolScreenItem userField = root.children().get(3);
        assertEquals("SCR-USER", userField.name());
        assertEquals(Integer.valueOf(4), userField.line());
        assertEquals(Integer.valueOf(16), userField.column());
        assertEquals("WS-USUARIO", userField.usingField());
        assertEquals(Integer.valueOf(3), userField.foregroundColor());
    }

    @Test
    @DisplayName("Deve renderizar sequencias ANSI de cores e cursor ao executar DISPLAY da tela")
    void deveRenderizarAnsiAoExecutarDisplay() {
        String codigo = """
                IDENTIFICATION DIVISION.
                PROGRAM-ID. TELA-EXEC.
                DATA DIVISION.
                WORKING-STORAGE SECTION.
                01 WS-NOME PIC X(10) VALUE "ANTIGRAVITY".
                SCREEN SECTION.
                01 TELA-BOAS-VINDAS.
                   05 BLANK SCREEN.
                   05 LINE 5 COL 12 VALUE "STATUS OK" FOREGROUND-COLOR 2 HIGHLIGHT.
                   05 LINE 6 COL 12 USING WS-NOME FOREGROUND-COLOR 6.
                PROCEDURE DIVISION.
                    DISPLAY TELA-BOAS-VINDAS.
                    GOBACK.
                """;

        CobolStandaloneRunner runner = CobolStandaloneRunner.carregar(codigo);
        var resultado = runner.executar();

        assertNotNull(resultado);
        assertFalse(resultado.logs().isEmpty(), "Logs de saída não podem estar vazios");

        // Verifica limpeza de tela ANSI \033[2J\033[H
        assertTrue(resultado.logs().stream().anyMatch(l -> l.contains("\033[2J\033[H")),
                "Deve emitir sequencia ANSI de limpeza de tela");

        // Verifica coordenadas e cor verde highlight: \033[5;12H e \033[1;32m
        assertTrue(resultado.logs().stream().anyMatch(l ->
                l.contains("\033[5;12H") && l.contains("\033[1;32m") && l.contains("STATUS OK")
        ), "Deve emitir ANSI para linha 5 col 12, verde brilhante");

        // Verifica valor de WS-NOME renderizado na tela com cor amarela (33m)
        assertTrue(resultado.logs().stream().anyMatch(l ->
                l.contains("\033[6;12H") && l.contains("\033[33m") && l.contains("ANTIGRAVITY")
        ), "Deve emitir ANSI para linha 6 col 12 com o valor de WS-NOME");
    }
}
