package com.cjstudio.condominio_sociedade_morro_grande.data;

import android.net.Uri;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Assembleias na coleção "assembleias" do Firestore (compartilhadas entre todos os aparelhos). */
public class AssembleiaDAO {

    private final ColecaoFirestore colecao = new ColecaoFirestore("assembleias");

    public void inserirAssembleia(JSONObject assembleia, ColecaoFirestore.Callback<String> callback) {
        completarCampos(assembleia);
        colecao.inserir(assembleia, callback);
    }

    public void atualizarAssembleia(String id, JSONObject assembleia, ColecaoFirestore.Callback<Void> callback) {
        completarCampos(assembleia);
        colecao.atualizar(id, assembleia, callback);
    }

    public void excluirAssembleia(String id, ColecaoFirestore.Callback<Void> callback) {
        colecao.excluir(id, callback);
    }

    public void listarAssembleias(ColecaoFirestore.Callback<List<JSONObject>> callback) {
        colecao.listar(null, false, callback);
    }

    private void completarCampos(JSONObject assembleia) {
        try {
            if (!assembleia.has("documento")) assembleia.put("documento", new JSONArray().toString());
            if (!assembleia.has("assunto")) assembleia.put("assunto", "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String anexosParaJson(List<Uri> anexos) {
        JSONArray jsonArray = new JSONArray();
        for (Uri uri : anexos) {
            jsonArray.put(uri.toString());
        }
        return jsonArray.toString();
    }

    public static List<Uri> jsonParaAnexos(String json) {
        List<Uri> lista = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                lista.add(Uri.parse(jsonArray.getString(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }
}
