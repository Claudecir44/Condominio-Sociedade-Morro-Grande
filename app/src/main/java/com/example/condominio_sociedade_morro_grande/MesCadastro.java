package com.cjstudio.condominio_sociedade_morro_grande;

import org.json.JSONObject;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mês de um registro (assembleia, aviso, despesa, manutenção, ocorrência): o campo "mes"
 * ("2026-11") de quem foi adicionado dentro de um mês; nos registros antigos, o mês da data
 * ("dd/MM/yyyy HH:mm"). Os meses criados ficam em meses_cadastro/{colecao}.meses.
 */
public final class MesCadastro {

    public static final String EXTRA_MES = "mes";
    private static final Pattern DATA = Pattern.compile("^\\d{2}/(\\d{2})/(\\d{4})");
    private static final Pattern DATA_HORA = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})(?:\\s+(\\d{2}):(\\d{2}))?");

    private MesCadastro() { }

    public static String mesDe(Object mes, Object dataHora) {
        if (mes != null && !mes.toString().isEmpty()) return mes.toString();
        Matcher data = DATA.matcher(dataHora != null ? dataHora.toString() : "");
        return data.find() ? data.group(2) + "-" + data.group(1) : Boleto.mesDe(new Date());
    }

    /** "dd/MM/yyyy HH:mm" vira "yyyyMMddHHmm", que ordena por data; sem data vai para o fim. */
    public static String chaveDeOrdem(Object dataHora) {
        Matcher d = DATA_HORA.matcher(dataHora != null ? dataHora.toString().trim() : "");
        if (!d.find()) return "~";
        return d.group(3) + d.group(2) + d.group(1)
                + (d.group(4) != null ? d.group(4) + d.group(5) : "0000");
    }

    /** Sem filtro (lista aberta fora do mês a mês), tudo pertence. */
    public static boolean pertence(String filtro, Object mes, Object dataHora) {
        return filtro == null || filtro.equals(mesDe(mes, dataHora));
    }

    public static void filtrar(List<JSONObject> lista, String campoData, String filtro) {
        if (filtro == null) return;
        for (Iterator<JSONObject> i = lista.iterator(); i.hasNext(); ) {
            JSONObject registro = i.next();
            if (!pertence(filtro, registro.optString("mes", ""), registro.optString(campoData, ""))) i.remove();
        }
    }
}
