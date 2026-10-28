package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.auth.FirebaseAuth;

/**
 * App travado: assinatura do mês sem pagamento a partir do dia 06 (Assinatura.Situacao.BLOQUEADA).
 * Vale para moradores e admins; só o admin master tem o botão de pagar. Quando o mês fica pago,
 * volta para o início do app.
 */
public class BloqueioAssinaturaActivity extends AppCompatActivity {

    private final AssinaturaRepository repository = new AssinaturaRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bloqueio_assinatura);

        boolean admin = "admin".equals(BuildConfig.PERFIL);
        ((TextView) findViewById(R.id.tvMensagemBloqueio)).setText(admin
                ? "A assinatura do aplicativo não foi paga até o dia 05.\nO admin master precisa pagar em Assinaturas para liberar o app."
                : "O aplicativo do condomínio está temporariamente indisponível.\nProcure a administração do condomínio.");

        View pagar = findViewById(R.id.btnPagarAssinatura);
        pagar.setOnClickListener(v -> startActivity(new Intent(this, AssinaturasActivity.class)));
        repository.ehAdminMaster(new ColecaoFirestore.Callback<Boolean>() {
            @Override
            public void onSucesso(Boolean master) {
                pagar.setVisibility(Boolean.TRUE.equals(master) ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onErro(Exception e) { }
        });

        findViewById(R.id.btnTentarNovamente).setOnClickListener(v -> verificar(true));
        findViewById(R.id.btnSairBloqueio).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            voltarAoInicio();
        });

        // Travado: voltar não sai desta tela
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        verificar(false);
    }

    private void verificar(boolean avisar) {
        repository.estado(new ColecaoFirestore.Callback<Assinatura.Estado>() {
            @Override
            public void onSucesso(Assinatura.Estado estado) {
                if (estado.situacao != Assinatura.Situacao.BLOQUEADA) {
                    Toast.makeText(BloqueioAssinaturaActivity.this, "Assinatura em dia. App liberado!", Toast.LENGTH_SHORT).show();
                    voltarAoInicio();
                } else if (avisar) {
                    Toast.makeText(BloqueioAssinaturaActivity.this, "O pagamento ainda não foi confirmado.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onErro(Exception e) {
                if (avisar) Toast.makeText(BloqueioAssinaturaActivity.this,
                        "Sem conexão: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void voltarAoInicio() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
