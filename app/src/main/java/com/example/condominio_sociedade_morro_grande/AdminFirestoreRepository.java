package com.cjstudio.condominio_sociedade_morro_grande.data.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class AdminFirestoreRepository {

    private final FirebaseFirestore db;

    public AdminFirestoreRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void listarAdmins(OnCompleteListener<List<AdminInfo>> listener) {
        // Coleção 'administradores' – TODOS são admins, sem filtro
        db.collection("administradores")
                .orderBy("nome", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<AdminInfo> admins = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String uid = doc.getId();
                        String nome = doc.getString("nome");
                        String email = doc.getString("email");
                        if (nome == null || nome.isEmpty()) {
                            nome = doc.getString("primeiroNome");
                        }
                        admins.add(new AdminInfo(uid, nome, email));
                    }
                    listener.onComplete(admins);
                })
                .addOnFailureListener(listener::onError);
    }

    public void removerAdmin(String uid, OnCompleteListener<Void> listener) {
        db.collection("administradores").document(uid)
                .delete()
                .addOnSuccessListener(aVoid -> listener.onComplete(null))
                .addOnFailureListener(listener::onError);
    }

    public interface OnCompleteListener<T> {
        void onComplete(T result);
        void onError(Exception e);
    }

    public static class AdminInfo {
        public final String uid;
        public final String nome;
        public final String email;

        public AdminInfo(String uid, String nome, String email) {
            this.uid = uid;
            this.nome = nome;
            this.email = email;
        }
    }
}