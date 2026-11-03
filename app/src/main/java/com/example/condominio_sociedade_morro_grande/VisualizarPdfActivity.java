package com.cjstudio.condominio_sociedade_morro_grande;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;

import java.io.File;

/**
 * PDF aberto dentro do app, só para ver: sem baixar, salvar, exportar nem abrir em outro aplicativo.
 * Usado pelo morador na prestação de contas, nos avisos e nas assembleias.
 */
public class VisualizarPdfActivity extends AppCompatActivity {

    public static final String EXTRA_CAMINHO = "caminho";

    private LinearLayout paginas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String caminho = getIntent().getStringExtra(EXTRA_CAMINHO);
        ScrollView rolagem = new ScrollView(this);
        rolagem.setBackgroundColor(Color.parseColor("#E5E7EB"));
        paginas = new LinearLayout(this);
        paginas.setOrientation(LinearLayout.VERTICAL);
        int margem = ListaAnexos.dp(this, 8);
        paginas.setPadding(margem, margem, margem, margem);
        rolagem.addView(paginas);
        setContentView(rolagem);
        setTitle(ArquivosStorage.nomeDoAnexo(this, caminho));

        TextView carregando = new TextView(this);
        carregando.setText("Carregando…");
        carregando.setPadding(margem, margem, margem, margem);
        paginas.addView(carregando);

        ArquivosStorage.baixarParaCache(this, caminho, this::mostrar);
    }

    private void mostrar(File arquivo) {
        if (isFinishing()) return;
        paginas.removeAllViews();
        int largura = getResources().getDisplayMetrics().widthPixels - 2 * ListaAnexos.dp(this, 8);
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer pdf = new PdfRenderer(fd)) {
            for (int i = 0; i < pdf.getPageCount(); i++) {
                try (PdfRenderer.Page pagina = pdf.openPage(i)) {
                    int altura = (int) ((long) largura * pagina.getHeight() / pagina.getWidth());
                    Bitmap imagem = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888);
                    imagem.eraseColor(Color.WHITE);
                    pagina.render(imagem, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    ImageView view = new ImageView(this);
                    view.setImageBitmap(imagem);
                    view.setAdjustViewBounds(true);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = ListaAnexos.dp(this, 8);
                    paginas.addView(view, lp);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir o PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        } finally {
            // A cópia temporária não fica no celular
            arquivo.delete();
        }
    }
}
