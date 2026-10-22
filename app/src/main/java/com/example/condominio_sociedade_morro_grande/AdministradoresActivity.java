package com.cjstudio.condominio_sociedade_morro_grande;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.AdminFirestoreRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class AdministradoresActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "admin_prefs";
    private static final String KEY_ADMIN_LOGADO = "admin_logado";

    private LinearLayout listaAdmins;
    private String adminLogadoUid;
    private AdminFirestoreRepository repository;
    private boolean isLoading = false; // flag para evitar carregamentos simultâneos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_administradores);

        listaAdmins = findViewById(R.id.listaAdmins);
        repository = new AdminFirestoreRepository();

        adminLogadoUid = getAdminLogadoUid(this);

        findViewById(R.id.btnNovoAdmin).setOnClickListener(v ->
                startActivity(new Intent(this, CadastroAdminActivity.class)
                        .putExtra(CadastroAdminActivity.EXTRA_POR_ADMIN, true)));
    }

    // ==================== PEDIDOS DE CADASTRO (Cadastre-se do login) ====================

    /** Mostra no topo da lista os pedidos pendentes, com Aprovar e Recusar. */
    private void carregarPedidos() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("solicitacoes_admin").whereEqualTo("status", "pendente").get()
                .addOnSuccessListener(query -> {
                    if (isFinishing() || query.isEmpty()) return;
                    int posicao = 0;
                    TextView titulo = new TextView(this);
                    titulo.setText("Pedidos de cadastro (" + query.size() + ")");
                    titulo.setTextSize(16);
                    titulo.setTypeface(null, android.graphics.Typeface.BOLD);
                    titulo.setTextColor(Color.parseColor("#B45309"));
                    titulo.setPadding(16, 16, 16, 8);
                    listaAdmins.addView(titulo, posicao++);
                    for (DocumentSnapshot pedido : query.getDocuments()) {
                        listaAdmins.addView(criarItemPedido(pedido), posicao++);
                    }
                    TextView tituloAdmins = new TextView(this);
                    tituloAdmins.setText("Administradores");
                    tituloAdmins.setTextSize(16);
                    tituloAdmins.setTypeface(null, android.graphics.Typeface.BOLD);
                    tituloAdmins.setPadding(16, 24, 16, 8);
                    listaAdmins.addView(tituloAdmins, posicao);
                });
    }

    private LinearLayout criarItemPedido(DocumentSnapshot pedido) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(16, 12, 16, 12);
        item.setBackgroundColor(Color.parseColor("#FFF7ED"));

        TextView dados = new TextView(this);
        dados.setText(pedido.getString("nome") + "\n" + pedido.getString("email"));
        dados.setTextSize(15);
        dados.setTextColor(Color.BLACK);
        item.addView(dados);

        LinearLayout botoes = new LinearLayout(this);
        botoes.setOrientation(LinearLayout.HORIZONTAL);
        Button aprovar = new Button(this);
        aprovar.setText("Aprovar");
        aprovar.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Aprovar administrador")
                .setMessage("Dar acesso de administrador para " + pedido.getString("nome") + "?")
                .setPositiveButton("Aprovar", (d, w) -> aprovarPedido(pedido))
                .setNegativeButton("Cancelar", null)
                .show());
        Button recusar = new Button(this);
        recusar.setText("Recusar");
        recusar.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Recusar pedido")
                .setMessage("Recusar o pedido de " + pedido.getString("nome") + "?")
                .setPositiveButton("Recusar", (d, w) -> recusarPedido(pedido))
                .setNegativeButton("Cancelar", null)
                .show());
        botoes.addView(aprovar);
        botoes.addView(recusar);
        item.addView(botoes);
        return item;
    }

    private void aprovarPedido(DocumentSnapshot pedido) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> admin = new HashMap<>();
        for (String campo : new String[]{"nome", "nomeLowercase", "primeiroNome", "primeiroNomeLowercase", "email", "telefone"}) {
            Object valor = pedido.get(campo);
            admin.put(campo, valor != null ? valor : "");
        }
        admin.put("tipo", "admin");
        WriteBatch lote = db.batch();
        lote.set(db.collection("administradores").document(pedido.getId()), admin);
        lote.delete(pedido.getReference());
        lote.commit()
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Administrador aprovado!", Toast.LENGTH_SHORT).show();
                    carregarAdministradores();
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Erro ao aprovar: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void recusarPedido(DocumentSnapshot pedido) {
        pedido.getReference().update("status", "recusado")
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Pedido recusado.", Toast.LENGTH_SHORT).show();
                    carregarAdministradores();
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Erro ao recusar: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private String getAdminLogadoUid(Context context) {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ADMIN_LOGADO, null);
    }

    private void carregarAdministradores() {
        // Evita carregamento simultâneo
        if (isLoading) {
            return;
        }
        isLoading = true;

        // Limpa a lista ANTES de iniciar a requisição
        listaAdmins.removeAllViews();

        repository.listarAdmins(new AdminFirestoreRepository.OnCompleteListener<List<AdminFirestoreRepository.AdminInfo>>() {
            @Override
            public void onComplete(List<AdminFirestoreRepository.AdminInfo> admins) {
                runOnUiThread(() -> {
                    // Verifica se ainda estamos na mesma atividade e se a lista não foi limpa novamente
                    if (isFinishing() || isLoading == false) {
                        isLoading = false;
                        return;
                    }

                    if (admins.isEmpty()) {
                        TextView tvEmpty = new TextView(AdministradoresActivity.this);
                        tvEmpty.setText("Nenhum administrador cadastrado");
                        tvEmpty.setTextSize(14);
                        tvEmpty.setPadding(16, 16, 16, 16);
                        tvEmpty.setTextColor(Color.GRAY);
                        tvEmpty.setGravity(android.view.Gravity.CENTER);
                        listaAdmins.addView(tvEmpty);
                        isLoading = false;
                        return;
                    }

                    // Separa o admin logado
                    AdminFirestoreRepository.AdminInfo loggedAdminInfo = null;
                    List<AdminFirestoreRepository.AdminInfo> outrosAdmins = new ArrayList<>();

                    for (AdminFirestoreRepository.AdminInfo admin : admins) {
                        if (admin.uid.equals(adminLogadoUid)) {
                            loggedAdminInfo = admin;
                        } else {
                            outrosAdmins.add(admin);
                        }
                    }

                    // Adiciona o admin logado primeiro (se houver)
                    if (loggedAdminInfo != null) {
                        TextView tv = criarTextViewAdmin(
                                loggedAdminInfo.nome + " (" + loggedAdminInfo.email + ")",
                                true,
                                loggedAdminInfo.uid,
                                loggedAdminInfo.master
                        );
                        listaAdmins.addView(tv);
                    }

                    // Adiciona os demais
                    for (AdminFirestoreRepository.AdminInfo admin : outrosAdmins) {
                        TextView tv = criarTextViewAdmin(
                                admin.nome + " (" + admin.email + ")",
                                false,
                                admin.uid,
                                admin.master
                        );
                        listaAdmins.addView(tv);
                    }

                    isLoading = false;
                    carregarPedidos();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(AdministradoresActivity.this,
                            "Erro ao carregar administradores: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    isLoading = false;
                });
            }
        });
    }

    /**
     * Cria um TextView representando um administrador.
     * @param nome Nome a ser exibido (com email entre parênteses)
     * @param logado true se for o admin logado
     * @param uid UID do administrador (armazenado como tag)
     */
    private TextView criarTextViewAdmin(String nome, boolean logado, String uid, boolean master) {
        TextView tv = new TextView(this);
        tv.setText(nome);
        tv.setTextSize(16);
        tv.setPadding(16, 16, 16, 16);
        tv.setTag(uid); // guarda o UID para exclusão

        if (logado) {
            tv.setTextColor(Color.parseColor("#2E7D32"));
            tv.setBackgroundColor(Color.parseColor("#E8F5E9"));
            tv.append(" (logado)");
        } else {
            tv.setTextColor(Color.BLACK);
        }

        tv.setOnClickListener(v -> {
            if (logado) {
                Toast.makeText(this, "Você não pode remover seu próprio usuário!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (master) {
                Toast.makeText(this, "O admin master não pode ser removido.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Obtém o UID armazenado na tag
            String uidParaRemover = (String) tv.getTag();

            // Extrai o email para exibir na mensagem (opcional)
            String email = nome.substring(nome.indexOf("(") + 1, nome.indexOf(")"));

            new AlertDialog.Builder(this)
                    .setTitle("Remover Administrador")
                    .setMessage("Deseja realmente remover o administrador \"" + email + "\"?")
                    .setPositiveButton("Sim", (dialog, which) -> removerAdmin(uidParaRemover))
                    .setNegativeButton("Não", null)
                    .show();
        });

        return tv;
    }

    private void removerAdmin(String uid) {
        repository.removerAdmin(uid, new AdminFirestoreRepository.OnCompleteListener<Void>() {
            @Override
            public void onComplete(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(AdministradoresActivity.this,
                            "Administrador removido com sucesso!",
                            Toast.LENGTH_SHORT).show();
                    carregarAdministradores(); // recarrega a lista
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(AdministradoresActivity.this,
                                "Erro ao remover: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recarrega apenas se a lista estiver vazia ou se for necessário
        carregarAdministradores();
    }
}