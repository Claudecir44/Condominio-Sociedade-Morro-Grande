package com.cjstudio.condominio_sociedade_morro_grande;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;


import com.cjstudio.condominio_sociedade_morro_grande.data.DespesaDAO;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Despesa;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class RegistroDespesasActivity extends AppCompatActivity {

    private EditText editDataHora, editNome, editDescricao, editValor;
    private Button btnSalvar, btnLista, btnAnexar;
    private List<String> anexos = new ArrayList<>();
    private String editId = null;
    private DespesaDAO despesaDAO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro_despesas);

        editDataHora = findViewById(R.id.editDataHoraDespesa);
        editNome = findViewById(R.id.editNomeDespesa);
        editDescricao = findViewById(R.id.editDescricaoDespesa);
        editValor = findViewById(R.id.editValorDespesa);

        btnSalvar = findViewById(R.id.btnSalvarDespesa);
        btnLista = findViewById(R.id.btnListaDespesas);
        btnAnexar = findViewById(R.id.btnAnexarNota);

        despesaDAO = new DespesaDAO();

        String dataHoraAtual = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(Calendar.getInstance().getTime());
        editDataHora.setText(dataHoraAtual);

        btnAnexar.setOnClickListener(v -> selecionarArquivo());
        btnSalvar.setOnClickListener(v -> salvarDespesa());

        btnLista.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.ListaDespesasActivity.class);
            startActivity(intent);
        });

        if (getIntent().hasExtra("despesaJSON")) {
            Despesa d = despesaDAO.deJson(getIntent().getStringExtra("despesaJSON"));
            if (d != null) {
                editId = d.getId();
                editDataHora.setText(d.getDataHora());
                editNome.setText(d.getNome());
                editDescricao.setText(d.getDescricao());
                editValor.setText(String.valueOf(d.getValor()));
                anexos = d.getAnexos() != null ? new ArrayList<>(d.getAnexos()) : new ArrayList<>();
            }
        }
    }

    private void selecionarArquivo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/pdf",
                "application/msword",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, 200);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                anexos.add(uri.toString());
                Toast.makeText(this, "Arquivo anexado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void salvarDespesa() {
        String dataHora = editDataHora.getText().toString();
        String nome = editNome.getText().toString();
        String descricao = editDescricao.getText().toString();
        String valorStr = editValor.getText().toString();

        if (dataHora.isEmpty() || nome.isEmpty() || descricao.isEmpty() || valorStr.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            return;
        }

        double valor;
        try {
            // Aceita "12,50" e "1.234,50" (formato brasileiro), além de "12.50"
            String normalizado = valorStr.trim();
            if (normalizado.contains(",")) {
                normalizado = normalizado.replace(".", "").replace(",", ".");
            }
            valor = Double.parseDouble(normalizado);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Valor inválido", Toast.LENGTH_SHORT).show();
            return;
        }
        btnSalvar.setEnabled(false);
        // Notas/anexos vão pro Firebase Storage; a despesa guarda os links
        ArquivosStorage.enviar(this, "despesas", new ArrayList<>(anexos), new ColecaoFirestore.Callback<List<String>>() {
            @Override
            public void onSucesso(List<String> links) {
                gravarDespesa(new Despesa(dataHora, nome, descricao, valor, links));
            }

            @Override
            public void onErro(Exception e) {
                aoFalhar(new Exception("não foi possível enviar os anexos (" + e.getMessage() + ")"));
            }
        });
    }

    private void gravarDespesa(Despesa despesa) {
        if (editId == null) {
            despesaDAO.inserir(despesa, new ColecaoFirestore.Callback<String>() {
                @Override
                public void onSucesso(String id) {
                    aoSalvar("Despesa salva!");
                }

                @Override
                public void onErro(Exception e) {
                    aoFalhar(e);
                }
            });
        } else {
            despesa.setId(editId);
            despesaDAO.atualizar(despesa, new ColecaoFirestore.Callback<Void>() {
                @Override
                public void onSucesso(Void resultado) {
                    aoSalvar("Despesa atualizada!");
                }

                @Override
                public void onErro(Exception e) {
                    aoFalhar(e);
                }
            });
        }
    }

    private void aoSalvar(String mensagem) {
        btnSalvar.setEnabled(true);
        Toast.makeText(this, mensagem, Toast.LENGTH_SHORT).show();
        editDataHora.setText(new SimpleDateFormat("dd/MM/yyyy HH:mm").format(Calendar.getInstance().getTime()));
        editNome.setText("");
        editDescricao.setText("");
        editValor.setText("");
        anexos.clear();
        editId = null;
    }

    private void aoFalhar(Exception e) {
        btnSalvar.setEnabled(true);
        Toast.makeText(this, "Erro ao salvar despesa: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
}