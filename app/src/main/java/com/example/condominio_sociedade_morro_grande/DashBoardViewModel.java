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
    // Conta master no app Moradores (morador com verTodos)
    private boolean currentMaster;

    public DashBoardViewModel(Application application) {
        super(application);
        uiState.setValue(com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardUiState.loading());
    }

    public LiveData<com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardUiState> getUiState() { return uiState; }
    public LiveData<NavigationEvent> getNavigationEvent() { return navigationEvent; }

    public void loadUserPermissions(String tipoUsuario, String moradorNome) {
        loadUserPermissions(tipoUsuario, moradorNome, false);
    }

    public void loadUserPermissions(String tipoUsuario, String moradorNome, boolean master) {
        this.currentTipoUsuario = tipoUsuario;
        this.currentMoradorNome = moradorNome;
        this.currentMaster = master;
        ButtonVisibility visibility = getButtonVisibility();
        uiState.setValue(com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardUiState.ready(visibility, tipoUsuario, moradorNome));
    }

    private ButtonVisibility getButtonVisibility() {
        ButtonVisibility visibility = new ButtonVisibility();
        boolean isAdmin = "admin".equals(currentTipoUsuario);
        boolean isMorador = "morador".equals(currentTipoUsuario);

        // Admin – todos os botões de admin
        visibility.setVisible("cadastro", isAdmin);
        visibility.setVisible("boletosMorador", isAdmin);
        visibility.setVisible("verComoMorador", isAdmin);
        visibility.setVisible("chatMoradores", isAdmin);
        visibility.setVisible("chatAdmin", isAdmin);
        visibility.setVisible("backup", isAdmin);
        visibility.setVisible("leiturasAgua", isAdmin);

        // Morador – apenas os que ficam na Dashboard (sem listaAssembleias)
        // Conta master: só Assinaturas
        visibility.setVisible("listaDespesas", isMorador && !currentMaster);
        visibility.setVisible("avisosMorador", isMorador && !currentMaster);
        visibility.setVisible("leituraAgua", isMorador && !currentMaster);
        visibility.setVisible("pagamentos", isMorador && !currentMaster);
        visibility.setVisible("assinaturas", isMorador && currentMaster);
        visibility.setVisible("assembleiasMorador", isMorador && !currentMaster);

        // No app Moradores o "Listas" repetia o que já está no painel: fica só para o admin
        visibility.setVisible("listas", isAdmin);

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
            case "cadastro": return NavigationTarget.CADASTROS;
            case "boletosMorador": return NavigationTarget.BOLETOS_MORADOR;
            case "verComoMorador": return NavigationTarget.VER_COMO_MORADOR;
            case "chatMoradores": return NavigationTarget.CHAT_MORADORES;
            case "chatAdmin": return NavigationTarget.CHAT_ADMIN;
            case "backup": return NavigationTarget.BACKUP;
            case "listas": return NavigationTarget.LISTAS;
            case "leiturasAgua": return NavigationTarget.LISTA_LEITURAS_AGUA;
            // Morador
            case "listaDespesas": return NavigationTarget.LISTA_DESPESAS;
            case "avisosMorador": return NavigationTarget.LISTA_AVISOS;
            case "leituraAgua": return NavigationTarget.LEITURA_AGUA;
            case "pagamentos": return NavigationTarget.PAGAMENTOS;
            case "assinaturas": return NavigationTarget.ASSINATURAS;
            case "assembleiasMorador": return NavigationTarget.LISTA_ASSEMBLEIAS;
            // O case "listaAssembleias" não é mais usado na Dashboard
            default: return null;
        }
    }

    public enum NavigationTarget {
        CADASTROS,
        VER_COMO_MORADOR,
        CHAT_MORADORES,
        CHAT_ADMIN,
        BACKUP,
        LISTAS,
        LISTA_ASSEMBLEIAS,
        LISTA_DESPESAS,
        LISTA_AVISOS,
        BOLETOS_MORADOR,
        LEITURA_AGUA,
        LISTA_LEITURAS_AGUA,
        PAGAMENTOS,
        ASSINATURAS
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