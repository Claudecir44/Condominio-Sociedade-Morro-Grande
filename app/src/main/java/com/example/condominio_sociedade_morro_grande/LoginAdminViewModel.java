package com.cjstudio.condominio_sociedade_morro_grande.presentation.loginadmin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginAdminViewModel extends ViewModel {

    private final MutableLiveData<LoginAdminUiState> uiState = new MutableLiveData<>(new LoginAdminUiState());
    private final MutableLiveData<Boolean> navigationToDashboard = new MutableLiveData<>(false);

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public LiveData<LoginAdminUiState> getUiState() {
        return uiState;
    }

    public LiveData<Boolean> getNavigationToDashboard() {
        return navigationToDashboard;
    }

    public void onLoginClicked(String email, String senha) {
        if (email.isEmpty() || senha.isEmpty()) {
            uiState.setValue(new LoginAdminUiState(false, null, "Preencha todos os campos.", false));
            return;
        }

        uiState.setValue(new LoginAdminUiState(true, null, null, false));

        auth.signInWithEmailAndPassword(email, senha)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            verificarTipoAdmin(user.getUid());
                        } else {
                            uiState.setValue(new LoginAdminUiState(false, null, "Erro ao obter usuário.", false));
                        }
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Erro desconhecido";
                        uiState.setValue(new LoginAdminUiState(false, null, "Falha no login: " + error, false));
                    }
                });
    }

    private void verificarTipoAdmin(String uid) {
        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String tipo = documentSnapshot.getString("tipo");
                        if ("admin".equals(tipo)) {
                            uiState.setValue(new LoginAdminUiState(false, "Login bem-sucedido!", null, false));
                            navigationToDashboard.setValue(true);
                        } else {
                            auth.signOut();
                            uiState.setValue(new LoginAdminUiState(false, null, "Usuário não é administrador.", false));
                        }
                    } else {
                        auth.signOut();
                        uiState.setValue(new LoginAdminUiState(false, null, "Usuário não registrado no sistema.", false));
                    }
                })
                .addOnFailureListener(e -> {
                    auth.signOut();
                    uiState.setValue(new LoginAdminUiState(false, null, "Erro ao verificar permissão: " + e.getMessage(), false));
                });
    }

    public void onMessageShown() {
        uiState.setValue(new LoginAdminUiState(false, null, null, false));
    }
}