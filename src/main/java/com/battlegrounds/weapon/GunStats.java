package com.battlegrounds.weapon;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.battlegrounds.weapon.AttachmentSlot.*;
import static com.battlegrounds.weapon.FireMode.*;

/**
 * Характеристики оружия.
 * Урон в единицах Minecraft (20 = полное здоровье): урон из PUBG / 5.
 * fireDelay — секунд между выстрелами, как в PUBG.
 */
public record GunStats(
        String id,
        AmmoType ammo,
        float damage,
        int magazine,
        int extMagazine,
        float fireDelay,
        int reloadTicks,
        float hipSpread,
        float adsSpread,
        float recoilUp,
        float recoilSide,
        float range,
        float headMultiplier,
        float ironZoom,
        List<FireMode> modes,
        int burstCount,
        Set<AttachmentSlot> slots,
        float soundPitch
) {
    private static final Set<AttachmentSlot> ALL = EnumSet.allOf(AttachmentSlot.class);
    private static final Set<AttachmentSlot> NO_STOCK = EnumSet.of(SCOPE, MUZZLE, GRIP, MAGAZINE);
    private static final Set<AttachmentSlot> NO_GRIP_STOCK = EnumSet.of(SCOPE, MUZZLE, MAGAZINE);

    public boolean accepts(AttachmentType type) {
        if (!slots.contains(type.slot)) return false;
        // как в PUBG: 8x и выше — только для марксманок и снайперок (позже)
        return true;
    }

    // перезарядка: 2.3 с с патроном в патроннике, 2.6 с с пустым магазином
    public static final List<GunStats> ASSAULT_RIFLES = List.of();
}
