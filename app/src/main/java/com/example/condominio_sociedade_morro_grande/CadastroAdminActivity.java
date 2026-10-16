package com.cjstudio.condominio_sociedade_morro_grande;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ContaHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * Cadastro de administrador. Três situações:
 * - aberto pela tela Administradores (EXTRA_POR_ADMIN): um admin logado cadastra outro admin direto;
 * - aberto pelo "Cadastre-se" do login, sem nenhum admin ainda (config/setup não existe): primeiro admin;
 * - aberto pelo "Cadastre-se" do login, já existindo admin: vira um pedido (solicitacoes_admin) que um
 *   administrador aprova ou recusa na tela Administradores.
 */
public class CadastroAdminActivity extends AppCompatActivity {

    public static final String EXTRA_POR_ADMIN = "porAdmin";

    private enum Modo { POR_ADMIN, PRIMEIRO_ADMIN, PEDIDO }

    private EditText editEmail, editSenha, editNome, editTelefone;
    private TextView textTitulo, textInfo;
    private Button btnCadastrar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private Modo modo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_admin);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        textTitulo = findViewById(R.id.textTituloCadastroAdmin);
        textInfo = findViewById(R.id.textInfoCadastroAdmin);
        editEmail = findViewById(R.id.editEmailAdmin);
        editSenha = findViewById(R.id.editSenhaAdmin);
        editNome = findViewById(R.id.editNomeAdmin);
        editTelefone = findViewById(R.id.editTelefoneAdmin);
        btnCadastrar = findViewById(R.id.btnCadastrarAdmin);

        btnCadastrar.setOnClickListener(v -> cadastrar());

        if (getIntent().getBooleanExtra(EXTRA_POR_ADMIN, false)) {
            aplicarModo(Modo.POR_ADMIN);
        } else {
            textInfo.setText("Verificando…");
            // Se o servidor não responder, não deixa a tela presa em "Verificando…"
            Runnable semResposta = () -> {
                if (modo == null) {
                    textInfo.setText("Não foi possível falar com o servidor do condomínio. Verifique a internet e tente de novo mais tarde.");
                }
            };
            textInfo.postDelayed(semResposta, 10000);
            db.collection("config").document("setup").get()
                    .addOnSuccessListener(doc -> {
                        textInfo.removeCallbacks(semResposta);
                        aplicarModo(doc.exists() ? Modo.PEDIDO : Modo.PRIMEIRO_ADMIN);
                    })
                    .addOnFailureListener(e -> {
                        textInfo.removeCallbacks(semResposta);
                        aplicarModo(Modo.PEDIDO);
                    });
        }
    }

    private void aplicarModo(Modo novoModo) {
        modo = novoModo;
        btnCadastrar.setEnabled(true);
        switch (modo) {
            case POR_ADMIN:
                textTitulo.setText("Novo administrador");
                textInfo.setText("O administrador cadastrado já pode entrar com este e-mail e senha.");
                btnCadastrar.setText("Cadastrar administrador");
                break;
            case PRIMEIRO_ADMIN:
                textTitulo.setText("Primeiro administrador");
                textInfo.setText("Ainda não existe nenhum administrador. Quem se cadastrar agora será o administrador principal do condomínio.");
                btnCadastrar.setText("Cadastrar administrador");
                break;
            default:
                textTitulo.setText("Cadastre-se como administrador");
                textInfo.setText("Seu pedido será enviado aos administradores do condomínio. Você poderá entrar assim que um deles aprovar.");
                btnCadastrar.setText("Enviar pedido de cadastro");
                break;
        }
    }

    private static String extrairPrimeiroNome(String nomeCompleto) {
        if (nomeCompleto == null) return "";
        String[] partes = nomeCompleto.trim().split(" ");
        return partes.length > 0 ? partes[0] : nomeCompleto;
    }

    private Map<String, Object> dadosAdmin(String nomeCompleto, String email, String telefone) {
        String primeiroNome = extrairPrimeiroNome(nomeCompleto);
        Map<String, Object> dados = new HashMap<>();
        dados.put("nome", nomeCompleto);
        dados.put("nomeLowercase", nomeCompleto.toLowerCase());
        dados.put("primeiroNome", primeiroNome);
        dados.put("primeiroNomeLowercase", primeiroNome.toLowerCase());
        dados.put("email", email);
        dados.put("tipo", "admin");
        dados.put("telefone", telefone);
        return dados;
    }

    private void cadastrar() {
        if (modo == null) {
            // Ainda sem resposta do servidor: avisa em vez de o botão parecer travado
            Toast.makeText(this, "O servidor do condomínio não está respondendo. Não é possível cadastrar agora.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String email = editEmail.getText().toString().trim();
        String senha = editSenha.getText().toString().trim();
        String nomeCompleto = editNome.getText().toString().trim();
        String telefone = editTelefone.getText().toString().trim();

        if (email.isEmpty() || senha.isEmpty() || nomeCompleto.isEmpty()) {
            Toast.makeText(this, "Preencha nome, e-mail e senha", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "E-mail inválido!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (senha.length() != 6 || !senha.matches("\\d+")) {
            Toast.makeText(this, "A senha deve ter 6 dígitos numéricos.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCadastrar.setEnabled(false);
        Map<String, Object> dados = dadosAdmin(nomeCompleto, email, telefone);
        switch (modo) {
            case POR_ADMIN:
                cadastrarPorAdmin(email, senha, dados);
                break;
            case PRIMEIRO_ADMIN:
                cadastrarPrimeiroAdmin(email, senha, dados);
                break;
            default:
                enviarPedido(email, senha, dados);
                break;
        }
    }

    // Admin logado cadastra outro admin: conta criada numa instância separada, sem deslogar quem cadastra
    private void cadastrarPorAdmin(String email, String senha, Map<String, Object> dados) {
        ContaHelper.criarConta(this, email, senha, new ColecaoFirestore.Callback<String>() {
            @Override
            public void onSucesso(String uid) {
                db.collection("administradores").document(uid).set(dados)
                        .addOnSuccessListener(aVoid -> concluir("Administrador cadastrado!",
                                "Ele já pode entrar com o e-mail " + email + "."))
                        .addOnFailureListener(e -> falhar("Erro ao salvar dados: " + e.getMessage()));
            }

            @Override
            public void onErro(Exception e) {
                falhar(mensagemErroConta(e));
            }
        });
    }

    // Primeiro admin: grava o admin e config/setup no mesmo lote (as regras só aceitam uma vez)
    private void cadastrarPrimeiroAdmin(String email, String senha, Map<String, Object> dados) {
        auth.createUserWithEmailAndPassword(email, senha)
                .addOnSuccessListener(resultado -> {
                    FirebaseUser user = resultado.getUser();
                    if (user == null) {
                        falhar("Não foi possível criar a conta.");
                        return;
                    }
                    Map<String, Object> setup = new HashMap<>();
                    setup.put("primeiroAdmin", user.getUid());
                    setup.put("criadoEm", System.currentTimeMillis());

                    WriteBatch lote = db.batch();
                    lote.set(db.collection("administradores").document(user.getUid()), dados);
                    lote.set(db.collection("config").document("setup"), setup);
                    lote.commit()
                            .addOnSuccessListener(aVoid -> {
                                auth.signOut();
                                concluir("Administrador cadastrado!", "Agora é só entrar com o seu e-mail e senha.");
                            })
                            .addOnFailureListener(e -> {
                                // Alguém já virou o primeiro admin: transforma em pedido de aprovação
                                user.delete();
                                auth.signOut();
                                aplicarModo(Modo.PEDIDO);
                                falhar("Já existe um administrador. Envie seu cadastro como pedido.");
                            });
                })
                .addOnFailureListener(e -> falhar(mensagemErroConta(e)));
    }

    // Pedido: cria a conta e o pedido em solicitacoes_admin/{uid}; um admin aprova depois
    private void enviarPedido(String email, String senha, Map<String, Object> dados) {
        auth.createUserWithEmailAndPassword(email, senha)
                .addOnSuccessListener(resultado -> {
                    FirebaseUser user = resultado.getUser();
                    if (user == null) {
                        falhar("Não foi possível criar a conta.");
                        return;
                    }
                    Map<String, Object> pedido = new HashMap<>(dados);
                    pedido.put("status", "pendente");
                    pedido.put("criadoEm", System.currentTimeMillis());
                    db.collection("solicitacoes_admin").document(user.getUid()).set(pedido)
                            .addOnSuccessListener(aVoid -> {
                                auth.signOut();
                                concluir("Pedido enviado!",
                                        "Um administrador do condomínio precisa aprovar seu cadastro. Depois disso, entre com o seu e-mail e senha.");
                            })
                            .addOnFailureListener(e -> {
                                user.delete();
                                auth.signOut();
                                falhar("Erro ao enviar o pedido: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> falhar(mensagemErroConta(e)));
    }

    private String mensagemErroConta(Exception e) {
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "Este e-mail já está cadastrado. Faça login ou use outro e-mail.";
        }
        return "Erro no cadastro: " + e.getMessage();
    }

    private void concluir(String titulo, String mensagem) {
        btnCadastrar.setEnabled(true);
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(mensagem)
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> finish())
                .show();
    }

    private void falhar(String mensagem) {
        btnCadastrar.setEnabled(true);
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
    }
}
