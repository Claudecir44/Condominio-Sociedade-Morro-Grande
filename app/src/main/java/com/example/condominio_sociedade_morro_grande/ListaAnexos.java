package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Mostra anexos (fotos, PDF, Word, Excel...) como linhas com miniatura/ícone e nome.
 * Tocar abre o menu: Abrir, Baixar no celular, Exportar/enviar.
 * Usado nas telas de lista (só leitura) e pelo PainelAnexos dos cadastros (com remover).
 */
public final class ListaAnexos {

    private ListaAnexos() { }

    public interface AoRemover { void remover(String caminho); }

    /** Preenche o container com os anexos. Lista vazia esconde o container. */
    public static void mostrar(LinearLayout container, List<String> anexos) {
        mostrar(container, anexos, null);
    }

    public static void mostrar(LinearLayout container, List<String> anexos, AoRemover aoRemover) {
        container.removeAllViews();
        List<String> lista = anexos != null ? anexos : new ArrayList<>();
        container.setVisibility(lista.isEmpty() && aoRemover == null ? View.GONE : View.VISIBLE);
        if (!lista.isEmpty() && aoRemover == null) {
            TextView titulo = new TextView(container.getContext());
            titulo.setText("Toque no arquivo para abrir, baixar ou exportar");
            titulo.setTextSize(13);
            titulo.setTextColor(Color.parseColor("#4B5563"));
            titulo.setPadding(0, dp(container.getContext(), 6), 0, dp(container.getContext(), 2));
            container.addView(titulo);
        }
        for (String caminho : lista) {
            container.addView(linha(container.getContext(), caminho, aoRemover));
        }
    }

    /** Lê anexos gravados como lista (List/JSONArray) ou como texto JSON ("[\"https://...\"]"). */
    public static List<String> deValor(Object valor) {
        List<String> lista = new ArrayList<>();
        if (valor == null) return lista;
        try {
            JSONArray array;
            if (valor instanceof JSONArray) array = (JSONArray) valor;
            else if (valor instanceof List) array = new JSONArray((List<?>) valor);
            else {
                String texto = valor.toString().trim();
                if (texto.isEmpty() || "null".equals(texto)) return lista;
                if (!texto.startsWith("[")) { lista.add(texto); return lista; }
                array = new JSONArray(texto);
            }
            for (int i = 0; i < array.length(); i++) {
                String s = array.optString(i, "");
                if (!s.isEmpty() && !"null".equals(s)) lista.add(s);
            }
        } catch (Exception ignored) { }
        return lista;
    }

    private static View linha(Context context, String caminho, AoRemover aoRemover) {
        LinearLayout linha = new LinearLayout(context);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        linha.setPadding(dp(context, 8), dp(context, 6), dp(context, 4), dp(context, 6));
        GradientDrawable fundo = new GradientDrawable();
        fundo.setColor(Color.parseColor("#F3F4F6"));
        fundo.setCornerRadius(dp(context, 10));
        linha.setBackground(fundo);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, 6);
        linha.setLayoutParams(lp);

        ImageView icone = new ImageView(context);
        icone.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));
        icone.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int iconeTipo = ArquivosStorage.iconeDoTipo(context, caminho);
        if (ArquivosStorage.ehImagem(context, caminho)) {
            ArquivosStorage.carregarImagem(icone, caminho, iconeTipo);
        } else {
            icone.setImageResource(iconeTipo);
        }
        linha.addView(icone);

        TextView nome = new TextView(context);
        LinearLayout.LayoutParams lpNome = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lpNome.leftMargin = dp(context, 10);
        nome.setLayoutParams(lpNome);
        nome.setText(ArquivosStorage.nomeDoAnexo(context, caminho)
                + (ArquivosStorage.ehLink(caminho) ? "" : "\n(novo — será enviado ao salvar)"));
        nome.setTextSize(14);
        nome.setTextColor(Color.parseColor("#1F2937"));
        nome.setTypeface(null, Typeface.BOLD);
        nome.setMaxLines(3);
        nome.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        linha.addView(nome);

        if (aoRemover != null) {
            ImageButton remover = new ImageButton(context);
            remover.setImageResource(android.R.drawable.ic_menu_delete);
            remover.setBackgroundColor(Color.TRANSPARENT);
            remover.setContentDescription("Remover anexo");
            remover.setOnClickListener(v -> new AlertDialog.Builder(context)
                    .setTitle("Remover anexo")
                    .setMessage("Remover \"" + ArquivosStorage.nomeDoAnexo(context, caminho) + "\" deste cadastro?")
                    .setPositiveButton("Remover", (d, w) -> aoRemover.remover(caminho))
                    .setNegativeButton("Cancelar", null)
                    .show());
            linha.addView(remover);
        }

        linha.setOnClickListener(v -> opcoes(context, caminho));
        return linha;
    }

    /** Menu do anexo: abrir, baixar, exportar. */
    public static void opcoes(Context context, String caminho) {
        String[] itens = {"Abrir", "Baixar no celular", "Exportar / enviar (WhatsApp, e-mail…)"};
        new AlertDialog.Builder(context)
                .setTitle(ArquivosStorage.nomeDoAnexo(context, caminho))
                .setItems(itens, (d, qual) -> {
                    if (qual == 0) ArquivosStorage.abrirNoApp(context, caminho);
                    else if (qual == 1) ArquivosStorage.baixarNoCelular(context, caminho);
                    else ArquivosStorage.compartilhar(context, caminho);
                })
                .show();
    }

    static int dp(Context context, int valor) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor,
                context.getResources().getDisplayMetrics());
    }
}
