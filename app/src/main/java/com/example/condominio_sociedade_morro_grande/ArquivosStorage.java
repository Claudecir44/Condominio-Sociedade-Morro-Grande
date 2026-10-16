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

    /**
     * Envia um arquivo para {modulo}/{pasta}/{nomeArquivo}, numa pasta fixa (ex.: o UID do morador),
     * para que as regras do Storage confiram que o dono é quem está enviando.
     */
    public static void enviarNaPasta(Context context, String modulo, String pasta, String nomeArquivo,
                                     Uri arquivo, ColecaoFirestore.Callback<String> callback) {
        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child(modulo).child(pasta).child(nomeArquivo);
        // Tipo explícito: as regras do Storage só aceitam imagem nas pastas do morador
        com.google.firebase.storage.StorageMetadata tipo = new com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(nomeArquivo.toLowerCase().endsWith(".jpg") ? "image/jpeg" : context.getContentResolver().getType(arquivo))
                .build();
        ref.putFile(arquivo, tipo)
                .continueWithTask(tarefa -> {
                    if (!tarefa.isSuccessful() && tarefa.getException() != null) throw tarefa.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(link -> callback.onSucesso(link.toString()))
                .addOnFailureListener(callback::onErro);
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
        String nome = nomeDoArquivo(context, uri);
        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child(modulo)
                .child(UUID.randomUUID().toString())
                .child(nome);
        // Tipo explícito (PDF, Word, Excel, foto...) para abrir certo no celular e no painel web
        String tipo = null;
        try { tipo = context.getContentResolver().getType(uri); } catch (Exception ignored) { }
        if (tipo == null) tipo = tipoDoNome(nome);
        ref.putFile(uri, new com.google.firebase.storage.StorageMetadata.Builder()
                        .setContentType(tipo)
                        .setContentDisposition("inline; filename=\"" + nome.replace("\"", "") + "\"")
                        .build())
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

    /** Nome legível do anexo: tira a pasta/uuid do link do Storage ou do caminho local. */
    public static String nomeDoAnexo(Context context, String caminho) {
        if (caminho == null || caminho.isEmpty()) return "arquivo";
        String nome = null;
        if (ehLink(caminho)) {
            int o = caminho.indexOf("/o/");
            if (o >= 0) {
                String resto = caminho.substring(o + 3);
                int q = resto.indexOf('?');
                if (q >= 0) resto = resto.substring(0, q);
                try { resto = java.net.URLDecoder.decode(resto, "UTF-8"); } catch (Exception ignored) { }
                nome = resto.substring(resto.lastIndexOf('/') + 1);
            } else {
                nome = Uri.parse(caminho).getLastPathSegment();
            }
        } else {
            try { nome = nomeDoArquivo(context, paraUri(caminho)); } catch (Exception ignored) { }
        }
        return nome == null || nome.isEmpty() ? "arquivo" : nome;
    }

    /** Tipo (MIME) pelo nome do arquivo; "application/octet-stream" se não reconhecer. */
    public static String tipoDoNome(String nome) {
        String ext = nome != null && nome.contains(".") ? nome.substring(nome.lastIndexOf('.') + 1).toLowerCase() : "";
        String tipo = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return tipo != null ? tipo : "application/octet-stream";
    }

    public static boolean ehImagem(Context context, String caminho) {
        return tipoDoNome(nomeDoAnexo(context, caminho)).startsWith("image/");
    }

    /** Ícone do tipo de arquivo (PDF, Word, Excel, imagem ou genérico). */
    public static int iconeDoTipo(Context context, String caminho) {
        String nome = nomeDoAnexo(context, caminho).toLowerCase();
        if (nome.endsWith(".pdf")) return com.cjstudio.condominio_sociedade_morro_grande.R.drawable.ic_arquivo_pdf;
        if (nome.matches(".*\\.(doc|docx|odt|rtf|txt)$")) return com.cjstudio.condominio_sociedade_morro_grande.R.drawable.ic_arquivo_word;
        if (nome.matches(".*\\.(xls|xlsx|ods|csv)$")) return com.cjstudio.condominio_sociedade_morro_grande.R.drawable.ic_arquivo_excel;
        if (ehImagem(context, caminho)) return com.cjstudio.condominio_sociedade_morro_grande.R.drawable.ic_arquivo_imagem;
        return com.cjstudio.condominio_sociedade_morro_grande.R.drawable.ic_arquivo_generico;
    }

    public interface AoBaixar { void arquivo(File arquivo); }

    /**
     * Baixa o anexo (pelo link com token, que não depende das regras) para o cache do app.
     * Caminhos locais antigos são usados direto. O callback chega na thread principal.
     */
    public static void baixarParaCache(Context context, String caminho, AoBaixar aoBaixar) {
        android.os.Handler principal = new android.os.Handler(android.os.Looper.getMainLooper());
        String nome = nomeDoAnexo(context, caminho);
        Toast.makeText(context, "Preparando \"" + nome + "\"…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File pasta = new File(context.getCacheDir(), "anexos");
                if (!pasta.exists()) pasta.mkdirs();
                File destino = new File(pasta, nome);
                java.io.InputStream entrada;
                if (ehLink(caminho)) {
                    java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(caminho).openConnection();
                    con.setConnectTimeout(15000);
                    con.setReadTimeout(60000);
                    if (con.getResponseCode() != 200) throw new java.io.IOException("HTTP " + con.getResponseCode());
                    entrada = con.getInputStream();
                } else {
                    Uri uri = paraUri(caminho);
                    entrada = context.getContentResolver().openInputStream(uri);
                }
                try (java.io.InputStream in = entrada; java.io.OutputStream out = new java.io.FileOutputStream(destino)) {
                    byte[] buffer = new byte[16384];
                    int lidos;
                    while ((lidos = in.read(buffer)) > 0) out.write(buffer, 0, lidos);
                }
                principal.post(() -> aoBaixar.arquivo(destino));
            } catch (Exception e) {
                principal.post(() -> Toast.makeText(context,
                        "Não foi possível obter o arquivo: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static Uri uriCompartilhavel(Context context, File arquivo) {
        return FileProvider.getUriForFile(context, context.getPackageName() + ".provider", arquivo);
    }

    /** Abre no app certo (leitor de PDF, Word, Excel, galeria...). */
    public static void abrirNoApp(Context context, String caminho) {
        baixarParaCache(context, caminho, arquivo -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uriCompartilhavel(context, arquivo), tipoDoNome(arquivo.getName()))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(Intent.createChooser(intent, "Abrir com")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(context, "Não há aplicativo para abrir este tipo de arquivo.", Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Exporta: manda o arquivo para WhatsApp, e-mail, Drive etc. */
    public static void compartilhar(Context context, String caminho) {
        baixarParaCache(context, caminho, arquivo -> {
            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType(tipoDoNome(arquivo.getName()))
                    .putExtra(Intent.EXTRA_STREAM, uriCompartilhavel(context, arquivo))
                    .putExtra(Intent.EXTRA_SUBJECT, arquivo.getName())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Exportar / enviar para")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
    }

    /** Download para a pasta Downloads/Morro Grande do celular (aparece nas notificações). */
    public static void baixarNoCelular(Context context, String caminho) {
        String nome = nomeDoAnexo(context, caminho);
        if (!ehLink(caminho)) {
            Toast.makeText(context, "Este arquivo já está neste aparelho.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.app.DownloadManager.Request pedido = new android.app.DownloadManager.Request(Uri.parse(caminho))
                    .setTitle(nome)
                    .setDescription("Condomínio Sociedade Morro Grande")
                    .setMimeType(tipoDoNome(nome))
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,
                            "Morro Grande/" + nome);
            android.app.DownloadManager dm = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(pedido);
            Toast.makeText(context, "Baixando para Downloads/Morro Grande…", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Não foi possível baixar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Abre o anexo no app adequado (baixa e abre no leitor de PDF, Word, Excel, galeria...). */
    public static void abrir(Context context, String caminho) {
        abrirNoApp(context, caminho);
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
