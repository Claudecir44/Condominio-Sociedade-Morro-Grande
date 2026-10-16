package com.cjstudio.condominio_sociedade_morro_grande;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CadastroAvisosActivity extends AppCompatActivity {

    private EditText editDataHora, editAssunto, editDescricao;
    private Button buttonSelecionarArquivos, buttonSalvar, buttonVoltarLista;
    private LinearLayout layoutAnexos, layoutAnexosExistentes;
    private TextView tvAnexosExistentes;

    private List<String> anexos = new ArrayList<>();          // Novos anexos (caminhos locais)
    private List<String> anexosExistentes = new ArrayList<>(); // Anexos já salvos (caminhos)
    private List<String> anexosRemovidos = new ArrayList<>();  // Anexos removidos na edição

    private String editarId = null; // ID do documento no Firestore (String)

    // Firestore
    private FirebaseFirestore db;

    private final ActivityResultLauncher<String[]> selecionarArquivosLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    if (anexos.size() + anexosExistentes.size() + uris.size() > 5) {
                        Toast.makeText(this, "Máximo de 5 anexos.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (Uri uri : uris) {
                        File arquivo = copiarUriParaArquivo(uri);
                        if (arquivo != null) anexos.add(arquivo.getAbsolutePath());
                        else Toast.makeText(this, "Falha ao copiar arquivo.", Toast.LENGTH_SHORT).show();
                    }
                    atualizarAnexos();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_avisos);

        // Inicializa Firestore
        db = FirebaseFirestore.getInstance();

        // Bind views
        editDataHora = findViewById(R.id.editDataHora);
        editAssunto = findViewById(R.id.editAssunto);
        editDescricao = findViewById(R.id.editDescricao);
        buttonSelecionarArquivos = findViewById(R.id.buttonSelecionarArquivos);
        buttonSalvar = findViewById(R.id.buttonSalvar);
        buttonVoltarLista = findViewById(R.id.buttonVoltarLista);
        layoutAnexos = findViewById(R.id.layoutAnexos);
        layoutAnexosExistentes = findViewById(R.id.layoutAnexosExistentes);
        tvAnexosExistentes = findViewById(R.id.tvAnexosExistentes);

        // Listeners
        editDataHora.setOnClickListener(v -> mostrarDateTimePicker());

        buttonSelecionarArquivos.setOnClickListener(v -> {
            String[] tipos = {
                    "image/*",
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            };
            selecionarArquivosLauncher.launch(tipos);
        });

        buttonSalvar.setOnClickListener(v -> salvarAviso());

        buttonVoltarLista.setOnClickListener(v -> {
            startActivity(new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.ListaAvisosActivity.class));
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

    // ==================== COPIAR URI PARA ARQUIVO LOCAL ====================
    private File copiarUriParaArquivo(Uri uri) {
        try {
            String fileName = "anexo_" + System.currentTimeMillis();
            String mimeType = getContentResolver().getType(uri);
            if (mimeType != null) {
                String ext = mimeType.split("/")[1];
                fileName += "." + ext;
            }
            File destFile = new File(getFilesDir(), fileName);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(destFile)) {
                if (in == null) return null;
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                return destFile;
            }
        } catch (Exception e) {
            Log.e("CadastroAvisos", "Erro ao copiar arquivo: " + e.getMessage());
            return null;
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

        // Monta lista final de anexos (existentes + novos, removendo os excluídos)
        List<String> anexosFinais = new ArrayList<>();
        for (String path : anexosExistentes) {
            if (!anexosRemovidos.contains(path)) {
                anexosFinais.add(path);
            }
        }
        anexosFinais.addAll(anexos); // adiciona os novos

        // Anexos vão pro Firebase Storage; o aviso guarda os links
        ArquivosStorage.enviar(this, "avisos", anexosFinais, new ColecaoFirestore.Callback<List<String>>() {
            @Override
            public void onSucesso(List<String> links) {
                gravarAviso(dataHora, assunto, descricao, links);
            }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(CadastroAvisosActivity.this,
                        "Erro ao enviar anexos: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void gravarAviso(String dataHora, String assunto, String descricao, List<String> anexosFinais) {
        // Converte para JSONArray para manter compatibilidade com o formato anterior
        JSONArray jsonAnexos = new JSONArray(anexosFinais);
        String anexosStr = jsonAnexos.toString();

        // Cria o mapa de dados
        Map<String, Object> avisoMap = new HashMap<>();
        avisoMap.put("datahora", dataHora);
        avisoMap.put("assunto", assunto);
        avisoMap.put("descricao", descricao);
        avisoMap.put("anexos", anexosStr);
        avisoMap.put("timestamp", System.currentTimeMillis());

        if (editarId != null) {
            // Atualiza documento existente
            db.collection("avisos")
                    .document(editarId)
                    .update(avisoMap)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Aviso atualizado!", Toast.LENGTH_SHORT).show();
                        removerArquivosExcluidos();
                        limparCampos();
                        startActivity(new Intent(this, com.cjstudio.condominio_sociedade_morro_grande.ListaAvisosActivity.class));
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Erro ao atualizar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            // Novo documento
            db.collection("avisos")
                    .add(avisoMap)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Aviso salvo!", Toast.LENGTH_SHORT).show();
                        limparCampos();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Erro ao salvar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // ==================== REMOVER ARQUIVOS EXCLUÍDOS ====================
    private void removerArquivosExcluidos() {
        for (String path : anexosRemovidos) {
            File arquivo = new File(path);
            if (arquivo.exists()) {
                arquivo.delete();
            }
        }
        anexosRemovidos.clear();
    }

    // ==================== LIMPAR CAMPOS ====================
    private void limparCampos() {
        editDataHora.setText("");
        editAssunto.setText("");
        editDescricao.setText("");
        anexos.clear();
        anexosExistentes.clear();
        anexosRemovidos.clear();
        atualizarAnexos();
        atualizarAnexosExistentes();
        editarId = null;
        buttonVoltarLista.setVisibility(View.VISIBLE);
    }

    // ==================== ATUALIZAR UI DOS ANEXOS ====================
    private void atualizarAnexos() {
        layoutAnexos.removeAllViews();
        for (String path : anexos) {
            adicionarAnexoUI(path, false);
        }
    }

    private void atualizarAnexosExistentes() {
        layoutAnexosExistentes.removeAllViews();
        for (String path : anexosExistentes) {
            if (!anexosRemovidos.contains(path)) {
                adicionarAnexoUI(path, true);
            }
        }

        if (anexosExistentes.isEmpty() || anexosExistentes.size() == anexosRemovidos.size()) {
            tvAnexosExistentes.setVisibility(View.GONE);
            layoutAnexosExistentes.setVisibility(View.GONE);
        } else {
            tvAnexosExistentes.setVisibility(View.VISIBLE);
            layoutAnexosExistentes.setVisibility(View.VISIBLE);
        }
    }

    private void adicionarAnexoUI(String path, boolean isExistente) {
        File file = new File(path);

        LinearLayout layoutAnexo = new LinearLayout(this);
        layoutAnexo.setOrientation(LinearLayout.HORIZONTAL);
        layoutAnexo.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        layoutAnexo.setPadding(0, 8, 0, 8);

        ImageView img = new ImageView(this);
        LinearLayout.LayoutParams paramsImagem = new LinearLayout.LayoutParams(100, 100);
        img.setLayoutParams(paramsImagem);
        img.setPadding(8, 8, 8, 8);

        if (file.getName().toLowerCase().endsWith(".jpg") ||
                file.getName().toLowerCase().endsWith(".png") ||
                file.getName().toLowerCase().endsWith(".jpeg")) {
            img.setImageURI(Uri.fromFile(file));
        } else {
            img.setImageResource(android.R.drawable.ic_menu_report_image);
        }
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);

        TextView tvNomeArquivo = new TextView(this);
        LinearLayout.LayoutParams paramsTexto = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
        );
        tvNomeArquivo.setLayoutParams(paramsTexto);
        tvNomeArquivo.setText(file.getName());
        tvNomeArquivo.setPadding(16, 0, 0, 0);
        tvNomeArquivo.setGravity(Gravity.CENTER_VERTICAL);

        Button btnExcluir = new Button(this);
        LinearLayout.LayoutParams paramsBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnExcluir.setLayoutParams(paramsBtn);
        btnExcluir.setText("Excluir");
        btnExcluir.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
        btnExcluir.setTextColor(getResources().getColor(android.R.color.white));
        btnExcluir.setAllCaps(false);
        btnExcluir.setPadding(16, 8, 16, 8);

        final String caminhoFinal = path;
        btnExcluir.setOnClickListener(v -> {
            if (isExistente) {
                anexosRemovidos.add(caminhoFinal);
                atualizarAnexosExistentes();
            } else {
                anexos.remove(caminhoFinal);
                File arquivoParaExcluir = new File(caminhoFinal);
                if (arquivoParaExcluir.exists()) {
                    arquivoParaExcluir.delete();
                }
                atualizarAnexos();
            }
            Toast.makeText(this, "Arquivo removido", Toast.LENGTH_SHORT).show();
        });

        layoutAnexo.addView(img);
        layoutAnexo.addView(tvNomeArquivo);
        layoutAnexo.addView(btnExcluir);

        if (isExistente) {
            layoutAnexosExistentes.addView(layoutAnexo);
        } else {
            layoutAnexos.addView(layoutAnexo);
        }
    }

    // ==================== DATE/TIME PICKER ====================
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

    // ==================== PREENCHER CAMPOS NA EDIÇÃO ====================
    private void preencherCampos(JSONObject aviso) {
        try {
            editDataHora.setText(aviso.optString("datahora", ""));
            editAssunto.setText(aviso.optString("assunto", ""));
            editDescricao.setText(aviso.optString("descricao", ""));

            anexos.clear();
            anexosExistentes.clear();
            anexosRemovidos.clear();

            JSONArray jsonAnexos = new JSONArray(aviso.optString("anexos", "[]"));
            for (int i = 0; i < jsonAnexos.length(); i++) {
                String path = jsonAnexos.getString(i);
                anexosExistentes.add(path);
            }

            atualizarAnexosExistentes();
            atualizarAnexos();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== REMOVIDO O MÉTODO fechar() - NÃO É MAIS NECESSÁRIO ====================
}