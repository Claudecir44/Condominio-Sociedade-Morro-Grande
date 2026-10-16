package com.cjstudio.condominio_sociedade_morro_grande.presentation.cadastroadmin;

public class CadastroAdminUiState {
    private final boolean loading;
    private final String successMessage;
    private final String errorMessage;

    public CadastroAdminUiState() {
        this(false, null, null);
    }

    public CadastroAdminUiState(boolean loading, String successMessage, String errorMessage) {
        this.loading = loading;
        this.successMessage = successMessage;
        this.errorMessage = errorMessage;
    }

    public boolean isLoading() { return loading; }
    public String getSuccessMessage() { return successMessage; }
    public String getErrorMessage() { return errorMessage; }
}