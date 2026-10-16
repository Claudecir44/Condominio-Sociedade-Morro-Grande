package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Bloco de anexos dos cadastros: "Tirar foto", "Buscar no celular" (fotos, PDF, Word, Excel...)
 * e a lista do que já está anexado (abrir, baixar, exportar, remover).
 * Criar no onCreate (registra os launchers) passando um LinearLayout vazio do layout.
 * Ao salvar, chamar enviar(): sobe os arquivos novos pro Storage e devolve só links.
 */
public class PainelAnexos {

    /** Tipos aceitos na busca do celular. */
    private static final String[] TIPOS = {
            "image/*", "application/pdf",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
            "text/plain", "text/csv", "text/comma-separated-values", "*/*"
    };

    private final AppCompatActivity activity;
    private final LinearLayout lista;
    private final TextView titulo;
    private final List<String> anexos = new ArrayList<>();
    private LinearLayout botoes;
    private TextView dica;
    private boolean somenteLeitura = false;
    private ActivityResultLauncher<String[]> buscar;
    private CapturaFoto captura;

    public PainelAnexos(AppCompatActivity activity, LinearLayout container) {
        this.activity = activity;
        container.setOrientation(LinearLayout.VERTICAL);
        container.removeAllViews();

        titulo = new TextView(activity);
        titulo.setTextSize(16);
        titulo.setTypeface(null, Typeface.BOLD);
        titulo.setTextColor(Color.parseColor("#1A1A2E"));
        titulo.setPadding(0, ListaAnexos.dp(activity, 12), 0, ListaAnexos.dp(activity, 6));
        container.addView(titulo);

        botoes = new LinearLayout(activity);
        botoes.setOrientation(LinearLayout.HORIZONTAL);
        botoes.addView(botao("Tirar foto", R.drawable.ic_camera_branco, R.color.painelAgua, v -> captura.tirarFoto()), pesoUm(0));
        botoes.addView(botao("Buscar no celular", R.drawable.ic_anexar, R.color.botaoSecundario,
                v -> buscar.launch(TIPOS)), pesoUm(ListaAnexos.dp(activity, 8)));
        container.addView(botoes);

        dica = new TextView(activity);
        dica.setText("Fotos, PDF, Word, Excel e outros arquivos (até 20 MB cada).");
        dica.setTextSize(12);
        dica.setTextColor(Color.parseColor("#6B7280"));
        container.addView(dica);

        lista = new LinearLayout(activity);
        lista.setOrientation(LinearLayout.VERTICAL);
        container.addView(lista);

        captura = new CapturaFoto(activity, uri -> adicionar(uri));
        buscar = activity.registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris == null) return;
            for (Uri uri : uris) {
                try {
                    activity.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) { }
                adicionar(uri);
            }
        });
        atualizar();
    }

    private LinearLayout.LayoutParams pesoUm(int margemEsquerda) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = margemEsquerda;
        return lp;
    }

    private MaterialButton botao(String texto, int icone, int cor, View.OnClickListener clique) {
        MaterialButton b = new MaterialButton(activity);
        b.setText(texto);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setIconResource(icone);
        b.setIconGravity(MaterialButton.ICON_GRAVITY_TOP);
        b.setIconTint(ColorStateList.valueOf(Color.WHITE));
        b.setIconSize(ListaAnexos.dp(activity, 22));
        b.setCornerRadius(ListaAnexos.dp(activity, 14));
        b.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(cor)));
        b.setOnClickListener(clique);
        return b;
    }

    private void adicionar(Uri uri) {
        anexos.add(uri.toString());
        atualizar();
    }

    private void atualizar() {
        titulo.setText(anexos.isEmpty() ? "Anexos" : "Anexos (" + anexos.size() + ")");
        ListaAnexos.mostrar(lista, anexos, somenteLeitura ? null : caminho -> {
            anexos.remove(caminho);
            atualizar();
        });
    }

    /** Só mostra os anexos (abrir/baixar/exportar), sem tirar foto, buscar ou remover. */
    public void somenteLeitura() {
        somenteLeitura = true;
        botoes.setVisibility(View.GONE);
        dica.setVisibility(View.GONE);
        atualizar();
    }

    /** Carrega os anexos já salvos no cadastro (edição). */
    public void definir(List<String> existentes) {
        anexos.clear();
        if (existentes != null) {
            for (String s : existentes) if (s != null && !s.isEmpty()) anexos.add(s);
        }
        atualizar();
    }

    public List<String> todos() {
        return new ArrayList<>(anexos);
    }

    public void limpar() {
        anexos.clear();
        atualizar();
    }

    /** Sobe os anexos novos para {modulo}/... no Storage; devolve a lista final só com links. */
    public void enviar(String modulo, ColecaoFirestore.Callback<List<String>> callback) {
        boolean temNovo = false;
        for (String s : anexos) if (!ArquivosStorage.ehLink(s)) temNovo = true;
        if (temNovo) Toast.makeText(activity, "Enviando anexos…", Toast.LENGTH_SHORT).show();
        ArquivosStorage.enviar(activity, modulo, todos(), new ColecaoFirestore.Callback<List<String>>() {
            @Override
            public void onSucesso(List<String> links) {
                anexos.clear();
                anexos.addAll(links);
                atualizar();
                callback.onSucesso(links);
            }

            @Override
            public void onErro(Exception e) {
                callback.onErro(e);
            }
        });
    }
}
