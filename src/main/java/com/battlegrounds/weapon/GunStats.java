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
    public static final GunStats M416 = new GunStats("m416", AmmoType.A556, 8.2f, 30, 40,
            0.0857f, 46, 2.2f, 0.25f, 0.85f, 0.35f, 300f, 2.35f, 1.25f,
            List.of(AUTO, SINGLE), 1, ALL, 1.0f);

    public static final GunStats AKM = new GunStats("akm", AmmoType.A762, 9.4f, 30, 40,
            0.1f, 58, 2.6f, 0.3f, 1.25f, 0.6f, 300f, 2.35f, 1.25f,
            List.of(AUTO, SINGLE), 1, NO_GRIP_STOCK, 0.9f);

    public static final GunStats M16A4 = new GunStats("m16a4", AmmoType.A556, 8.6f, 30, 40,
            0.075f, 44, 2.0f, 0.2f, 0.8f, 0.25f, 320f, 2.35f, 1.25f,
            List.of(BURST, SINGLE), 3, NO_GRIP_STOCK, 1.05f);

    public static final GunStats SCAR_L = new GunStats("scar_l", AmmoType.A556, 8.2f, 30, 40,
            0.096f, 44, 2.2f, 0.25f, 0.8f, 0.35f, 300f, 2.35f, 1.25f,
            List.of(AUTO, SINGLE), 1, NO_STOCK, 0.98f);

    public static final GunStats QBZ = new GunStats("qbz", AmmoType.A556, 8.2f, 30, 40,
            0.092f, 48, 2.2f, 0.25f, 0.8f, 0.4f, 300f, 2.35f, 1.25f,
            List.of(AUTO, SINGLE), 1, NO_STOCK, 1.02f);

    public static final GunStats G36C = new GunStats("g36c", AmmoType.A556, 8.2f, 30, 40,
            0.086f, 46, 2.3f, 0.25f, 0.85f, 0.4f, 280f, 2.35f, 1.25f,
            List.of(AUTO, SINGLE), 1, NO_STOCK, 1.08f);

    public static final GunStats AUG = new GunStats("aug", AmmoType.A556, 8.2f, 30, 40,
            0.0857f, 60, 2.1f, 0.22f, 0.65f, 0.3f, 300f, 2.35f, 1.25f,
            List.of(AUTO, SINGLE), 1, NO_STOCK, 1.04f);

    public static final GunStats BERYL = new GunStats("beryl_m762", AmmoType.A762, 8.8f, 30, 40,
            0.086f, 58, 2.6f, 0.3f, 1.3f, 0.75f, 300f, 2.35f, 1.25f,
            List.of(AUTO, BURST, SINGLE), 3, NO_STOCK, 0.92f);

    public static final GunStats GROZA = new GunStats("groza", AmmoType.A762, 9.4f, 30, 40,
            0.08f, 60, 2.5f, 0.3f, 1.05f, 0.5f, 280f, 2.35f, 1.25f,
            List.of(AUTO, SINGLE), 1, NO_GRIP_STOCK, 0.88f);

    public static final GunStats MK47 = new GunStats("mk47_mutant", AmmoType.A762, 9.8f, 20, 30,
            0.1f, 56, 2.4f, 0.2f, 1.1f, 0.35f, 330f, 2.35f, 1.25f,
            List.of(BURST, SINGLE), 2, NO_STOCK, 0.86f);

    public static final GunStats ACE32 = new GunStats("ace32", AmmoType.A762, 8.6f, 30, 40,
            0.088f, 58, 2.5f, 0.3f, 1.0f, 0.55f, 300f, 2.35f, 1.25f,
            List.of(AUTO, SINGLE), 1, ALL, 0.9f);

    public static final GunStats K2 = new GunStats("k2", AmmoType.A556, 8.2f, 30, 40,
            0.0857f, 50, 2.2f, 0.25f, 0.85f, 0.4f, 300f, 2.35f, 1.25f,
            List.of(AUTO, BURST, SINGLE), 3, NO_STOCK, 1.03f);

    public static final GunStats FAMAS = new GunStats("famas", AmmoType.A556, 7.8f, 25, 35,
            0.0667f, 52, 2.3f, 0.25f, 0.75f, 0.45f, 280f, 2.35f, 1.25f,
            List.of(AUTO, BURST, SINGLE), 3, NO_GRIP_STOCK, 1.1f);

    public static final List<GunStats> ASSAULT_RIFLES = List.of(
            M416, AKM, M16A4, SCAR_L, QBZ, G36C, AUG, BERYL, GROZA, MK47, ACE32, K2, FAMAS);
}
