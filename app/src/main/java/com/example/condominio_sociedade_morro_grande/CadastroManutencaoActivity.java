package com.cjstudio.condominio_sociedade_morro_grande;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;


import com.cjstudio.condominio_sociedade_morro_grande.data.ManutencaoDAO;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Manutencao;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CadastroManutencaoActivity extends AppCompatActivity {

    private EditText etTipo, etDataHora, etLocal, etServico, etResponsavel, etValor, etNotas;
    private Button btnSalvar, btnLista;
    private PainelAnexos painelAnexos;
    private ManutencaoDAO manutencaoDAO;
    private String idEdicao = null;

    private final SimpleDateFormat formatoDataHora = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_manutencao);

        etTipo = findViewById(R.id.editTipoManutencao);
        etDataHora = findViewById(R.id.editDataHora);
        etLocal = findViewById(R.id.editLocal);
        etServico = findViewById(R.id.editServico);
        etResponsavel = findViewById(R.id.editResponsavel);
        etValor = findViewById(R.id.editValor);
        etNotas = findViewById(R.id.editNotas);

        painelAnexos = new PainelAnexos(this, findViewById(R.id.painelAnexos));
        btnSalvar = findViewById(R.id.buttonSalvarManutencao);
        btnLista = findViewById(R.id.buttonListaManutencao);

        manutencaoDAO = new ManutencaoDAO();

        etDataHora.setOnClickListener(v -> mostrarDateTimePicker());

        btnSalvar.setOnClickListener(v -> salvarManutencao());

        btnLista.setOnClickListener(v -> {
            Intent intent = new Intent(CadastroManutencaoActivity.this, ListaManutencaoActivity.class);
            startActivity(intent);
        });

        if (getIntent().hasExtra("manutencaoJSON")) {
            carregarManutencaoParaEdicao(manutencaoDAO.deJson(getIntent().getStringExtra("manutencaoJSON")));
        }
    }

    private void mostrarDateTimePicker() {
        final Calendar c = Calendar.getInstance();
        int ano = c.get(Calendar.YEAR);
        int mes = c.get(Calendar.MONTH);
        int dia = c.get(Calendar.DAY_OF_MONTH);
        int hora = c.get(Calendar.HOUR_OF_DAY);
        int minuto = c.get(Calendar.MINUTE);

        DatePickerDialog dpd = new DatePickerDialog(this, (view, y, m, d) -> {
            TimePickerDialog tpd = new TimePickerDialog(this, (timeView, h, min) -> {
                c.set(y, m, d, h, min);
                etDataHora.setText(formatoDataHora.format(c.getTime()));
            }, hora, minuto, true);
            tpd.show();
        }, ano, mes, dia);
        dpd.show();
    }

    private void salvarManutencao() {
        String tipo = etTipo.getText().toString().trim();
        String dataHora = etDataHora.getText().toString().trim();
        String local = etLocal.getText().toString().trim();
        String servico = etServico.getText().toString().trim();
        String responsavel = etResponsavel.getText().toString().trim();
        String valor = etValor.getText().toString().trim();
        String notas = etNotas.getText().toString().trim();

        if (tipo.isEmpty() || dataHora.isEmpty()) {
            Toast.makeText(this, "Tipo e Data/Hora são obrigatórios!", Toast.LENGTH_SHORT).show();
            return;
        }

        Manutencao m = new Manutencao();
        m.setId(idEdicao);
        m.setTipo(tipo);
        m.setDataHora(dataHora);
        m.setLocal(local);
        m.setServico(servico);
        m.setResponsavel(responsavel);
        m.setValor(valor);
        m.setNotas(notas);
        btnSalvar.setEnabled(false);
        // Anexos vão pro Firebase Storage; a manutenção guarda os links
        painelAnexos.enviar("manutencoes", new ColecaoFirestore.Callback<List<String>>() {
            @Override
            public void onSucesso(List<String> links) {
                m.setAnexos(links);
                gravarManutencao(m);
            }

            @Override
            public void onErro(Exception e) {
                btnSalvar.setEnabled(true);
                Toast.makeText(CadastroManutencaoActivity.this,
                        "Erro ao enviar anexos: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void gravarManutencao(Manutencao m) {
        manutencaoDAO.salvarManutencao(m, new ColecaoFirestore.Callback<Void>() {
            @Override
            public void onSucesso(Void resultado) {
                btnSalvar.setEnabled(true);
                Toast.makeText(CadastroManutencaoActivity.this, "Manutenção salva com sucesso!", Toast.LENGTH_SHORT).show();
                limparCampos();
            }

            @Override
            public void onErro(Exception e) {
                btnSalvar.setEnabled(true);
                Toast.makeText(CadastroManutencaoActivity.this,
                        "Erro ao salvar manutenção: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void limparCampos() {
        etTipo.setText("");
        etDataHora.setText("");
        etLocal.setText("");
        etServico.setText("");
        etResponsavel.setText("");
        etValor.setText("");
        etNotas.setText("");
        painelAnexos.limpar();
        idEdicao = null;
    }

    private void carregarManutencaoParaEdicao(Manutencao m) {
        if (m != null) {
            idEdicao = m.getId();
            etTipo.setText(m.getTipo());
            etDataHora.setText(m.getDataHora());
            etLocal.setText(m.getLocal());
            etServico.setText(m.getServico());
            etResponsavel.setText(m.getResponsavel());
            etValor.setText(m.getValor());
            etNotas.setText(m.getNotas());
            painelAnexos.definir(m.getAnexos());
        }
    }

}