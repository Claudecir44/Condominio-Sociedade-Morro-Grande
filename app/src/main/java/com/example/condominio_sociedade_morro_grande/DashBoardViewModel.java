package com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cjstudio.condominio_sociedade_morro_grande.domain.model.ButtonVisibility;

public class DashBoardViewModel extends AndroidViewModel {

    private final MutableLiveData<com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<NavigationEvent> navigationEvent = new MutableLiveData<>();
    private String currentTipoUsuario;
    private String currentMoradorNome;

    public DashBoardViewModel(Application application) {
        super(application);
        uiState.setValue(com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardUiState.loading());
    }

    public LiveData<com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardUiState> getUiState() { return uiState; }
    public LiveData<NavigationEvent> getNavigationEvent() { return navigationEvent; }

    public void loadUserPermissions(String tipoUsuario, String moradorNome) {
        this.currentTipoUsuario = tipoUsuario;
        this.currentMoradorNome = moradorNome;
        ButtonVisibility visibility = getButtonVisibility();
        uiState.setValue(com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardUiState.ready(visibility, tipoUsuario, moradorNome));
    }

    private ButtonVisibility getButtonVisibility() {
        ButtonVisibility visibility = new ButtonVisibility();
        boolean isAdmin = "admin".equals(currentTipoUsuario);
        boolean isMorador = "morador".equals(currentTipoUsuario);

        // Admin – todos os botões de admin
        visibility.setVisible("administradores", isAdmin);
        visibility.setVisible("assembleias", isAdmin);
        visibility.setVisible("despesas", isAdmin);
        visibility.setVisible("avisos", isAdmin);
        visibility.setVisible("cadastro", isAdmin);
        visibility.setVisible("ocorrencias", isAdmin);
        visibility.setVisible("manutencao", isAdmin);
        visibility.setVisible("funcionarios", isAdmin);

        // Morador – apenas os que ficam na Dashboard (sem listaAssembleias)
        visibility.setVisible("listaDespesas", isMorador);
        visibility.setVisible("avisosMorador", isMorador);

        // "Listas" – visível para ambos
        visibility.setVisible("listas", isAdmin || isMorador);

        // O botão "listaAssembleias" não está no layout, portanto não definimos visibilidade

        return visibility;
    }

    public void onButtonClicked(String buttonKey) {
        NavigationTarget target = getNavigationTarget(buttonKey);
        if (target != null) {
            navigationEvent.setValue(new NavigationEvent(target, currentTipoUsuario));
        }
    }

    private NavigationTarget getNavigationTarget(String buttonKey) {
        switch (buttonKey) {
            // Admin
            case "administradores": return NavigationTarget.ADMINISTRADORES;
            case "assembleias": return NavigationTarget.REGISTRO_ASSEMBLEIAS;
            case "despesas": return NavigationTarget.REGISTRO_DESPESAS;
            case "avisos": return NavigationTarget.CADASTRO_AVISOS;
            case "cadastro": return NavigationTarget.CADASTRO_MORADOR;
            case "ocorrencias": return NavigationTarget.REGISTRO_OCORRENCIAS;
            case "manutencao": return NavigationTarget.MANUTENCAO;
            case "funcionarios": return NavigationTarget.FUNCIONARIOS;
            case "listas": return NavigationTarget.LISTAS;
            // Morador
            case "listaDespesas": return NavigationTarget.LISTA_DESPESAS;
            case "avisosMorador": return NavigationTarget.LISTA_AVISOS;
            // O case "listaAssembleias" não é mais usado na Dashboard
            default: return null;
        }
    }

    public enum NavigationTarget {
        CADASTRO_MORADOR,
        REGISTRO_OCORRENCIAS,
        FUNCIONARIOS,
        MANUTENCAO,
        REGISTRO_ASSEMBLEIAS,
        REGISTRO_DESPESAS,
        ADMINISTRADORES,
        CADASTRO_AVISOS,
        LISTAS,
        LISTA_ASSEMBLEIAS,
        LISTA_DESPESAS,
        LISTA_AVISOS,
        LISTA_MORADORES
    }

    public static class NavigationEvent {
        private final NavigationTarget target;
        private final String tipoUsuario;
        public NavigationEvent(NavigationTarget target, String tipoUsuario) {
            this.target = target;
            this.tipoUsuario = tipoUsuario;
        }
        public NavigationTarget getTarget() { return target; }
        public String getTipoUsuario() { return tipoUsuario; }
    }
}