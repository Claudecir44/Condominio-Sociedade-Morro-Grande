package com.cjstudio.condominio_sociedade_morro_grande.presentation.loginmorador;

import org.json.JSONObject;

public class LoginMoradorUiState {
    private final boolean isLoading;
    private final String errorMessage;
    private final String successMessage;
    private final JSONObject moradorData;

    private LoginMoradorUiState(Builder builder) {
        this.isLoading = builder.isLoading;
        this.errorMessage = builder.errorMessage;
        this.successMessage = builder.successMessage;
        this.moradorData = builder.moradorData;
    }

    public static LoginMoradorUiState idle() {
        return new Builder().setIsLoading(false).build();
    }

    public static LoginMoradorUiState loading() {
        return new Builder().setIsLoading(true).build();
    }

    public static LoginMoradorUiState success(JSONObject moradorData) {
        return new Builder()
                .setIsLoading(false)
                .setSuccessMessage("Login realizado com sucesso!")
                .setMoradorData(moradorData)
                .build();
    }

    public static LoginMoradorUiState error(String message) {
        return new Builder()
                .setIsLoading(false)
                .setErrorMessage(message)
                .build();
    }

    public boolean isLoading() { return isLoading; }
    public String getErrorMessage() { return errorMessage; }
    public String getSuccessMessage() { return successMessage; }
    public JSONObject getMoradorData() { return moradorData; }

    public static class Builder {
        private boolean isLoading = false;
        private String errorMessage;
        private String successMessage;
        private JSONObject moradorData;

        public Builder setIsLoading(boolean isLoading) {
            this.isLoading = isLoading;
            return this;
        }

        public Builder setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder setSuccessMessage(String successMessage) {
            this.successMessage = successMessage;
            return this;
        }

        public Builder setMoradorData(JSONObject moradorData) {
            this.moradorData = moradorData;
            return this;
        }

        public LoginMoradorUiState build() {
            return new LoginMoradorUiState(this);
        }
    }
}