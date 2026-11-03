package com.cjstudio.condominio_sociedade_morro_grande;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

public class BoletoTest {

    private static Boleto postadoEm(int ano, int mes, int dia) {
        Calendar c = Calendar.getInstance();
        c.set(ano, mes, dia, 12, 0, 0);
        return new Boleto("1", "morador", "link", "boleto.pdf", c.getTime(), false);
    }

    @Test
    public void boletoSemMesUsaOMesDaPostagem() {
        assertEquals("2026-10", postadoEm(2026, Calendar.OCTOBER, 2).getMes());
        assertEquals("15/10/2026", postadoEm(2026, Calendar.OCTOBER, 2).vencimentoTexto());
    }

    @Test
    public void boletoDoMesCriadoVencePeloMesDele() {
        Boleto b = new Boleto("1", "morador", "link", "boleto.pdf", new java.util.Date(), false, "2026-11");
        assertEquals("15/11/2026", b.vencimentoTexto());
    }

    @Test
    public void textoDoMes() {
        assertEquals("Outubro de 2026", Boleto.textoDoMes("2026-10"));
        assertEquals("Março de 2027", Boleto.textoDoMes("2027-03"));
    }

    @Test
    public void proximoMesViraOAno() {
        assertEquals("2026-11", Boleto.proximoMes("2026-10"));
        assertEquals("2027-01", Boleto.proximoMes("2026-12"));
    }
}
