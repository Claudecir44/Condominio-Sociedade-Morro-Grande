package com.cjstudio.condominio_sociedade_morro_grande;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * Notificação de mensagem nova no chat. O servidor (Cloud Function notificarMensagemChat) manda
 * só "data" (titulo, corpo); quem monta a notificação é este serviço — mesmo desenho do Manicure.
 * O token do aparelho fica em dispositivos/{uid}: token e administracao (admins e conta master
 * recebem as mensagens dos moradores; o morador recebe as da Administração).
 */
public class CondominioMessagingService extends FirebaseMessagingService {

    private static final String CANAL_CHAT = "chat";

    @Override
    public void onNewToken(@NonNull String token) {
        // Sem saber se é a conta master, não mexe no campo administracao (o painel grava ao abrir)
        salvarToken(token, null);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage mensagem) {
        Map<String, String> dados = mensagem.getData();
        String titulo = dados.containsKey("titulo") ? dados.get("titulo") : "Nova mensagem";
        String corpo = dados.containsKey("corpo") ? dados.get("corpo") : "";
        mostrar(this, titulo, corpo);
    }

    /** Grava o token deste aparelho pra conta logada. administracao null = não altera. */
    public static void registrarAparelho(Boolean administracao) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> salvarToken(token, administracao));
    }

    private static void salvarToken(String token, Boolean administracao) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || token == null) return;
        Map<String, Object> dados = new HashMap<>();
        dados.put("token", token);
        dados.put("app", BuildConfig.PERFIL);
        if (administracao != null) dados.put("administracao", administracao);
        dados.put("atualizadoEm", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("dispositivos").document(user.getUid())
                .set(dados, com.google.firebase.firestore.SetOptions.merge());
    }

    /** Ao sair da conta: este aparelho para de receber as mensagens dela. */
    public static void esquecerAparelho() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) FirebaseFirestore.getInstance().collection("dispositivos").document(user.getUid()).delete();
        FirebaseMessaging.getInstance().deleteToken();
    }

    private static void mostrar(Context context, String titulo, String corpo) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CANAL_CHAT) == null) {
            NotificationChannel canal = new NotificationChannel(CANAL_CHAT, "Chat", NotificationManager.IMPORTANCE_HIGH);
            canal.setDescription("Mensagens novas do chat com a Administração");
            nm.createNotificationChannel(canal);
        }
        // Toque: abre o app (que vai pro painel; o botão Chat mostra o selo)
        Intent abrir = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder n = new NotificationCompat.Builder(context, CANAL_CHAT)
                .setSmallIcon(R.drawable.ic_chat_branco)
                .setContentTitle(titulo)
                .setContentText(corpo)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(corpo))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            NotificationManagerCompat.from(context).notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), n.build());
        } catch (SecurityException ignored) {
            // Sem permissão de notificação (Android 13+): o selo no botão Chat continua avisando
        }
    }
}
