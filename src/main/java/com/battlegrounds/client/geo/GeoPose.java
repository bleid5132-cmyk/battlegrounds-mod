package com.battlegrounds.client.geo;

import java.util.HashMap;
import java.util.Map;

/** Поза: для каждой кости pos(3), rot(3, градусы), scale(3). Плюс «камера». */
public final class GeoPose {
    public final GeoModel model;
    public final float[][] bones;
    public final float[] camera = new float[9];
    private final Map<String, float[]> byName = new HashMap<>();

    public GeoPose(GeoModel model) {
        this.model = model;
        this.bones = new float[model.bones.size()][9];
        for (GeoModel.Bone b : model.bones) byName.put(b.name, bones[b.index]);
        byName.put("camera", camera);
        reset();
    }

    public void reset() {
        for (float[] b : bones) clear(b);
        clear(camera);
    }

    private static void clear(float[] b) {
        b[0] = b[1] = b[2] = b[3] = b[4] = b[5] = 0f;
        b[6] = b[7] = b[8] = 1f;
    }

    public float[] get(String bone) {
        return byName.get(bone);
    }

    public float[] get(int index) {
        return bones[index];
    }
}
