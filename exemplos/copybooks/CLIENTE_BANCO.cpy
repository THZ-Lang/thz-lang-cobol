      * ==================================================================
      * COPYBOOK: CLIENTE_BANCO.CPY
      * Sistema de Gestão Financeira Legada
      * ==================================================================
       01  REGISTRO-CLIENTE-BANCO.
           05  CLI-ID                  PIC 9(9).
           05  CLI-NOME                PIC X(40).
           05  CLI-DOCUMENTO-CPF       PIC 9(11).
           05  CLI-SALDO-CONTA         PIC S9(13)V99 COMP-3.
           05  CLI-LIMITE-CHEQUE       PIC S9(9)V99 COMP-3.
           05  CLI-TAXA-JUROS          PIC S9(3)V99 COMP-3.
           05  CLI-STATUS-CONTA        PIC X(1).
               88  CLI-STATUS-REGULAR  VALUE 'R'.
               88  CLI-STATUS-DEVEDOR  VALUE 'D'.
               88  CLI-STATUS-BLOQUEADO VALUE 'B'.
