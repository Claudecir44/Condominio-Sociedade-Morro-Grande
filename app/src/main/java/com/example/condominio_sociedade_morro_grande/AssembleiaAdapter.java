package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AssembleiaAdapter extends RecyclerView.Adapter<AssembleiaAdapter.AssembleiaViewHolder> {

    private final List<JSONObject> lista;
    private final OnVisualizarClickListener visualizarListener;
    private final OnExcluirClickListener excluirListener;
    private final OnEditarClickListener editarListener;
    private final Context context;
    private final String tipoUsuario;
    private int expandedPosition = -1;

    public interface OnVisualizarClickListener {
        void onVisualizar(JSONObject assembleia);
    }

    public interface OnExcluirClickListener {
        void onExcluir(JSONObject assembleia);
    }

    public interface OnEditarClickListener {
        void onEditar(JSONObject assembleia);
    }

    public AssembleiaAdapter(Context context, List<JSONObject> lista,
                             OnVisualizarClickListener visualizarListener,
                             OnExcluirClickListener excluirListener,
                             OnEditarClickListener editarListener,
                             String tipoUsuario) {
        this.context = context;
        this.lista = lista;
        this.visualizarListener = visualizarListener;
        this.excluirListener = excluirListener;
        this.editarListener = editarListener;
        this.tipoUsuario = tipoUsuario;
    }

    @NonNull
    @Override
    public AssembleiaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_assembleia, parent, false);
        return new AssembleiaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AssembleiaViewHolder holder, int position) {
        JSONObject assembleia = lista.get(position);
        boolean isExpanded = position == expandedPosition;
        holder.bind(assembleia, isExpanded, position);
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    class AssembleiaViewHolder extends RecyclerView.ViewHolder {

        TextView tvDataHora, tvAssunto, tvLocal, tvDescricao, tvAnexosLabel, tvExpandir;
        LinearLayout layoutExpandido, layoutAnexos;
        Button btnExcluir, btnEditar;

        public AssembleiaViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDataHora = itemView.findViewById(R.id.tvDataHora);
            tvAssunto = itemView.findViewById(R.id.tvAssunto);
            tvLocal = itemView.findViewById(R.id.tvLocal);
            tvDescricao = itemView.findViewById(R.id.tvDescricao);
            tvAnexosLabel = itemView.findViewById(R.id.tvAnexosLabel);
            tvExpandir = itemView.findViewById(R.id.tvExpandir);
            layoutExpandido = itemView.findViewById(R.id.layoutExpandido);
            layoutAnexos = itemView.findViewById(R.id.layoutAnexos);
            btnExcluir = itemView.findViewById(R.id.btnExcluir);
            btnEditar = itemView.findViewById(R.id.btnEditar);
        }

        void bind(JSONObject assembleia, boolean isExpanded, int position) {
            String dataHora = assembleia.optString("datahora", "");
            String dataFormatada = formatarDataHora(dataHora);
            tvDataHora.setText("📅 " + dataFormatada);

            String assunto = assembleia.optString("assunto", "");
            tvAssunto.setText(assunto.isEmpty() ? "Assembleia" : assunto);

            String local = assembleia.optString("local", "");
            tvLocal.setText(local.isEmpty() ? "📍 Local: Não informado" : "📍 Local: " + local);

            String descricao = assembleia.optString("descricao", "");
            tvDescricao.setText(descricao.isEmpty() ? "📝 Descrição: Sem descrição" : "📝 Descrição: " + descricao);

            if ("morador".equalsIgnoreCase(tipoUsuario)) {
                btnExcluir.setVisibility(View.GONE);
                btnEditar.setVisibility(View.GONE);
            } else {
                btnExcluir.setVisibility(View.VISIBLE);
                btnEditar.setVisibility(View.VISIBLE);
            }

            tvExpandir.setText(isExpanded ? "▲" : "▼");
            layoutExpandido.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

            if (isExpanded) {
                layoutAnexos.removeAllViews();

                try {
                    JSONArray anexos = new JSONArray(assembleia.optString("anexos", "[]"));
                    if (anexos.length() > 0) {
                        tvAnexosLabel.setVisibility(View.VISIBLE);
                        layoutAnexos.setVisibility(View.VISIBLE);

                        for (int i = 0; i < anexos.length(); i++) {
                            String caminho = anexos.getString(i);
                            String nomeArquivo = getFileNameFromPath(caminho);
                            String icone = getIconeArquivo(nomeArquivo);

                            TextView tvAnexo = new TextView(context);
                            tvAnexo.setText(icone + " " + nomeArquivo);
                            tvAnexo.setTextSize(13);
                            tvAnexo.setTextColor(0xFF2196F3);
                            tvAnexo.setPadding(8, 8, 8, 8);
                            tvAnexo.setBackgroundResource(android.R.drawable.list_selector_background);
                            tvAnexo.setOnClickListener(v -> abrirArquivo(caminho));
                            layoutAnexos.addView(tvAnexo);
                        }
                    } else {
                        tvAnexosLabel.setVisibility(View.GONE);
                        layoutAnexos.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    tvAnexosLabel.setVisibility(View.GONE);
                    layoutAnexos.setVisibility(View.GONE);
                }
            } else {
                tvAnexosLabel.setVisibility(View.GONE);
                layoutAnexos.setVisibility(View.GONE);
            }

            tvAssunto.setOnClickListener(v -> {
                if (expandedPosition == position) {
                    expandedPosition = -1;
                    notifyItemChanged(position);
                } else {
                    int oldPosition = expandedPosition;
                    expandedPosition = position;
                    if (oldPosition != -1) notifyItemChanged(oldPosition);
                    notifyItemChanged(position);
                }
            });

            tvExpandir.setOnClickListener(v -> {
                if (expandedPosition == position) {
                    expandedPosition = -1;
                    notifyItemChanged(position);
                } else {
                    int oldPosition = expandedPosition;
                    expandedPosition = position;
                    if (oldPosition != -1) notifyItemChanged(oldPosition);
                    notifyItemChanged(position);
                }
            });

            btnExcluir.setOnClickListener(v -> {
                if (excluirListener != null) {
                    excluirListener.onExcluir(assembleia);
                }
            });

            btnEditar.setOnClickListener(v -> {
                if (editarListener != null) {
                    editarListener.onEditar(assembleia);
                }
            });
        }

        private String formatarDataHora(String dataHoraStr) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                Date date = inputFormat.parse(dataHoraStr);
                return outputFormat.format(date);
            } catch (Exception e) {
                return dataHoraStr;
            }
        }

        private String getIconeArquivo(String fileName) {
            if (fileName == null) return "📎";
            if (fileName.endsWith(".pdf")) return "📄";
            if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) return "📝";
            if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) return "📊";
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png")) return "🖼️";
            if (fileName.endsWith(".mp4")) return "🎥";
            return "📎";
        }

        private String getFileNameFromPath(String path) {
            try {
                Uri uri = Uri.parse(path);
                String result = uri.getLastPathSegment();
                if (result != null && result.contains("/")) {
                    result = result.substring(result.lastIndexOf("/") + 1);
                }
                return result != null ? result : "Anexo";
            } catch (Exception e) {
                return "Anexo";
            }
        }

        private void abrirArquivo(String caminhoArquivo) {
            com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage.abrir(context, caminhoArquivo);
        }

        private String getMimeType(String fileName) {
            if (fileName.endsWith(".pdf")) return "application/pdf";
            if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) return "application/msword";
            if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) return "application/vnd.ms-excel";
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
            if (fileName.endsWith(".png")) return "image/png";
            if (fileName.endsWith(".mp4")) return "video/mp4";
            return "*/*";
        }
    }
}