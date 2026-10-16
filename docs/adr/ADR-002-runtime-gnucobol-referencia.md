# ADR-002: Disponibilização do Runtime e Compilador de Referência GnuCOBOL 3.2

## Status
Aceito

## Contexto
O ecossistema THZ-LANG possui um módulo dedicado à modernização e interoperabilidade com COBOL (`thz-lang-cobol`), capaz de realizar parsing de Copybooks, tradução de rotinas e execução embutida/standalone na JVM e futuro alvo AOT LLVM.
No entanto, durante testes e desenvolvimento de código COBOL legado ou validação de regras de sintaxe (como formatação de 72 colunas em formato fixo vs formato livre, precisão aritmética de COMP-3 e comportamento de desvios), é altamente vantajoso dispor de um compilador COBOL nativo oficial e homologado (GnuCOBOL) localmente no ambiente.

## Decisão
1. Disponibilizar a distribuição completa do **GnuCOBOL 3.2 (MinGW com GCC 9.2, BDB 18.1, GMP 6.2.1, PDCursesMod)** no caminho padrão de ferramentas gerenciadas pelo usuário (`%USERPROFILE%\scoop\apps\gnucobol\current`).
2. Configurar variáveis de ambiente do sistema e do usuário (`COB_MAIN_DIR`, `COB_CONFIG_DIR`, `COB_COPY_DIR`, `COB_CFLAGS`, `COB_LDFLAGS`, `COB_LIBRARY_PATH`) referenciando dinamicamente `%USERPROFILE%`.
3. Registrar shims e wrappers (`cobc.cmd`, `cobc.ps1`, `cobcrun.cmd`, `cobcrun.ps1`) no PATH do usuário (`%USERPROFILE%\scoop\shims\`), permitindo chamadas diretas como `cobc --version` ou `cobc -x programa.cbl` em qualquer terminal.
4. Adicionar scripts utilitários em `thz-lang-cobol/scripts/` para automação de build e execução de fontes `.cbl`, utilizando exclusivamente variáveis dinâmicas (`$env:USERPROFILE`, `%USERPROFILE%`) para garantir total portabilidade entre diferentes máquinas e perfis.

## Consequências
- A equipe THZ-LANG tem à disposição compilação nativa COBOL de referência imediata.
- Casos em que fontes COBOL necessitarem de compilação C/ELF/EXE autônoma podem ser validados diretamente com o padrão ANSI/ISO COBOL.
- Zero dependência externa de emuladores ou containers para executar COBOL no Windows.
