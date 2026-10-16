package com.cjstudio.condominio_sociedade_morro_grande.data;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;

import org.json.JSONObject;

import java.util.List;

/** Ocorrências na coleção "ocorrencias" do Firestore (compartilhadas entre todos os aparelhos). */
public class OcorrenciaDAO {

    private final ColecaoFirestore colecao = new ColecaoFirestore("ocorrencias");

    public void inserirOcorrencia(JSONObject ocorrencia, ColecaoFirestore.Callback<String> callback) {
        colecao.inserir(ocorrencia, callback);
    }

    public void atualizarOcorrencia(String id, JSONObject ocorrencia, ColecaoFirestore.Callback<Void> callback) {
        colecao.atualizar(id, ocorrencia, callback);
    }

    public void excluirOcorrencia(String id, ColecaoFirestore.Callback<Void> callback) {
        colecao.excluir(id, callback);
    }

    public void listarOcorrencias(ColecaoFirestore.Callback<List<JSONObject>> callback) {
        colecao.listar(null, false, callback);
    }
}
