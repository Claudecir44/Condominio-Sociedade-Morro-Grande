package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.storage.FirebaseStorage;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Quadro "Backup": a Cloud Function backupDiario grava o banco todo dia à meia-noite; aqui o admin
 * vê os backups, baixa, gera um agora ou gera o Pacote completo (banco, planilhas, arquivos,
 * instruções), que é o que leva os dados para outro aplicativo.
 */
public class BackupActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private LinearLayout lista;
    private Button btnAgora;
    private Button btnPacote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);
        lista = findViewById(R.id.layoutBackups);
        btnAgora = findViewById(R.id.btnBackupAgora);
        btnPacote = findViewById(R.id.btnPacoteCompleto);
        btnAgora.setOnClickListener(v -> gerar("gerarBackupAgora", btnAgora, "Backup gerado!"));
        btnPacote.setOnClickListener(v -> gerar("gerarPacoteCompleto", btnPacote, "Pacote completo gerado!"));
        TempoReal.observar(this, this::carregar, db.collection("backups"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregar();
    }

    private void carregar() {
        db.collection("backups").orderBy("criadoEm", Query.Direction.DESCENDING).get()
                .addOnSuccessListener(snap -> {
                    lista.removeAllViews();
                    if (snap.isEmpty()) lista.addView(texto("Nenhum backup ainda. O primeiro sai à meia-noite.", false));
                    for (DocumentSnapshot doc : snap.getDocuments()) lista.addView(cartao(doc));
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Erro ao carregar: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private TextView texto(String conteudo, boolean destaque) {
        TextView t = new TextView(this);
        t.setText(conteudo);
        t.setTextSize(destaque ? 16 : 13);
        t.setTextColor(Color.parseColor(destaque ? "#1A1A2E" : "#6B7280"));
        if (destaque) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private View cartao(DocumentSnapshot doc) {
        LinearLayout cartao = new LinearLayout(this);
        cartao.setOrientation(LinearLayout.VERTICAL);
        int m = ListaAnexos.dp(this, 12);
        cartao.setPadding(0, m, 0, m);
        boolean pacote = "pacote".equals(doc.getString("tipo"));
        cartao.addView(texto((pacote ? "Pacote completo — " : "Backup do banco — ") + doc.getString("titulo"), true));
        Long tamanho = doc.getLong("tamanho");
        Long registros = doc.getLong("registros");
        cartao.addView(texto(String.format(Locale.ROOT, "%.1f MB · %d registros",
                (tamanho != null ? tamanho : 0) / 1048576.0, registros != null ? registros : 0), false));
        Button baixar = new Button(this);
        baixar.setText("Baixar");
        baixar.setAllCaps(false);
        baixar.setOnClickListener(v -> baixar(doc.getString("caminho")));
        cartao.addView(baixar);
        cartao.addView(ListaBoletos.separador(this));
        return cartao;
    }

    private void baixar(String caminho) {
        FirebaseStorage.getInstance().getReference(caminho).getDownloadUrl()
                .addOnSuccessListener(url -> startActivity(new Intent(Intent.ACTION_VIEW, url)))
                .addOnFailureListener(e -> Toast.makeText(this, "Erro ao baixar: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void gerar(String funcao, Button botao, String sucesso) {
        botao.setEnabled(false);
        Toast.makeText(this, "Gerando… isso pode levar alguns minutos.", Toast.LENGTH_LONG).show();
        FirebaseFunctions.getInstance(AssinaturaRepository.REGIAO_FUNCOES).getHttpsCallable(funcao)
                .withTimeout(540, TimeUnit.SECONDS).call()
                .addOnSuccessListener(r -> {
                    botao.setEnabled(true);
                    Toast.makeText(this, sucesso, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    botao.setEnabled(true);
                    Toast.makeText(this, "Erro ao gerar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
