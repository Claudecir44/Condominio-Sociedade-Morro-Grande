package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.cjstudio.condominio_sociedade_morro_grande.data.OcorrenciaDAO;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ListaOcorrenciasActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private OcorrenciasAdapter adapter;
    private List<JSONObject> listaOcorrencias;
    private OcorrenciaDAO ocorrenciaDAO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_ocorrencias);

        recyclerView = findViewById(R.id.recyclerOcorrencias);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ocorrenciaDAO = new OcorrenciaDAO();
        listaOcorrencias = new ArrayList<>();

        adapter = new OcorrenciasAdapter(listaOcorrencias);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarLista();
    }

    private void ordenarPorDataHora() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        Collections.sort(listaOcorrencias, (o1, o2) -> {
            try {
                Date d1 = sdf.parse(o1.getString("datahora"));
                Date d2 = sdf.parse(o2.getString("datahora"));
                return d2.compareTo(d1);
            } catch (Exception e) {
                e.printStackTrace();
                return 0;
            }
        });
    }

    private void atualizarLista() {
        ocorrenciaDAO.listarOcorrencias(new ColecaoFirestore.Callback<List<JSONObject>>() {
            @Override
            public void onSucesso(List<JSONObject> lista) {
                listaOcorrencias.clear();
                listaOcorrencias.addAll(lista);
                ordenarPorDataHora();
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ListaOcorrenciasActivity.this,
                        "Erro ao carregar ocorrências: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ===================== Adapter =====================
    private class OcorrenciasAdapter extends RecyclerView.Adapter<OcorrenciasAdapter.ViewHolder> {

        private final List<JSONObject> listaOcorrencias;
        private int expandedPosition = -1;

        public OcorrenciasAdapter(List<JSONObject> listaOcorrencias) {
            this.listaOcorrencias = listaOcorrencias;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ocorrencia, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject ocorrencia = listaOcorrencias.get(position);
            boolean isExpanded = position == expandedPosition;
            holder.bind(ocorrencia, isExpanded);

            holder.textTitulo.setOnClickListener(v -> {
                if (expandedPosition == position) {
                    expandedPosition = -1;
                } else {
                    int oldPos = expandedPosition;
                    expandedPosition = position;
                    notifyItemChanged(oldPos);
                }
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() {
            return listaOcorrencias.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            TextView textTitulo, textDataHora;
            TextView textDescricaoTitulo, textDescricaoExpandida;
            TextView textEnvolvidosTitulo, textEnvolvidos;
            TextView textAnexosTitulo;
            LinearLayout layoutExpandido, layoutAnexos, layoutBotoes;
            Button buttonEditar, buttonExcluir;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                textTitulo = itemView.findViewById(R.id.textTipoOcorrencia);
                textDataHora = itemView.findViewById(R.id.textDataHoraOcorrencia);
                textDescricaoTitulo = itemView.findViewById(R.id.textDescricaoTitulo);
                textDescricaoExpandida = itemView.findViewById(R.id.textDescricaoExpandida);
                textEnvolvidosTitulo = itemView.findViewById(R.id.textEnvolvidosTitulo);
                textEnvolvidos = itemView.findViewById(R.id.textEnvolvidos);
                textAnexosTitulo = itemView.findViewById(R.id.textAnexosTitulo);
                layoutExpandido = itemView.findViewById(R.id.layoutExpandido);
                layoutAnexos = itemView.findViewById(R.id.layoutAnexos);
                layoutBotoes = itemView.findViewById(R.id.layoutBotoes);
                buttonEditar = itemView.findViewById(R.id.buttonEditar);
                buttonExcluir = itemView.findViewById(R.id.buttonExcluir);
            }

            public void bind(JSONObject ocorrencia, boolean isExpanded) {
                try {
                    Context context = itemView.getContext();

                    textTitulo.setText(ocorrencia.optString("tipo"));
                    textDataHora.setText(ocorrencia.optString("datahora"));

                    String descricao = ocorrencia.optString("descricao", "");
                    textDescricaoExpandida.setText(descricao);
                    textDescricaoTitulo.setVisibility(isExpanded && !descricao.isEmpty() ? View.VISIBLE : View.GONE);
                    textDescricaoExpandida.setVisibility(isExpanded && !descricao.isEmpty() ? View.VISIBLE : View.GONE);

                    JSONArray envolvidosArray = new JSONArray(ocorrencia.optString("envolvidos", "[]"));
                    if (envolvidosArray.length() > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < envolvidosArray.length(); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(envolvidosArray.getString(i));
                        }
                        textEnvolvidos.setText(sb.toString());
                        textEnvolvidosTitulo.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                        textEnvolvidos.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                    } else {
                        textEnvolvidosTitulo.setVisibility(View.GONE);
                        textEnvolvidos.setVisibility(View.GONE);
                    }

                    layoutAnexos.removeAllViews();
                    JSONArray anexosArray = new JSONArray(ocorrencia.optString("anexos", "[]"));
                    if (anexosArray.length() > 0 && isExpanded) {
                        textAnexosTitulo.setVisibility(View.VISIBLE);
                        layoutAnexos.setVisibility(View.VISIBLE);

                        for (int i = 0; i < anexosArray.length(); i++) {
                            String caminho = anexosArray.getString(i);
                            TextView anexItem = new TextView(context);
                            anexItem.setText("- Anexo " + (i + 1));
                            anexItem.setTextSize(14);
                            anexItem.setTextColor(0xFF0000FF);
                            anexItem.setPadding(8, 4, 8, 4);
                            anexItem.setOnClickListener(v -> ArquivosStorage.abrir(context, caminho));
                            layoutAnexos.addView(anexItem);
                        }
                    } else {
                        textAnexosTitulo.setVisibility(View.GONE);
                        layoutAnexos.setVisibility(View.GONE);
                    }

                    buttonEditar.setOnClickListener(v -> {
                        try {
                            Intent intent = new Intent(ListaOcorrenciasActivity.this, com.cjstudio.condominio_sociedade_morro_grande.RegistroOcorrenciasActivity.class);
                            intent.putExtra("ocorrenciaJSON", ocorrencia.toString());
                            startActivityForResult(intent, 100);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                    buttonExcluir.setOnClickListener(v -> {
                        new AlertDialog.Builder(ListaOcorrenciasActivity.this)
                                .setTitle("Excluir ocorrência")
                                .setMessage("Tem certeza que deseja excluir esta ocorrência?")
                                .setPositiveButton("Sim", (dialog, which) -> {
                                    int pos = getAdapterPosition();
                                    if (pos != RecyclerView.NO_POSITION) {
                                        JSONObject oc = listaOcorrencias.get(pos);
                                        String id = oc.optString("id", "");
                                        if (!id.isEmpty()) {
                                            ocorrenciaDAO.excluirOcorrencia(id, new ColecaoFirestore.Callback<Void>() {
                                                @Override
                                                public void onSucesso(Void resultado) {
                                                    atualizarLista();
                                                    Toast.makeText(ListaOcorrenciasActivity.this, "Ocorrência excluída", Toast.LENGTH_SHORT).show();
                                                }

                                                @Override
                                                public void onErro(Exception e) {
                                                    Toast.makeText(ListaOcorrenciasActivity.this,
                                                            "Erro ao excluir: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                }
                                            });
                                        }
                                    }
                                })
                                .setNegativeButton("Não", null)
                                .show();
                    });

                    layoutBotoes.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                    layoutExpandido.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            private File copyToInternal(Context context, Uri uri) {
                try {
                    String fileName = new File(uri.getPath()).getName();
                    File file = new File(context.getFilesDir(), fileName);

                    InputStream in = context.getContentResolver().openInputStream(uri);
                    if (in == null) return null;

                    FileOutputStream out = new FileOutputStream(file);
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    in.close();
                    out.close();
                    return file;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            private String getMimeType(String fileName) {
                String ext = "";
                int i = fileName.lastIndexOf('.');
                if (i > 0) ext = fileName.substring(i + 1).toLowerCase();

                switch (ext) {
                    case "pdf": return "application/pdf";
                    case "doc": return "application/msword";
                    case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    case "xls": return "application/vnd.ms-excel";
                    case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                    case "jpg":
                    case "jpeg": return "image/jpeg";
                    case "png": return "image/png";
                    case "mp4": return "video/mp4";
                    default: return "*/*";
                }
            }
        }
    }
}