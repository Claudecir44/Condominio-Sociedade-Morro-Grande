package com.cjstudio.condominio_sociedade_morro_grande;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Criptografia (AES-256-GCM) do texto das mensagens do chat morador x Administração — mesmo
 * esquema do ChatAdminCryptoUtil do Caronas, com chave própria. Protege contra leitura direta no
 * Firestore (export, console); a chave vive no app, então não é barreira contra quem descompila
 * o APK. Texto sem o prefixo ENC1: é devolvido como veio.
 */
public final class ChatCrypto {

    private ChatCrypto() { }

    private static final String CHAVE_BASE64 = "NIYJ8BQYYrEbXaBbFWKG/AcgGY2IhZfmY3wQHCi2A8s=";
    private static final String PREFIXO = "ENC1:";
    private static final int TAMANHO_IV = 12;
    private static final int TAG_BITS = 128;

    private static SecretKeySpec chave() {
        return new SecretKeySpec(Base64.decode(CHAVE_BASE64, Base64.NO_WRAP), "AES");
    }

    public static String cifrar(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        try {
            byte[] iv = new byte[TAMANHO_IV];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chave(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] tudo = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, tudo, 0, iv.length);
            System.arraycopy(cifrado, 0, tudo, iv.length, cifrado.length);
            return PREFIXO + Base64.encodeToString(tudo, Base64.NO_WRAP);
        } catch (Exception e) {
            return texto;
        }
    }

    public static String decifrar(String texto) {
        if (texto == null || !texto.startsWith(PREFIXO)) return texto;
        try {
            byte[] tudo = Base64.decode(texto.substring(PREFIXO.length()), Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chave(), new GCMParameterSpec(TAG_BITS, tudo, 0, TAMANHO_IV));
            return new String(c.doFinal(tudo, TAMANHO_IV, tudo.length - TAMANHO_IV), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "(mensagem não pôde ser lida)";
        }
    }
}
