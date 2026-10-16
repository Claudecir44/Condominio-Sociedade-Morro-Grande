package com.cjstudio.condominio_sociedade_morro_grande.presentation.loginadmin;

public class LoginAdminUiState {
    private final boolean loading;
    private final String successMessage;
    private final String errorMessage;
    private final boolean showCadastroForm;

    public LoginAdminUiState() {
        this(false, null, null, false);
    }

    public LoginAdminUiState(boolean loading, String successMessage, String errorMessage, boolean showCadastroForm) {
        this.loading = loading;
        this.successMessage = successMessage;
        this.errorMessage = errorMessage;
        this.showCadastroForm = showCadastroForm;
    }

    public boolean isLoading() { return loading; }
    public String getSuccessMessage() { return successMessage; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isShowCadastroForm() { return showCadastroForm; }
}