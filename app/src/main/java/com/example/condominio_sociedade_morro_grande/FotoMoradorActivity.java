package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Obrigatória no primeiro login do morador que ainda não tem foto: só segue para o painel depois
 * de salvar a foto (moradores/{uid}/foto.jpg no Storage + campo "foto" no cadastro).
 * Recebe os mesmos extras que o DashBoardActivity e repassa ao concluir.
 */
public class FotoMoradorActivity extends AppCompatActivity {

    private ImageView imageFoto;
    private Button btnSalvar;
    private Uri fotoEscolhida;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foto_morador);

        imageFoto = findViewById(R.id.imageFotoMorador);
        btnSalvar = findViewById(R.id.btnSalvarFoto);

        String nome = getIntent().getStringExtra("morador_nome");
        if (nome != null && !nome.trim().isEmpty()) {
            ((TextView) findViewById(R.id.textOlaMorador)).setText("Olá, " + nome.trim().split(" ")[0]
                    + "! Antes de entrar, coloque uma foto sua. Ela ajuda a portaria e a administração a identificar os moradores.");
        }

        CapturaFoto captura = new CapturaFoto(this, uri -> {
            fotoEscolhida = uri;
            ArquivosStorage.carregarImagem(imageFoto, uri.toString(), android.R.drawable.ic_menu_camera);
            btnSalvar.setEnabled(true);
        });
        findViewById(R.id.btnTirarFoto).setOnClickListener(v -> captura.tirarFoto());
        findViewById(R.id.btnGaleria).setOnClickListener(v -> captura.escolherDaGaleria());
        btnSalvar.setOnClickListener(v -> salvar());
    }

    private void salvar() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || fotoEscolhida == null) {
            Toast.makeText(this, "Faça o login de novo.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        btnSalvar.setEnabled(false);
        btnSalvar.setText("Enviando…");
        String uid = user.getUid();
        ArquivosStorage.enviarNaPasta(this, "moradores", uid, "foto.jpg", fotoEscolhida,
                new ColecaoFirestore.Callback<String>() {
                    @Override
                    public void onSucesso(String link) {
                        FirebaseFirestore.getInstance().collection("moradores").document(uid)
                                .update("foto", link)
                                .addOnSuccessListener(aVoid -> irParaPainel())
                                .addOnFailureListener(e -> falhar("Erro ao salvar a foto: " + e.getMessage()));
                    }

                    @Override
                    public void onErro(Exception e) {
                        falhar("Erro ao enviar a foto: " + e.getMessage());
                    }
                });
    }

    private void falhar(String mensagem) {
        btnSalvar.setEnabled(true);
        btnSalvar.setText("Salvar foto e entrar");
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
    }

    private void irParaPainel() {
        Toast.makeText(this, "Foto salva!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, DashBoardActivity.class);
        if (getIntent().getExtras() != null) intent.putExtras(getIntent().getExtras());
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Sem foto não entra, e voltar não desloga (só o botão Sair do painel desloga)
        android.widget.Toast.makeText(this, "Cadastre sua foto para entrar.", android.widget.Toast.LENGTH_SHORT).show();
    }
}
