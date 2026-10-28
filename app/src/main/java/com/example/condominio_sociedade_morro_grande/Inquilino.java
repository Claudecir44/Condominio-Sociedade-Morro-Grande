package com.cjstudio.condominio_sociedade_morro_grande;

import org.json.JSONObject;

import java.util.Map;

/**
 * Inquilino do lote (campos no cadastro do morador): temInquilino, inquilinoNome, inquilinoCpf,
 * inquilinoTelefone. Havendo inquilino, ele é o responsável pelo app: entra com a quadra + lote e
 * os 6 primeiros dígitos do CPF dele, cadastra a foto no primeiro acesso e é quem aparece no
 * "Olá" do painel e no chat.
 */
public final class Inquilino {

    private Inquilino() { }

    public static boolean tem(JSONObject morador) {
        return morador != null && morador.optBoolean("temInquilino")
                && !morador.optString("inquilinoNome", "").trim().isEmpty();
    }

    public static boolean tem(Map<String, Object> morador) {
        return morador != null && Boolean.TRUE.equals(morador.get("temInquilino"))
                && morador.get("inquilinoNome") != null && !morador.get("inquilinoNome").toString().trim().isEmpty();
    }

    /** Nome de quem usa o app: o inquilino, se houver, senão o morador. */
    public static String nomeResponsavel(JSONObject morador) {
        if (morador == null) return "";
        return tem(morador) ? morador.optString("inquilinoNome").trim() : morador.optString("nome").trim();
    }

    public static String nomeResponsavel(Map<String, Object> morador) {
        if (morador == null) return "";
        Object nome = tem(morador) ? morador.get("inquilinoNome") : morador.get("nome");
        return nome != null ? nome.toString().trim() : "";
    }

    /** CPF que vira a senha (6 primeiros dígitos): do inquilino, se houver, senão do morador. */
    public static String cpfResponsavel(JSONObject morador) {
        return tem(morador) ? morador.optString("inquilinoCpf") : morador.optString("cpf");
    }
}
