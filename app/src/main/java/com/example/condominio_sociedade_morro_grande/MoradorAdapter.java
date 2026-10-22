package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

public class MoradorAdapter extends RecyclerView.Adapter<MoradorAdapter.MoradorViewHolder> {

    public interface OnItemClickListener {
        void onEditar(JSONObject morador, int position);
        void onExcluir(JSONObject morador, int position);
    }

    private Context context;
    private List<JSONObject> moradores;
    private OnItemClickListener listener;
    // Morador de visualização: só vê a lista, sem Editar/Excluir
    private boolean somenteLeitura = false;

    public MoradorAdapter(Context context, List<JSONObject> moradores, OnItemClickListener listener) {
        this.context = context;
        this.moradores = moradores;
        this.listener = listener;
    }

    public void setSomenteLeitura(boolean somenteLeitura) {
        this.somenteLeitura = somenteLeitura;
    }

    @NonNull
    @Override
    public MoradorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_morador, parent, false);
        return new MoradorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MoradorViewHolder holder, int position) {
        JSONObject morador = moradores.get(position);
        String codigo = morador.optString("cod");
        String nome = morador.optString("nome");
        String cpf = morador.optString("cpf");
        String email = morador.optString("email", "");
        String telefone = morador.optString("telefone", "");
        String rua = morador.optString("rua", "");
        String numero = morador.optString("numero", "");
        String bairro = morador.optString("bairro", "");
        String cidade = morador.optString("cidade", "");
        String estado = morador.optString("estado", "");
        String quadra = morador.optString("quadra", "");
        String lote = morador.optString("lote", "");
        String imagePath = morador.optString("foto", "");

        holder.tvNomeMorador.setText(nome != null && !nome.isEmpty() ? nome : "Nome não informado");
        holder.tvCodigoMorador.setText("📋 Código: " + codigo);

        if (telefone != null && !telefone.isEmpty()) {
            holder.tvTelefoneMorador.setText("📞 Telefone: " + telefone);
            holder.tvTelefoneMorador.setVisibility(View.VISIBLE);
        } else {
            holder.tvTelefoneMorador.setText("📞 Telefone: Não informado");
            holder.tvTelefoneMorador.setVisibility(View.VISIBLE);
        }

        if (cpf != null && !cpf.isEmpty()) {
            holder.tvCpfMorador.setText("📄 CPF: " + cpf);
            holder.tvCpfMorador.setVisibility(View.VISIBLE);
        } else {
            holder.tvCpfMorador.setVisibility(View.GONE);
        }

        StringBuilder endereco = new StringBuilder();
        boolean temEndereco = false;

        if (rua != null && !rua.isEmpty()) {
            endereco.append(rua);
            temEndereco = true;
        }
        if (numero != null && !numero.isEmpty()) {
            if (temEndereco) endereco.append(", ");
            endereco.append(numero);
            temEndereco = true;
        }
        if (bairro != null && !bairro.isEmpty()) {
            if (temEndereco) endereco.append(" - ");
            endereco.append(bairro);
            temEndereco = true;
        }
        if (cidade != null && !cidade.isEmpty()) {
            if (temEndereco) endereco.append("\n📍 ");
            endereco.append(cidade);
            temEndereco = true;
        }
        if (estado != null && !estado.isEmpty()) {
            if (temEndereco && !endereco.toString().contains(cidade)) endereco.append(" - ");
            endereco.append(estado);
            temEndereco = true;
        }

        if (temEndereco) {
            holder.tvEnderecoMorador.setText("📍 Endereço: " + endereco.toString());
            holder.tvEnderecoMorador.setVisibility(View.VISIBLE);
        } else {
            holder.tvEnderecoMorador.setVisibility(View.GONE);
        }

        boolean temQuadra = (quadra != null && !quadra.isEmpty());
        boolean temLote = (lote != null && !lote.isEmpty());

        if (temQuadra || temLote) {
            if (temQuadra) {
                holder.tvQuadraMorador.setText("📐 Quadra: " + quadra);
                holder.tvQuadraMorador.setVisibility(View.VISIBLE);
            } else {
                holder.tvQuadraMorador.setVisibility(View.GONE);
            }

            if (temLote) {
                holder.tvLoteMorador.setText("🏷️ Lote: " + lote);
                holder.tvLoteMorador.setVisibility(View.VISIBLE);
            } else {
                holder.tvLoteMorador.setVisibility(View.GONE);
            }
            holder.layoutQuadraLote.setVisibility(View.VISIBLE);
        } else {
            holder.layoutQuadraLote.setVisibility(View.GONE);
        }

        if (email != null && !email.isEmpty()) {
            holder.tvEmailMorador.setText("📧 Email: " + email);
            holder.tvEmailMorador.setVisibility(View.VISIBLE);
        } else {
            holder.tvEmailMorador.setVisibility(View.GONE);
        }

        // Documentos anexados ao cadastro: abrir, baixar, exportar
        ListaAnexos.mostrar(holder.layoutAnexosItem, ListaAnexos.deValor(morador.opt("anexos")));

        carregarFoto(holder.imageMoradorItem, imagePath);

        int botoes = somenteLeitura ? View.GONE : View.VISIBLE;
        holder.btnEditarMorador.setVisibility(botoes);
        holder.btnExcluirMorador.setVisibility(botoes);

        holder.btnEditarMorador.setOnClickListener(v -> {
            if (listener != null) listener.onEditar(morador, position);
        });

        holder.btnExcluirMorador.setOnClickListener(v -> {
            if (listener != null) listener.onExcluir(morador, position);
        });
    }

    // ==================== MÉTODOS LOCAIS PARA IMAGENS (sem BackupUtil) ====================

    private void carregarFoto(ImageView imageView, String imagePath) {
        com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage.carregarImagem(
                imageView, imagePath, android.R.drawable.ic_menu_camera);
    }

    // ==================== FIM DOS MÉTODOS LOCAIS ====================

    @Override
    public int getItemCount() {
        return moradores.size();
    }

    public void atualizarListaCompleta(List<JSONObject> novaLista) {
        moradores.clear();
        moradores.addAll(novaLista);
        notifyDataSetChanged();
    }

    public void removerItemPorCodigo(String codigo) {
        for (int i = 0; i < moradores.size(); i++) {
            if (moradores.get(i).optString("cod").equals(codigo)) {
                moradores.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    public void atualizarItem(JSONObject moradorAtualizado, int position) {
        if (position >= 0 && position < moradores.size()) {
            moradores.set(position, moradorAtualizado);
            notifyItemChanged(position);
        }
    }

    public int encontrarPosicaoPorCodigo(String codigo) {
        for (int i = 0; i < moradores.size(); i++) {
            if (moradores.get(i).optString("cod").equals(codigo)) {
                return i;
            }
        }
        return -1;
    }

    public static class MoradorViewHolder extends RecyclerView.ViewHolder {
        ImageView imageMoradorItem;
        TextView tvNomeMorador;
        TextView tvCodigoMorador;
        TextView tvTelefoneMorador;
        TextView tvCpfMorador;
        TextView tvEnderecoMorador;
        LinearLayout layoutQuadraLote;
        TextView tvQuadraMorador;
        TextView tvLoteMorador;
        TextView tvEmailMorador;
        LinearLayout layoutAnexosItem;
        Button btnEditarMorador;
        Button btnExcluirMorador;

        public MoradorViewHolder(@NonNull View itemView) {
            super(itemView);
            imageMoradorItem = itemView.findViewById(R.id.imageMoradorItem);
            tvNomeMorador = itemView.findViewById(R.id.tvNomeMorador);
            tvCodigoMorador = itemView.findViewById(R.id.tvCodigoMorador);
            tvTelefoneMorador = itemView.findViewById(R.id.tvTelefoneMorador);
            tvCpfMorador = itemView.findViewById(R.id.tvCpfMorador);
            tvEnderecoMorador = itemView.findViewById(R.id.tvEnderecoMorador);
            layoutQuadraLote = itemView.findViewById(R.id.layoutQuadraLote);
            tvQuadraMorador = itemView.findViewById(R.id.tvQuadraMorador);
            tvLoteMorador = itemView.findViewById(R.id.tvLoteMorador);
            tvEmailMorador = itemView.findViewById(R.id.tvEmailMorador);
            layoutAnexosItem = itemView.findViewById(R.id.layoutAnexosItem);
            btnEditarMorador = itemView.findViewById(R.id.btnEditarMorador);
            btnExcluirMorador = itemView.findViewById(R.id.btnExcluirMorador);
        }
    }
}