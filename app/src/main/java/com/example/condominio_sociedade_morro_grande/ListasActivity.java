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
        boolean admin = "admin".equals(BuildConfig.PERFIL);
        if (!admin) {
            btnListaMoradores.setVisibility(View.GONE);
        }

        btnListaMoradores.setOnClickListener(v -> startActivity(new Intent(this, ListaMoradoresActivity.class)
                .putExtra("somenteLeitura", !admin)));
        btnListaAssembleias.setOnClickListener(v -> verMeses(MesesCadastroActivity.Modulo.ASSEMBLEIAS));
        btnListaDespesas.setOnClickListener(v -> verMeses(MesesCadastroActivity.Modulo.DESPESAS));
        btnListaAvisos.setOnClickListener(v -> verMeses(MesesCadastroActivity.Modulo.AVISOS));
    }

    private void verMeses(MesesCadastroActivity.Modulo modulo) {
        startActivity(new Intent(this, MesesCadastroActivity.class)
                .putExtra(MesesCadastroActivity.EXTRA_MODULO, modulo.name())
                .putExtra(MesesCadastroActivity.EXTRA_SO_VER, true));
    }
}