package com.cjstudio.condominio_sociedade_morro_grande.data;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Despesa;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Despesas na coleção "despesas" do Firestore (compartilhadas entre todos os aparelhos). */
public class DespesaDAO {

    private final ColecaoFirestore colecao = new ColecaoFirestore("despesas");
    private final Gson gson = new Gson();

    public void inserir(Despesa despesa, ColecaoFirestore.Callback<String> callback) {
        try {
            colecao.inserir(paraJson(despesa), callback);
        } catch (Exception e) {
            callback.onErro(e);
        }
    }

    public void atualizar(Despesa despesa, ColecaoFirestore.Callback<Void> callback) {
        try {
            colecao.atualizar(despesa.getId(), paraJson(despesa), callback);
        } catch (Exception e) {
            callback.onErro(e);
        }
    }

    public void excluir(String id, ColecaoFirestore.Callback<Void> callback) {
        colecao.excluir(id, callback);
    }

    public void listarTodos(ColecaoFirestore.Callback<List<Despesa>> callback) {
        colecao.listar(null, false, new ColecaoFirestore.Callback<List<JSONObject>>() {
            @Override
            public void onSucesso(List<JSONObject> lista) {
                List<Despesa> despesas = new ArrayList<>();
                for (JSONObject json : lista) {
                    despesas.add(deJson(json.toString()));
                }
                callback.onSucesso(despesas);
            }

            @Override
            public void onErro(Exception e) {
                callback.onErro(e);
            }
        });
    }

    public String paraTexto(Despesa despesa) {
        return gson.toJson(despesa);
    }

    public Despesa deJson(String json) {
        return gson.fromJson(json, Despesa.class);
    }

    private JSONObject paraJson(Despesa despesa) throws Exception {
        JSONObject json = new JSONObject(gson.toJson(despesa));
        json.remove("id");
        return json;
    }
}
