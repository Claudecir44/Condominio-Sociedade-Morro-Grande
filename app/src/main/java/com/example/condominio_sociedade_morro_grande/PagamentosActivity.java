package com.cjstudio.condominio_sociedade_morro_grande;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Tela "Pagamentos" do morador: boletos em PDF postados pelos admins, só leitura. */
public class PagamentosActivity extends AppCompatActivity {

    private LinearLayout layoutBoletos;
    private LinearLayout layoutAvisoVencido;
    private final BoletosRepository repository = new BoletosRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pagamentos);

        layoutBoletos = findViewById(R.id.layoutBoletos);
        layoutAvisoVencido = findViewById(R.id.layoutAvisoVencido);
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario != null) {
            TempoReal.observar(this, this::carregar, com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("boletos").whereEqualTo("moradorUid", usuario.getUid()));
        }

        Calendar hoje = Calendar.getInstance();
        hoje.set(Calendar.DAY_OF_MONTH, Boleto.DIA_VENCIMENTO);
        String vencimentoDoMes = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR")).format(hoje.getTime());
        ((TextView) findViewById(R.id.tvInfoVencimento)).setText(
                "O boleto vence sempre no dia 15 do mês vigente.\nVencimento deste mês: " + vencimentoDoMes);
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregar();
    }

    private void carregar() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        repository.listarDoMorador(user.getUid(), new ColecaoFirestore.Callback<List<Boleto>>() {
            @Override
            public void onSucesso(List<Boleto> boletos) {
                boolean algumVencido = false;
                for (Boleto b : boletos) if (b.isNaoPago()) algumVencido = true;
                layoutAvisoVencido.setVisibility(algumVencido ? View.VISIBLE : View.GONE);
                ListaBoletos.mostrar(layoutBoletos, boletos);
            }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(PagamentosActivity.this,
                        "Erro ao carregar boletos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
