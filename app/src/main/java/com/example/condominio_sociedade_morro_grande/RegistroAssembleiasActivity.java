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

import com.cjstudio.condominio_sociedade_morro_grande.data.AssembleiaDAO;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class RegistroAssembleiasActivity extends AppCompatActivity {

    private EditText editDataHora, editAssunto, editLocal, editDescricao;
    private Button buttonSalvar, buttonListaAssembleias;
    private PainelAnexos painelAnexos;

    private AssembleiaDAO assembleiaDAO;
    private String editarId = null;
    private boolean modoVisualizacao = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro_assembleias);

        editDataHora = findViewById(R.id.editDataHora);
        editAssunto = findViewById(R.id.editAssunto);
        editLocal = findViewById(R.id.editLocal);
        editDescricao = findViewById(R.id.editDescricao);
        buttonSalvar = findViewById(R.id.buttonSalvar);
        buttonListaAssembleias = findViewById(R.id.buttonListaAssembleias);
        painelAnexos = new PainelAnexos(this, findViewById(R.id.painelAnexos));

        assembleiaDAO = new AssembleiaDAO();

        editDataHora.setOnClickListener(v -> mostrarDateTimePicker());

        buttonSalvar.setOnClickListener(v -> {
            if (!modoVisualizacao) salvarAssembleia();
        });

        buttonListaAssembleias.setOnClickListener(v -> {
            Intent intent = new Intent(RegistroAssembleiasActivity.this, com.cjstudio.condominio_sociedade_morro_grande.ListaAssembleiasActivity.class);
            startActivity(intent);
        });

        if (getIntent().hasExtra("assembleiaJSON")) {
            try {
                JSONObject assembleia = new JSONObject(getIntent().getStringExtra("assembleiaJSON"));
                editarId = assembleia.optString("id", null);
                modoVisualizacao = getIntent().getBooleanExtra("modoVisualizacao", false);
                preencherCampos(assembleia);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (modoVisualizacao) {
            editDataHora.setEnabled(false);
            editAssunto.setEnabled(false);
            editLocal.setEnabled(false);
            editDescricao.setEnabled(false);
            buttonSalvar.setEnabled(false);
            painelAnexos.somenteLeitura();
        }
    }

    private void salvarAssembleia() {
        String dataHora = editDataHora.getText().toString().trim();
        String assunto = editAssunto.getText().toString().trim();
        String local = editLocal.getText().toString().trim();
        String descricao = editDescricao.getText().toString().trim();

        if (dataHora.isEmpty() || assunto.isEmpty() || local.isEmpty() || descricao.isEmpty()) {
            Toast.makeText(this, "Preencha data/hora, assunto, local e descrição.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject assembleia = new JSONObject();
            assembleia.put("datahora", dataHora);
            assembleia.put("assunto", assunto);
            assembleia.put("local", local);
            assembleia.put("descricao", descricao);

            buttonSalvar.setEnabled(false);
            // Anexos vão pro Firebase Storage; o registro guarda os links
            painelAnexos.enviar("assembleias", new ColecaoFirestore.Callback<List<String>>() {
                @Override
                public void onSucesso(List<String> links) {
                    try {
                        assembleia.put("anexos", new JSONArray(links).toString());
                    } catch (Exception e) {
                        aoFalhar(e);
                        return;
                    }
                    gravarAssembleia(assembleia);
                }

                @Override
                public void onErro(Exception e) {
                    aoFalhar(new Exception("não foi possível enviar os anexos (" + e.getMessage() + ")"));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao salvar assembleia.", Toast.LENGTH_SHORT).show();
        }
    }

    private void gravarAssembleia(JSONObject assembleia) {
        {
            if (editarId != null) {
                assembleiaDAO.atualizarAssembleia(editarId, assembleia, new ColecaoFirestore.Callback<Void>() {
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
                assembleiaDAO.inserirAssembleia(assembleia, new ColecaoFirestore.Callback<String>() {
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
        Toast.makeText(this, "Assembleia salva com sucesso!", Toast.LENGTH_SHORT).show();
        limparCampos();
        editarId = null;
    }

    private void aoFalhar(Exception e) {
        buttonSalvar.setEnabled(true);
        Toast.makeText(this, "Erro ao salvar assembleia: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void limparCampos() {
        editDataHora.setText("");
        editAssunto.setText("");
        editLocal.setText("");
        editDescricao.setText("");
        painelAnexos.limpar();
    }

    private void mostrarDateTimePicker() {
        if (modoVisualizacao) return;

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

    private void preencherCampos(JSONObject assembleia) {
        try {
            editDataHora.setText(assembleia.optString("datahora", ""));
            editAssunto.setText(assembleia.optString("assunto", ""));
            editLocal.setText(assembleia.optString("local", ""));
            editDescricao.setText(assembleia.optString("descricao", ""));

            painelAnexos.definir(ListaAnexos.deValor(assembleia.opt("anexos")));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}