package com.cjstudio.condominio_sociedade_morro_grande;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Morador envia a leitura do hidrômetro: número + foto tirada na hora.
 * Grava em leituras_agua (com quadra/lote do cadastro) e a foto em leituras/{uid}/ no Storage.
 */
public class LeituraAguaActivity extends AppCompatActivity {

    private EditText editLeitura;
    private ImageView imagePrevia;
    private Button btnEnviar;
    private Uri foto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leitura_agua);

        editLeitura = findViewById(R.id.editLeitura);
        imagePrevia = findViewById(R.id.imagePreviaHidrometro);
        btnEnviar = findViewById(R.id.btnEnviarLeitura);
        Button btnFoto = findViewById(R.id.btnFotoHidrometro);

        CapturaFoto captura = new CapturaFoto(this, uri -> {
            foto = uri;
            imagePrevia.setVisibility(View.VISIBLE);
            findViewById(R.id.textDicaFoto).setVisibility(View.VISIBLE);
            ArquivosStorage.carregarImagem(imagePrevia, uri.toString(), android.R.drawable.ic_menu_camera);
            btnFoto.setText("Tirar outra foto");
        });
        btnFoto.setOnClickListener(v -> captura.tirarFoto());
        btnEnviar.setOnClickListener(v -> enviar());
        findViewById(R.id.btnSuasLeituras).setOnClickListener(v -> startActivity(
                new android.content.Intent(this, ListaLeiturasAguaActivity.class)
                        .putExtra(ListaLeiturasAguaActivity.EXTRA_SO_MINHAS, true)));
    }

    private void enviar() {
        String leitura = editLeitura.getText().toString().trim();
        if (leitura.isEmpty()) {
            editLeitura.setError("Digite a leitura");
            editLeitura.requestFocus();
            return;
        }
        if (foto == null) {
            Toast.makeText(this, "Tire a foto do hidrômetro antes de enviar.", Toast.LENGTH_LONG).show();
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sessão expirada. Faça o login de novo.", Toast.LENGTH_LONG).show();
            return;
        }

        btnEnviar.setEnabled(false);
        btnEnviar.setText("Enviando…");
        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Quadra/lote vêm do cadastro do morador (não do que ele digita)
        db.collection("moradores").document(uid).get()
                .addOnSuccessListener(cadastro -> ArquivosStorage.enviarNaPasta(this, "leituras", uid,
                        "leitura_" + System.currentTimeMillis() + ".jpg", foto,
                        new ColecaoFirestore.Callback<String>() {
                            @Override
                            public void onSucesso(String link) {
                                Map<String, Object> dados = new HashMap<>();
                                dados.put("moradorUid", uid);
                                dados.put("nome", texto(cadastro.getString("nome")));
                                dados.put("quadra", texto(cadastro.getString("quadra")));
                                dados.put("lote", texto(cadastro.getString("lote")));
                                dados.put("leitura", leitura);
                                dados.put("foto", link);
                                dados.put("criadoEm", FieldValue.serverTimestamp());
                                db.collection("leituras_agua").add(dados)
                                        .addOnSuccessListener(ref -> concluir())
                                        .addOnFailureListener(e -> falhar("Erro ao salvar a leitura: " + e.getMessage()));
                            }

                            @Override
                            public void onErro(Exception e) {
                                falhar("Erro ao enviar a foto: " + e.getMessage());
                            }
                        }))
                .addOnFailureListener(e -> falhar("Erro ao ler seu cadastro: " + e.getMessage()));
    }

    private static String texto(String valor) {
        return valor != null ? valor : "";
    }

    private void concluir() {
        new AlertDialog.Builder(this)
                .setTitle("Leitura enviada!")
                .setMessage("A administração já recebeu a sua leitura d'água.")
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> finish())
                .show();
    }

    private void falhar(String mensagem) {
        btnEnviar.setEnabled(true);
        btnEnviar.setText("Enviar");
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
    }
}
