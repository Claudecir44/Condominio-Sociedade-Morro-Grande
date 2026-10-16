package com.cjstudio.condominio_sociedade_morro_grande;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cjstudio.condominio_sociedade_morro_grande.data.OcorrenciaDAO;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class RegistroOcorrenciasActivity extends AppCompatActivity {

    private EditText editTipo, editDescricao, editDataHora;
    private Button buttonSalvar, buttonListaOcorrencias;
    private PainelAnexos painelAnexos;
    private LinearLayout layoutEnvolvidos;


    private OcorrenciaDAO ocorrenciaDAO;
    private String editarId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro_ocorrencias);

        editTipo = findViewById(R.id.editTipo);
        editDescricao = findViewById(R.id.editDescricao);
        editDataHora = findViewById(R.id.editDataHora);
        buttonSalvar = findViewById(R.id.buttonSalvar);
        buttonListaOcorrencias = findViewById(R.id.buttonListaOcorrencias);
        painelAnexos = new PainelAnexos(this, findViewById(R.id.painelAnexos));
        layoutEnvolvidos = findViewById(R.id.layoutEnvolvidos);

        ocorrenciaDAO = new OcorrenciaDAO();

        editDataHora.setOnClickListener(v -> mostrarDateTimePicker());

        buttonSalvar.setOnClickListener(v -> salvarOcorrencia());

        buttonListaOcorrencias.setOnClickListener(v -> {
            Intent intent = new Intent(RegistroOcorrenciasActivity.this, ListaOcorrenciasActivity.class);
            startActivity(intent);
        });

        if (getIntent().hasExtra("ocorrenciaJSON")) {
            try {
                JSONObject ocorrencia = new JSONObject(getIntent().getStringExtra("ocorrenciaJSON"));
                editarId = ocorrencia.optString("id", null);
                preencherCamposParaEdicao(ocorrencia);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void salvarOcorrencia() {
        String tipo = editTipo.getText().toString().trim();
        String descricao = editDescricao.getText().toString().trim();
        String dataHora = editDataHora.getText().toString().trim();

        if (tipo.isEmpty() || descricao.isEmpty() || dataHora.isEmpty()) {
            Toast.makeText(this, "Preencha tipo, descrição e data/hora.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject ocorrencia = new JSONObject();
            ocorrencia.put("tipo", tipo);
            ocorrencia.put("descricao", descricao);
            ocorrencia.put("datahora", dataHora);

            JSONArray jsonEnvolvidos = new JSONArray();
            for (int i = 0; i < layoutEnvolvidos.getChildCount(); i++) {
                if (layoutEnvolvidos.getChildAt(i) instanceof EditText) {
                    String nome = ((EditText) layoutEnvolvidos.getChildAt(i)).getText().toString().trim();
                    if (!nome.isEmpty()) {
                        jsonEnvolvidos.put(nome);
                    }
                }
            }
            ocorrencia.put("envolvidos", jsonEnvolvidos.toString());

            buttonSalvar.setEnabled(false);
            // Anexos vão pro Firebase Storage; o registro guarda os links
            painelAnexos.enviar("ocorrencias", new ColecaoFirestore.Callback<List<String>>() {
                @Override
                public void onSucesso(List<String> links) {
                    try {
                        ocorrencia.put("anexos", new JSONArray(links).toString());
                    } catch (Exception e) {
                        aoFalhar(e);
                        return;
                    }
                    gravarOcorrencia(ocorrencia);
                }

                @Override
                public void onErro(Exception e) {
                    aoFalhar(new Exception("não foi possível enviar os anexos (" + e.getMessage() + ")"));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao salvar ocorrência.", Toast.LENGTH_SHORT).show();
        }
    }

    private void gravarOcorrencia(JSONObject ocorrencia) {
        {
            if (editarId != null) {
                ocorrenciaDAO.atualizarOcorrencia(editarId, ocorrencia, new ColecaoFirestore.Callback<Void>() {
                    @Override
                    public void onSucesso(Void resultado) {
                        aoSalvar();
                    }

                    @Override
                    public void onErro(Exception e) {
                        aoFalhar(e);
                    }
                });
            } else {
                ocorrenciaDAO.inserirOcorrencia(ocorrencia, new ColecaoFirestore.Callback<String>() {
                    @Override
                    public void onSucesso(String id) {
                        aoSalvar();
                    }

                    @Override
                    public void onErro(Exception e) {
                        aoFalhar(e);
                    }
                });
            }
        }
    }

    private void aoSalvar() {
        buttonSalvar.setEnabled(true);
        Toast.makeText(this, "Ocorrência salva com sucesso!", Toast.LENGTH_SHORT).show();
        limparCampos();
        editarId = null;
    }

    private void aoFalhar(Exception e) {
        buttonSalvar.setEnabled(true);
        Toast.makeText(this, "Erro ao salvar ocorrência: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void limparCampos() {
        editTipo.setText("");
        editDescricao.setText("");
        editDataHora.setText("");
        painelAnexos.limpar();
        for (int i = 0; i < layoutEnvolvidos.getChildCount(); i++) {
            if (layoutEnvolvidos.getChildAt(i) instanceof EditText) {
                ((EditText) layoutEnvolvidos.getChildAt(i)).setText("");
            }
        }
    }

    private void mostrarDateTimePicker() {
        final Calendar calendar = Calendar.getInstance();

        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(year, month, dayOfMonth);
            new TimePickerDialog(this, (view1, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                String dataHoraFormatada = DateFormat.format("dd/MM/yyyy HH:mm", calendar).toString();
                editDataHora.setText(dataHoraFormatada);
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void preencherCamposParaEdicao(JSONObject ocorrencia) {
        try {
            editTipo.setText(ocorrencia.optString("tipo", ""));
            editDescricao.setText(ocorrencia.optString("descricao", ""));
            editDataHora.setText(ocorrencia.optString("datahora", ""));

            painelAnexos.definir(ListaAnexos.deValor(ocorrencia.opt("anexos")));

            JSONArray jsonEnvolvidos = new JSONArray(ocorrencia.optString("envolvidos", "[]"));
            for (int i = 0; i < layoutEnvolvidos.getChildCount() && i < jsonEnvolvidos.length(); i++) {
                if (layoutEnvolvidos.getChildAt(i) instanceof EditText) {
                    ((EditText) layoutEnvolvidos.getChildAt(i)).setText(jsonEnvolvidos.getString(i));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}