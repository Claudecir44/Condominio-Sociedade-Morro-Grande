package com.cjstudio.condominio_sociedade_morro_grande.data.firestore;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;

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
    private static final String DOMINIO_ADMIN = "@admins.morrogrande.app";
    private static final String USUARIO_MASTER = "condominiosociedademorrogrande";

    private ContaHelper() { }

    /** Login do morador (quadra+lote, ex.: A10) convertido no e-mail interno usado no Firebase Auth. */
    public static String emailDoMorador(String usuario) {
        return limpar(usuario) + DOMINIO_MORADOR;
    }

    /**
     * Login do admin: e-mail normal, ou um nome de usuário sem @ (ex.: o admin master
     * "Condominio sociedade Morro grande") convertido no e-mail interno do Firebase Auth.
     */
    public static String emailDoAdmin(String usuario) {
        String texto = usuario.trim();
        return texto.contains("@") ? texto : limpar(texto) + DOMINIO_ADMIN;
    }

    /** Usuário "Condominio sociedade Morro grande" (admin master e morador de visualização). */
    public static boolean ehUsuarioMaster(String usuario) {
        return USUARIO_MASTER.equals(limpar(usuario));
    }

    /**
     * A senha é só numérica pra todo mundo; só quando o usuário digitado é o master o campo
     * passa a aceitar letras (a senha do master tem letras).
     */
    public static void liberarSenhaDoMaster(EditText usuario, EditText senha) {
        int numerica = senha.getInputType();
        int comLetras = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        usuario.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                int tipo = ehUsuarioMaster(s.toString()) ? comLetras : numerica;
                if (senha.getInputType() != tipo) {
                    senha.setInputType(tipo);
                    senha.setSelection(senha.length());
                }
            }
        });
    }

    private static String limpar(String usuario) {
        return Normalizer.normalize(usuario.trim(), Normalizer.Form.NFD)
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
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
