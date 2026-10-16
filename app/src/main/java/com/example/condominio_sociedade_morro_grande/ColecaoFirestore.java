package com.cjstudio.condominio_sociedade_morro_grande.data.firestore;

import android.util.Log;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Acesso genérico a uma coleção do Firestore usando JSONObject, no mesmo estilo do
 * MoradorFirestoreRepository. O campo "id" de cada registro é o ID do documento.
 * Os callbacks chegam na thread principal.
 */
public class ColecaoFirestore {

    private static final String TAG = "ColecaoFirestore";
    public static final String CAMPO_ID = "id";

    private final CollectionReference colecao;

    public ColecaoFirestore(String nomeColecao) {
        colecao = FirebaseFirestore.getInstance().collection(nomeColecao);
    }

    public void listar(String ordenarPor, boolean decrescente, Callback<List<JSONObject>> callback) {
        Query query = colecao;
        if (ordenarPor != null) {
            query = colecao.orderBy(ordenarPor,
                    decrescente ? Query.Direction.DESCENDING : Query.Direction.ASCENDING);
        }
        query.get()
                .addOnSuccessListener(snapshot -> {
                    List<JSONObject> lista = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        lista.add(paraJson(doc));
                    }
                    callback.onSucesso(lista);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erro ao listar " + colecao.getId() + ": " + e.getMessage());
                    callback.onErro(e);
                });
    }

    public void buscar(String id, Callback<JSONObject> callback) {
        colecao.document(id).get()
                .addOnSuccessListener(doc -> callback.onSucesso(doc.exists() ? paraJson(doc) : null))
                .addOnFailureListener(callback::onErro);
    }

    public void inserir(JSONObject dados, Callback<String> callback) {
        String id = colecao.document().getId();
        Map<String, Object> mapa = paraMapa(dados);
        mapa.put(CAMPO_ID, id);
        colecao.document(id).set(mapa)
                .addOnSuccessListener(v -> callback.onSucesso(id))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erro ao inserir em " + colecao.getId() + ": " + e.getMessage());
                    callback.onErro(e);
                });
    }

    public void atualizar(String id, JSONObject dados, Callback<Void> callback) {
        Map<String, Object> mapa = paraMapa(dados);
        mapa.put(CAMPO_ID, id);
        colecao.document(id).set(mapa)
                .addOnSuccessListener(v -> callback.onSucesso(null))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erro ao atualizar " + colecao.getId() + "/" + id + ": " + e.getMessage());
                    callback.onErro(e);
                });
    }

    public void excluir(String id, Callback<Void> callback) {
        colecao.document(id).delete()
                .addOnSuccessListener(v -> callback.onSucesso(null))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erro ao excluir " + colecao.getId() + "/" + id + ": " + e.getMessage());
                    callback.onErro(e);
                });
    }

    private static JSONObject paraJson(DocumentSnapshot doc) {
        Map<String, Object> dados = doc.getData();
        JSONObject json = dados != null ? new JSONObject(dados) : new JSONObject();
        try {
            json.put(CAMPO_ID, doc.getId());
        } catch (JSONException e) {
            Log.e(TAG, "Erro ao colocar id: " + e.getMessage());
        }
        return json;
    }

    /** Converte JSONObject em Map aceito pelo Firestore (JSONArray vira List, JSONObject vira Map). */
    public static Map<String, Object> paraMapa(JSONObject json) {
        Map<String, Object> mapa = new HashMap<>();
        Iterator<String> chaves = json.keys();
        while (chaves.hasNext()) {
            String chave = chaves.next();
            mapa.put(chave, converter(json.opt(chave)));
        }
        return mapa;
    }

    private static Object converter(Object valor) {
        if (valor == null || valor == JSONObject.NULL) return null;
        if (valor instanceof JSONObject) return paraMapa((JSONObject) valor);
        if (valor instanceof JSONArray) {
            JSONArray array = (JSONArray) valor;
            List<Object> lista = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                lista.add(converter(array.opt(i)));
            }
            return lista;
        }
        return valor;
    }

    public interface Callback<T> {
        void onSucesso(T resultado);
        void onErro(Exception e);
    }
}
