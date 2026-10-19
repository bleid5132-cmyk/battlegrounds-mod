package com.battlegrounds.weapon;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

/** Чтение/запись обвесов в предмете оружия и их суммарный эффект. */
public final class Attachments {
    public static final String KEY = "Att";

    @Nullable
    public static AttachmentType get(ItemStack gun, AttachmentSlot slot) {
        NbtCompound nbt = gun.getSubNbt(KEY);
        if (nbt == null || !nbt.contains(slot.key)) return null;
        return AttachmentType.byId(nbt.getString(slot.key));
    }

    public static void set(ItemStack gun, AttachmentSlot slot, @Nullable AttachmentType type) {
        if (type == null) {
            NbtCompound nbt = gun.getSubNbt(KEY);
            if (nbt != null) nbt.remove(slot.key);
        } else {
            gun.getOrCreateSubNbt(KEY).putString(slot.key, type.id);
        }
    }

    public record Mods(
            float recoilUp,
            float recoilSide,
            float aimSpeed,
            float zoom,
            AttachmentType.Reticle reticle,
            @Nullable AttachmentType scope,
            boolean suppressed,
            boolean flashHidden,
            int capacity,
            float reloadMul
    ) {
    }

    public static Mods compute(ItemStack gun, GunStats stats) {
        float up = 1f, side = 1f, aim = 1f, reload = 1f;
        int cap = stats.magazine();
        boolean suppressed = false, flash = false;

        AttachmentType scope = get(gun, AttachmentSlot.SCOPE);
        float zoom = scope != null ? scope.zoom() : stats.ironZoom();
        AttachmentType.Reticle reticle = scope != null ? scope.reticle() : AttachmentType.Reticle.NONE;
        if (scope != null && scope.zoom() >= 4f) aim *= 0.8f;

        AttachmentType muzzle = get(gun, AttachmentSlot.MUZZLE);
        if (muzzle == AttachmentType.COMPENSATOR) { up *= 0.75f; side *= 0.75f; }
        if (muzzle == AttachmentType.FLASH_HIDER) { up *= 0.9f; side *= 0.9f; flash = true; }
        if (muzzle == AttachmentType.SUPPRESSOR) { up *= 0.95f; suppressed = true; flash = true; }

        AttachmentType grip = get(gun, AttachmentSlot.GRIP);
        if (grip == AttachmentType.VERTICAL_GRIP) { up *= 0.85f; }
        if (grip == AttachmentType.ANGLED_GRIP) { side *= 0.8f; aim *= 1.2f; }
        if (grip == AttachmentType.HALF_GRIP) { up *= 0.92f; side *= 0.92f; }
        if (grip == AttachmentType.THUMB_GRIP) { up *= 0.95f; aim *= 1.3f; }
        if (grip == AttachmentType.LIGHT_GRIP) { side *= 0.85f; }

        AttachmentType mag = get(gun, AttachmentSlot.MAGAZINE);
        if (mag == AttachmentType.EXTENDED_MAG || mag == AttachmentType.EXTENDED_QUICKDRAW_MAG) cap = stats.extMagazine();
        if (mag == AttachmentType.QUICKDRAW_MAG || mag == AttachmentType.EXTENDED_QUICKDRAW_MAG) reload *= 0.7f;

        AttachmentType stock = get(gun, AttachmentSlot.STOCK);
        if (stock == AttachmentType.TACTICAL_STOCK) { up *= 0.9f; side *= 0.9f; aim *= 1.1f; }

        return new Mods(up, side, aim, zoom, reticle, scope, suppressed, flash, cap, reload);
    }

    private Attachments() {
    }
}
