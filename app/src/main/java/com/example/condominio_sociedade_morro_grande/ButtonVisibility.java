package com.cjstudio.condominio_sociedade_morro_grande.domain.model;

import java.util.HashMap;
import java.util.Map;

public class ButtonVisibility {
    private final Map<String, Boolean> visibilityMap;

    public ButtonVisibility() {
        this.visibilityMap = new HashMap<>();
    }

    public ButtonVisibility(Map<String, Boolean> visibilityMap) {
        this.visibilityMap = visibilityMap != null ? visibilityMap : new HashMap<>();
    }

    public void setVisible(String buttonKey, boolean visible) {
        visibilityMap.put(buttonKey, visible);
    }

    public boolean isVisible(String buttonKey) {
        return visibilityMap.getOrDefault(buttonKey, false);
    }
}