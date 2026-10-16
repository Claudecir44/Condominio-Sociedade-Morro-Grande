package com.cjstudio.condominio_sociedade_morro_grande;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;

/**
 * Tirar foto com a câmera ou escolher da galeria. Precisa ser criado no onCreate da Activity
 * (registra os launchers). A foto da câmera fica num arquivo temporário do cache do app.
 */
public class CapturaFoto {

    public interface AoEscolher {
        void foto(Uri uri);
    }

    private final AppCompatActivity activity;
    private final AoEscolher aoEscolher;
    private final ActivityResultLauncher<Uri> camera;
    private final ActivityResultLauncher<String> galeria;
    private final ActivityResultLauncher<String> permissao;
    private Uri fotoTemporaria;

    public CapturaFoto(AppCompatActivity activity, AoEscolher aoEscolher) {
        this.activity = activity;
        this.aoEscolher = aoEscolher;
        camera = activity.registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (Boolean.TRUE.equals(ok) && fotoTemporaria != null) aoEscolher.foto(fotoTemporaria);
        });
        galeria = activity.registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) aoEscolher.foto(uri);
        });
        permissao = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedida -> {
            if (Boolean.TRUE.equals(concedida)) {
                abrirCamera();
            } else {
                Toast.makeText(activity, "Sem permissão da câmera não dá para tirar a foto.", Toast.LENGTH_LONG).show();
            }
        });
    }

    public void tirarFoto() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            abrirCamera();
        } else {
            permissao.launch(Manifest.permission.CAMERA);
        }
    }

    public void escolherDaGaleria() {
        galeria.launch("image/*");
    }

    private void abrirCamera() {
        try {
            File pasta = new File(activity.getCacheDir(), "fotos");
            if (!pasta.exists()) pasta.mkdirs();
            File arquivo = new File(pasta, "foto_" + System.currentTimeMillis() + ".jpg");
            fotoTemporaria = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".provider", arquivo);
            camera.launch(fotoTemporaria);
        } catch (Exception e) {
            Toast.makeText(activity, "Não foi possível abrir a câmera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
