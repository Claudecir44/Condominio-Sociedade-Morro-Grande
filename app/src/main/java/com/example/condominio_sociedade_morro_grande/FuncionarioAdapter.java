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


import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Funcionario;

import java.util.List;

public class FuncionarioAdapter extends RecyclerView.Adapter<FuncionarioAdapter.ViewHolder> {

    private List<Funcionario> lista;
    private Context context;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onEditar(Funcionario funcionario, int position);
        void onExcluir(Funcionario funcionario, int position);
    }

    public FuncionarioAdapter(List<Funcionario> lista, OnItemClickListener listener) {
        this.lista = lista;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_funcionario, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Funcionario f = lista.get(position);

        holder.tvNomeFuncionario.setText(f.getNome() != null && !f.getNome().isEmpty() ? f.getNome() : "Nome não informado");

        if (f.getCargo() != null && !f.getCargo().isEmpty()) {
            holder.tvCargoFuncionario.setText("📌 Cargo: " + f.getCargo());
            holder.tvCargoFuncionario.setVisibility(View.VISIBLE);
        } else {
            holder.tvCargoFuncionario.setVisibility(View.GONE);
        }

        if (f.getTelefone() != null && !f.getTelefone().isEmpty()) {
            holder.tvTelefoneFuncionario.setText("📞 Telefone: " + f.getTelefone());
            holder.tvTelefoneFuncionario.setVisibility(View.VISIBLE);
        } else {
            holder.tvTelefoneFuncionario.setText("📞 Telefone: Não informado");
            holder.tvTelefoneFuncionario.setVisibility(View.VISIBLE);
        }

        StringBuilder endereco = new StringBuilder();
        boolean temEndereco = false;

        if (f.getRua() != null && !f.getRua().isEmpty()) {
            endereco.append(f.getRua());
            temEndereco = true;
        }
        if (f.getNumero() != null && !f.getNumero().isEmpty()) {
            if (temEndereco) endereco.append(", ");
            endereco.append(f.getNumero());
            temEndereco = true;
        }
        if (f.getBairro() != null && !f.getBairro().isEmpty()) {
            if (temEndereco) endereco.append(" - ");
            endereco.append(f.getBairro());
            temEndereco = true;
        }
        if (f.getCidade() != null && !f.getCidade().isEmpty()) {
            if (temEndereco) endereco.append("\n📍 ");
            endereco.append(f.getCidade());
            temEndereco = true;
        }
        if (f.getEstado() != null && !f.getEstado().isEmpty()) {
            if (temEndereco && !endereco.toString().contains(f.getCidade())) endereco.append(" - ");
            endereco.append(f.getEstado());
            temEndereco = true;
        }
        if (f.getCep() != null && !f.getCep().isEmpty()) {
            if (temEndereco) endereco.append(", CEP: ");
            endereco.append(f.getCep());
            temEndereco = true;
        }

        if (temEndereco) {
            holder.tvEnderecoCompleto.setText("📍 Endereço: " + endereco.toString());
            holder.tvEnderecoCompleto.setVisibility(View.VISIBLE);
        } else {
            holder.tvEnderecoCompleto.setVisibility(View.GONE);
        }

        boolean temRg = (f.getRg() != null && !f.getRg().isEmpty());
        boolean temCpf = (f.getCpf() != null && !f.getCpf().isEmpty());

        if (temRg || temCpf) {
            if (temRg) {
                holder.tvRGFuncionario.setText("📄 RG: " + f.getRg());
                holder.tvRGFuncionario.setVisibility(View.VISIBLE);
            } else {
                holder.tvRGFuncionario.setVisibility(View.GONE);
            }

            if (temCpf) {
                holder.tvCPFFuncionario.setText("📄 CPF: " + f.getCpf());
                holder.tvCPFFuncionario.setVisibility(View.VISIBLE);
            } else {
                holder.tvCPFFuncionario.setVisibility(View.GONE);
            }
            holder.layoutDocumentos.setVisibility(View.VISIBLE);
        } else {
            holder.layoutDocumentos.setVisibility(View.GONE);
        }

        if (f.getCargaHoraria() != null && !f.getCargaHoraria().isEmpty()) {
            holder.tvCargaHoraria.setText("⏱️ Carga Horária: " + f.getCargaHoraria() + "h");
            holder.tvCargaHoraria.setVisibility(View.VISIBLE);
        } else {
            holder.tvCargaHoraria.setVisibility(View.GONE);
        }

        if (f.getTurno() != null && !f.getTurno().isEmpty()) {
            holder.tvTurno.setText("🔄 Turno: " + f.getTurno());
            holder.tvTurno.setVisibility(View.VISIBLE);
        } else {
            holder.tvTurno.setVisibility(View.GONE);
        }

        StringBuilder horario = new StringBuilder();
        boolean temHorario = false;

        if (f.getHoraEntrada() != null && !f.getHoraEntrada().isEmpty()) {
            horario.append(f.getHoraEntrada());
            temHorario = true;
        }
        if (f.getHoraSaida() != null && !f.getHoraSaida().isEmpty()) {
            if (temHorario) horario.append(" às ");
            horario.append(f.getHoraSaida());
            temHorario = true;
        }

        if (temHorario) {
            holder.tvHorarioTrabalho.setText("⏰ Horário: " + horario.toString());
            holder.tvHorarioTrabalho.setVisibility(View.VISIBLE);
        } else {
            holder.tvHorarioTrabalho.setVisibility(View.GONE);
        }

        if (f.getEmail() != null && !f.getEmail().isEmpty()) {
            holder.tvEmail.setText("📧 Email: " + f.getEmail());
            holder.tvEmail.setVisibility(View.VISIBLE);
        } else {
            holder.tvEmail.setVisibility(View.GONE);
        }

        com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage.carregarImagem(
                holder.imgFotoFuncionario, f.getImagemUri(), android.R.drawable.ic_menu_camera);

        holder.btnEditarFuncionario.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditar(f, position);
            }
        });

        holder.btnExcluirFuncionario.setOnClickListener(v -> {
            if (listener != null) {
                listener.onExcluir(f, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return lista != null ? lista.size() : 0;
    }

    public void atualizarLista(List<Funcionario> novaLista) {
        this.lista = novaLista;
        notifyDataSetChanged();
    }

    public void removerItem(int position) {
        if (lista != null && position >= 0 && position < lista.size()) {
            lista.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, lista.size() - position);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgFotoFuncionario;
        TextView tvNomeFuncionario;
        TextView tvCargoFuncionario;
        TextView tvTelefoneFuncionario;
        TextView tvEnderecoCompleto;
        LinearLayout layoutDocumentos;
        TextView tvRGFuncionario;
        TextView tvCPFFuncionario;
        TextView tvCargaHoraria;
        TextView tvTurno;
        TextView tvHorarioTrabalho;
        TextView tvEmail;
        Button btnEditarFuncionario;
        Button btnExcluirFuncionario;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFotoFuncionario = itemView.findViewById(R.id.imgFotoFuncionario);
            tvNomeFuncionario = itemView.findViewById(R.id.tvNomeFuncionario);
            tvCargoFuncionario = itemView.findViewById(R.id.tvCargoFuncionario);
            tvTelefoneFuncionario = itemView.findViewById(R.id.tvTelefoneFuncionario);
            tvEnderecoCompleto = itemView.findViewById(R.id.tvEnderecoCompleto);
            layoutDocumentos = itemView.findViewById(R.id.layoutDocumentos);
            tvRGFuncionario = itemView.findViewById(R.id.tvRGFuncionario);
            tvCPFFuncionario = itemView.findViewById(R.id.tvCPFFuncionario);
            tvCargaHoraria = itemView.findViewById(R.id.tvCargaHoraria);
            tvTurno = itemView.findViewById(R.id.tvTurno);
            tvHorarioTrabalho = itemView.findViewById(R.id.tvHorarioTrabalho);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            btnEditarFuncionario = itemView.findViewById(R.id.btnEditarFuncionario);
            btnExcluirFuncionario = itemView.findViewById(R.id.btnExcluirFuncionario);
        }
    }
}