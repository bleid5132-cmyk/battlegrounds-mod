package com.battlegrounds.registry;

import com.battlegrounds.Battlegrounds;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    public static final SoundEvent M416_SHOT = register("m416_shot");
    public static final SoundEvent RELOAD = register("reload");
    public static final SoundEvent DRY_FIRE = register("dry_fire");
    public static final SoundEvent FIRE_MODE = register("fire_mode");
    public static final SoundEvent BULLET_IMPACT = register("bullet_impact");

    private static SoundEvent register(String name) {
        Identifier id = Battlegrounds.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
    }

    private ModSounds() {
    }
}
