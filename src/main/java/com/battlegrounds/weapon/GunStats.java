package com.battlegrounds.weapon;

/**
 * Характеристики оружия.
 * Урон указан в единицах Minecraft (20 = полное здоровье игрока).
 * Урон из PUBG делится на 5 (там у игрока 100 HP).
 */
public record GunStats(
        AmmoType ammo,
        float damage,
        int magazine,
        int fireInterval,   // тиков между выстрелами (20 тиков = 1 сек)
        int reloadTicks,
        float hipSpread,    // разброс от бедра, градусы
        float adsSpread,    // разброс в прицеле, градусы
        float recoilUp,     // подброс ствола за выстрел, градусы
        float recoilSide,   // боковой увод, градусы
        float range,        // дальность, блоков
        float headMultiplier,
        float adsZoom,      // во сколько раз приближает прицеливание
        boolean automatic
) {
    // M416: 41 урона, 30 патронов, ~700 выстр/мин, перезарядка 2.1 сек
    public static final GunStats M416 = new GunStats(
            AmmoType.A556, 8.2f, 30, 2, 42,
            2.2f, 0.25f, 0.85f, 0.35f,
            300f, 2.35f, 1.25f, true);
}
