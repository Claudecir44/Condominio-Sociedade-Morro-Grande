package com.cjstudio.condominio_sociedade_morro_grande;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CadastroAvisosActivity extends AppCompatActivity {

    private EditText editDataHora, editAssunto, editDescricao;
    private Button buttonSalvar, buttonVoltarLista;
    private PainelAnexos painelAnexos;

    private String editarId = null; // ID do documento no Firestore (String)

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_avisos);

        db = FirebaseFirestore.getInstance();

        editDataHora = findViewById(R.id.editDataHora);
        editAssunto = findViewById(R.id.editAssunto);
        editDescricao = findViewById(R.id.editDescricao);
        buttonSalvar = findViewById(R.id.buttonSalvar);
        buttonVoltarLista = findViewById(R.id.buttonVoltarLista);
        painelAnexos = new PainelAnexos(this, findViewById(R.id.painelAnexos));

        editDataHora.setOnClickListener(v -> mostrarDateTimePicker());
        buttonSalvar.setOnClickListener(v -> salvarAviso());
        buttonVoltarLista.setOnClickListener(v -> {
            startActivity(new Intent(this, ListaAvisosActivity.class));
            finish();
        });

        // Verifica se é edição
        if (getIntent().hasExtra("avisoJSON")) {
            try {
                JSONObject aviso = new JSONObject(getIntent().getStringExtra("avisoJSON"));
                editarId = aviso.optString("id", null);
                preencherCampos(aviso);
                buttonVoltarLista.setVisibility(Button.GONE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ==================== SALVAR NO FIRESTORE ====================
    private void salvarAviso() {
        String dataHora = editDataHora.getText().toString().trim();
        String assunto = editAssunto.getText().toString().trim();
        String descricao = editDescricao.getText().toString().trim();

        if (dataHora.isEmpty() || assunto.isEmpty() || descricao.isEmpty()) {
            Toast.makeText(this, "Preencha data/hora, assunto e descrição.", Toast.LENGTH_SHORT).show();
            return;
        }

        buttonSalvar.setEnabled(false);
        // Anexos vão pro Firebase Storage; o aviso guarda os links
        painelAnexos.enviar("avisos", new ColecaoFirestore.Callback<List<String>>() {
            @Override
            public void onSucesso(List<String> links) {
                gravarAviso(dataHora, assunto, descricao, links);
            }

            @Override
            public void onErro(Exception e) {
                buttonSalvar.setEnabled(true);
                Toast.makeText(CadastroAvisosActivity.this,
                        "Erro ao enviar anexos: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void gravarAviso(String dataHora, String assunto, String descricao, List<String> anexosFinais) {
        Map<String, Object> avisoMap = new HashMap<>();
        avisoMap.put("datahora", dataHora);
        avisoMap.put("assunto", assunto);
        avisoMap.put("descricao", descricao);
        // Texto JSON, mesmo formato de sempre (a lista e o painel web leem assim)
        avisoMap.put("anexos", new JSONArray(anexosFinais).toString());
        avisoMap.put("timestamp", System.currentTimeMillis());

        if (editarId != null) {
            db.collection("avisos").document(editarId).update(avisoMap)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Aviso atualizado!", Toast.LENGTH_SHORT).show();
                        limparCampos();
                        startActivity(new Intent(this, ListaAvisosActivity.class));
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        buttonSalvar.setEnabled(true);
                        Toast.makeText(this, "Erro ao atualizar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            db.collection("avisos").add(avisoMap)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Aviso salvo!", Toast.LENGTH_SHORT).show();
                        limparCampos();
                    })
                    .addOnFailureListener(e -> {
                        buttonSalvar.setEnabled(true);
                        Toast.makeText(this, "Erro ao salvar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void limparCampos() {
        buttonSalvar.setEnabled(true);
        editDataHora.setText("");
        editAssunto.setText("");
        editDescricao.setText("");
        painelAnexos.limpar();
        editarId = null;
        buttonVoltarLista.setVisibility(View.VISIBLE);
    }

    private void mostrarDateTimePicker() {
        final Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(year, month, dayOfMonth);
            new TimePickerDialog(this, (view1, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                editDataHora.setText(DateFormat.format("dd/MM/yyyy HH:mm", calendar));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void preencherCampos(JSONObject aviso) {
        editDataHora.setText(aviso.optString("datahora", ""));
        editAssunto.setText(aviso.optString("assunto", ""));
        editDescricao.setText(aviso.optString("descricao", ""));
        painelAnexos.definir(ListaAnexos.deValor(aviso.opt("anexos")));
    }
}
