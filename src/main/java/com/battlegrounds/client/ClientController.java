package com.battlegrounds.client;

import com.battlegrounds.item.GunItem;
import com.battlegrounds.player.BgPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

/** Вся клиентская логика: стрельба, прицеливание, отдача, стойки, наклоны. */
public final class ClientController {
    private static boolean aiming;
    private static float aimProgress, prevAimProgress;
    private static Perspective savedPerspective;

    private static int fireCooldown;
    private static boolean wasAttackDown;
    private static float pendingRecoilPitch, pendingRecoilYaw;
    private static float kick, prevKick;

    private static boolean wasJumpDown;
    private static int sentLean;

    public static boolean holdingGun(PlayerEntity player) {
        return player != null && player.getMainHandStack().getItem() instanceof GunItem;
    }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            aiming = false;
            savedPerspective = null;
            return;
        }
        BgPlayer bg = BgPlayer.of(p);
        boolean inGame = mc.currentScreen == null;
        ItemStack stack = p.getMainHandStack();
        boolean gun = stack.getItem() instanceof GunItem;

        // ---------- клавиши оружия ----------
        while (BattlegroundsClient.RELOAD.wasPressed()) {
            if (gun) ClientNet.reload();
        }
        while (BattlegroundsClient.FIRE_MODE.wasPressed()) {
            if (gun) ClientNet.fireMode();
        }

        // ---------- стойки ----------
        int stance = bg.bg$getStance();
        while (BattlegroundsClient.CROUCH.wasPressed()) {
            setStance(bg, stance == BgPlayer.CROUCH ? BgPlayer.STAND : BgPlayer.CROUCH);
            stance = bg.bg$getStance();
        }
        while (BattlegroundsClient.PRONE.wasPressed()) {
            setStance(bg, stance == BgPlayer.PRONE ? BgPlayer.STAND : BgPlayer.PRONE);
            stance = bg.bg$getStance();
        }
        if (stance != BgPlayer.STAND && mc.options.sprintKey.isPressed() && p.input.pressingForward) {
            setStance(bg, BgPlayer.STAND);
            stance = BgPlayer.STAND;
        }
        if (stance != BgPlayer.STAND && (p.isTouchingWater() || p.getAbilities().flying || p.hasVehicle())) {
            setStance(bg, BgPlayer.STAND);
            stance = BgPlayer.STAND;
        }

        // ---------- прыжок: встать из положения лёжа или перелезть ----------
        boolean jumpDown = inGame && mc.options.jumpKey.isPressed();
        if (jumpDown && !wasJumpDown) {
            if (stance == BgPlayer.PRONE) {
                setStance(bg, BgPlayer.CROUCH);
            } else if (!Mantle.isActive()) {
                if (Mantle.tryStart(p) && stance != BgPlayer.STAND) {
                    setStance(bg, BgPlayer.STAND);
                }
            }
        }
        wasJumpDown = jumpDown;
        Mantle.tick(p);

        // ---------- прицеливание ----------
        boolean wantAim = gun && inGame && mc.options.useKey.isPressed() && !Mantle.isActive();
        if (wantAim && !aiming) {
            Perspective persp = mc.options.getPerspective();
            if (!persp.isFirstPerson()) {
                savedPerspective = persp;
                mc.options.setPerspective(Perspective.FIRST_PERSON);
            }
        } else if (!wantAim && aiming) {
            if (savedPerspective != null && mc.options.getPerspective().isFirstPerson()) {
                mc.options.setPerspective(savedPerspective);
            }
            savedPerspective = null;
        }
        aiming = wantAim;
        if (aiming) p.setSprinting(false);
        prevAimProgress = aimProgress;
        aimProgress = MathHelper.clamp(aimProgress + (aiming ? 0.3f : -0.3f), 0f, 1f);

        // ---------- наклоны (Q / E с оружием в руках) ----------
        int lean = 0;
        if (gun && inGame && stance != BgPlayer.PRONE && !p.isSprinting() && !Mantle.isActive()) {
            if (mc.options.dropKey.isPressed()) lean -= 1;
            if (mc.options.inventoryKey.isPressed()) lean += 1;
        }
        if (lean != sentLean) {
            sentLean = lean;
            bg.bg$setLean(lean);
            ClientNet.lean(lean);
        }

        // ---------- стрельба ----------
        if (fireCooldown > 0) fireCooldown--;
        boolean attackDown = gun && inGame && mc.options.attackKey.isPressed() && !Mantle.isActive();
        if (gun && attackDown) {
            GunItem g = (GunItem) stack.getItem();
            boolean newPress = !wasAttackDown;
            if ((g.isAutoMode(stack) || newPress) && fireCooldown <= 0) {
                if (GunItem.isReloading(stack)) {
                    // ждём конца перезарядки
                } else if (GunItem.getAmmo(stack) > 0) {
                    ClientNet.fire(aiming);
                    fireCooldown = g.stats.fireInterval();
                    p.setSprinting(false);
                    addRecoil(p, g, bg);
                } else if (newPress) {
                    ClientNet.fire(aiming); // щелчок пустого магазина
                    ClientNet.reload();
                    fireCooldown = 6;
                }
            }
        }
        wasAttackDown = attackDown;

        // ---------- отдача ----------
        if (pendingRecoilPitch != 0 || pendingRecoilYaw != 0) {
            float dp = pendingRecoilPitch * 0.6f;
            float dy = pendingRecoilYaw * 0.6f;
            if (Math.abs(pendingRecoilPitch) < 0.02f) dp = pendingRecoilPitch;
            if (Math.abs(pendingRecoilYaw) < 0.02f) dy = pendingRecoilYaw;
            p.setPitch(MathHelper.clamp(p.getPitch() - dp, -90f, 90f));
            p.setYaw(p.getYaw() + dy);
            pendingRecoilPitch -= dp;
            pendingRecoilYaw -= dy;
        }
        prevKick = kick;
        kick *= 0.5f;
    }

    private static void addRecoil(ClientPlayerEntity p, GunItem g, BgPlayer bg) {
        float mul = aiming ? 0.75f : 1.0f;
        if (bg.bg$getStance() == BgPlayer.CROUCH) mul *= 0.85f;
        if (bg.bg$getStance() == BgPlayer.PRONE) mul *= 0.6f;
        pendingRecoilPitch += g.stats.recoilUp() * mul * (0.85f + p.getRandom().nextFloat() * 0.3f);
        pendingRecoilYaw += (p.getRandom().nextFloat() * 2f - 1f) * g.stats.recoilSide() * mul;
        kick = 1.0f;
    }

    private static void setStance(BgPlayer bg, int stance) {
        bg.bg$setStance(stance);
        ClientNet.stance(stance);
    }

    // ---------- данные для отрисовки ----------

    public static boolean isAiming() {
        return aiming;
    }

    public static float getAimProgress(float tickDelta) {
        float a = MathHelper.lerp(tickDelta, prevAimProgress, aimProgress);
        return a * a * (3f - 2f * a);
    }

    public static float getKick(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevKick, kick);
    }

    /** Во сколько раз приближено изображение сейчас. */
    public static double getZoom(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 1.0;
        ItemStack stack = mc.player.getMainHandStack();
        if (!(stack.getItem() instanceof GunItem g)) return 1.0;
        return 1.0 + (g.stats.adsZoom() - 1.0) * getAimProgress(tickDelta);
    }

    private ClientController() {
    }
}
