package com.cjstudio.condominio_sociedade_morro_grande;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConsumoAguaTest {

    @Test
    public void gastoEAtualMenosAnterior() {
        assertEquals("50", ConsumoAgua.gasto("1250", "1200"));
        assertEquals("0", ConsumoAgua.gasto("1200", "1200"));
        assertEquals("12", ConsumoAgua.gasto("00132", "00120"));
    }

    @Test
    public void aceitaCasasDecimais() {
        assertEquals("1,5", ConsumoAgua.gasto("10,75", "9.25"));
    }

    @Test
    public void semComoCalcularMostraTraco() {
        assertEquals("—", ConsumoAgua.gasto("1250", null));
        assertEquals("—", ConsumoAgua.gasto("abc", "1200"));
        assertEquals("—", ConsumoAgua.gasto("100", "1200"));
    }
}
