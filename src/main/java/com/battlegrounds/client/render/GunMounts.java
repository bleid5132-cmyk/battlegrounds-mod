package com.battlegrounds.client.render;

import java.util.HashMap;
import java.util.Map;

/** Точки крепления обвесов на моделях оружия (пиксели модели). Сгенерировано скриптом вместе с моделями. */
public final class GunMounts {
    public record Mount(float railY, float scopeZ, float ironY, float muzzleY, float muzzleZ,
                        float gripY, float gripZ, float magY, float magZ, float magAngle,
                        float stockY, float stockZ) {
    }

    private static final Map<String, Mount> MOUNTS = new HashMap<>();

    private static void put(String id, float railY, float scopeZ, float ironY, float muzzleY, float muzzleZ,
                            float gripY, float gripZ, float magY, float magZ, float magAngle, float stockY, float stockZ) {
        MOUNTS.put(id, new Mount(railY, scopeZ, ironY, muzzleY, muzzleZ, gripY, gripZ, magY, magZ, magAngle, stockY, stockZ));
    }

    static {
    }

    public static Mount get(String id) {
        return MOUNTS.get(id);
    }

    private GunMounts() {
    }
}
