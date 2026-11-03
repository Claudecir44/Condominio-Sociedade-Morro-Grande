package com.cjstudio.condominio_sociedade_morro_grande;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Com o alvo no Android 16 (exigência da Play Store), do Android 15 em diante as telas ocupam a
 * tela inteira, por baixo da barra de status e da barra de navegação. Aqui o conteúdo de toda
 * tela ganha o espaço dessas barras, como era antes; a barra de título (ActionBar) já desce sozinha,
 * então o topo só é reservado nas telas sem ela.
 */
public class CondominioApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return;
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPostCreated(@NonNull Activity activity, @Nullable Bundle estado) {
                View conteudo = activity.findViewById(android.R.id.content);
                if (conteudo == null) return;
                ViewCompat.setOnApplyWindowInsetsListener(conteudo, (view, insets) -> {
                    Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
                    // O teclado também empurra o conteúdo, como o adjustResize fazia
                    int baixo = Math.max(barras.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
                    view.setPadding(barras.left, temBarraDeTitulo(activity) ? 0 : barras.top, barras.right, baixo);
                    return insets;
                });
                ViewCompat.requestApplyInsets(conteudo);
            }

            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle estado) { }
            @Override public void onActivityStarted(@NonNull Activity activity) { }
            @Override public void onActivityResumed(@NonNull Activity activity) { }
            @Override public void onActivityPaused(@NonNull Activity activity) { }
            @Override public void onActivityStopped(@NonNull Activity activity) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle estado) { }
            @Override public void onActivityDestroyed(@NonNull Activity activity) { }
        });
    }

    private static boolean temBarraDeTitulo(Activity activity) {
        if (!(activity instanceof AppCompatActivity)) return false;
        ActionBar barra = ((AppCompatActivity) activity).getSupportActionBar();
        return barra != null && barra.isShowing();
    }
}
