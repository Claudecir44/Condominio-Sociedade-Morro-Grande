package com.cjstudio.condominio_sociedade_morro_grande.data;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Funcionario;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Funcionários na coleção "funcionarios" do Firestore (compartilhados entre todos os aparelhos). */
public class FuncionarioDAO {

    private final ColecaoFirestore colecao = new ColecaoFirestore("funcionarios");
    private final Gson gson = new Gson();

    public void inserir(Funcionario funcionario, ColecaoFirestore.Callback<String> callback) {
        try {
            colecao.inserir(paraJson(funcionario), callback);
        } catch (Exception e) {
            callback.onErro(e);
        }
    }

    public void atualizar(Funcionario funcionario, ColecaoFirestore.Callback<Void> callback) {
        try {
            colecao.atualizar(funcionario.getId(), paraJson(funcionario), callback);
        } catch (Exception e) {
            callback.onErro(e);
        }
    }

    public void excluir(String id, ColecaoFirestore.Callback<Void> callback) {
        colecao.excluir(id, callback);
    }

    public void listarTodos(ColecaoFirestore.Callback<List<Funcionario>> callback) {
        colecao.listar("nome", false, new ColecaoFirestore.Callback<List<JSONObject>>() {
            @Override
            public void onSucesso(List<JSONObject> lista) {
                List<Funcionario> funcionarios = new ArrayList<>();
                for (JSONObject json : lista) {
                    funcionarios.add(deJson(json.toString()));
                }
                callback.onSucesso(funcionarios);
            }

            @Override
            public void onErro(Exception e) {
                callback.onErro(e);
            }
        });
    }

    public String paraTexto(Funcionario funcionario) {
        return gson.toJson(funcionario);
    }

    public Funcionario deJson(String json) {
        return gson.fromJson(json, Funcionario.class);
    }

    private JSONObject paraJson(Funcionario funcionario) throws Exception {
        JSONObject json = new JSONObject(gson.toJson(funcionario));
        json.remove("id");
        return json;
    }
}
