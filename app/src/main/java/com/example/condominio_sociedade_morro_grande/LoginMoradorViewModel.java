package com.cjstudio.condominio_sociedade_morro_grande.presentation.loginmorador;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.MoradorFirestoreRepository;

import org.json.JSONObject;

public class LoginMoradorViewModel extends AndroidViewModel {

    private final MoradorFirestoreRepository repository;
    private final MutableLiveData<LoginMoradorUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<LoginSuccessData> navigationToDashboard = new MutableLiveData<>();

    public LoginMoradorViewModel(Application application) {
        super(application);
        this.repository = new MoradorFirestoreRepository();
        uiState.setValue(LoginMoradorUiState.idle());
    }

    public LiveData<LoginMoradorUiState> getUiState() {
        return uiState;
    }

    public LiveData<LoginSuccessData> getNavigationToDashboard() {
        return navigationToDashboard;
    }

    public void onLoginClicked(String usuario, String senha) {
        if (usuario == null || usuario.isEmpty() || senha == null || senha.isEmpty()) {
            uiState.setValue(LoginMoradorUiState.error("Preencha os campos!"));
            return;
        }

        uiState.setValue(LoginMoradorUiState.loading());

        repository.validarLogin(usuario, senha, new MoradorFirestoreRepository.OnCompleteListener<JSONObject>() {
            @Override
            public void onComplete(JSONObject morador) {
                if (morador != null) {
                    uiState.setValue(LoginMoradorUiState.success(morador));
                    navigationToDashboard.setValue(new LoginSuccessData(morador));
                } else {
                    uiState.setValue(LoginMoradorUiState.error("Usuário ou senha incorretos!"));
                }
            }

            @Override
            public void onError(Exception e) {
                uiState.setValue(LoginMoradorUiState.error("Erro ao fazer login: " + e.getMessage()));
            }
        });
    }

    public void onMessageShown() {
        LoginMoradorUiState current = uiState.getValue();
        if (current != null && current.getErrorMessage() != null) {
            uiState.setValue(LoginMoradorUiState.idle());
        }
    }

    public void clearFields() {
        uiState.setValue(LoginMoradorUiState.idle());
    }

    public static class LoginSuccessData {
        private final JSONObject moradorData;

        public LoginSuccessData(JSONObject moradorData) {
            this.moradorData = moradorData;
        }

        public JSONObject getMoradorData() { return moradorData; }
        public String getMoradorNome() { return moradorData.optString("nome"); }
        public String getMoradorCod() { return moradorData.optString("cod"); }
    }
}