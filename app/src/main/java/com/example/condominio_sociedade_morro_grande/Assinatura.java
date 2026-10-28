package com.cjstudio.condominio_sociedade_morro_grande;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Assinatura mensal do app (paga pela conta master no app Moradores, pelo Google Play). Regras:
 * - R$ 460,00 por mês; o pagamento vale o mês vigente inteiro;
 * - vence no dia 01; pode ser paga do dia 01 ao dia 05 sem travar o app (lembrete diário);
 * - a partir do dia 06 sem pagamento, o app trava para todos (moradores e admins);
 * - do dia 25 em diante, ícone no quadro "Assinaturas" lembrando que vence dia 01.
 * O que está pago fica em config/assinatura.pagoAteMes ("2026-11"), gravado só pela Cloud
 * Function confirmarCompraAssinaturaCondominio, que aplica as mesmas regras.
 */
public final class Assinatura {

    private Assinatura() { }

    public static final String PRODUTO_GOOGLE_PLAY = "condominio_assinatura_mensal";
    public static final String VALOR = "R$ 460,00";
    public static final int DIA_VENCIMENTO = 1;
    public static final int ULTIMO_DIA_SEM_TRAVAR = 5;
    public static final int DIA_LEMBRETE = 25;
    /** Primeiro mês cobrado: antes disso o app está liberado. */
    public static final String MES_INICIO_COBRANCA = "2026-11";
    /**
     * Cobrança desligada enquanto o app não é finalizado (Play Console, chave do Google Play e
     * deploy da função): ninguém trava, sem lembretes nem ícone. Ligar junto com o
     * MES_INICIO_COBRANCA certo (aqui e em functions/index.js).
     */
    public static final boolean COBRANCA_ATIVA = false;

    public enum Situacao {
        /** Mês vigente pago (ou ainda antes do início da cobrança). */
        EM_DIA,
        /** Dia 01 a 05 sem pagamento: não trava, mas lembra todo dia. */
        AGUARDANDO_PAGAMENTO,
        /** Dia 06 em diante sem pagamento: app travado. */
        BLOQUEADA
    }

    public static final class Estado {
        public final Situacao situacao;
        public final String mesAtual;
        public final int dia;
        public final boolean cobrancaAtiva;

        Estado(Situacao situacao, String mesAtual, int dia, boolean cobrancaAtiva) {
            this.situacao = situacao;
            this.mesAtual = mesAtual;
            this.dia = dia;
            this.cobrancaAtiva = cobrancaAtiva;
        }

        public boolean podePagar() {
            return situacao != Situacao.EM_DIA;
        }

        /** Ícone vermelho no quadro: falta pagar o mês, ou já é dia 25 (vence dia 01). */
        public boolean mostrarIcone() {
            return cobrancaAtiva && (situacao != Situacao.EM_DIA || dia >= DIA_LEMBRETE);
        }
    }

    /** Horário de Brasília, igual ao servidor. */
    public static Calendar agora() {
        return Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"), new Locale("pt", "BR"));
    }

    public static String mes(Calendar c) {
        return String.format(Locale.ROOT, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    public static Estado estado(String pagoAteMes) {
        return estado(pagoAteMes, agora(), COBRANCA_ATIVA);
    }

    public static Estado estado(String pagoAteMes, Calendar agora, boolean cobrancaAtiva) {
        String mesAtual = mes(agora);
        int dia = agora.get(Calendar.DAY_OF_MONTH);
        boolean cobrado = cobrancaAtiva && mesAtual.compareTo(MES_INICIO_COBRANCA) >= 0;
        boolean pago = !cobrado || (pagoAteMes != null && pagoAteMes.compareTo(mesAtual) >= 0);
        Situacao s;
        if (pago) s = Situacao.EM_DIA;
        else if (dia <= ULTIMO_DIA_SEM_TRAVAR) s = Situacao.AGUARDANDO_PAGAMENTO;
        else s = Situacao.BLOQUEADA;
        return new Estado(s, mesAtual, dia, cobrancaAtiva);
    }

    /** "11/2026" a partir de "2026-11". */
    public static String mesTexto(String mes) {
        if (mes == null || mes.length() != 7) return "—";
        return mes.substring(5) + "/" + mes.substring(0, 4);
    }
}
