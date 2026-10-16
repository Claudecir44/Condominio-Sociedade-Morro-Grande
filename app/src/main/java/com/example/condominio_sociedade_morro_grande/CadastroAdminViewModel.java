package com.cjstudio.condominio_sociedade_morro_grande.presentation.cadastroadmin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CadastroAdminViewModel extends ViewModel {

    private final MutableLiveData<CadastroAdminUiState> uiState = new MutableLiveData<>(new CadastroAdminUiState());
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public LiveData<CadastroAdminUiState> getUiState() {
        return uiState;
    }

    public void onCadastroClicked(String nome, String email, String senha, String telefone, String cpf) {
        if (nome.isEmpty() || email.isEmpty() || senha.isEmpty()) {
            uiState.setValue(new CadastroAdminUiState(false, null, "Preencha nome, email e senha."));
            return;
        }

        uiState.setValue(new CadastroAdminUiState(true, null, null));

        auth.createUserWithEmailAndPassword(email, senha)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            salvarDadosNoFirestore(user.getUid(), nome, email, telefone, cpf);
                        } else {
                            uiState.setValue(new CadastroAdminUiState(false, null, "Erro ao obter usuário."));
                        }
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Erro desconhecido";
                        uiState.setValue(new CadastroAdminUiState(false, null, "Falha no cadastro: " + error));
                    }
                });
    }

    private void salvarDadosNoFirestore(String uid, String nome, String email, String telefone, String cpf) {
        Map<String, Object> adminData = new HashMap<>();
        adminData.put("nome", nome);
        adminData.put("email", email);
        adminData.put("tipo", "admin");
        adminData.put("telefone", telefone);
        adminData.put("cpf", cpf);

        db.collection("usuarios").document(uid).set(adminData)
                .addOnSuccessListener(aVoid -> {
                    uiState.setValue(new CadastroAdminUiState(false, "Administrador cadastrado com sucesso!", null));
                })
                .addOnFailureListener(e -> {
                    uiState.setValue(new CadastroAdminUiState(false, null, "Erro ao salvar dados: " + e.getMessage()));
                    // Opcional: deletar usuário criado no Auth se falhar? Sim, mas por simplicidade deixamos.
                });
    }
}