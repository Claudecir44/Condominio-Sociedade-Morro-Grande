package com.cjstudio.condominio_sociedade_morro_grande;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MesCadastroTest {

    @Test
    public void mesGravadoTemPrioridade() {
        assertEquals("2026-11", MesCadastro.mesDe("2026-11", "30/10/2026 10:00"));
    }

    @Test
    public void registroAntigoUsaOMesDaData() {
        assertEquals("2026-10", MesCadastro.mesDe(null, "30/10/2026 10:00"));
        assertEquals("2026-10", MesCadastro.mesDe("", "01/10/2026"));
    }

    @Test
    public void semFiltroTudoPertence() {
        assertTrue(MesCadastro.pertence(null, null, "30/10/2026 10:00"));
        assertTrue(MesCadastro.pertence("2026-10", null, "30/10/2026 10:00"));
        assertFalse(MesCadastro.pertence("2026-11", null, "30/10/2026 10:00"));
    }
}
