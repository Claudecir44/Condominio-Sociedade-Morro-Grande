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
import com.cjstudio.condominio_sociedade_morro_grande.Assinatura;
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
import com.google.android.material.button.MaterialButton;
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
        viewModel.loadUserPermissions(tipoUsuario, moradorNome, ehMaster());

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
        buttons.put("pagamentos", findViewById(R.id.buttonPagamentos));
        buttons.put("moradoresMaster", findViewById(R.id.buttonMoradoresMaster));
        buttons.put("assinaturas", findViewById(R.id.buttonAssinaturas));

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
                // Master no app Moradores: cards compactos, sem Editar/Excluir
                intent.putExtra("somenteLeitura", "morador".equals(event.getTipoUsuario()));
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
            case PAGAMENTOS:
                intent = new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.PagamentosActivity.class);
                break;
            case ASSINATURAS:
                intent = new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.AssinaturasActivity.class);
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
        viewModel.loadUserPermissions(tipoUsuario, moradorNome, ehMaster());
        if ("morador".equals(tipoUsuario) && !ehMaster()) atualizarAlertaDeBoleto();
        conferirAssinatura();
    }

    /**
     * Assinatura do app (Assinatura.java): a partir do dia 06 sem pagamento trava tudo; do dia 01
     * ao 05 os admins e a conta master recebem um lembrete por dia; a conta master (app Moradores)
     * vê o quadro "Assinaturas" com ícone vermelho do dia 25 em diante ou enquanto o mês não
     * estiver pago.
     * Sem conexão não trava (não dá pra saber se foi pago).
     */
    private void conferirAssinatura() {
        com.cjstudio.condominio_sociedade_morro_grande.AssinaturaRepository repo =
                new com.cjstudio.condominio_sociedade_morro_grande.AssinaturaRepository();
        repo.estado(new com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore.Callback<Assinatura.Estado>() {
            @Override
            public void onSucesso(Assinatura.Estado estado) {
                if (isFinishing()) return;
                if (estado.situacao == Assinatura.Situacao.BLOQUEADA) {
                    Intent intent = new Intent(DashBoardActivity.this,
                            com.cjstudio.condominio_sociedade_morro_grande.BloqueioAssinaturaActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                    return;
                }
                boolean admin = "admin".equals(getIntent().getStringExtra("tipo_usuario"));
                if ((admin || ehMaster()) && estado.situacao == Assinatura.Situacao.AGUARDANDO_PAGAMENTO) lembreteDiario();
                if (ehMaster()) mostrarIconeAssinaturas(estado);
            }

            @Override
            public void onErro(Exception e) { }
        });
    }

    private void mostrarIconeAssinaturas(Assinatura.Estado estado) {
        MaterialButton quadro = (MaterialButton) buttons.get("assinaturas");
        if (quadro == null) return;
        if (estado.mostrarIcone()) {
            // Sem tint: o ícone já tem o cartão branco e o alerta vermelho
            quadro.setIconTint(null);
            quadro.setIconResource(R.drawable.ic_painel_assinaturas_alerta);
            quadro.setContentDescription("Assinaturas: lembrete de vencimento");
        } else {
            quadro.setIconTint(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
            quadro.setIconResource(R.drawable.ic_painel_assinaturas);
            quadro.setContentDescription("Assinaturas");
        }
    }

    /** Dia 01 a 05 sem pagamento: um aviso por dia (por aparelho) para os admins. */
    private void lembreteDiario() {
        String hoje = Assinatura.mes(Assinatura.agora()) + "-" + Assinatura.agora().get(java.util.Calendar.DAY_OF_MONTH);
        android.content.SharedPreferences prefs = getSharedPreferences("assinatura", MODE_PRIVATE);
        if (hoje.equals(prefs.getString("ultimoLembrete", ""))) return;
        prefs.edit().putString("ultimoLembrete", hoje).apply();
        new AlertDialog.Builder(this)
                .setTitle("Assinatura do app")
                .setMessage("O vencimento da sua assinatura é até o dia 05 de cada mês ("
                        + Assinatura.VALOR + "). Sem pagamento, no dia 06 o app trava para moradores e administradores.")
                .setPositiveButton("OK", null)
                .show();
    }

    /** Alerta vermelho no quadro "Pagamentos" quando algum boleto foi marcado como não pago. */
    private void atualizarAlertaDeBoleto() {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        new com.cjstudio.condominio_sociedade_morro_grande.BoletosRepository().listarDoMorador(user.getUid(),
                new com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore.Callback<
                        java.util.List<com.cjstudio.condominio_sociedade_morro_grande.Boleto>>() {
                    @Override
                    public void onSucesso(java.util.List<com.cjstudio.condominio_sociedade_morro_grande.Boleto> boletos) {
                        boolean vencido = false;
                        for (com.cjstudio.condominio_sociedade_morro_grande.Boleto b : boletos) {
                            if (b.isNaoPago()) vencido = true;
                        }
                        MaterialButton pagamentos = (MaterialButton) buttons.get("pagamentos");
                        if (pagamentos == null) return;
                        if (vencido) {
                            // Sem tint: o ícone já tem o recibo branco e o alerta vermelho
                            pagamentos.setIconTint(null);
                            pagamentos.setIconResource(R.drawable.ic_painel_pagamentos_vencido);
                            pagamentos.setContentDescription("Pagamentos: boleto vencido");
                        } else {
                            pagamentos.setIconTint(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
                            pagamentos.setIconResource(R.drawable.ic_painel_pagamentos);
                            pagamentos.setContentDescription("Pagamentos");
                        }
                    }

                    @Override
                    public void onErro(Exception e) { }
                });
    }

    /** Conta master no app Moradores: o cadastro dela tem verTodos = true. */
    private boolean ehMaster() {
        String json = getIntent().getStringExtra("morador_json");
        if (json == null) return false;
        try {
            return new org.json.JSONObject(json).optBoolean("verTodos");
        } catch (org.json.JSONException e) {
            return false;
        }
    }
}