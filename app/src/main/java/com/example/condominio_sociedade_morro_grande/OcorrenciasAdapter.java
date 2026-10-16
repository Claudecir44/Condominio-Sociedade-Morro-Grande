package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.ActivityNotFoundException;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OcorrenciasAdapter extends RecyclerView.Adapter<OcorrenciasAdapter.ViewHolder> {

    private final List<JSONObject> listaOcorrencias;
    private int expandedPosition = -1;

    public OcorrenciasAdapter(List<JSONObject> listaOcorrencias) {
        this.listaOcorrencias = listaOcorrencias;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ocorrencia, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject ocorrencia = listaOcorrencias.get(position);
        boolean isExpanded = position == expandedPosition;

        holder.bind(ocorrencia, isExpanded);

        holder.layoutPrincipal.setOnClickListener(v -> {
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
    }

    @Override
    public int getItemCount() {
        return listaOcorrencias.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        LinearLayout layoutPrincipal;
        TextView textTipo, textStatus, textDataHora;
        TextView textDescricaoTitulo, textDescricaoExpandida;
        TextView textEnvolvidosTitulo, textEnvolvidos;
        TextView textAnexosTitulo;
        LinearLayout layoutExpandido, layoutAnexos, layoutBotoes;
        Button buttonEditar, buttonExcluir;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutPrincipal = itemView.findViewById(R.id.layoutOcorrenciaItem);
            textTipo = itemView.findViewById(R.id.textTipoOcorrencia);
            textStatus = itemView.findViewById(R.id.textStatusOcorrencia);
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

                String tipo = ocorrencia.optString("tipo", "Sem tipo");
                textTipo.setText(tipo);

                String status = ocorrencia.optString("status", "pendente");
                textStatus.setText(status.toUpperCase());
                atualizarCorStatus(status);

                String dataHora = ocorrencia.optString("datahora", "");
                String dataFormatada = formatarDataHora(dataHora);
                textDataHora.setText("📅 " + dataFormatada);

                String descricao = ocorrencia.optString("descricao", "");
                textDescricaoExpandida.setText(descricao.isEmpty() ? "Sem descrição" : descricao);

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
                        String originalUriStr = anexosArray.getString(i);
                        Uri originalUri = Uri.parse(originalUriStr);

                        String fileName = "Anexo_" + (i + 1) + "_" + new File(originalUri.getPath()).getName();
                        File internalFile = copyToInternal(context, originalUri, fileName);

                        if (internalFile != null) {
                            TextView anexItem = new TextView(context);
                            String icone = getIconeArquivo(internalFile.getName());
                            anexItem.setText(icone + " " + internalFile.getName());
                            anexItem.setTextSize(13);
                            anexItem.setTextColor(0xFF2196F3);
                            anexItem.setPadding(8, 4, 8, 4);
                            anexItem.setBackgroundResource(android.R.drawable.list_selector_background);

                            anexItem.setOnClickListener(v -> {
                                try {
                                    Uri fileUri = FileProvider.getUriForFile(context,
                                            context.getPackageName() + ".provider", internalFile);
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    intent.setDataAndType(fileUri, getMimeType(internalFile.getName()));
                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    context.startActivity(Intent.createChooser(intent, "Abrir com"));
                                } catch (ActivityNotFoundException e) {
                                    Toast.makeText(context, "Não há aplicativo disponível para abrir este arquivo.", Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    Toast.makeText(context, "Erro ao abrir anexo.", Toast.LENGTH_SHORT).show();
                                    e.printStackTrace();
                                }
                            });

                            layoutAnexos.addView(anexItem);
                        }
                    }
                } else {
                    textAnexosTitulo.setVisibility(View.GONE);
                    layoutAnexos.setVisibility(View.GONE);
                }

                textDescricaoTitulo.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                textDescricaoExpandida.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                layoutBotoes.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                layoutExpandido.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

                buttonEditar.setOnClickListener(v -> {
                    Toast.makeText(context, "Editar ocorrência", Toast.LENGTH_SHORT).show();
                });

                buttonExcluir.setOnClickListener(v -> {
                    Toast.makeText(context, "Excluir ocorrência", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void atualizarCorStatus(String status) {
            int cor;
            switch (status.toLowerCase()) {
                case "resolvido":
                case "concluído":
                    cor = 0xFF4CAF50;
                    break;
                case "em andamento":
                case "processando":
                    cor = 0xFFFF9800;
                    break;
                default:
                    cor = 0xFFF44336;
                    break;
            }
            textStatus.setBackgroundColor(cor);
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
            if (fileName.endsWith(".pdf")) return "📄";
            if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) return "📝";
            if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) return "📊";
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png")) return "🖼️";
            if (fileName.endsWith(".mp4")) return "🎥";
            return "📎";
        }

        private File copyToInternal(Context context, Uri uri, String fileName) {
            try {
                File file = new File(context.getFilesDir(), fileName);
                if (file.exists()) return file;

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