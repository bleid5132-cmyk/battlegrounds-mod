package com.battlegrounds.registry;

import com.battlegrounds.Battlegrounds;
import com.battlegrounds.weapon.AmmoType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    public static final SoundEvent SHOT_556 = register("shot_556");
    public static final SoundEvent SHOT_762 = register("shot_762");
    public static final SoundEvent SHOT_SUPPRESSED = register("shot_suppressed");
    public static final SoundEvent RELOAD = register("reload");
    public static final SoundEvent DRY_FIRE = register("dry_fire");
    public static final SoundEvent FIRE_MODE = register("fire_mode");
    public static final SoundEvent BULLET_IMPACT = register("bullet_impact");
    public static final SoundEvent ATTACH = register("attach");
    public static final SoundEvent SHELL_DROP = register("shell_drop");
    public static final SoundEvent SHOT_FAR = register("shot_far");
    public static final SoundEvent SHOT_FAR_762 = register("shot_far_762");
    public static final SoundEvent MAG_OUT = register("mag_out");
    public static final SoundEvent MAG_IN = register("mag_in");
    public static final SoundEvent MAG_TAP = register("mag_tap");
    public static final SoundEvent CHARGE_PULL = register("charge_pull");
    public static final SoundEvent CHARGE_RELEASE = register("charge_release");
    public static final SoundEvent GUN_RATTLE = register("gun_rattle");
    public static final SoundEvent GUN_DRAW = register("gun_draw");
    public static final SoundEvent GUN_HOLSTER = register("gun_holster");
    public static final SoundEvent AIM_IN = register("aim_in");
    public static final SoundEvent AIM_OUT = register("aim_out");

    public static SoundEvent shotFor(AmmoType ammo, boolean suppressed) {
        if (suppressed) return SHOT_SUPPRESSED;
        return ammo == AmmoType.A762 ? SHOT_762 : SHOT_556;
    }

    private static SoundEvent register(String name) {
        Identifier id = Battlegrounds.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
    }

    private ModSounds() {
    }
}
