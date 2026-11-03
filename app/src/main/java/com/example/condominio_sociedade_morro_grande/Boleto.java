package com.cjstudio.condominio_sociedade_morro_grande;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Boleto (PDF) que o admin posta para um morador, em boletos/{id}.
 * O vencimento é sempre o dia 15 do mês do boleto ("mes", "2026-11"). Boletos antigos, sem esse
 * campo, usam o mês em que foram postados.
 */
public class Boleto {

    public static final int DIA_VENCIMENTO = 15;

    private final String id;
    private final String moradorUid;
    private final String arquivo;
    private final String nomeArquivo;
    private final Date postadoEm;
    private final boolean naoPago;
    private final String mes;

    public Boleto(String id, String moradorUid, String arquivo, String nomeArquivo, Date postadoEm, boolean naoPago) {
        this(id, moradorUid, arquivo, nomeArquivo, postadoEm, naoPago, null);
    }

    public Boleto(String id, String moradorUid, String arquivo, String nomeArquivo, Date postadoEm, boolean naoPago, String mes) {
        this.id = id;
        this.moradorUid = moradorUid;
        this.arquivo = arquivo;
        this.nomeArquivo = nomeArquivo;
        this.postadoEm = postadoEm != null ? postadoEm : new Date();
        this.naoPago = naoPago;
        this.mes = mes != null && !mes.isEmpty() ? mes : mesDe(this.postadoEm);
    }

    public String getId() { return id; }
    public String getMoradorUid() { return moradorUid; }
    public String getArquivo() { return arquivo; }
    public String getNomeArquivo() { return nomeArquivo; }
    public Date getPostadoEm() { return postadoEm; }
    public boolean isNaoPago() { return naoPago; }
    public String getMes() { return mes; }

    /** Dia 15 do mês do boleto, no fim do dia. */
    public Date getVencimento() {
        Calendar c = calendarioDo(mes);
        c.set(Calendar.DAY_OF_MONTH, DIA_VENCIMENTO);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** O admin só pode marcar "não pago" depois do dia 15. */
    public boolean passouDoVencimento() {
        return new Date().after(getVencimento());
    }

    public String postadoEmTexto() {
        return new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR")).format(postadoEm);
    }

    public String vencimentoTexto() {
        return new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR")).format(getVencimento());
    }

    /** "2026-10" do mês de uma data. */
    public static String mesDe(Date data) {
        return new SimpleDateFormat("yyyy-MM", Locale.ROOT).format(data);
    }

    /** "2026-12" vira "2027-01". */
    public static String proximoMes(String mes) {
        Calendar c = calendarioDo(mes);
        c.add(Calendar.MONTH, 1);
        return mesDe(c.getTime());
    }

    /** Título do mês na lista "Boletos Morador": "2026-10" vira "Outubro de 2026". */
    public static String textoDoMes(String mes) {
        Locale ptBr = new Locale("pt", "BR");
        String texto = new SimpleDateFormat("MMMM 'de' yyyy", ptBr).format(calendarioDo(mes).getTime());
        return texto.substring(0, 1).toUpperCase(ptBr) + texto.substring(1);
    }

    /** Primeiro dia do mês "2026-10". */
    private static Calendar calendarioDo(String mes) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(mes.substring(0, 4)), Integer.parseInt(mes.substring(5, 7)) - 1, 1);
        return c;
    }
}
