package com.cjstudio.condominio_sociedade_morro_grande.data;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Manutencao;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Manutenções na coleção "manutencoes" do Firestore (compartilhadas entre todos os aparelhos). */
public class ManutencaoDAO {

    private final ColecaoFirestore colecao = new ColecaoFirestore("manutencoes");
    private final Gson gson = new Gson();

    /** Insere quando a manutenção ainda não tem id; senão atualiza. */
    public void salvarManutencao(Manutencao m, ColecaoFirestore.Callback<Void> callback) {
        JSONObject json;
        try {
            json = new JSONObject(gson.toJson(m));
            json.remove("id");
        } catch (Exception e) {
            callback.onErro(e);
            return;
        }
        if (m.getId() == null || m.getId().isEmpty()) {
            colecao.inserir(json, new ColecaoFirestore.Callback<String>() {
                @Override
                public void onSucesso(String id) {
                    m.setId(id);
                    callback.onSucesso(null);
                }

                @Override
                public void onErro(Exception e) {
                    callback.onErro(e);
                }
            });
        } else {
            colecao.atualizar(m.getId(), json, callback);
        }
    }

    public void excluirManutencao(String id, ColecaoFirestore.Callback<Void> callback) {
        colecao.excluir(id, callback);
    }

    /** Lista da mais recente para a mais antiga (data no formato dd/MM/yyyy HH:mm). */
    public void getTodasManutencoes(ColecaoFirestore.Callback<List<Manutencao>> callback) {
        colecao.listar(null, false, new ColecaoFirestore.Callback<List<JSONObject>>() {
            @Override
            public void onSucesso(List<JSONObject> lista) {
                List<Manutencao> manutencoes = new ArrayList<>();
                for (JSONObject json : lista) {
                    manutencoes.add(deJson(json.toString()));
                }
                SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                Collections.sort(manutencoes, (a, b) -> Long.compare(
                        dataEmMillis(formato, b.getDataHora()), dataEmMillis(formato, a.getDataHora())));
                callback.onSucesso(manutencoes);
            }

            @Override
            public void onErro(Exception e) {
                callback.onErro(e);
            }
        });
    }

    public String paraTexto(Manutencao m) {
        return gson.toJson(m);
    }

    public Manutencao deJson(String json) {
        return gson.fromJson(json, Manutencao.class);
    }

    private static long dataEmMillis(SimpleDateFormat formato, String dataHora) {
        try {
            Date data = formato.parse(dataHora);
            return data != null ? data.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
