package com.cjstudio.condominio_sociedade_morro_grande.data.firestore;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MoradorFirestoreRepository {

    private static final String TAG = "MoradorFirestore";
    private final FirebaseFirestore db;
    private final CollectionReference moradoresRef;

    public MoradorFirestoreRepository() {
        db = FirebaseFirestore.getInstance();
        moradoresRef = db.collection("moradores");
    }

    /**
     * Cria a conta de login do morador no Firebase Auth (usuário = quadra+lote) e grava os dados
     * em moradores/{uid}. A senha fica só no Auth, nunca no banco.
     */
    public void inserirMorador(Context context, JSONObject morador, String usuario, String senha,
                               OnCompleteListener<String> listener) {
        ContaHelper.criarConta(context, ContaHelper.emailDoMorador(usuario), senha,
                new ColecaoFirestore.Callback<String>() {
                    @Override
                    public void onSucesso(String uid) {
                        Map<String, Object> data = jsonToMap(morador);
                        data.put("cod", uid);
                        data.put("usuario", usuario);
                        data.put("loginEmail", ContaHelper.emailDoMorador(usuario));
                        moradoresRef.document(uid).set(data)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Morador inserido com ID: " + uid);
                                    listener.onComplete(uid);
                                })
                                .addOnFailureListener(listener::onError);
                    }

                    @Override
                    public void onErro(Exception e) {
                        Log.e(TAG, "Erro ao criar login do morador: " + e.getMessage());
                        if (e instanceof FirebaseAuthUserCollisionException) {
                            listener.onError(new Exception("Já existe um morador com o login " + usuario
                                    + " (quadra + lote)."));
                        } else {
                            listener.onError(e);
                        }
                    }
                });
    }

    public void atualizarMorador(String cod, JSONObject morador, OnCompleteListener<Void> listener) {
        moradoresRef.whereEqualTo("cod", cod).get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        DocumentSnapshot atual = query.getDocuments().get(0);
                        String docId = atual.getId();
                        Map<String, Object> data = jsonToMap(morador);
                        // O login (quadra+lote) é o da conta criada no cadastro; não muda na edição
                        data.put("cod", docId);
                        data.put("usuario", atual.getString("usuario"));
                        data.put("loginEmail", atual.getString("loginEmail"));
                        data.remove("senha");
                        moradoresRef.document(docId).set(data)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Morador atualizado: " + cod);
                                    listener.onComplete(null);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Erro ao atualizar: " + e.getMessage());
                                    listener.onError(e);
                                });
                    } else {
                        listener.onError(new Exception("Morador não encontrado"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erro ao buscar morador: " + e.getMessage());
                    listener.onError(e);
                });
    }

    public void excluirMorador(String cod, OnCompleteListener<Void> listener) {
        moradoresRef.whereEqualTo("cod", cod).get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        String docId = query.getDocuments().get(0).getId();
                        moradoresRef.document(docId).delete()
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Morador excluído: " + cod);
                                    listener.onComplete(null);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Erro ao excluir: " + e.getMessage());
                                    listener.onError(e);
                                });
                    } else {
                        listener.onError(new Exception("Morador não encontrado"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erro ao buscar morador: " + e.getMessage());
                    listener.onError(e);
                });
    }

    public void getListaMoradores(OnCompleteListener<List<JSONObject>> listener) {
        moradoresRef.orderBy("nome", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(query -> {
                    List<JSONObject> lista = new ArrayList<>();
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        Map<String, Object> data = doc.getData();
                        JSONObject obj = new JSONObject(data);
                        if (!obj.has("cod")) {
                            try {
                                obj.put("cod", doc.getId());
                            } catch (JSONException e) {
                                Log.e(TAG, "Erro ao adicionar cod: " + e.getMessage());
                            }
                        }
                        lista.add(obj);
                    }
                    listener.onComplete(lista);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erro ao listar: " + e.getMessage());
                    listener.onError(e);
                });
    }

    /**
     * Login do morador pelo Firebase Auth. Devolve os dados do morador, ou null se usuário/senha
     * estiverem errados ou se o cadastro do morador tiver sido excluído.
     */
    public void validarLogin(String usuario, String senha, OnCompleteListener<JSONObject> listener) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signInWithEmailAndPassword(ContaHelper.emailDoMorador(usuario), senha)
                .addOnSuccessListener(resultado -> {
                    String uid = resultado.getUser() != null ? resultado.getUser().getUid() : null;
                    if (uid == null) {
                        listener.onComplete(null);
                        return;
                    }
                    moradoresRef.document(uid).get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists() || doc.getData() == null) {
                                    auth.signOut();
                                    listener.onComplete(null);
                                    return;
                                }
                                JSONObject obj = new JSONObject(doc.getData());
                                try {
                                    obj.put("cod", doc.getId());
                                } catch (JSONException e) {
                                    Log.e(TAG, "Erro ao adicionar cod: " + e.getMessage());
                                }
                                listener.onComplete(obj);
                            })
                            .addOnFailureListener(e -> {
                                auth.signOut();
                                listener.onError(e);
                            });
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthInvalidCredentialsException
                            || e instanceof FirebaseAuthInvalidUserException) {
                        listener.onComplete(null);
                    } else {
                        Log.e(TAG, "Erro no login: " + e.getMessage());
                        listener.onError(e);
                    }
                });
    }

    // ==================== CONVERSÃO JSON -> MAP ====================
    private Map<String, Object> jsonToMap(JSONObject json) {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                Object value = json.get(key);
                map.put(key, value);
            } catch (JSONException e) {
                Log.e(TAG, "Erro ao converter JSON para Map: " + e.getMessage());
            }
        }
        return map;
    }

    public interface OnCompleteListener<T> {
        void onComplete(T result);
        void onError(Exception e);
    }
}