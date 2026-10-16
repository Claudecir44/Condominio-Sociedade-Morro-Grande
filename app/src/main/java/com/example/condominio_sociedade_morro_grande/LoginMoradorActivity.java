package com.cjstudio.condominio_sociedade_morro_grande.presentation.loginmorador;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.cjstudio.condominio_sociedade_morro_grande.R;
import com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardActivity;

public class LoginMoradorActivity extends AppCompatActivity {

    private com.cjstudio.condominio_sociedade_morro_grande.presentation.loginmorador.LoginMoradorViewModel viewModel;
    private EditText editUsuario, editSenha;
    private Button btnEntrar;
    private TextView tvLembrete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_morador);

        editUsuario = findViewById(R.id.editUsuario);
        editSenha = findViewById(R.id.editSenha);
        btnEntrar = findViewById(R.id.btnEntrar);
        tvLembrete = findViewById(R.id.tvLembrete);

        viewModel = new ViewModelProvider(this).get(com.cjstudio.condominio_sociedade_morro_grande.presentation.loginmorador.LoginMoradorViewModel.class);

        // Ajusta os hints
        editUsuario.setHint("Quadra + Lote (ex: A10)");
        editSenha.setHint("3 primeiros dígitos do CPF");

        configurarCampoUsuario();

        viewModel.getUiState().observe(this, this::updateUi);
        viewModel.getNavigationToDashboard().observe(this, this::navigateToDashboard);

        btnEntrar.setOnClickListener(v -> {
            String usuario = editUsuario.getText().toString().trim().toUpperCase(); // ex: A10
            String senha = editSenha.getText().toString().trim();
            viewModel.onLoginClicked(usuario, senha);
        });
    }

    private void configurarCampoUsuario() {
        // Remove o TextWatcher que convertia para minúsculo, pois agora é maiúsculo
        editUsuario.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                if (!text.equals(text.toUpperCase())) {
                    editUsuario.setText(text.toUpperCase());
                    editUsuario.setSelection(editUsuario.length());
                }
            }
        });
        tvLembrete.setVisibility(TextView.VISIBLE);
        tvLembrete.setText("ℹ️ Dica: Use a quadra + lote (ex: A10) e os 3 primeiros dígitos do CPF.");
    }

    private void updateUi(com.cjstudio.condominio_sociedade_morro_grande.presentation.loginmorador.LoginMoradorUiState uiState) {
        if (uiState.isLoading()) {
            btnEntrar.setEnabled(false);
            btnEntrar.setText("Entrando...");
            return;
        }

        btnEntrar.setEnabled(true);
        btnEntrar.setText("Entrar");

        if (uiState.getErrorMessage() != null) {
            Toast.makeText(this, uiState.getErrorMessage(), Toast.LENGTH_SHORT).show();
            viewModel.onMessageShown();
        }
    }

    private void navigateToDashboard(com.cjstudio.condominio_sociedade_morro_grande.presentation.loginmorador.LoginMoradorViewModel.LoginSuccessData data) {
        if (data == null) return;

        Toast.makeText(this, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, DashBoardActivity.class);
        intent.putExtra("tipo_usuario", "morador");
        intent.putExtra("morador_nome", data.getMoradorNome());
        intent.putExtra("morador_cod", data.getMoradorCod());
        intent.putExtra("morador_json", data.getMoradorData().toString());
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        editUsuario.setText("");
        editSenha.setText("");
        editUsuario.requestFocus();
        viewModel.clearFields();
        tvLembrete.setVisibility(TextView.VISIBLE);
    }
}