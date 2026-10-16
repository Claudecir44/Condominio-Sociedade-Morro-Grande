package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Manutencao;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ManutencaoAdapter extends RecyclerView.Adapter<ManutencaoAdapter.ManutencaoViewHolder> {

    public interface OnItemClickListener {
        void onEditar(int position);
        void onExcluir(int position);
    }

    private final Context context;
    private final List<Manutencao> lista;
    private final OnItemClickListener listener;

    public ManutencaoAdapter(Context context, List<Manutencao> lista, OnItemClickListener listener) {
        this.context = context;
        this.lista = lista;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ManutencaoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_manutencao, parent, false);
        return new ManutencaoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ManutencaoViewHolder holder, int position) {
        Manutencao m = lista.get(position);

        holder.tvTipoManutencao.setText(m.getTipo() != null ? m.getTipo() : "Manutenção");

        String dataHora = m.getDataHora() != null ? m.getDataHora() : "";
        String dataFormatada = formatarDataHora(dataHora);
        holder.tvDataHoraManutencao.setText("📅 " + dataFormatada);

        String servico = m.getServico() != null ? m.getServico() : "Não informado";
        holder.tvServicoManutencao.setText("🔧 Serviço: " + servico);

        String local = m.getLocal() != null ? m.getLocal() : "Não informado";
        holder.tvLocalManutencao.setText("📍 Local: " + local);

        String responsavel = m.getResponsavel() != null ? m.getResponsavel() : "Não informado";
        holder.tvResponsavelManutencao.setText("👤 Responsável: " + responsavel);

        String valor = m.getValor() != null ? m.getValor() : "0,00";
        holder.tvValorManutencao.setText("💰 Valor: R$ " + valor);

        String notas = m.getNotas() != null ? m.getNotas() : "";
        if (!notas.isEmpty()) {
            holder.tvNotasManutencao.setText("📝 Observações: " + notas);
            holder.tvNotasManutencao.setVisibility(View.VISIBLE);
        } else {
            holder.tvNotasManutencao.setVisibility(View.GONE);
        }

        holder.layoutAnexos.removeAllViews();

        if (m.getAnexos() != null && !m.getAnexos().isEmpty()) {
            holder.tvAnexosLabel.setVisibility(View.VISIBLE);
            holder.layoutAnexos.setVisibility(View.VISIBLE);

            for (String anexoUri : m.getAnexos()) {
                TextView tvAnexo = new TextView(context);
                String fileName = getFileNameFromUri(Uri.parse(anexoUri));
                String icone = getIconeArquivo(fileName);
                tvAnexo.setText(icone + " " + fileName);
                tvAnexo.setTextSize(13);
                tvAnexo.setTextColor(0xFF2196F3);
                tvAnexo.setPadding(8, 8, 8, 8);
                tvAnexo.setBackgroundResource(android.R.drawable.list_selector_background);

                tvAnexo.setOnClickListener(v -> abrirAnexo(Uri.parse(anexoUri)));
                holder.layoutAnexos.addView(tvAnexo);
            }
        } else {
            holder.tvAnexosLabel.setVisibility(View.GONE);
            holder.layoutAnexos.setVisibility(View.GONE);
        }

        holder.btnEditar.setOnClickListener(v -> {
            if (listener != null) listener.onEditar(position);
        });

        holder.btnExcluir.setOnClickListener(v -> {
            if (listener != null) listener.onExcluir(position);
        });
    }

    @Override
    public int getItemCount() {
        return lista != null ? lista.size() : 0;
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
        if (fileName.endsWith(".zip") || fileName.endsWith(".rar")) return "🗜️";
        return "📎";
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private void abrirAnexo(Uri uri) {
        com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage.abrir(context, uri.toString());
    }

    private String getMimeTypeFromUri(Uri uri) {
        String fileName = getFileNameFromUri(uri);
        return getMimeType(fileName);
    }

    private String getMimeType(String fileName) {
        if (fileName == null) return "*/*";

        String extension = "";
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot != -1 && lastDot < fileName.length() - 1) {
            extension = fileName.substring(lastDot + 1).toLowerCase();
        }

        switch (extension) {
            case "pdf": return "application/pdf";
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "txt": return "text/plain";
            case "zip": return "application/zip";
            case "rar": return "application/x-rar-compressed";
            default: return "*/*";
        }
    }

    static class ManutencaoViewHolder extends RecyclerView.ViewHolder {
        TextView tvTipoManutencao;
        TextView tvDataHoraManutencao;
        TextView tvServicoManutencao;
        TextView tvLocalManutencao;
        TextView tvResponsavelManutencao;
        TextView tvValorManutencao;
        TextView tvNotasManutencao;
        TextView tvAnexosLabel;
        LinearLayout layoutAnexos;
        Button btnEditar, btnExcluir;

        public ManutencaoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTipoManutencao = itemView.findViewById(R.id.tvTipoManutencao);
            tvDataHoraManutencao = itemView.findViewById(R.id.tvDataHoraManutencao);
            tvServicoManutencao = itemView.findViewById(R.id.tvServicoManutencao);
            tvLocalManutencao = itemView.findViewById(R.id.tvLocalManutencao);
            tvResponsavelManutencao = itemView.findViewById(R.id.tvResponsavelManutencao);
            tvValorManutencao = itemView.findViewById(R.id.tvValorManutencao);
            tvNotasManutencao = itemView.findViewById(R.id.tvNotasManutencao);
            tvAnexosLabel = itemView.findViewById(R.id.tvAnexosLabel);
            layoutAnexos = itemView.findViewById(R.id.layoutAnexosManutencao);
            btnEditar = itemView.findViewById(R.id.btnEditarManutencao);
            btnExcluir = itemView.findViewById(R.id.btnExcluirManutencao);
        }
    }
}