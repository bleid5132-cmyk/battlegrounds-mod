package com.battlegrounds.screen;

import com.battlegrounds.item.AttachmentItem;
import com.battlegrounds.item.GunItem;
import com.battlegrounds.registry.ModItems;
import com.battlegrounds.registry.ModScreens;
import com.battlegrounds.registry.ModSounds;
import com.battlegrounds.weapon.AttachmentSlot;
import com.battlegrounds.weapon.AttachmentType;
import com.battlegrounds.weapon.Attachments;
import com.battlegrounds.weapon.GunLogic;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

/** Меню обвесов: 5 ячеек вокруг оружия + инвентарь игрока. */
public class AttachmentScreenHandler extends ScreenHandler {
    public static final int[][] SLOT_POS = {
            {80, 18},  // прицел
            {26, 40},  // дульный
            {62, 62},  // рукоятка
            {98, 62},  // магазин
            {134, 40}  // приклад
    };

    public final PlayerInventory playerInventory;
    public final int gunSlot;
    private final SimpleInventory attachments = new SimpleInventory(5);
    private boolean loading;

    public AttachmentScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ModScreens.ATTACHMENTS, syncId);
        this.playerInventory = playerInventory;
        this.gunSlot = playerInventory.selectedSlot;

        ItemStack gun = getGun();
        loading = true;
        for (AttachmentSlot s : AttachmentSlot.values()) {
            AttachmentType t = gun.getItem() instanceof GunItem ? Attachments.get(gun, s) : null;
            attachments.setStack(s.ordinal(), t == null ? ItemStack.EMPTY : new ItemStack(ModItems.ATTACHMENTS.get(t)));
        }
        loading = false;
        attachments.addListener(inv -> onAttachmentsChanged());

        for (AttachmentSlot s : AttachmentSlot.values()) {
            int[] p = SLOT_POS[s.ordinal()];
            addSlot(new AttSlot(attachments, s, p[0], p[1]));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            if (col == gunSlot) {
                addSlot(new LockedSlot(playerInventory, col, 8 + col * 18, 160));
            } else {
                addSlot(new Slot(playerInventory, col, 8 + col * 18, 160));
            }
        }
    }

    public ItemStack getGun() {
        return playerInventory.getStack(gunSlot);
    }

    public GunItem getGunItem() {
        return getGun().getItem() instanceof GunItem g ? g : null;
    }

    private void onAttachmentsChanged() {
        if (loading) return;
        ItemStack gun = getGun();
        if (!(gun.getItem() instanceof GunItem g)) return;
        for (AttachmentSlot s : AttachmentSlot.values()) {
            ItemStack st = attachments.getStack(s.ordinal());
            AttachmentType t = st.getItem() instanceof AttachmentItem a ? a.type : null;
            Attachments.set(gun, s, t);
        }
        if (playerInventory.player instanceof ServerPlayerEntity sp) {
            GunLogic.clampAmmo(sp, gun, g);
            sp.playSound(ModSounds.ATTACH, SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (actionType == SlotActionType.SWAP && button == gunSlot) return;
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index < 5) {
            if (!insertItem(stack, 5, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < 5; i++) {
                Slot target = this.slots.get(i);
                if (!target.hasStack() && target.canInsert(stack)) {
                    target.setStack(stack.split(1));
                    moved = true;
                    break;
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        return copy;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return getGun().getItem() instanceof GunItem && player.getInventory().selectedSlot == gunSlot;
    }

    /** Ячейка для одного типа обвеса. */
    public class AttSlot extends Slot {
        public final AttachmentSlot type;

        public AttSlot(SimpleInventory inv, AttachmentSlot type, int x, int y) {
            super(inv, type.ordinal(), x, y);
            this.type = type;
        }

        public boolean isAllowedForGun() {
            GunItem g = getGunItem();
            return g != null && g.stats.slots().contains(type);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            GunItem g = getGunItem();
            return g != null && stack.getItem() instanceof AttachmentItem a
                    && a.type.slot == type && g.stats.accepts(a.type);
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }
    }

    /** Ячейка с самим оружием — её нельзя трогать, пока открыто меню. */
    public static class LockedSlot extends Slot {
        public LockedSlot(PlayerInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean canTakeItems(PlayerEntity player) {
            return false;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }
    }
}
