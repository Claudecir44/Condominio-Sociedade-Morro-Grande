package com.cjstudio.condominio_sociedade_morro_grande.data.firestore;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Contas do Firebase Auth do condomínio.
 *
 * O admin cria contas (de morador ou de outro admin) numa instância separada do Firebase,
 * assim a sessão dele no app não é trocada pela conta recém-criada.
 */
public final class ContaHelper {

    private static final String APP_CADASTRO = "cadastro-contas";
    private static final String DOMINIO_MORADOR = "@moradores.morrogrande.app";

    private ContaHelper() { }

    /** Login do morador (quadra+lote, ex.: A10) convertido no e-mail interno usado no Firebase Auth. */
    public static String emailDoMorador(String usuario) {
        String limpo = Normalizer.normalize(usuario.trim(), Normalizer.Form.NFD)
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
        return limpo + DOMINIO_MORADOR;
    }

    /** Senha inicial do morador: 6 primeiros dígitos do CPF (o Firebase exige no mínimo 6). */
    public static String senhaInicialMorador(String cpf) {
        String numeros = cpf.replaceAll("[^0-9]", "");
        return numeros.length() >= 6 ? numeros.substring(0, 6) : null;
    }

    /** Cria a conta sem deslogar quem está usando o app. Devolve o UID da conta nova. */
    public static void criarConta(Context context, String email, String senha,
                                  ColecaoFirestore.Callback<String> callback) {
        FirebaseAuth authCadastro = FirebaseAuth.getInstance(appDeCadastro(context));
        authCadastro.createUserWithEmailAndPassword(email, senha)
                .addOnSuccessListener(resultado -> {
                    FirebaseUser novo = resultado.getUser();
                    authCadastro.signOut();
                    if (novo == null) {
                        callback.onErro(new Exception("Conta criada sem usuário"));
                    } else {
                        callback.onSucesso(novo.getUid());
                    }
                })
                .addOnFailureListener(callback::onErro);
    }

    private static FirebaseApp appDeCadastro(Context context) {
        for (FirebaseApp app : FirebaseApp.getApps(context)) {
            if (APP_CADASTRO.equals(app.getName())) return app;
        }
        FirebaseOptions opcoes = FirebaseApp.getInstance().getOptions();
        return FirebaseApp.initializeApp(context, opcoes, APP_CADASTRO);
    }
}
