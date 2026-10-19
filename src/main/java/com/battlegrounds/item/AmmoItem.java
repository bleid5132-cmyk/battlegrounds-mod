package com.battlegrounds.item;

import com.battlegrounds.weapon.AmmoType;
import net.minecraft.item.Item;

public class AmmoItem extends Item {
    public final AmmoType type;

    public AmmoItem(Settings settings, AmmoType type) {
        super(settings);
        this.type = type;
    }
}
