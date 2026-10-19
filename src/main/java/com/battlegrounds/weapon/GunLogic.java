package com.battlegrounds.weapon;

import com.battlegrounds.Battlegrounds;
import com.battlegrounds.item.AmmoItem;
import com.battlegrounds.item.GunItem;
import com.battlegrounds.player.BgPlayer;
import com.battlegrounds.registry.ModSounds;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GunLogic {
    public static final RegistryKey<DamageType> BULLET = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Battlegrounds.id("bullet"));

    /** Запас выстрелов у игрока: пополняется со временем, выстрел тратит 1. Позволяет темп быстрее 20 выстр/сек. */
    private static final Map<UUID, double[]> BUDGET = new HashMap<>();

    private static boolean takeShot(ServerPlayerEntity player, GunStats s, long now) {
        double[] b = BUDGET.computeIfAbsent(player.getUuid(), k -> new double[]{2.0, now});
        double cap = Math.max(2.0, s.burstCount());
        b[0] = Math.min(cap, b[0] + (now - b[1]) * 0.05 / s.fireDelay());
        b[1] = now;
        if (b[0] < 0.95) return false;
        b[0] -= 1.0;
        return true;
    }

    // ---------------- стрельба ----------------

    public static void fire(ServerPlayerEntity player, boolean aiming) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof GunItem gun) || player.isSpectator()) return;
        GunStats s = gun.stats;
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();

        if (GunItem.isReloading(stack) || BgPlayer.of(player).bg$isMantling()) return;
        if (!takeShot(player, s, now)) return;
        Attachments.Mods mods = Attachments.compute(stack, s);

        int ammo = GunItem.getAmmo(stack);
        if (ammo <= 0) {
            player.playSound(ModSounds.DRY_FIRE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            return;
        }
        GunItem.setAmmo(stack, ammo - 1);
        player.setSprinting(false);

        // разброс
        BgPlayer bg = BgPlayer.of(player);
        float spread = aiming ? s.adsSpread() : s.hipSpread();
        if (bg.bg$getStance() == BgPlayer.CROUCH) spread *= 0.8f;
        if (bg.bg$getStance() == BgPlayer.PRONE) spread *= 0.6f;
        if (!player.isOnGround()) spread *= 3.0f;
        Random rnd = player.getRandom();
        float dPitch = (float) rnd.nextGaussian() * spread * 0.5f;
        float dYaw = (float) rnd.nextGaussian() * spread * 0.5f;
        Vec3d dir = Vec3d.fromPolar(player.getPitch() + dPitch, player.getYaw() + dYaw);

        // точка выстрела (учитываем наклон)
        float yawRad = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d right = new Vec3d(-MathHelper.cos(yawRad), 0, -MathHelper.sin(yawRad));
        Vec3d origin = player.getEyePos().add(right.multiply(bg.bg$getLean() * 0.45));
        Vec3d end = origin.add(dir.multiply(s.range()));

        BlockHitResult blockHit = world.raycast(new RaycastContext(origin, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d blockEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getPos();

        EntityHitResult entityHit = ProjectileUtil.raycast(player, origin, blockEnd,
                new Box(origin, blockEnd).expand(1.0),
                e -> !e.isSpectator() && e.canHit() && e != player && !player.isConnectedThroughVehicle(e),
                origin.squaredDistanceTo(blockEnd));

        // звук и вспышка
        world.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.shotFor(s.ammo(), mods.suppressed()),
                SoundCategory.PLAYERS, mods.suppressed() ? 1.2f : 4.0f, s.soundPitch() * (0.95f + rnd.nextFloat() * 0.1f));
        Vec3d muzzle = origin.add(dir.multiply(1.0)).add(aiming ? Vec3d.ZERO : right.multiply(0.25)).add(0, aiming ? -0.05 : -0.15, 0);
        if (!mods.flashHidden()) {
            world.spawnParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.02, 0.02, 0.02, 0.0);
        }
        world.spawnParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 1, 0.02, 0.02, 0.02, 0.01);

        Vec3d hitPos;
        if (entityHit != null) {
            hitPos = entityHit.getPos();
            hitEntity(player, gun, entityHit.getEntity(), hitPos);
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            hitPos = blockHit.getPos();
            hitBlock(player, world, blockHit);
        } else {
            hitPos = end;
        }

        // трассер (с глушителем не виден)
        if (mods.suppressed()) return;
        double dist = origin.distanceTo(hitPos);
        for (double d = 3.0; d < Math.min(dist, 60.0); d += 2.5) {
            Vec3d p = origin.add(dir.multiply(d));
            world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static void hitEntity(ServerPlayerEntity player, GunItem gun, Entity target, Vec3d hitPos) {
        GunStats s = gun.stats;
        float damage = s.damage();
        if (target instanceof LivingEntity living) {
            double rel = (hitPos.y - living.getY()) / Math.max(0.1, living.getHeight());
            double eyeRel = living.getStandingEyeHeight() / Math.max(0.1, living.getHeight());
            if (rel >= eyeRel - 0.12) {
                damage *= s.headMultiplier();
                player.playSound(ModSounds.BULLET_IMPACT, SoundCategory.PLAYERS, 0.8f, 1.6f);
            } else if (rel < 0.4) {
                damage *= 0.9f;
            }
            living.timeUntilRegen = 0;
        }
        Vec3d velocity = target.getVelocity();
        DamageSource source = new DamageSource(
                player.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BULLET), player, player);
        target.damage(source, damage);
        target.setVelocity(velocity);
        if (target instanceof LivingEntity living) {
            living.timeUntilRegen = 0;
        }
        player.getServerWorld().spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
                hitPos.x, hitPos.y, hitPos.z, 8, 0.1, 0.1, 0.1, 0.1);
    }

    private static void hitBlock(ServerPlayerEntity player, ServerWorld world, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        Vec3d p = hit.getPos();
        if ((state.isIn(ConventionalBlockTags.GLASS_BLOCKS) || state.isIn(ConventionalBlockTags.GLASS_PANES))
                && player.canModifyBlocks() && world.canPlayerModifyAt(player, pos)) {
            world.breakBlock(pos, false, player);
            return;
        }
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), p.x, p.y, p.z, 6, 0.05, 0.05, 0.05, 0.1);
        world.spawnParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0.01);
        world.playSound(null, p.x, p.y, p.z, ModSounds.BULLET_IMPACT, SoundCategory.BLOCKS, 0.6f, 0.9f + world.random.nextFloat() * 0.2f);
    }

    // ---------------- перезарядка ----------------

    public static void startReload(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof GunItem gun)) return;
        if (GunItem.isReloading(stack)) return;
        Attachments.Mods mods = Attachments.compute(stack, gun.stats);
        if (GunItem.getAmmo(stack) >= mods.capacity()) return;
        if (!player.isCreative() && countAmmo(player.getInventory(), gun.stats.ammo()) <= 0) {
            player.playSound(ModSounds.DRY_FIRE, SoundCategory.PLAYERS, 1.0f, 0.8f);
            return;
        }
        long now = player.getWorld().getTime();
        stack.getOrCreateNbt().putLong(GunItem.RELOAD_START, now);
        stack.getOrCreateNbt().putLong(GunItem.RELOAD_END, now + Math.round(gun.stats.reloadTicks() * (GunItem.getAmmo(stack) == 0 ? 1.13f : 1f) * mods.reloadMul()));
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RELOAD,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    public static void finishReload(ServerPlayerEntity player, ItemStack stack, GunItem gun) {
        int have = GunItem.getAmmo(stack);
        int need = Math.max(0, gun.getCapacity(stack) - have);
        int got = player.isCreative() ? need : takeAmmo(player.getInventory(), gun.stats.ammo(), need);
        GunItem.setAmmo(stack, have + got);
        GunItem.clearReload(stack);
    }

    public static int countAmmo(PlayerInventory inv, AmmoType type) {
        int count = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.getItem() instanceof AmmoItem a && a.type == type) count += st.getCount();
        }
        return count;
    }

    private static int takeAmmo(PlayerInventory inv, AmmoType type, int amount) {
        int taken = 0;
        for (int i = 0; i < inv.size() && taken < amount; i++) {
            ItemStack st = inv.getStack(i);
            if (st.getItem() instanceof AmmoItem a && a.type == type) {
                int t = Math.min(st.getCount(), amount - taken);
                st.decrement(t);
                taken += t;
            }
        }
        return taken;
    }

    // ---------------- режим огня ----------------

    public static void toggleMode(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof GunItem gun) || gun.stats.modes().size() < 2) return;
        GunItem.setModeIndex(stack, (GunItem.getModeIndex(stack) + 1) % gun.stats.modes().size());
        player.playSound(ModSounds.FIRE_MODE, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    /** Убирает лишние патроны, если сняли расширенный магазин: возвращает их в инвентарь. */
    public static void clampAmmo(ServerPlayerEntity player, ItemStack stack, GunItem gun) {
        int cap = gun.getCapacity(stack);
        int have = GunItem.getAmmo(stack);
        if (have <= cap) return;
        GunItem.setAmmo(stack, cap);
        if (player.isCreative()) return;
        net.minecraft.item.Item ammoItem = gun.stats.ammo() == AmmoType.A762
                ? com.battlegrounds.registry.ModItems.AMMO_762 : com.battlegrounds.registry.ModItems.AMMO_556;
        player.getInventory().offerOrDrop(new ItemStack(ammoItem, have - cap));
    }

    private GunLogic() {
    }
}
