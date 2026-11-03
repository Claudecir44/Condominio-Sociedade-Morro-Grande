package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Quadro "Ver como morador" do painel do app Admins: os mesmos quadros que o morador vê ao entrar,
 * só para consulta. Abrem as telas do morador; a Leitura d'Água não envia nada (EXTRA_PREVIA).
 */
public class VerComoMoradorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ver_como_morador);

        abrirAoTocar(R.id.buttonListaDespesas, new Intent(this, ListaDespesasActivity.class).putExtra("tipo_usuario", "morador"));
        abrirAoTocar(R.id.buttonAvisosMorador, new Intent(this, ListaAvisosActivity.class).putExtra("tipo_usuario", "morador"));
        abrirAoTocar(R.id.buttonLeituraAgua, new Intent(this, LeituraAguaActivity.class).putExtra(LeituraAguaActivity.EXTRA_PREVIA, true));
        abrirAoTocar(R.id.buttonPagamentos, new Intent(this, PagamentosActivity.class));
        abrirAoTocar(R.id.buttonAssembleiasMorador, new Intent(this, ListaAssembleiasActivity.class).putExtra("tipo_usuario", "morador"));
    }

    private void abrirAoTocar(int botao, Intent tela) {
        findViewById(botao).setOnClickListener(v -> startActivity(tela));
    }
}
