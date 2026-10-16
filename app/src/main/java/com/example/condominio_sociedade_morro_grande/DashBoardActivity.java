package com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.cjstudio.condominio_sociedade_morro_grande.AdministradoresActivity;
import com.cjstudio.condominio_sociedade_morro_grande.CadastroAvisosActivity;
import com.cjstudio.condominio_sociedade_morro_grande.CadastroManutencaoActivity;
import com.cjstudio.condominio_sociedade_morro_grande.CadastroMoradorActivity;
import com.cjstudio.condominio_sociedade_morro_grande.FuncionariosActivity;
import com.cjstudio.condominio_sociedade_morro_grande.ListaAssembleiasActivity;
import com.cjstudio.condominio_sociedade_morro_grande.LeituraAguaActivity;
import com.cjstudio.condominio_sociedade_morro_grande.ListaAvisosActivity;
import com.cjstudio.condominio_sociedade_morro_grande.ListaDespesasActivity;
import com.cjstudio.condominio_sociedade_morro_grande.ListaLeiturasAguaActivity;
import com.cjstudio.condominio_sociedade_morro_grande.MainActivity;
import com.cjstudio.condominio_sociedade_morro_grande.ListaMoradoresActivity;
import com.cjstudio.condominio_sociedade_morro_grande.ListasActivity;
import com.cjstudio.condominio_sociedade_morro_grande.R;
import com.cjstudio.condominio_sociedade_morro_grande.RegistroAssembleiasActivity;
import com.cjstudio.condominio_sociedade_morro_grande.RegistroDespesasActivity;
import com.cjstudio.condominio_sociedade_morro_grande.RegistroOcorrenciasActivity;
import com.cjstudio.condominio_sociedade_morro_grande.domain.model.ButtonVisibility;
import com.google.firebase.auth.FirebaseAuth;

import java.util.HashMap;
import java.util.Map;

public class DashBoardActivity extends AppCompatActivity {

    private com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardViewModel viewModel;
    private Map<String, Button> buttons = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash_board);

        viewModel = new ViewModelProvider(this).get(com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardViewModel.class);
        initializeButtons();

        viewModel.getUiState().observe(this, this::updateUi);
        viewModel.getNavigationEvent().observe(this, this::handleNavigation);

        String tipoUsuario = getIntent().getStringExtra("tipo_usuario");
        String moradorNome = getIntent().getStringExtra("morador_nome");
        viewModel.loadUserPermissions(tipoUsuario, moradorNome);

        // Cabeçalho: "Administração" para admin, saudação para o morador
        TextView titulo = findViewById(R.id.textTitulo);
        if ("morador".equals(tipoUsuario)) {
            String primeiroNome = moradorNome != null ? moradorNome.trim().split(" ")[0] : "";
            titulo.setText(primeiroNome.isEmpty() ? "Área do Morador" : "Olá, " + primeiroNome + "!");
        }

        // Voltar não sai do app nem desloga: só o botão "Sair" faz isso
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(DashBoardActivity.this,
                        "Para sair, toque em \"Sair\" no canto superior direito.", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.buttonSair).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Sair")
                .setMessage("Deseja sair da sua conta?")
                .setPositiveButton("Sair", (d, w) -> sair())
                .setNegativeButton("Cancelar", null)
                .show());
    }

    private void sair() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initializeButtons() {
        // Admin
        buttons.put("administradores", findViewById(R.id.buttonAdministradores));
        buttons.put("assembleias", findViewById(R.id.buttonAssembleias));
        buttons.put("despesas", findViewById(R.id.buttonDespesas));
        buttons.put("avisos", findViewById(R.id.buttonAvisos));
        buttons.put("cadastro", findViewById(R.id.buttonCadastroMoradores));
        buttons.put("ocorrencias", findViewById(R.id.buttonRegistroOcorrencias));
        buttons.put("manutencao", findViewById(R.id.buttonManutencao));
        buttons.put("funcionarios", findViewById(R.id.buttonFuncionarios));
        buttons.put("leiturasAgua", findViewById(R.id.buttonLeiturasAgua));

        // Botões do morador (apenas os que ficam na Dashboard)
        buttons.put("listaDespesas", findViewById(R.id.buttonListaDespesas));
        buttons.put("avisosMorador", findViewById(R.id.buttonAvisosMorador));
        buttons.put("leituraAgua", findViewById(R.id.buttonLeituraAgua));

        // Botão "Listas" (admin e morador)
        buttons.put("listas", findViewById(R.id.buttonListas));

        // O botão "listaAssembleias" NÃO está mais no mapa

        for (Map.Entry<String, Button> entry : buttons.entrySet()) {
            entry.getValue().setOnClickListener(v ->
                    viewModel.onButtonClicked(entry.getKey()));
        }
    }

    private void updateUi(com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardUiState uiState) {
        if (uiState.isLoading()) return;
        if (uiState.getErrorMessage() != null) {
            Toast.makeText(this, uiState.getErrorMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        ButtonVisibility visibility = uiState.getButtonVisibility();
        if (visibility != null) {
            // Botões de outro perfil saem da grade (GONE no GridLayout ainda ocupa a posição e deixa buraco)
            for (Map.Entry<String, Button> entry : buttons.entrySet()) {
                Button botao = entry.getValue();
                if (!visibility.isVisible(entry.getKey()) && botao.getParent() instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) botao.getParent()).removeView(botao);
                }
            }
        }
    }

    private void handleNavigation(com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardViewModel.NavigationEvent event) {
        Intent intent = null;
        switch (event.getTarget()) {
            case CADASTRO_MORADOR:
                intent = new Intent(this, CadastroMoradorActivity.class);
                break;
            case LISTA_MORADORES:
                intent = new Intent(this, ListaMoradoresActivity.class);
                break;
            case REGISTRO_OCORRENCIAS:
                intent = new Intent(this, RegistroOcorrenciasActivity.class);
                break;
            case FUNCIONARIOS:
                intent = new Intent(this, FuncionariosActivity.class);
                break;
            case MANUTENCAO:
                intent = new Intent(this, CadastroManutencaoActivity.class);
                break;
            case REGISTRO_ASSEMBLEIAS:
                intent = new Intent(this, RegistroAssembleiasActivity.class);
                break;
            case REGISTRO_DESPESAS:
                intent = new Intent(this, RegistroDespesasActivity.class);
                break;
            case ADMINISTRADORES:
                intent = new Intent(this, AdministradoresActivity.class);
                break;
            case CADASTRO_AVISOS:
                intent = new Intent(this, CadastroAvisosActivity.class);
                break;
            case LISTA_ASSEMBLEIAS:
                // Este case ainda é mantido, mas não será chamado pela Dashboard (só pela ListasActivity)
                intent = new Intent(this, ListaAssembleiasActivity.class);
                intent.putExtra("tipo_usuario", event.getTipoUsuario());
                break;
            case LISTA_DESPESAS:
                intent = new Intent(this, ListaDespesasActivity.class);
                intent.putExtra("tipo_usuario", event.getTipoUsuario());
                break;
            case LISTA_AVISOS:
                intent = new Intent(this, ListaAvisosActivity.class);
                intent.putExtra("tipo_usuario", event.getTipoUsuario());
                break;
            case LISTAS:
                intent = new Intent(this, ListasActivity.class);
                break;
            case LEITURA_AGUA:
                intent = new Intent(this, LeituraAguaActivity.class);
                break;
            case LISTA_LEITURAS_AGUA:
                intent = new Intent(this, ListaLeiturasAguaActivity.class);
                break;
            default:
                break;
        }
        if (intent != null) {
            startActivity(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String tipoUsuario = getIntent().getStringExtra("tipo_usuario");
        String moradorNome = getIntent().getStringExtra("morador_nome");
        viewModel.loadUserPermissions(tipoUsuario, moradorNome);
    }
}