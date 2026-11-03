package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** Quadro "Cadastro" do painel do app Admins: reúne os cadastros do condomínio. */
public class CadastrosActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastros);

        abrirAoTocar(R.id.buttonAdministradores, AdministradoresActivity.class);
        abrirMesesAoTocar(R.id.buttonAssembleias, MesesCadastroActivity.Modulo.ASSEMBLEIAS);
        abrirMesesAoTocar(R.id.buttonDespesas, MesesCadastroActivity.Modulo.DESPESAS);
        abrirMesesAoTocar(R.id.buttonAvisos, MesesCadastroActivity.Modulo.AVISOS);
        abrirAoTocar(R.id.buttonCadastroMoradores, CadastroMoradorActivity.class);
        abrirMesesAoTocar(R.id.buttonRegistroOcorrencias, MesesCadastroActivity.Modulo.OCORRENCIAS);
        abrirMesesAoTocar(R.id.buttonManutencao, MesesCadastroActivity.Modulo.MANUTENCAO);
        abrirAoTocar(R.id.buttonFuncionarios, FuncionariosActivity.class);
    }

    private void abrirMesesAoTocar(int botao, MesesCadastroActivity.Modulo modulo) {
        findViewById(botao).setOnClickListener(v -> startActivity(new Intent(this, MesesCadastroActivity.class)
                .putExtra(MesesCadastroActivity.EXTRA_MODULO, modulo.name())));
    }

    private void abrirAoTocar(int botao, Class<?> tela) {
        findViewById(botao).setOnClickListener(v -> startActivity(new Intent(this, tela)));
    }
}
