package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.List;

/**
 * Mostra os boletos (PDF) como linhas: nome, data/hora da postagem e o vencimento (dia 15 do mês
 * vigente) em verde, ou "Boleto vencido" em vermelho quando o admin marcou que não foi pago.
 * Tocar abre o menu do arquivo: abrir, baixar, exportar.
 * Com AcoesAdmin: checkbox "não pago" (liberada depois do dia 15) e excluir.
 */
public final class ListaBoletos {

    private ListaBoletos() { }

    public interface AcoesAdmin {
        void marcarNaoPago(Boleto boleto, boolean naoPago);
        void excluir(Boleto boleto);
    }

    private static final int VERDE = Color.parseColor("#16A34A");
    private static final int VERMELHO = Color.parseColor("#DC2626");
    private static final int CINZA = Color.parseColor("#6B7280");

    public static void mostrar(LinearLayout container, List<Boleto> boletos, AcoesAdmin acoes) {
        Context context = container.getContext();
        container.removeAllViews();
        if (boletos == null || boletos.isEmpty()) {
            TextView vazio = new TextView(context);
            vazio.setText("Nenhum boleto postado.");
            vazio.setTextSize(13);
            vazio.setTextColor(CINZA);
            vazio.setPadding(0, ListaAnexos.dp(context, 4), 0, 0);
            container.addView(vazio);
            return;
        }
        for (Boleto boleto : boletos) {
            container.addView(linha(context, boleto, acoes));
        }
    }

    private static View linha(Context context, Boleto boleto, AcoesAdmin acoes) {
        LinearLayout caixa = new LinearLayout(context);
        caixa.setOrientation(LinearLayout.VERTICAL);
        caixa.setPadding(dp(context, 10), dp(context, 8), dp(context, 6), dp(context, 8));
        GradientDrawable fundo = new GradientDrawable();
        fundo.setColor(Color.parseColor(boleto.isNaoPago() ? "#FEF2F2" : "#F3F4F6"));
        fundo.setCornerRadius(dp(context, 10));
        if (boleto.isNaoPago()) fundo.setStroke(dp(context, 1), VERMELHO);
        caixa.setBackground(fundo);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, 6);
        caixa.setLayoutParams(lp);

        // Arquivo: ícone de PDF, nome e data/hora da postagem
        LinearLayout topo = new LinearLayout(context);
        topo.setOrientation(LinearLayout.HORIZONTAL);
        topo.setGravity(Gravity.CENTER_VERTICAL);

        ImageView pdf = new ImageView(context);
        pdf.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));
        pdf.setImageResource(R.drawable.ic_arquivo_pdf);
        topo.addView(pdf);

        LinearLayout textos = new LinearLayout(context);
        textos.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpTextos = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lpTextos.leftMargin = dp(context, 8);
        textos.setLayoutParams(lpTextos);

        TextView nome = new TextView(context);
        nome.setText(boleto.getNomeArquivo() != null ? boleto.getNomeArquivo() : "boleto.pdf");
        nome.setTextSize(14);
        nome.setTypeface(null, Typeface.BOLD);
        nome.setTextColor(Color.parseColor("#1F2937"));
        nome.setMaxLines(2);
        nome.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        textos.addView(nome);

        TextView postado = new TextView(context);
        postado.setText("Postado em " + boleto.postadoEmTexto());
        postado.setTextSize(12);
        postado.setTextColor(CINZA);
        textos.addView(postado);
        topo.addView(textos);

        if (acoes != null) {
            ImageButton excluir = new ImageButton(context);
            excluir.setImageResource(android.R.drawable.ic_menu_delete);
            excluir.setBackgroundColor(Color.TRANSPARENT);
            excluir.setContentDescription("Excluir boleto");
            excluir.setOnClickListener(v -> new AlertDialog.Builder(context)
                    .setTitle("Excluir boleto")
                    .setMessage("Excluir o boleto postado em " + boleto.postadoEmTexto() + "?")
                    .setPositiveButton("Excluir", (d, w) -> acoes.excluir(boleto))
                    .setNegativeButton("Cancelar", null)
                    .show());
            topo.addView(excluir);
        }
        caixa.addView(topo);

        // Vencimento (verde) ou "Boleto vencido" (vermelho)
        LinearLayout status = new LinearLayout(context);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(0, dp(context, 6), 0, 0);

        ImageView icone = new ImageView(context);
        icone.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));
        icone.setImageResource(boleto.isNaoPago() ? R.drawable.ic_boleto_vencido : R.drawable.ic_boleto_em_dia);
        status.addView(icone);

        TextView textoStatus = new TextView(context);
        LinearLayout.LayoutParams lpStatus = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lpStatus.leftMargin = dp(context, 6);
        textoStatus.setLayoutParams(lpStatus);
        textoStatus.setTextSize(13);
        if (boleto.isNaoPago()) {
            textoStatus.setText("Boleto vencido (venceu em " + boleto.vencimentoTexto() + ")");
            textoStatus.setTextColor(VERMELHO);
            textoStatus.setTypeface(null, Typeface.BOLD);
        } else {
            textoStatus.setText("Vence em " + boleto.vencimentoTexto() + " (sempre dia 15 do mês vigente)");
            textoStatus.setTextColor(VERDE);
        }
        status.addView(textoStatus);
        caixa.addView(status);

        if (acoes != null) {
            CheckBox naoPago = new CheckBox(context);
            naoPago.setChecked(boleto.isNaoPago());
            naoPago.setTextSize(13);
            naoPago.setButtonTintList(ColorStateList.valueOf(VERMELHO));
            boolean liberada = boleto.passouDoVencimento() || boleto.isNaoPago();
            naoPago.setEnabled(liberada);
            naoPago.setText(liberada
                    ? "Não pago após o dia 15"
                    : "Não pago (liberado após " + boleto.vencimentoTexto() + ")");
            naoPago.setOnCheckedChangeListener((b, marcado) -> acoes.marcarNaoPago(boleto, marcado));
            caixa.addView(naoPago);
        }

        caixa.setOnClickListener(v -> ListaAnexos.opcoes(context, boleto.getArquivo()));
        return caixa;
    }

    private static int dp(Context context, int valor) {
        return ListaAnexos.dp(context, valor);
    }
}
