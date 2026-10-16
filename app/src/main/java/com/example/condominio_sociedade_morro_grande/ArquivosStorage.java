package com.cjstudio.condominio_sociedade_morro_grande.data.firestore;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Anexos e fotos no Firebase Storage, para todos os aparelhos verem o mesmo arquivo.
 *
 * Os registros guardam o link (https) do arquivo. Registros antigos podem ter caminho local
 * ("file://", "content://" ou caminho de arquivo): abrir/carregar continua funcionando para eles
 * no aparelho onde foram anexados.
 */
public final class ArquivosStorage {

    private ArquivosStorage() { }

    public static boolean ehLink(String caminho) {
        return caminho != null && (caminho.startsWith("https://") || caminho.startsWith("http://"));
    }

    /**
     * Envia para {modulo}/{uuid}/{nome} cada arquivo que ainda não está no Storage e devolve a
     * lista final, na mesma ordem, só com links.
     */
    public static void enviar(Context context, String modulo, List<String> caminhos,
                              ColecaoFirestore.Callback<List<String>> callback) {
        List<String> resultado = new ArrayList<>(caminhos);
        enviarAPartirDe(context.getApplicationContext(), modulo, resultado, 0, callback);
    }

    /** Atalho para um arquivo só (foto). Devolve null se o caminho for vazio. */
    public static void enviarUm(Context context, String modulo, String caminho,
                                ColecaoFirestore.Callback<String> callback) {
        if (caminho == null || caminho.isEmpty() || ehLink(caminho)) {
            callback.onSucesso(caminho);
            return;
        }
        List<String> um = new ArrayList<>();
        um.add(caminho);
        enviar(context, modulo, um, new ColecaoFirestore.Callback<List<String>>() {
            @Override
            public void onSucesso(List<String> links) {
                callback.onSucesso(links.get(0));
            }

            @Override
            public void onErro(Exception e) {
                callback.onErro(e);
            }
        });
    }

    private static void enviarAPartirDe(Context context, String modulo, List<String> lista, int indice,
                                        ColecaoFirestore.Callback<List<String>> callback) {
        if (indice >= lista.size()) {
            callback.onSucesso(lista);
            return;
        }
        String caminho = lista.get(indice);
        if (caminho == null || caminho.isEmpty() || ehLink(caminho)) {
            enviarAPartirDe(context, modulo, lista, indice + 1, callback);
            return;
        }

        Uri uri = paraUri(caminho);
        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child(modulo)
                .child(UUID.randomUUID().toString())
                .child(nomeDoArquivo(context, uri));
        ref.putFile(uri)
                .continueWithTask(tarefa -> {
                    if (!tarefa.isSuccessful() && tarefa.getException() != null) throw tarefa.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(link -> {
                    lista.set(indice, link.toString());
                    enviarAPartirDe(context, modulo, lista, indice + 1, callback);
                })
                .addOnFailureListener(callback::onErro);
    }

    /** Abre o anexo no app adequado (navegador/leitor de PDF para links). */
    public static void abrir(Context context, String caminho) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (ehLink(caminho)) {
                intent.setData(Uri.parse(caminho));
            } else {
                Uri uri = paraUri(caminho);
                if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
                    uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider",
                            new File(uri.getPath()));
                }
                intent.setDataAndType(uri, context.getContentResolver().getType(uri));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(intent, "Abrir anexo com")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "Não há aplicativo para abrir este arquivo.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(context, "Este anexo não está disponível neste aparelho.", Toast.LENGTH_LONG).show();
        }
    }

    /** Carrega foto de link, caminho de arquivo ou content:// na ImageView. */
    public static void carregarImagem(ImageView imagem, String caminho, int padrao) {
        if (caminho == null || caminho.isEmpty()) {
            imagem.setImageResource(padrao);
            return;
        }
        Object fonte = ehLink(caminho) ? caminho : paraUri(caminho);
        Glide.with(imagem.getContext()).load(fonte).placeholder(padrao).error(padrao).into(imagem);
    }

    public static Uri paraUri(String caminho) {
        if (caminho.startsWith("/")) return Uri.fromFile(new File(caminho));
        return Uri.parse(caminho);
    }

    private static String nomeDoArquivo(Context context, Uri uri) {
        String nome = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0) nome = c.getString(i);
                }
            } catch (Exception ignored) {
            }
        }
        if (nome == null) nome = uri.getLastPathSegment();
        if (nome == null || nome.isEmpty()) nome = "arquivo";
        return nome.replaceAll("[/\\\\]", "_");
    }
}
