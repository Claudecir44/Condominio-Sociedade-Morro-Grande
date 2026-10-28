package com.cjstudio.condominio_sociedade_morro_grande;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;


import java.io.File;

public class ImageViewerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        ImageView imageView = findViewById(R.id.imageViewFull);
        ImageView btnClose = findViewById(R.id.btnClose);

        String imagePath = getIntent().getStringExtra("imagePath");
        if (imagePath != null && imagePath.startsWith("http")) {
            // Foto do Storage (ex.: imagem enviada no chat)
            com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage.carregarImagem(
                    imageView, imagePath, android.R.drawable.ic_menu_gallery);
        } else if (imagePath != null) {
            File imageFile = new File(imagePath);
            if (imageFile.exists()) {
                Glide.with(this)
                        .load(imageFile)
                        .into(imageView);
            }
        }

        btnClose.setOnClickListener(v -> finish());
    }
}