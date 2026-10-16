package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class ListasActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listas);

        Button btnListaMoradores = findViewById(R.id.btnListaMoradores);
        Button btnListaAssembleias = findViewById(R.id.btnListaAssembleias);
        Button btnListaDespesas = findViewById(R.id.btnListaDespesas);
        Button btnListaAvisos = findViewById(R.id.btnListaAvisos);

        // Dados pessoais dos moradores (CPF, telefone) só para o administrador
        if (!"admin".equals(BuildConfig.PERFIL)) {
            btnListaMoradores.setVisibility(View.GONE);
        }

        btnListaMoradores.setOnClickListener(v -> startActivity(new Intent(this, ListaMoradoresActivity.class)));
        btnListaAssembleias.setOnClickListener(v -> startActivity(new Intent(this, ListaAssembleiasActivity.class)));
        btnListaDespesas.setOnClickListener(v -> startActivity(new Intent(this, ListaDespesasActivity.class)));
        btnListaAvisos.setOnClickListener(v -> startActivity(new Intent(this, ListaAvisosActivity.class)));
    }
}