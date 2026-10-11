# thz-lang-cobol

Módulo do ecossistema **THZ-LANG** dedicado à modernização, entendimento, transpilação e interoperabilidade nativa com **COBOL**.

---

## 🏛️ Objetivos e Escopo

1. **Ingestão de Contratos Legados (Copybooks)**: Parsing nativo de arquivos `.cpy` e `.cbl` (Data Division), convertendo estruturas `01`, `05`, `PIC X`, `PIC 9`, `COMP-3` (Packed Decimal) diretamente para `ESTRUTURA` THZ-LANG com precisão monetária ISO 4217 / decimal de alta precisão.
2. **COBOL Embutido (`BLOCO_COBOL`)**: Suporte a blocos COBOL declarados dentro de programas `.thz`, permitindo execução híbrida e modernização gradual sem reescrita brusca.
3. **Transpilação de Lógica (Procedure Division)**: Tradução de rotinas e regras de negócio COBOL (`COMPUTE`, `MOVE`, `IF`, `PERFORM`) diretamente para o AST e comandos tipados do THZ-LANG.
4. **Zero JVM e AOT via LLVM IR**: Geração de bindings C-ABI e representação intermediária LLVM IR nativa, permitindo compilação AOT para binários autônomos (`.exe` e `.elf`) sem qualquer dependência da máquina virtual Java em produção.
5. **Autocompilação (Self-Hosting)**: Alinhamento com a arquitetura de bootstrap e compilação independente do compilador `thz-compiler`.

---

## 🚀 Uso Rápido

Consulte a pasta `exemplos/` e a documentação técnica para guia de integração.
