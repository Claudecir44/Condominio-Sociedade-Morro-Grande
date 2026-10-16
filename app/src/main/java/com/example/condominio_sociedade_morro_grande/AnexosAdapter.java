package com.cjstudio.condominio_sociedade_morro_grande;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;

import java.util.List;

public class AnexosAdapter extends RecyclerView.Adapter<AnexosAdapter.AnexoViewHolder> {

    private final List<Uri> anexos;

    public AnexosAdapter(List<Uri> anexos) {
        this.anexos = anexos;
    }

    @NonNull
    @Override
    public AnexoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new AnexoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AnexoViewHolder holder, int position) {
        Uri anexo = anexos.get(position);
        String caminho = anexo.toString();
        holder.textView.setText("📎 " + ArquivosStorage.nomeDoAnexo(holder.itemView.getContext(), caminho));
        // Mesmo menu das outras listas: abrir, baixar, exportar
        holder.itemView.setOnClickListener(v -> ListaAnexos.opcoes(v.getContext(), caminho));
    }

    @Override
    public int getItemCount() {
        return anexos.size();
    }

    static class AnexoViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public AnexoViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}