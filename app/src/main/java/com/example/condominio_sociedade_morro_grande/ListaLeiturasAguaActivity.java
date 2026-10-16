package com.cjstudio.condominio_sociedade_morro_grande;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leituras d'água, mais recentes primeiro, um cartão por envio.
 * Admin: todas, com Excluir. Morador (EXTRA_SO_MINHAS): só as dele, sem Excluir.
 */
public class ListaLeiturasAguaActivity extends AppCompatActivity {

    public static final String EXTRA_SO_MINHAS = "so_minhas";

    private boolean soMinhas;
    private LinearLayout lista;
    private TextView textResumo;
    private final SimpleDateFormat formatoData = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_leituras_agua);
        lista = findViewById(R.id.listaLeituras);
        textResumo = findViewById(R.id.textResumoLeituras);
        soMinhas = getIntent().getBooleanExtra(EXTRA_SO_MINHAS, false);
        if (soMinhas) ((TextView) findViewById(R.id.textTituloLeituras)).setText("Sua Leitura");
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregar();
    }

    private void carregar() {
        Query consulta = FirebaseFirestore.getInstance().collection("leituras_agua");
        if (soMinhas) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                textResumo.setText("Sessão expirada. Faça o login de novo.");
                return;
            }
            // Só o filtro (sem orderBy junto, que exigiria índice composto); a ordem é feita aqui
            consulta = consulta.whereEqualTo("moradorUid", user.getUid());
        } else {
            consulta = consulta.orderBy("criadoEm", Query.Direction.DESCENDING);
        }
        consulta.get()
                .addOnSuccessListener(query -> {
                    lista.removeAllViews();
                    int total = query.size();
                    textResumo.setText(total == 0 ? "Nenhuma leitura enviada ainda."
                            : total + (total == 1 ? " leitura enviada" : " leituras enviadas"));
                    List<DocumentSnapshot> docs = new ArrayList<>(query.getDocuments());
                    if (soMinhas) {
                        docs.sort((a, b) -> {
                            Timestamp ta = a.getTimestamp("criadoEm"), tb = b.getTimestamp("criadoEm");
                            if (ta == null || tb == null) return ta == null ? (tb == null ? 0 : 1) : -1;
                            return tb.compareTo(ta);
                        });
                    }
                    for (DocumentSnapshot doc : docs) {
                        lista.addView(criarCartao(doc));
                    }
                })
                .addOnFailureListener(e -> textResumo.setText("Erro ao carregar: " + e.getMessage()));
    }

    private int dp(int valor) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor, getResources().getDisplayMetrics());
    }

    private View criarCartao(DocumentSnapshot doc) {
        LinearLayout cartao = new LinearLayout(this);
        cartao.setOrientation(LinearLayout.VERTICAL);
        cartao.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable fundo = new GradientDrawable();
        fundo.setColor(Color.WHITE);
        fundo.setCornerRadius(dp(16));
        cartao.setBackground(fundo);
        cartao.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(8), dp(4), dp(8));
        cartao.setLayoutParams(lp);

        TextView quadraLote = new TextView(this);
        quadraLote.setText("Quadra " + valor(doc, "quadra") + "  ·  Lote " + valor(doc, "lote"));
        quadraLote.setTextSize(19);
        quadraLote.setTypeface(null, Typeface.BOLD);
        quadraLote.setTextColor(Color.parseColor("#0C4A6E"));
        cartao.addView(quadraLote);

        TextView nomeData = new TextView(this);
        Timestamp quando = doc.getTimestamp("criadoEm");
        String data = quando != null ? formatoData.format(quando.toDate()) : "";
        nomeData.setText(valor(doc, "nome") + (data.isEmpty() ? "" : "  —  " + data));
        nomeData.setTextSize(13);
        nomeData.setTextColor(Color.parseColor("#6B7280"));
        cartao.addView(nomeData);

        TextView leitura = new TextView(this);
        leitura.setText("Leitura: " + valor(doc, "leitura"));
        leitura.setTextSize(22);
        leitura.setTypeface(null, Typeface.BOLD);
        leitura.setTextColor(Color.parseColor("#1A1A2E"));
        leitura.setPadding(0, dp(10), 0, dp(10));
        cartao.addView(leitura);

        String linkFoto = doc.getString("foto");
        if (linkFoto != null && !linkFoto.isEmpty()) {
            ImageView foto = new ImageView(this);
            foto.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)));
            foto.setScaleType(ImageView.ScaleType.CENTER_CROP);
            foto.setContentDescription("Foto do hidrômetro");
            ArquivosStorage.carregarImagem(foto, linkFoto, android.R.drawable.ic_menu_gallery);
            foto.setOnClickListener(v -> ArquivosStorage.abrir(this, linkFoto));
            cartao.addView(foto);
        }
        if (soMinhas) return cartao; // morador só consulta

        Button excluir = new Button(this);
        excluir.setText("Excluir");
        excluir.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.botaoPerigo)));
        LinearLayout.LayoutParams lpBotao = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBotao.topMargin = dp(8);
        excluir.setLayoutParams(lpBotao);
        excluir.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Excluir leitura")
                .setMessage("Excluir a leitura da quadra " + valor(doc, "quadra") + ", lote " + valor(doc, "lote") + "?")
                .setPositiveButton("Excluir", (d, w) -> doc.getReference().delete()
                        .addOnSuccessListener(x -> carregar())
                        .addOnFailureListener(e -> Toast.makeText(this,
                                "Erro ao excluir: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                .setNegativeButton("Cancelar", null)
                .show());
        cartao.addView(excluir);
        return cartao;
    }

    private static String valor(DocumentSnapshot doc, String campo) {
        String v = doc.getString(campo);
        return v != null && !v.isEmpty() ? v : "—";
    }
}
