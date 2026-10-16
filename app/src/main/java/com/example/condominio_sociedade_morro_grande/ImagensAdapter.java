package com.cjstudio.condominio_sociedade_morro_grande;

import android.net.Uri;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ImagensAdapter extends RecyclerView.Adapter<ImagensAdapter.ImagemViewHolder> {

    private final List<Uri> imagens;

    public ImagensAdapter(List<Uri> imagens) {
        this.imagens = imagens;
    }

    @NonNull
    @Override
    public ImagemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView img = new ImageView(parent.getContext());
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(150, 150);
        params.setMargins(8, 8, 8, 8);
        img.setLayoutParams(params);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return new ImagemViewHolder(img);
    }

    @Override
    public void onBindViewHolder(@NonNull ImagemViewHolder holder, int position) {
        holder.imageView.setImageURI(imagens.get(position));
    }

    @Override
    public int getItemCount() {
        return imagens.size();
    }

    static class ImagemViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ImagemViewHolder(@NonNull ImageView itemView) {
            super(itemView);
            imageView = itemView;
        }
    }
}