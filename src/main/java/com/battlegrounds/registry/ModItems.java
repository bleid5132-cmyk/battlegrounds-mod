package com.battlegrounds.registry;

import com.battlegrounds.Battlegrounds;
import com.battlegrounds.item.AmmoItem;
import com.battlegrounds.item.GunItem;
import com.battlegrounds.weapon.AmmoType;
import com.battlegrounds.weapon.GunStats;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItems {
    public static final GunItem M416 = register("m416",
            new GunItem(new Item.Settings().maxCount(1), GunStats.M416));

    public static final AmmoItem AMMO_556 = register("ammo_556",
            new AmmoItem(new Item.Settings().maxCount(64), AmmoType.A556));

    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, Battlegrounds.id("main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(M416))
                    .displayName(Text.translatable("itemGroup.battlegrounds.main"))
                    .entries((context, entries) -> {
                        entries.add(M416.createLoaded());
                        entries.add(new ItemStack(AMMO_556, 64));
                    })
                    .build());

    private static <T extends Item> T register(String name, T item) {
        return Registry.register(Registries.ITEM, Battlegrounds.id(name), item);
    }

    public static void init() {
    }

    private ModItems() {
    }
}
