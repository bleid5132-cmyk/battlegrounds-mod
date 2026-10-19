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
        put("m416", 10.5f, 5.0f, 11.6f, 8.25f, -9f, 6.5f, -0.25f, 2.400f, 6.000f, 0.0f, 9.6f, 16.5f);
        put("akm", 10.5f, 9.25f, 11.0f, 8.25f, -10f, 7f, -1.0f, 1.398f, 4.505f, 22.5f, 9.4f, 15.5f);
        put("m16a4", 10.5f, 8.25f, 11.8f, 8.25f, -13f, 6.8f, -0.75f, 2.400f, 6.000f, 0.0f, 9.6f, 16.5f);
        put("scar_l", 10.5f, 5.25f, 11.8f, 8.25f, -9f, 6.55f, 0.25f, 2.600f, 6.500f, 0.0f, 9.8f, 15f);
        put("qbz", 12.9f, 4.0f, 12.6f, 8.25f, -10f, 6.8f, -1.0f, 2.400f, 10.500f, 0.0f, 9.6f, 16f);
        put("g36c", 12.9f, 8.0f, 12.4f, 8.25f, -8f, 6.6f, 0.0f, 2.400f, 6.000f, 0.0f, 9.8f, 14.5f);
        put("aug", 10.5f, 3.0f, 11.8f, 8.25f, -11f, 6.6f, -2.0f, 2.400f, 11.500f, 0.0f, 9.6f, 16f);
        put("beryl_m762", 10.5f, 4.75f, 11.6f, 8.25f, -9.5f, 6.65f, -0.75f, 1.398f, 4.505f, 22.5f, 9.6f, 16.0f);
        put("groza", 10.5f, 2.5f, 11.6f, 8.25f, -8.5f, 6.8f, -1.5f, 1.398f, 7.505f, 22.5f, 9.6f, 16f);
        put("mk47_mutant", 10.5f, 4.25f, 11.6f, 8.25f, -11f, 6.55f, -1.0f, 1.398f, 5.005f, 22.5f, 9.6f, 16.5f);
        put("ace32", 10.5f, 4.75f, 11.6f, 8.25f, -9.5f, 6.65f, -0.75f, 1.398f, 4.505f, 22.5f, 9.6f, 16.0f);
        put("k2", 10.5f, 8.5f, 11.6f, 8.25f, -10f, 6.8f, -0.5f, 2.400f, 6.000f, 0.0f, 9.6f, 16.5f);
        put("famas", 12.9f, 5.0f, 13.2f, 8.25f, -10f, 6.8f, -1.75f, 3.400f, 10.250f, 0.0f, 9.6f, 16f);
    }

    public static Mount get(String id) {
        return MOUNTS.getOrDefault(id, MOUNTS.get("m416"));
    }

    private GunMounts() {
    }
}
