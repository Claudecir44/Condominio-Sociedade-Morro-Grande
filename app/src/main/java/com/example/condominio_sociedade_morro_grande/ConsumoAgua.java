package com.cjstudio.condominio_sociedade_morro_grande;

import java.math.BigDecimal;

/** Gasto de água: leitura atual menos a leitura anterior do mesmo lote. */
public final class ConsumoAgua {

    private ConsumoAgua() { }

    /** "1250" e "1200" dão "50". Sem como calcular (sem anterior, texto ou gasto negativo): "—". */
    public static String gasto(String atual, String anterior) {
        BigDecimal a = numero(atual), b = numero(anterior);
        if (a == null || b == null || a.compareTo(b) < 0) return "—";
        BigDecimal gasto = a.subtract(b);
        return gasto.signum() == 0 ? "0" : gasto.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static BigDecimal numero(String leitura) {
        if (leitura == null) return null;
        try {
            return new BigDecimal(leitura.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
