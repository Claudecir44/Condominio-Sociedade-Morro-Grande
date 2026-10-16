package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListaAvisosActivity extends AppCompatActivity {

    private LinearLayout layoutAvisos;
    private String tipoUsuario;

    // Firestore
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inicializa Firestore
        db = FirebaseFirestore.getInstance();

        // Recebe o tipo de usuário (admin ou morador)
        tipoUsuario = getIntent().getStringExtra("tipo_usuario");
        if (tipoUsuario == null) {
            tipoUsuario = BuildConfig.PERFIL; // padrão: perfil do app instalado
        }

        // Construção da UI (igual à original, sem XML)
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        TextView tvTitulo = new TextView(this);
        tvTitulo.setText("Avisos");
        tvTitulo.setTextSize(22);
        tvTitulo.setTextColor(getResources().getColor(android.R.color.black));
        tvTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitulo.setGravity(Gravity.CENTER);
        tvTitulo.setPadding(0, 16, 0, 16);

        LinearLayout.LayoutParams tituloParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tvTitulo.setLayoutParams(tituloParams);
        mainLayout.addView(tvTitulo);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        layoutAvisos = new LinearLayout(this);
        layoutAvisos.setOrientation(LinearLayout.VERTICAL);
        layoutAvisos.setPadding(16, 0, 16, 16);

        scrollView.addView(layoutAvisos);
        mainLayout.addView(scrollView);

        setContentView(mainLayout);

        // Carrega os avisos
        exibirAvisos();
    }

    // ==================== LISTAR AVISOS DO FIRESTORE ====================
    private void exibirAvisos() {
        // Mostra um indicador de carregamento (opcional)
        layoutAvisos.removeAllViews();
        TextView tvCarregando = new TextView(this);
        tvCarregando.setText("Carregando avisos...");
        tvCarregando.setGravity(Gravity.CENTER);
        tvCarregando.setPadding(0, 32, 0, 32);
        layoutAvisos.addView(tvCarregando);

        db.collection("avisos")
                .orderBy("timestamp") // ordena por timestamp (mais antigo primeiro) – ou use "datahora" se preferir
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // Limpa a lista e remove a mensagem de carregamento
                    layoutAvisos.removeAllViews();

                    if (queryDocumentSnapshots.isEmpty()) {
                        TextView tvVazio = new TextView(this);
                        tvVazio.setText("Nenhum aviso cadastrado");
                        tvVazio.setTextSize(16);
                        tvVazio.setGravity(Gravity.CENTER);
                        tvVazio.setPadding(0, 32, 0, 32);
                        layoutAvisos.addView(tvVazio);
                        return;
                    }

                    // Itera sobre os documentos
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        // Converte o documento para Map e depois para JSONObject (para compatibilidade com o código existente)
                        Map<String, Object> data = document.getData();
                        JSONObject aviso = new JSONObject();
                        try {
                            aviso.put("id", document.getId()); // ID do documento
                            aviso.put("datahora", data.get("datahora") != null ? data.get("datahora").toString() : "");
                            aviso.put("assunto", data.get("assunto") != null ? data.get("assunto").toString() : "");
                            aviso.put("descricao", data.get("descricao") != null ? data.get("descricao").toString() : "");
                            aviso.put("anexos", data.get("anexos") != null ? data.get("anexos").toString() : "[]");
                            aviso.put("timestamp", data.get("timestamp") != null ? data.get("timestamp").toString() : "");
                        } catch (Exception e) {
                            e.printStackTrace();
                            continue;
                        }

                        // Adiciona o card na UI
                        adicionarCardAviso(aviso);
                    }
                })
                .addOnFailureListener(e -> {
                    layoutAvisos.removeAllViews();
                    TextView tvErro = new TextView(this);
                    tvErro.setText("Erro ao carregar avisos: " + e.getMessage());
                    tvErro.setTextSize(16);
                    tvErro.setGravity(Gravity.CENTER);
                    tvErro.setPadding(0, 32, 0, 32);
                    layoutAvisos.addView(tvErro);
                    Log.e("ListaAvisos", "Erro ao buscar avisos", e);
                });
    }

    // ==================== ADICIONAR CARD DE AVISO NA UI ====================
    private void adicionarCardAviso(JSONObject aviso) {
        final String avisoId = aviso.optString("id");

        LinearLayout cardAviso = new LinearLayout(this);
        cardAviso.setOrientation(LinearLayout.VERTICAL);
        cardAviso.setPadding(16, 16, 16, 16);
        cardAviso.setBackgroundResource(R.drawable.card_background);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        cardAviso.setLayoutParams(cardParams);

        // Data/Hora
        TextView tvData = new TextView(this);
        tvData.setText("Data/Hora: " + aviso.optString("datahora"));
        tvData.setTextSize(14);
        tvData.setTextColor(getResources().getColor(android.R.color.darker_gray));
        cardAviso.addView(tvData);

        View espaco1 = new View(this);
        espaco1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 10
        ));
        cardAviso.addView(espaco1);

        // Assunto
        TextView tvAssunto = new TextView(this);
        tvAssunto.setText("Assunto:\n " + aviso.optString("assunto"));
        tvAssunto.setTextSize(18);
        tvAssunto.setTextColor(getResources().getColor(android.R.color.black));
        cardAviso.addView(tvAssunto);

        View espaco2 = new View(this);
        espaco2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 10
        ));
        cardAviso.addView(espaco2);

        // Descrição
        TextView tvDescricao = new TextView(this);
        tvDescricao.setText("Descrição:\n " + aviso.optString("descricao"));
        tvDescricao.setTextSize(16);
        tvDescricao.setTextColor(getResources().getColor(android.R.color.black));
        tvDescricao.setPadding(0, 0, 0, 16);
        cardAviso.addView(tvDescricao);

        View espaco3 = new View(this);
        espaco3.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 10
        ));
        cardAviso.addView(espaco3);

        // Anexos
        try {
            JSONArray jsonAnexos = new JSONArray(aviso.optString("anexos", "[]"));
            if (jsonAnexos.length() > 0) {
                TextView tvSubtituloAnexos = new TextView(this);
                tvSubtituloAnexos.setText("Documentos em Anexo:");
                tvSubtituloAnexos.setTextSize(16);
                tvSubtituloAnexos.setTextColor(getResources().getColor(android.R.color.black));
                tvSubtituloAnexos.setTypeface(null, android.graphics.Typeface.BOLD);
                tvSubtituloAnexos.setPadding(0, 0, 0, 8);
                cardAviso.addView(tvSubtituloAnexos);

                LinearLayout layoutAnexos = new LinearLayout(this);
                layoutAnexos.setOrientation(LinearLayout.HORIZONTAL);
                layoutAnexos.setPadding(0, 8, 0, 16);

                for (int j = 0; j < jsonAnexos.length(); j++) {
                    String path = jsonAnexos.optString(j);
                    boolean ehLink = ArquivosStorage.ehLink(path);
                    String nomeArquivo = new File(path).getName();
                    if (ehLink) {
                        String segmento = Uri.parse(path).getLastPathSegment();
                        if (segmento != null) nomeArquivo = segmento.substring(segmento.lastIndexOf('/') + 1);
                    }
                    boolean existe = ehLink || new File(path).exists();

                    LinearLayout containerAnexo = new LinearLayout(this);
                    containerAnexo.setOrientation(LinearLayout.VERTICAL);
                    containerAnexo.setPadding(0, 0, 16, 0);

                    ImageView img = new ImageView(this);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(120, 120);
                    img.setLayoutParams(params);
                    img.setPadding(8, 8, 8, 8);
                    img.setBackgroundResource(R.drawable.image_border);

                    TextView tvNomeArquivo = new TextView(this);
                    tvNomeArquivo.setLayoutParams(new LinearLayout.LayoutParams(
                            120, LinearLayout.LayoutParams.WRAP_CONTENT
                    ));
                    tvNomeArquivo.setText(getNomeArquivoCompactado(nomeArquivo));
                    tvNomeArquivo.setTextSize(10);
                    tvNomeArquivo.setGravity(Gravity.CENTER);
                    tvNomeArquivo.setMaxLines(2);
                    tvNomeArquivo.setEllipsize(android.text.TextUtils.TruncateAt.END);

                    if (existe) {
                        if (isImagem(nomeArquivo)) {
                            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            ArquivosStorage.carregarImagem(img, path, R.drawable.ic_document);
                        } else {
                            img.setImageResource(R.drawable.ic_document);
                            img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        }
                        img.setOnClickListener(v -> ArquivosStorage.abrir(ListaAvisosActivity.this, path));
                    } else {
                        img.setImageResource(android.R.drawable.ic_delete);
                        img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        tvNomeArquivo.setText("Arquivo\nnão encontrado");
                    }

                    containerAnexo.addView(img);
                    containerAnexo.addView(tvNomeArquivo);
                    layoutAnexos.addView(containerAnexo);
                }
                cardAviso.addView(layoutAnexos);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("LISTA_AVISOS", "Erro ao carregar anexos: " + e.getMessage());
        }

        View separador = new View(this);
        separador.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
        ));
        separador.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        separador.setPadding(0, 8, 0, 8);
        cardAviso.addView(separador);

        // Botões de editar/excluir (apenas para admin)
        if (!"morador".equalsIgnoreCase(tipoUsuario)) {
            LinearLayout layoutBotoes = new LinearLayout(this);
            layoutBotoes.setOrientation(LinearLayout.HORIZONTAL);
            layoutBotoes.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            layoutBotoes.setGravity(Gravity.END);

            Button btnEditar = new Button(this);
            LinearLayout.LayoutParams paramsEditar = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            paramsEditar.setMargins(0, 0, 16, 0);
            btnEditar.setLayoutParams(paramsEditar);
            btnEditar.setText("Editar");
            btnEditar.setBackgroundColor(getResources().getColor(R.color.teal_700));
            btnEditar.setTextColor(getResources().getColor(android.R.color.white));
            btnEditar.setAllCaps(false);
            btnEditar.setPadding(32, 8, 32, 8);

            btnEditar.setOnClickListener(v -> editarAviso(aviso));

            Button btnExcluir = new Button(this);
            LinearLayout.LayoutParams paramsExcluir = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            btnExcluir.setLayoutParams(paramsExcluir);
            btnExcluir.setText("Excluir");
            btnExcluir.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            btnExcluir.setTextColor(getResources().getColor(android.R.color.white));
            btnExcluir.setAllCaps(false);
            btnExcluir.setPadding(32, 8, 32, 8);

            btnExcluir.setOnClickListener(v -> mostrarConfirmacaoExclusao(avisoId, aviso));

            layoutBotoes.addView(btnEditar);
            layoutBotoes.addView(btnExcluir);
            cardAviso.addView(layoutBotoes);
        }

        layoutAvisos.addView(cardAviso);
    }

    // ==================== MÉTODOS AUXILIARES (IMAGENS, NOMES, MIME) ====================
    private boolean isImagem(String fileName) {
        String nome = fileName.toLowerCase();
        return nome.endsWith(".jpg") || nome.endsWith(".jpeg") ||
                nome.endsWith(".png") || nome.endsWith(".gif");
    }

    private String getNomeArquivoCompactado(String fileName) {
        if (fileName.length() <= 15) {
            return fileName;
        }
        String extensao = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            extensao = fileName.substring(lastDot);
            fileName = fileName.substring(0, lastDot);
        }
        return fileName.substring(0, 12) + ".." + extensao;
    }

    private void abrirArquivoComAppExterno(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                Uri uri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".provider",
                        file
                );

                String mimeType = obterTipoMime(file.getName());

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Intent intentGenerico = new Intent(Intent.ACTION_VIEW);
                    intentGenerico.setDataAndType(uri, "*/*");
                    intentGenerico.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    if (intentGenerico.resolveActivity(getPackageManager()) != null) {
                        startActivity(intentGenerico);
                    } else {
                        Toast.makeText(this, "Nenhum app encontrado para abrir este arquivo", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "Arquivo não encontrado", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao abrir arquivo", Toast.LENGTH_SHORT).show();
        }
    }

    private String obterTipoMime(String fileName) {
        String nomeArquivo = fileName.toLowerCase();

        if (nomeArquivo.endsWith(".pdf")) return "application/pdf";
        if (nomeArquivo.endsWith(".doc")) return "application/msword";
        if (nomeArquivo.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (nomeArquivo.endsWith(".xls")) return "application/vnd.ms-excel";
        if (nomeArquivo.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (nomeArquivo.endsWith(".jpg") || nomeArquivo.endsWith(".jpeg")) return "image/jpeg";
        if (nomeArquivo.endsWith(".png")) return "image/png";
        if (nomeArquivo.endsWith(".txt")) return "text/plain";
        if (nomeArquivo.endsWith(".zip")) return "application/zip";

        return "*/*";
    }

    // ==================== EDIÇÃO (chama CadastroAvisosActivity) ====================
    private void editarAviso(JSONObject aviso) {
        try {
            Intent intent = new Intent(this, CadastroAvisosActivity.class);
            intent.putExtra("avisoJSON", aviso.toString());
            intent.putExtra("modoEdicao", true);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao editar aviso", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    // ==================== EXCLUSÃO ====================
    private void mostrarConfirmacaoExclusao(String avisoId, JSONObject aviso) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirmar Exclusão");
        builder.setMessage("Tem certeza que deseja excluir este aviso?");

        builder.setPositiveButton("Sim", (dialog, which) -> {
            excluirAviso(avisoId, aviso);
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void excluirAviso(String avisoId, JSONObject aviso) {
        // 1. Deleta os arquivos de anexo locais
        excluirArquivosAnexos(aviso);

        // 2. Remove o documento do Firestore
        db.collection("avisos")
                .document(avisoId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Aviso excluído com sucesso", Toast.LENGTH_SHORT).show();
                    // Recarrega a lista
                    exibirAvisos();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erro ao excluir aviso: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("ListaAvisos", "Erro ao excluir", e);
                });
    }

    private void excluirArquivosAnexos(JSONObject aviso) {
        try {
            JSONArray jsonAnexos = new JSONArray(aviso.optString("anexos", "[]"));
            for (int i = 0; i < jsonAnexos.length(); i++) {
                String path = jsonAnexos.getString(i);
                File arquivo = new File(path);
                if (arquivo.exists()) {
                    arquivo.delete();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== CICLO DE VIDA ====================
    @Override
    protected void onResume() {
        super.onResume();
        // Recarrega a lista quando a activity voltar ao primeiro plano
        exibirAvisos();
    }

    // ==================== NÃO HÁ MAIS onDestroy com fechamento de DAO ====================
}