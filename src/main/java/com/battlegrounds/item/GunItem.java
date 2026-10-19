package com.battlegrounds.item;

import com.battlegrounds.weapon.GunLogic;
import com.battlegrounds.weapon.GunStats;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GunItem extends Item {
    public static final String AMMO = "Ammo";
    public static final String RELOAD_END = "ReloadEnd";
    public static final String RELOAD_START = "ReloadStart";
    public static final String MODE = "Mode";

    public final GunStats stats;

    public GunItem(Settings settings, GunStats stats) {
        super(settings);
        this.stats = stats;
    }

    // ---------- данные в предмете ----------

    public static int getAmmo(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0 : nbt.getInt(AMMO);
    }

    public static void setAmmo(ItemStack stack, int ammo) {
        stack.getOrCreateNbt().putInt(AMMO, ammo);
    }

    public static boolean isReloading(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.getLong(RELOAD_END) > 0;
    }

    public static long getReloadEnd(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0 : nbt.getLong(RELOAD_END);
    }

    public static long getReloadStart(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0 : nbt.getLong(RELOAD_START);
    }

    public static void clearReload(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            nbt.remove(RELOAD_END);
            nbt.remove(RELOAD_START);
        }
    }

    /** 0 = автоматический огонь, 1 = одиночный */
    public static int getMode(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0 : nbt.getByte(MODE);
    }

    public static void setMode(ItemStack stack, int mode) {
        stack.getOrCreateNbt().putByte(MODE, (byte) mode);
    }

    public boolean isAutoMode(ItemStack stack) {
        return stats.automatic() && getMode(stack) == 0;
    }

    public ItemStack createLoaded() {
        ItemStack stack = new ItemStack(this);
        setAmmo(stack, stats.magazine());
        return stack;
    }

    // ---------- поведение предмета ----------

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof ServerPlayerEntity player)) return;
        if (!isReloading(stack)) return;
        boolean inHand = player.getMainHandStack() == stack;
        if (!inHand) {
            clearReload(stack);
            return;
        }
        if (world.getTime() >= getReloadEnd(stack)) {
            GunLogic.finishReload(player, stack, this);
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.fail(user.getStackInHand(hand));
    }

    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        return false;
    }

    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public boolean allowContinuingBlockBreaking(PlayerEntity player, ItemStack oldStack, ItemStack newStack) {
        return true;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13.0f * getAmmo(stack) / stats.magazine());
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        float f = (float) getAmmo(stack) / stats.magazine();
        return MathHelper.hsvToRgb(f / 3.0f, 0.8f, 1.0f);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.battlegrounds.ammo", getAmmo(stack), stats.magazine()).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.battlegrounds.caliber", stats.ammo().label).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.battlegrounds.damage", Math.round(stats.damage() * 5)).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.battlegrounds.rpm", Math.round(1200f / stats.fireInterval())).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(isAutoMode(stack) ? "hud.battlegrounds.mode.auto" : "hud.battlegrounds.mode.single").formatted(Formatting.DARK_GRAY));
    }
}
