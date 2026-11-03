package com.cjstudio.condominio_sociedade_morro_grande;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Atualização em tempo real das telas: enquanto a tela está visível, observa o Firestore e chama
 * recarregar quando algo muda. A carga inicial continua no onResume de cada tela, por isso o
 * primeiro retorno de cada escuta é ignorado. Várias mudanças seguidas recarregam uma vez só.
 */
public final class TempoReal {

    private static final long ESPERA_MS = 200;

    private interface Fonte {
        ListenerRegistration ouvir(Runnable mudou);
    }

    private TempoReal() { }

    public static void observar(LifecycleOwner tela, Runnable recarregar, Query... consultas) {
        List<Fonte> fontes = new ArrayList<>();
        for (Query consulta : consultas) {
            fontes.add(mudou -> {
                boolean[] primeira = {true};
                return consulta.addSnapshotListener((snap, erro) -> {
                    if (snap == null) return;
                    if (primeira[0]) primeira[0] = false;
                    else mudou.run();
                });
            });
        }
        ligar(tela, recarregar, fontes);
    }

    public static void observar(LifecycleOwner tela, Runnable recarregar, DocumentReference documento) {
        List<Fonte> fontes = new ArrayList<>();
        fontes.add(mudou -> {
            boolean[] primeira = {true};
            return documento.addSnapshotListener((snap, erro) -> {
                if (snap == null) return;
                if (primeira[0]) primeira[0] = false;
                else mudou.run();
            });
        });
        ligar(tela, recarregar, fontes);
    }

    private static void ligar(LifecycleOwner tela, Runnable recarregar, List<Fonte> fontes) {
        Handler principal = new Handler(Looper.getMainLooper());
        Runnable agendar = () -> {
            principal.removeCallbacks(recarregar);
            principal.postDelayed(recarregar, ESPERA_MS);
        };
        List<ListenerRegistration> escutas = new ArrayList<>();
        tela.getLifecycle().addObserver((LifecycleEventObserver) (dono, evento) -> {
            if (evento == Lifecycle.Event.ON_START) {
                for (Fonte fonte : fontes) escutas.add(fonte.ouvir(agendar));
            } else if (evento == Lifecycle.Event.ON_STOP) {
                for (ListenerRegistration escuta : escutas) escuta.remove();
                escutas.clear();
                principal.removeCallbacks(recarregar);
            }
        });
    }
}
