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

import com.cjstudio.condominio_sociedade_morro_grande.Assinatura;
import com.cjstudio.condominio_sociedade_morro_grande.ListaAssembleiasActivity;
import com.cjstudio.condominio_sociedade_morro_grande.LeituraAguaActivity;
import com.cjstudio.condominio_sociedade_morro_grande.ListaAvisosActivity;
import com.cjstudio.condominio_sociedade_morro_grande.ListaDespesasActivity;
import com.cjstudio.condominio_sociedade_morro_grande.ListaLeiturasAguaActivity;
import com.cjstudio.condominio_sociedade_morro_grande.MainActivity;
import com.cjstudio.condominio_sociedade_morro_grande.ListaMoradoresActivity;
import com.cjstudio.condominio_sociedade_morro_grande.ListasActivity;
import com.cjstudio.condominio_sociedade_morro_grande.R;
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
        } else if ("admin".equals(tipoUsuario) && moradorNome != null && !moradorNome.trim().isEmpty()) {
            TextView adminLogado = findViewById(R.id.textAdminLogado);
            adminLogado.setText(moradorNome.trim());
            adminLogado.setVisibility(View.VISIBLE);
        }

        // Voltar não sai do app nem desloga: só o botão "Sair" faz isso
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(DashBoardActivity.this,
                        "Para sair, toque em \"Sair\" no canto superior direito.", Toast.LENGTH_SHORT).show();
            }
        });
        prepararNotificacoes();
        observarEmTempoReal(tipoUsuario);

        // Configurações: engrenagem no canto superior esquerdo da barra, para admin e morador
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_configuracoes);
            getSupportActionBar().setHomeActionContentDescription("Configurações");
        }

        findViewById(R.id.buttonSair).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Sair")
                .setMessage("Deseja sair da sua conta?")
                .setPositiveButton("Sair", (d, w) -> sair())
                .setNegativeButton("Cancelar", null)
                .show());
    }

    // ==================== CHAT (morador x Administração) ====================

    private TextView seloChat;
    private com.google.firebase.firestore.ListenerRegistration ouvinteChat;
    private com.google.firebase.firestore.ListenerRegistration ouvinteChatAdmin;

    /**
     * Botão "Chat" no canto superior direito, na barra "Moradores"/"Admins". Admins e a conta
     * master do app Moradores respondem como "Administração"; o morador conversa com ela.
     */
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // No app Admins os chats são quadros do painel, com as não lidas no nome
        if ("admin".equals(getIntent().getStringExtra("tipo_usuario"))) return false;
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        View acao = menu.findItem(R.id.acaoChat).getActionView();
        if (acao != null) {
            seloChat = acao.findViewById(R.id.tvSeloChat);
            acao.setOnClickListener(v -> abrirChat());
        }
        escutarSeloChat();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            startActivity(new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.ConfiguracoesActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Admin ou conta master: fala como "Administração" (lista de conversas). */
    private boolean falaComoAdministracao() {
        return "admin".equals(getIntent().getStringExtra("tipo_usuario")) || ehMaster();
    }

    private void abrirChat() {
        // Administração: lista de conversas; morador: direto na conversa com a Administração
        startActivity(falaComoAdministracao()
                ? new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.ConversasActivity.class)
                : new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.ChatActivity.class));
    }

    /** Quadros de chat do admin: as não lidas aparecem no nome e atualizam na hora. */
    private void escutarQuadrosDeChat() {
        if (!"admin".equals(getIntent().getStringExtra("tipo_usuario")) || ouvinteChat != null) return;
        String uid = com.cjstudio.condominio_sociedade_morro_grande.ChatRepository.meuUid();
        if (uid == null) return;
        ouvinteChat = new com.cjstudio.condominio_sociedade_morro_grande.ChatRepository().escutarNaoLidas(true, total ->
                mostrarNaoLidas("chatMoradores", "Chat com Moradores", total));
        ouvinteChatAdmin = new com.cjstudio.condominio_sociedade_morro_grande.ChatAdminRepository().escutarNaoLidas(uid, total ->
                mostrarNaoLidas("chatAdmin", "Chat admin", total));
    }

    private void mostrarNaoLidas(String quadro, String nome, int total) {
        Button botao = buttons.get(quadro);
        if (botao != null) botao.setText(total > 0 ? nome + " (" + (total > 99 ? "99+" : total) + ")" : nome);
    }

    private void escutarSeloChat() {
        escutarQuadrosDeChat();
        if (seloChat == null || ouvinteChat != null) return;
        ouvinteChat = new com.cjstudio.condominio_sociedade_morro_grande.ChatRepository().escutarNaoLidas(falaComoAdministracao(), total -> {
            seloChat.setVisibility(total > 0 ? View.VISIBLE : View.GONE);
            seloChat.setText(total > 99 ? "99+" : String.valueOf(total));
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        escutarSeloChat();
    }

    @Override
    protected void onStop() {
        if (ouvinteChat != null) {
            ouvinteChat.remove();
            ouvinteChat = null;
        }
        if (ouvinteChatAdmin != null) {
            ouvinteChatAdmin.remove();
            ouvinteChatAdmin = null;
        }
        super.onStop();
    }

    /** Notificação de mensagem nova no chat: registra o aparelho e pede a permissão (Android 13+). */
    private void prepararNotificacoes() {
        com.cjstudio.condominio_sociedade_morro_grande.CondominioMessagingService.registrarAparelho(falaComoAdministracao());
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    /** Assinatura paga ou vencida e boleto novo refletem no painel com a tela aberta. */
    private void observarEmTempoReal(String tipoUsuario) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        com.cjstudio.condominio_sociedade_morro_grande.TempoReal.observar(this, this::conferirAssinatura,
                db.collection("config").document("assinatura"));
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if ("morador".equals(tipoUsuario) && !ehMaster() && user != null) {
            com.cjstudio.condominio_sociedade_morro_grande.TempoReal.observar(this, this::atualizarAlertaDeBoleto,
                    db.collection("boletos").whereEqualTo("moradorUid", user.getUid()));
        }
    }

    private void sair() {
        // Este aparelho para de receber as notificações da conta
        com.cjstudio.condominio_sociedade_morro_grande.CondominioMessagingService.esquecerAparelho();
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initializeButtons() {
        // Admin
        buttons.put("cadastro", findViewById(R.id.buttonCadastro));
        buttons.put("boletosMorador", findViewById(R.id.buttonBoletosMorador));
        buttons.put("verComoMorador", findViewById(R.id.buttonVerComoMorador));
        buttons.put("chatMoradores", findViewById(R.id.buttonChatMoradores));
        buttons.put("chatAdmin", findViewById(R.id.buttonChatAdmin));
        buttons.put("backup", findViewById(R.id.buttonBackup));
        buttons.put("leiturasAgua", findViewById(R.id.buttonLeiturasAgua));

        // Botões do morador (apenas os que ficam na Dashboard)
        buttons.put("listaDespesas", findViewById(R.id.buttonListaDespesas));
        buttons.put("avisosMorador", findViewById(R.id.buttonAvisosMorador));
        buttons.put("leituraAgua", findViewById(R.id.buttonLeituraAgua));
        buttons.put("pagamentos", findViewById(R.id.buttonPagamentos));
        buttons.put("assinaturas", findViewById(R.id.buttonAssinaturas));
        buttons.put("assembleiasMorador", findViewById(R.id.buttonAssembleiasMorador));

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
            case CADASTROS:
                intent = new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.CadastrosActivity.class);
                break;
            case CHAT_MORADORES:
                intent = new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.ConversasActivity.class);
                break;
            case CHAT_ADMIN:
                intent = new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.ConversasAdminActivity.class);
                break;
            case BACKUP:
                intent = new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.BackupActivity.class);
                break;
            case VER_COMO_MORADOR:
                intent = new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.VerComoMoradorActivity.class);
                break;
            case BOLETOS_MORADOR:
                intent = new Intent(this, ListaMoradoresActivity.class);
                // Cards compactos por quadra/lote, com os boletos e sem Editar/Excluir
                intent.putExtra("somenteLeitura", true);
                break;
            case LISTA_ASSEMBLEIAS:
                // Quadro "Assembleias" do painel do morador (no app Moradores não há mais "Listas")
                intent = new Intent(this, ListaAssembleiasActivity.class);
                intent.putExtra("tipo_usuario", event.getTipoUsuario());
                break;
            case LISTA_DESPESAS:
                // Mês a mês, como o admin vê em Cadastro, mas só para consulta
                intent = new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.MesesCadastroActivity.class);
                intent.putExtra(com.cjstudio.condominio_sociedade_morro_grande.MesesCadastroActivity.EXTRA_MODULO,
                        com.cjstudio.condominio_sociedade_morro_grande.MesesCadastroActivity.Modulo.DESPESAS.name());
                intent.putExtra(com.cjstudio.condominio_sociedade_morro_grande.MesesCadastroActivity.EXTRA_SO_VER, true);
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