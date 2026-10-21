package com.battlegrounds.client.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Анимация из ключевых кадров (линейно или catmull-rom). Значения добавляются к позе покоя. */
public final class GeoAnimation {
    public static final class Channel {
        final float[] times;
        final float[][] values;
        final boolean[] smooth;

        Channel(JsonArray keys) {
            int n = keys.size();
            times = new float[n];
            values = new float[n][3];
            smooth = new boolean[n];
            for (int i = 0; i < n; i++) {
                JsonArray k = keys.get(i).getAsJsonArray();
                times[i] = k.get(0).getAsFloat();
                values[i][0] = k.get(1).getAsFloat();
                values[i][1] = k.get(2).getAsFloat();
                values[i][2] = k.get(3).getAsFloat();
                smooth[i] = k.size() > 4 && "c".equals(k.get(4).getAsString());
            }
        }

        void sample(float t, float[] out) {
            int n = times.length;
            if (t <= times[0]) { copy(values[0], out); return; }
            if (t >= times[n - 1]) { copy(values[n - 1], out); return; }
            for (int i = 0; i < n - 1; i++) {
                if (t >= times[i] && t <= times[i + 1]) {
                    float span = times[i + 1] - times[i];
                    float u = span > 0 ? (t - times[i]) / span : 0f;
                    float[] a = values[i], b = values[i + 1];
                    if (smooth[i]) {
                        float[] p0 = i > 0 ? values[i - 1] : a;
                        float[] p3 = i + 2 < n ? values[i + 2] : b;
                        for (int c = 0; c < 3; c++) out[c] = catmull(p0[c], a[c], b[c], p3[c], u);
                    } else {
                        for (int c = 0; c < 3; c++) out[c] = a[c] + (b[c] - a[c]) * u;
                    }
                    return;
                }
            }
            copy(values[n - 1], out);
        }

        private static void copy(float[] v, float[] out) {
            out[0] = v[0]; out[1] = v[1]; out[2] = v[2];
        }

        private static float catmull(float p0, float p1, float p2, float p3, float u) {
            return 0.5f * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u
                    + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
        }
    }

    static final class BoneTrack {
        final String bone;
        Channel rot, pos, scale;

        BoneTrack(String bone) {
            this.bone = bone;
        }
    }

    public final String name;
    public final float length;
    public final boolean loop;
    final List<BoneTrack> tracks = new ArrayList<>();
    /** Звуки, привязанные к моментам анимации: время (с) и имя звука мода. */
    public float[] soundTimes = new float[0];
    public String[] soundNames = new String[0];

    private GeoAnimation(String name, float length, boolean loop) {
        this.name = name;
        this.length = length;
        this.loop = loop;
    }

    public static Map<String, GeoAnimation> parseAll(JsonObject root) {
        Map<String, GeoAnimation> out = new HashMap<>();
        JsonObject anims = root.getAsJsonObject("animations");
        for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
            JsonObject a = e.getValue().getAsJsonObject();
            GeoAnimation anim = new GeoAnimation(e.getKey(), a.get("length").getAsFloat(),
                    a.has("loop") && a.get("loop").getAsBoolean());
            for (Map.Entry<String, JsonElement> be : a.getAsJsonObject("bones").entrySet()) {
                JsonObject chs = be.getValue().getAsJsonObject();
                BoneTrack bt = new BoneTrack(be.getKey());
                if (chs.has("rot")) bt.rot = new Channel(chs.getAsJsonArray("rot"));
                if (chs.has("pos")) bt.pos = new Channel(chs.getAsJsonArray("pos"));
                if (chs.has("scale")) bt.scale = new Channel(chs.getAsJsonArray("scale"));
                anim.tracks.add(bt);
            }
            if (a.has("sounds")) {
                JsonArray ss = a.getAsJsonArray("sounds");
                anim.soundTimes = new float[ss.size()];
                anim.soundNames = new String[ss.size()];
                for (int i = 0; i < ss.size(); i++) {
                    JsonArray k = ss.get(i).getAsJsonArray();
                    anim.soundTimes[i] = k.get(0).getAsFloat();
                    anim.soundNames[i] = k.get(1).getAsString();
                }
            }
            out.put(e.getKey(), anim);
        }
        return out;
    }

    /** Добавляет кадр анимации в позу с весом. Кость «camera» не обязана быть в модели. */
    public void apply(GeoPose pose, float t, float weight) {
        if (weight <= 0f) return;
        float[] v = new float[3];
        for (BoneTrack bt : tracks) {
            float[] p = pose.get(bt.bone);
            if (p == null) continue;
            if (bt.pos != null) {
                bt.pos.sample(t, v);
                p[0] += v[0] * weight; p[1] += v[1] * weight; p[2] += v[2] * weight;
            }
            if (bt.rot != null) {
                bt.rot.sample(t, v);
                p[3] += v[0] * weight; p[4] += v[1] * weight; p[5] += v[2] * weight;
            }
            if (bt.scale != null) {
                bt.scale.sample(t, v);
                p[6] *= 1 + (v[0] - 1) * weight; p[7] *= 1 + (v[1] - 1) * weight; p[8] *= 1 + (v[2] - 1) * weight;
            }
        }
    }
}
