package com.battlegrounds.registry;

import com.battlegrounds.Battlegrounds;
import com.battlegrounds.item.AmmoItem;
import com.battlegrounds.item.AttachmentItem;
import com.battlegrounds.item.GunItem;
import com.battlegrounds.weapon.AmmoType;
import com.battlegrounds.weapon.AttachmentType;
import com.battlegrounds.weapon.GunStats;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModItems {
    public static final Map<String, GunItem> GUNS = new LinkedHashMap<>();
    public static final Map<AttachmentType, AttachmentItem> ATTACHMENTS = new EnumMap<>(AttachmentType.class);

    public static final GunItem M416;

    static {
        for (GunStats stats : GunStats.ASSAULT_RIFLES) {
            GUNS.put(stats.id(), register(stats.id(), new GunItem(new Item.Settings().maxCount(1), stats)));
        }
        M416 = GUNS.get("m416");
        for (AttachmentType type : AttachmentType.values()) {
            ATTACHMENTS.put(type, register(type.id, new AttachmentItem(new Item.Settings().maxCount(1), type)));
        }
    }

    public static final AmmoItem AMMO_556 = register("ammo_556",
            new AmmoItem(new Item.Settings().maxCount(64), AmmoType.A556));
    public static final AmmoItem AMMO_762 = register("ammo_762",
            new AmmoItem(new Item.Settings().maxCount(64), AmmoType.A762));

    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, Battlegrounds.id("main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(M416))
                    .displayName(Text.translatable("itemGroup.battlegrounds.main"))
                    .entries((context, entries) -> {
                        for (GunItem gun : GUNS.values()) entries.add(gun.createLoaded());
                        entries.add(AMMO_556);
                        entries.add(AMMO_762);
                        for (AttachmentItem att : ATTACHMENTS.values()) entries.add(att);
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
