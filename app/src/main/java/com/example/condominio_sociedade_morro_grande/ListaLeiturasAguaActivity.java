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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Leituras d'água, mais recentes primeiro, um cartão por envio.
 * Admin: todas, com Excluir, leitura anterior e gasto do mês (por quadra e lote).
 * Morador (EXTRA_SO_MINHAS): só as dele, sem Excluir.
 */
public class ListaLeiturasAguaActivity extends AppCompatActivity {

    public static final String EXTRA_SO_MINHAS = "so_minhas";

    private boolean soMinhas;
    private LinearLayout lista;
    private TextView textResumo;
    // Leitura anterior de cada envio, pelo id do documento
    private final Map<String, String> anteriores = new HashMap<>();
    private final SimpleDateFormat formatoData = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_leituras_agua);
        lista = findViewById(R.id.listaLeituras);
        textResumo = findViewById(R.id.textResumoLeituras);
        soMinhas = getIntent().getBooleanExtra(EXTRA_SO_MINHAS, false);
        if (soMinhas) ((TextView) findViewById(R.id.textTituloLeituras)).setText("Sua Leitura");
        Query consulta = consulta();
        if (consulta != null) TempoReal.observar(this, this::carregar, consulta);
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregar();
    }

    /** null quando a sessão do morador expirou. */
    private Query consulta() {
        Query consulta = FirebaseFirestore.getInstance().collection("leituras_agua");
        if (!soMinhas) return consulta.orderBy("criadoEm", Query.Direction.DESCENDING);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        // Só o filtro (sem orderBy junto, que exigiria índice composto); a ordem é feita aqui
        return user != null ? consulta.whereEqualTo("moradorUid", user.getUid()) : null;
    }

    private void carregar() {
        Query consulta = consulta();
        if (consulta == null) {
            textResumo.setText("Sessão expirada. Faça o login de novo.");
            return;
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
                    guardarAnteriores(docs);
                    for (DocumentSnapshot doc : docs) {
                        lista.addView(criarCartao(doc));
                    }
                })
                .addOnFailureListener(e -> textResumo.setText("Erro ao carregar: " + e.getMessage()));
    }

    /** docs vem do mais novo para o mais antigo: a anterior é a do envio de antes, no mesmo lote. */
    private void guardarAnteriores(List<DocumentSnapshot> docs) {
        anteriores.clear();
        Map<String, String> ultimaDoLote = new HashMap<>();
        for (int i = docs.size() - 1; i >= 0; i--) {
            DocumentSnapshot doc = docs.get(i);
            String lote = valor(doc, "quadra").toUpperCase(Locale.ROOT) + "|" + valor(doc, "lote");
            if (ultimaDoLote.containsKey(lote)) anteriores.put(doc.getId(), ultimaDoLote.get(lote));
            ultimaDoLote.put(lote, doc.getString("leitura"));
        }
    }

    /** Admin: "Leitura anterior" pequena em cima da atual e, ao lado, "Gastos do mês". */
    private void mostrarLeituraComGasto(LinearLayout cartao, DocumentSnapshot doc) {
        String anterior = anteriores.get(doc.getId());
        TextView textoAnterior = new TextView(this);
        textoAnterior.setText("Leitura anterior: " + (anterior != null ? anterior : "—"));
        textoAnterior.setTextSize(13);
        textoAnterior.setTextColor(Color.parseColor("#6B7280"));
        textoAnterior.setPadding(0, dp(10), 0, dp(2));
        cartao.addView(textoAnterior);

        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setGravity(android.view.Gravity.CENTER_VERTICAL);
        linha.setPadding(0, 0, 0, dp(10));

        TextView atual = new TextView(this);
        atual.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        atual.setText("Leitura atual: " + valor(doc, "leitura"));
        atual.setTextSize(20);
        atual.setTypeface(null, Typeface.BOLD);
        atual.setTextColor(Color.parseColor("#1A1A2E"));
        linha.addView(atual);

        LinearLayout gasto = new LinearLayout(this);
        gasto.setOrientation(LinearLayout.VERTICAL);
        gasto.setGravity(android.view.Gravity.END);
        gasto.setPadding(dp(10), 0, 0, 0);
        TextView tituloGasto = new TextView(this);
        tituloGasto.setText("Gastos do mês");
        tituloGasto.setTextSize(13);
        tituloGasto.setTextColor(Color.parseColor("#6B7280"));
        gasto.addView(tituloGasto);
        TextView valorGasto = new TextView(this);
        valorGasto.setText(ConsumoAgua.gasto(doc.getString("leitura"), anterior));
        valorGasto.setTextSize(20);
        valorGasto.setTypeface(null, Typeface.BOLD);
        valorGasto.setTextColor(Color.parseColor("#0C4A6E"));
        gasto.addView(valorGasto);
        linha.addView(gasto);
        cartao.addView(linha);
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

        if (soMinhas) {
            TextView leitura = new TextView(this);
            leitura.setText("Leitura: " + valor(doc, "leitura"));
            leitura.setTextSize(22);
            leitura.setTypeface(null, Typeface.BOLD);
            leitura.setTextColor(Color.parseColor("#1A1A2E"));
            leitura.setPadding(0, dp(10), 0, dp(10));
            cartao.addView(leitura);
        } else {
            mostrarLeituraComGasto(cartao, doc);
        }

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
