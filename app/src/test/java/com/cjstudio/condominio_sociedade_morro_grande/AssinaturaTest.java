package com.cjstudio.condominio_sociedade_morro_grande;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class AssinaturaTest {

    private static Calendar dia(int ano, int mes, int dia) {
        Calendar c = Assinatura.agora();
        c.clear();
        c.set(ano, mes - 1, dia, 10, 0, 0);
        return c;
    }

    @Test
    public void antesDoInicioDaCobrancaEstaEmDia() {
        Assinatura.Estado e = Assinatura.estado(null, dia(2026, 10, 28), true);
        assertEquals(Assinatura.Situacao.EM_DIA, e.situacao);
        assertTrue(e.mostrarIcone()); // dia 25 em diante: lembra que vence dia 01
        assertFalse(e.podePagar());
    }

    @Test
    public void dia01a05SemPagamentoAguardaSemTravar() {
        for (int d = 1; d <= 5; d++) {
            Assinatura.Estado e = Assinatura.estado("2026-11", dia(2026, 12, d), true);
            assertEquals(Assinatura.Situacao.AGUARDANDO_PAGAMENTO, e.situacao);
            assertTrue(e.podePagar());
            assertTrue(e.mostrarIcone());
        }
    }

    @Test
    public void dia06SemPagamentoTrava() {
        assertEquals(Assinatura.Situacao.BLOQUEADA, Assinatura.estado(null, dia(2026, 11, 6), true).situacao);
        assertEquals(Assinatura.Situacao.BLOQUEADA, Assinatura.estado("2026-11", dia(2026, 12, 20), true).situacao);
    }

    @Test
    public void mesPagoEmDiaSemIconeAteDia25() {
        Assinatura.Estado e = Assinatura.estado("2026-12", dia(2026, 12, 10), true);
        assertEquals(Assinatura.Situacao.EM_DIA, e.situacao);
        assertFalse(e.mostrarIcone());
        assertFalse(e.podePagar());
        assertTrue(Assinatura.estado("2026-12", dia(2026, 12, 25), true).mostrarIcone());
    }

    @Test
    public void cobrancaDesligadaNuncaTravaNemLembra() {
        Assinatura.Estado e = Assinatura.estado(null, dia(2026, 12, 20), false);
        assertEquals(Assinatura.Situacao.EM_DIA, e.situacao);
        assertFalse(e.mostrarIcone());
        assertFalse(e.podePagar());
    }
}
