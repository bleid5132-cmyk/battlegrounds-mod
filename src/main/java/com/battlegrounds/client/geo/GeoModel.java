package com.battlegrounds.client.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель оружия из костей и кубов (наш формат, см. tools/geolib.py).
 * Координаты — пиксели: x вправо, y вверх, ствол в -z. Кубы заданы в абсолютных координатах.
 */
public final class GeoModel {
    public static final class Cube {
        public final float x0, y0, z0, x1, y1, z1;
        public final int swatch;

        Cube(float x0, float y0, float z0, float x1, float y1, float z1, int swatch) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.swatch = swatch;
        }
    }

    public static final class Bone {
        public final int index;
        public final String name;
        public final int parent;
        public final Vector3f pivot;
        public final Vector3f rest;   // градусы
        public final Vector3f scale;
        public final List<Cube> cubes = new ArrayList<>();
        public final List<Integer> children = new ArrayList<>();

        Bone(int index, String name, int parent, Vector3f pivot, Vector3f rest, Vector3f scale) {
            this.index = index;
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.rest = rest;
            this.scale = scale;
        }
    }

    public final Identifier texture;
    public final int texWidth, texHeight, swatch;
    public final float thirdPersonScale, guiScale;
    public final List<Bone> bones = new ArrayList<>();
    public final List<Integer> roots = new ArrayList<>();
    private final Map<String, Integer> byName = new HashMap<>();
    // минимальные/максимальные координаты модели (для иконки)
    public final Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);

    private GeoModel(Identifier texture, int tw, int th, int swatch, float tps, float gs) {
        this.texture = texture;
        this.texWidth = tw;
        this.texHeight = th;
        this.swatch = swatch;
        this.thirdPersonScale = tps;
        this.guiScale = gs;
    }

    public int indexOf(String name) {
        Integer i = byName.get(name);
        return i == null ? -1 : i;
    }

    public Bone bone(String name) {
        int i = indexOf(name);
        return i < 0 ? null : bones.get(i);
    }

    private static Vector3f vec(JsonObject o, String key, float def) {
        if (!o.has(key)) return new Vector3f(def);
        JsonArray a = o.getAsJsonArray(key);
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    public static GeoModel parse(JsonObject root) {
        JsonArray ts = root.getAsJsonArray("texture_size");
        GeoModel m = new GeoModel(new Identifier(root.get("texture").getAsString()),
                ts.get(0).getAsInt(), ts.get(1).getAsInt(), root.get("swatch").getAsInt(),
                root.has("third_person_scale") ? root.get("third_person_scale").getAsFloat() : 0.6f,
                root.has("gui_scale") ? root.get("gui_scale").getAsFloat() : 0.42f);
        JsonArray bones = root.getAsJsonArray("bones");
        for (JsonElement e : bones) {
            JsonObject b = e.getAsJsonObject();
            String name = b.get("name").getAsString();
            int parent = b.has("parent") ? m.indexOf(b.get("parent").getAsString()) : -1;
            Bone bone = new Bone(m.bones.size(), name, parent, vec(b, "pivot", 0), vec(b, "rot", 0), vec(b, "scale", 1));
            if (b.has("cubes")) {
                for (JsonElement ce : b.getAsJsonArray("cubes")) {
                    JsonObject c = ce.getAsJsonObject();
                    JsonArray f = c.getAsJsonArray("f");
                    JsonArray t = c.getAsJsonArray("t");
                    Cube cube = new Cube(f.get(0).getAsFloat(), f.get(1).getAsFloat(), f.get(2).getAsFloat(),
                            t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat(), c.get("s").getAsInt());
                    bone.cubes.add(cube);
                    m.min.min(new Vector3f(cube.x0, cube.y0, cube.z0));
                    m.max.max(new Vector3f(cube.x1, cube.y1, cube.z1));
                }
            }
            m.byName.put(name, bone.index);
            m.bones.add(bone);
            if (parent >= 0) m.bones.get(parent).children.add(bone.index);
            else m.roots.add(bone.index);
        }
        return m;
    }

    /** Локальная матрица кости: T(pivot+pos) * R(rest) * R(anim) * S * T(-pivot). pose может быть null. */
    public Matrix4f local(Bone b, float[] pose) {
        Matrix4f m = new Matrix4f();
        float px = b.pivot.x, py = b.pivot.y, pz = b.pivot.z;
        float sx = b.scale.x, sy = b.scale.y, sz = b.scale.z;
        if (pose != null) {
            px += pose[0]; py += pose[1]; pz += pose[2];
            sx *= pose[6]; sy *= pose[7]; sz *= pose[8];
        }
        m.translate(px, py, pz);
        m.rotate(rotation(b.rest.x, b.rest.y, b.rest.z));
        if (pose != null && (pose[3] != 0 || pose[4] != 0 || pose[5] != 0)) {
            m.rotate(rotation(pose[3], pose[4], pose[5]));
        }
        m.scale(sx, sy, sz);
        m.translate(-b.pivot.x, -b.pivot.y, -b.pivot.z);
        return m;
    }

    /** R = Rz * Ry * Rx, углы в градусах. */
    public static Quaternionf rotation(float xDeg, float yDeg, float zDeg) {
        return new Quaternionf().rotationZYX((float) Math.toRadians(zDeg), (float) Math.toRadians(yDeg), (float) Math.toRadians(xDeg));
    }

    /** Мировая (модельная) матрица кости в покое. */
    public Matrix4f restWorld(int index) {
        Bone b = bones.get(index);
        Matrix4f l = local(b, null);
        if (b.parent < 0) return l;
        return restWorld(b.parent).mul(l);
    }

    /** Положение точки-локатора (pivot кости) в координатах модели, в покое. */
    public Vector3f locator(String name) {
        Bone b = bone(name);
        if (b == null) return null;
        return restWorld(b.index).transformPosition(new Vector3f(b.pivot));
    }
}
