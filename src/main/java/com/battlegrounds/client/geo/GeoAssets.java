package com.battlegrounds.client.geo;

import com.battlegrounds.Battlegrounds;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Загружает модели (geo/<id>.json) и анимации (animations/<id>.json) оружия из ресурсов. */
public final class GeoAssets {
    public record Entry(GeoModel model, Map<String, GeoAnimation> animations) {
        @Nullable
        public GeoAnimation anim(String name) {
            return animations.get(name);
        }
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static String[] ids = new String[0];

    @Nullable
    public static Entry get(String gunId) {
        return ENTRIES.get(gunId);
    }

    public static void init(String... gunIds) {
        ids = gunIds;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Battlegrounds.id("geo_models");
            }

            @Override
            public void reload(ResourceManager manager) {
                ENTRIES.clear();
                for (String id : ids) {
                    try {
                        JsonObject geo = read(manager, Battlegrounds.id("geo/" + id + ".json"));
                        if (geo == null) continue;
                        GeoModel model = GeoModel.parse(geo);
                        JsonObject an = read(manager, Battlegrounds.id("animations/" + id + ".json"));
                        Map<String, GeoAnimation> anims = an != null ? GeoAnimation.parseAll(an) : new HashMap<>();
                        ENTRIES.put(id, new Entry(model, anims));
                    } catch (Exception e) {
                        Battlegrounds.LOGGER.error("Не удалось загрузить модель {}", id, e);
                    }
                }
            }
        });
    }

    @Nullable
    private static JsonObject read(ResourceManager manager, Identifier id) throws Exception {
        Optional<Resource> res = manager.getResource(id);
        if (res.isEmpty()) return null;
        try (Reader reader = res.get().getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private GeoAssets() {
    }
}
