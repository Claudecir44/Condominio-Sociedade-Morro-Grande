package com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard;

import com.cjstudio.condominio_sociedade_morro_grande.domain.model.ButtonVisibility;

public class DashBoardUiState {
    private final boolean isLoading;
    private final ButtonVisibility buttonVisibility;
    private final String errorMessage;
    private final String tipoUsuario;
    private final String moradorNome;

    private DashBoardUiState(Builder builder) {
        this.isLoading = builder.isLoading;
        this.buttonVisibility = builder.buttonVisibility;
        this.errorMessage = builder.errorMessage;
        this.tipoUsuario = builder.tipoUsuario;
        this.moradorNome = builder.moradorNome;
    }

    public static DashBoardUiState loading() {
        return new Builder().setIsLoading(true).build();
    }

    public static DashBoardUiState ready(ButtonVisibility visibility, String tipoUsuario, String moradorNome) {
        return new Builder()
                .setIsLoading(false)
                .setButtonVisibility(visibility)
                .setTipoUsuario(tipoUsuario)
                .setMoradorNome(moradorNome)
                .build();
    }

    public static DashBoardUiState error(String message) {
        return new Builder()
                .setIsLoading(false)
                .setErrorMessage(message)
                .build();
    }

    public boolean isLoading() { return isLoading; }
    public ButtonVisibility getButtonVisibility() { return buttonVisibility; }
    public String getErrorMessage() { return errorMessage; }
    public String getTipoUsuario() { return tipoUsuario; }
    public String getMoradorNome() { return moradorNome; }

    public static class Builder {
        private boolean isLoading = false;
        private ButtonVisibility buttonVisibility;
        private String errorMessage;
        private String tipoUsuario;
        private String moradorNome;

        public Builder setIsLoading(boolean isLoading) {
            this.isLoading = isLoading;
            return this;
        }

        public Builder setButtonVisibility(ButtonVisibility buttonVisibility) {
            this.buttonVisibility = buttonVisibility;
            return this;
        }

        public Builder setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder setTipoUsuario(String tipoUsuario) {
            this.tipoUsuario = tipoUsuario;
            return this;
        }

        public Builder setMoradorNome(String moradorNome) {
            this.moradorNome = moradorNome;
            return this;
        }

        public DashBoardUiState build() {
            return new DashBoardUiState(this);
        }
    }
}