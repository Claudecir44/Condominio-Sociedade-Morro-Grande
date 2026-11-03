package com.cjstudio.condominio_sociedade_morro_grande;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Configurações (engrenagem no canto superior esquerdo do painel), igual no app Admins e no Moradores
 * e no painel web. Por enquanto: a Política de Privacidade, que abre aqui mesmo, logo abaixo do botão.
 * O texto vem do painel-web/privacidade.html, copiado para o app no build.
 */
public class ConfiguracoesActivity extends AppCompatActivity {

    // Cópia do painel-web/privacidade.html (o mesmo texto do site e da Play Store), abre mesmo sem internet
    public static final String URL_POLITICA_PRIVACIDADE = "file:///android_asset/privacidade.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuracoes);
        WebView web = findViewById(R.id.webPolitica);
        web.setWebViewClient(new WebViewClient()); // links continuam dentro do app
        findViewById(R.id.btnPoliticaPrivacidade).setOnClickListener(v -> {
            boolean abrir = web.getVisibility() != View.VISIBLE;
            if (abrir && web.getUrl() == null) web.loadUrl(URL_POLITICA_PRIVACIDADE);
            web.setVisibility(abrir ? View.VISIBLE : View.GONE);
        });
        ((TextView) findViewById(R.id.textVersao)).setText("Versão " + BuildConfig.VERSION_NAME);
    }
}
