package com.cjstudio.condominio_sociedade_morro_grande.presentation.loginadmin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.CadastroAdminActivity;
import com.cjstudio.condominio_sociedade_morro_grande.R;
import com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Login do administrador por e-mail e senha (Firebase Auth).
 * "Cadastre-se" abre o CadastroAdminActivity (primeiro admin ou pedido de aprovação).
 */
public class LoginAdminActivity extends AppCompatActivity {

    private EditText editLoginAdmin, editLoginSenha;
    private Button btnEntrar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_admin);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        editLoginAdmin = findViewById(R.id.editLoginAdmin);
        editLoginSenha = findViewById(R.id.editLoginSenha);
        com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ContaHelper.liberarSenhaDoMaster(editLoginAdmin, editLoginSenha);
        btnEntrar = findViewById(R.id.btnEntrar);

        btnEntrar.setOnClickListener(v -> {
            String login = editLoginAdmin.getText().toString().trim();
            String senha = editLoginSenha.getText().toString().trim();
            if (login.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, "Preencha e-mail e senha", Toast.LENGTH_SHORT).show();
                return;
            }
            // Sem @ é nome de usuário (admin master); com @ tem que ser um e-mail válido
            if (login.contains("@") && !Patterns.EMAIL_ADDRESS.matcher(login).matches()) {
                Toast.makeText(this, "Digite o e-mail do administrador", Toast.LENGTH_SHORT).show();
                return;
            }
            autenticarComEmail(com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ContaHelper.emailDoAdmin(login), senha);
        });

        findViewById(R.id.textCadastreSe).setOnClickListener(v ->
                startActivity(new Intent(this, CadastroAdminActivity.class)));
    }

    private void autenticarComEmail(String email, String senha) {
        btnEntrar.setEnabled(false);
        auth.signInWithEmailAndPassword(email, senha)
                .addOnCompleteListener(task -> {
                    btnEntrar.setEnabled(true);
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            verificarTipoAdmin(user.getUid());
                        }
                    } else if (task.getException() instanceof FirebaseAuthInvalidCredentialsException
                            || task.getException() instanceof FirebaseAuthInvalidUserException) {
                        Toast.makeText(LoginAdminActivity.this,
                                "E-mail ou senha incorretos.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(LoginAdminActivity.this,
                                "Erro no login: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void verificarTipoAdmin(String uid) {
        db.collection("administradores").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && "admin".equals(doc.getString("tipo"))) {
                        Intent intent = new Intent(LoginAdminActivity.this, DashBoardActivity.class);
                        intent.putExtra("tipo_usuario", "admin");
                        intent.putExtra("morador_nome", doc.getString("nome"));
                        startActivity(intent);
                        finish();
                    } else {
                        avisarSituacaoDoPedido(uid);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(LoginAdminActivity.this,
                            "Erro ao verificar permissão: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    auth.signOut();
                });
    }

    /** Conta existe mas ainda não é admin: diz se o pedido está aguardando ou foi recusado. */
    private void avisarSituacaoDoPedido(String uid) {
        db.collection("solicitacoes_admin").document(uid).get()
                .addOnCompleteListener(task -> {
                    String mensagem = "Acesso restrito a administradores.";
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        String status = task.getResult().getString("status");
                        mensagem = "recusado".equals(status)
                                ? "Seu pedido de cadastro foi recusado por um administrador."
                                : "Seu cadastro está aguardando aprovação de um administrador.";
                    }
                    Toast.makeText(LoginAdminActivity.this, mensagem, Toast.LENGTH_LONG).show();
                    auth.signOut();
                });
    }
}
