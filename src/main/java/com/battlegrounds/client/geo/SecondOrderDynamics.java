package com.battlegrounds.client.geo;

/**
 * Плавная «пружинная» кривая: f — частота, z — затухание, r — реакция на старте.
 * Используется для подъёма к прицелу и раскачки при прыжке.
 */
public final class SecondOrderDynamics {
    private final float k1, k2, k3;
    private float xp, y, yd;
    private long lastNanos = -1;

    public SecondOrderDynamics(float f, float z, float r, float x0) {
        double pi = Math.PI;
        k1 = (float) (z / (pi * f));
        k2 = (float) (1 / ((2 * pi * f) * (2 * pi * f)));
        k3 = (float) (r * z / (2 * pi * f));
        xp = x0;
        y = x0;
        yd = 0;
    }

    public float update(float x) {
        long now = System.nanoTime();
        float t = lastNanos < 0 ? 0f : Math.min(0.05f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        if (t <= 0f) return y;
        float xd = (x - xp) / t;
        xp = x;
        float k2Stable = Math.max(k2, Math.max(t * t / 2 + t * k1 / 2, t * k1));
        y = y + t * yd;
        yd = yd + t * (x + k3 * xd - y - k1 * yd) / k2Stable;
        return y;
    }

    public float get() {
        return y;
    }
}
