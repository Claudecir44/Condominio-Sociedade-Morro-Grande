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
import androidx.recyclerview.widget.RecyclerView;


import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Despesa;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DespesaAdapter extends RecyclerView.Adapter<DespesaAdapter.DespesaViewHolder> {

    private final List<Despesa> lista;
    private final Context context;
    private final OnDespesaListener listener;
    private final String tipoUsuario;

    public interface OnDespesaListener {
        void onEditar(Despesa despesa, int position);
        void onExcluir(Despesa despesa, int position);
    }

    public DespesaAdapter(Context context, List<Despesa> lista, OnDespesaListener listener, String tipoUsuario) {
        this.context = context;
        this.lista = lista;
        this.listener = listener;
        this.tipoUsuario = tipoUsuario;
    }

    @NonNull
    @Override
    public DespesaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_despesa, parent, false);
        return new DespesaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DespesaViewHolder holder, int position) {
        holder.bind(lista.get(position), position);
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    class DespesaViewHolder extends RecyclerView.ViewHolder {

        TextView textDataHora, textNome, textDescricao, textValor, tvAnexosLabel;
        LinearLayout layoutAnexos;
        Button btnEditar, btnExcluir;

        public DespesaViewHolder(@NonNull View itemView) {
            super(itemView);
            textDataHora = itemView.findViewById(R.id.textDataHora);
            textNome = itemView.findViewById(R.id.textNomeDespesa);
            textDescricao = itemView.findViewById(R.id.textDescricaoDespesa);
            textValor = itemView.findViewById(R.id.txtValor);
            tvAnexosLabel = itemView.findViewById(R.id.tvAnexosLabel);
            layoutAnexos = itemView.findViewById(R.id.layoutAnexos);
            btnEditar = itemView.findViewById(R.id.btnEditarDespesa);
            btnExcluir = itemView.findViewById(R.id.btnExcluirDespesa);
        }

        void bind(Despesa despesa, int position) {
            String dataFormatada = formatarDataHora(despesa.getDataHora());
            textDataHora.setText("📅 " + dataFormatada);
            textNome.setText(despesa.getNome() != null ? despesa.getNome() : "Despesa");

            String descricao = despesa.getDescricao() != null ? despesa.getDescricao() : "";
            textDescricao.setText(descricao.isEmpty() ? "Sem descrição" : "📝 " + descricao);

            NumberFormat formatoDinheiro = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
            textValor.setText(formatoDinheiro.format(despesa.getValor()));

            if ("morador".equalsIgnoreCase(tipoUsuario)) {
                btnEditar.setVisibility(View.GONE);
                btnExcluir.setVisibility(View.GONE);
            } else {
                btnEditar.setVisibility(View.VISIBLE);
                btnExcluir.setVisibility(View.VISIBLE);
            }

            layoutAnexos.removeAllViews();

            if (despesa.getAnexos() != null && !despesa.getAnexos().isEmpty()) {
                tvAnexosLabel.setVisibility(View.VISIBLE);
                layoutAnexos.setVisibility(View.VISIBLE);

                for (String uriString : despesa.getAnexos()) {
                    try {
                        Uri uri = Uri.parse(uriString);
                        String nomeArquivo = getFileNameFromUri(uri);
                        String icone = getIconeArquivo(nomeArquivo);

                        TextView tvAnexo = new TextView(context);
                        tvAnexo.setText(icone + " " + nomeArquivo);
                        tvAnexo.setTextSize(13);
                        tvAnexo.setTextColor(0xFF2196F3);
                        tvAnexo.setPadding(8, 8, 8, 8);
                        tvAnexo.setBackgroundResource(android.R.drawable.list_selector_background);
                        tvAnexo.setOnClickListener(v -> abrirArquivo(uri));
                        layoutAnexos.addView(tvAnexo);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                tvAnexosLabel.setVisibility(View.GONE);
                layoutAnexos.setVisibility(View.GONE);
            }

            btnEditar.setOnClickListener(v -> {
                if (listener != null) listener.onEditar(despesa, position);
            });

            btnExcluir.setOnClickListener(v -> {
                if (listener != null) listener.onExcluir(despesa, position);
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

        private String getFileNameFromUri(Uri uri) {
            String result = uri.getLastPathSegment();
            if (result != null && result.contains("/")) {
                result = result.substring(result.lastIndexOf("/") + 1);
            }
            return result != null ? result : "Anexo";
        }

        private void abrirArquivo(Uri uri) {
            com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage.abrir(context, uri.toString());
        }

        private String getMimeType(String caminho) {
            if (caminho.endsWith(".pdf")) return "application/pdf";
            if (caminho.endsWith(".doc") || caminho.endsWith(".docx")) return "application/msword";
            if (caminho.endsWith(".xls") || caminho.endsWith(".xlsx")) return "application/vnd.ms-excel";
            if (caminho.endsWith(".jpg") || caminho.endsWith(".jpeg")) return "image/jpeg";
            if (caminho.endsWith(".png")) return "image/png";
            if (caminho.endsWith(".mp4")) return "video/mp4";
            return "*/*";
        }
    }
}