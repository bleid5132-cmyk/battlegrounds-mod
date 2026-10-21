package com.battlegrounds.client;

import com.battlegrounds.client.geo.FirstPersonGun;
import com.battlegrounds.item.GunItem;
import com.battlegrounds.player.BgPlayer;
import com.battlegrounds.weapon.Attachments;
import com.battlegrounds.weapon.FireMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/** Вся клиентская логика: стрельба, прицеливание, отдача, стойки, наклоны. */
public final class ClientController {
    private static boolean aiming;
    private static float aimProgress, prevAimProgress;
    private static Perspective savedPerspective;

    private static float fireCredit;
    private static int burstLeft;
    private static boolean wasAttackDown;
    private static float pendingRecoilPitch, pendingRecoilYaw;
    private static float kick, prevKick;
    private static float sprintAnim, prevSprintAnim;

    private static boolean wasJumpDown;
    private static int sentLean;
    private static boolean sentAim;

    private static int lastSlot = -1;
    private static ItemStack lastStackRef = ItemStack.EMPTY;
    private static int equipTicks = 100;

    @Nullable
    private static Attachments.Mods mods;

    public static boolean holdingGun(PlayerEntity player) {
        return player != null && player.getMainHandStack().getItem() instanceof GunItem;
    }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            aiming = false;
            savedPerspective = null;
            mods = null;
            return;
        }
        BgPlayer bg = BgPlayer.of(p);
        boolean inGame = mc.currentScreen == null;
        ItemStack stack = p.getMainHandStack();
        GunItem gun = stack.getItem() instanceof GunItem g ? g : null;
        mods = gun != null ? Attachments.compute(stack, gun.stats) : null;

        // смена оружия в руках — анимация доставания
        int slot = p.getInventory().selectedSlot;
        if (slot != lastSlot || (stack.getItem() != lastStackRef.getItem())) {
            equipTicks = 0;
            burstLeft = 0;
            FirstPersonGun.onEquip(stack);
        }
        lastSlot = slot;
        lastStackRef = stack;
        if (equipTicks < 100) equipTicks++;

        // ---------- клавиши оружия ----------
        while (BattlegroundsClient.RELOAD.wasPressed()) {
            if (gun != null) ClientNet.reload();
        }
        while (BattlegroundsClient.FIRE_MODE.wasPressed()) {
            if (gun != null) ClientNet.fireMode();
        }
        while (BattlegroundsClient.INSPECT.wasPressed()) {
            if (gun != null && inGame) FirstPersonGun.inspect(stack);
        }
        while (BattlegroundsClient.ATTACHMENTS.wasPressed()) {
            if (gun != null && inGame) ClientNet.openAttachments();
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
        int readyTicks = FirstPersonGun.handles(stack) ? Math.round(FirstPersonGun.drawLength(stack) * 20f * 0.8f) : 6;
        boolean wantAim = gun != null && inGame && mc.options.useKey.isPressed() && !Mantle.isActive() && equipTicks > readyTicks - 2
                && !(FirstPersonGun.handles(stack) && GunItem.isReloading(stack));
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
        if (aiming != sentAim) {
            sentAim = aiming;
            bg.bg$setAiming(aiming);
            ClientNet.aim(aiming);
        }
        prevAimProgress = aimProgress;
        float aimSpeed = 0.28f * (mods != null ? mods.aimSpeed() : 1f);
        aimProgress = MathHelper.clamp(aimProgress + (aiming ? aimSpeed : -0.3f), 0f, 1f);

        // ---------- наклоны (Q / E с оружием в руках) ----------
        int lean = 0;
        if (gun != null && inGame && stance != BgPlayer.PRONE && !p.isSprinting() && !Mantle.isActive()) {
            if (mc.options.dropKey.isPressed()) lean -= 1;
            if (mc.options.inventoryKey.isPressed()) lean += 1;
        }
        if (lean != sentLean) {
            sentLean = lean;
            bg.bg$setLean(lean);
            ClientNet.lean(lean);
        }

        // ---------- стрельба ----------
        boolean attackDown = gun != null && inGame && mc.options.attackKey.isPressed() && !Mantle.isActive() && equipTicks > readyTicks;
        if (gun != null) {
            FireMode mode = gun.getFireMode(stack);
            boolean newPress = attackDown && !wasAttackDown;
            float delay = gun.stats.fireDelay();

            if (newPress && mode == FireMode.BURST && burstLeft == 0) burstLeft = gun.stats.burstCount();
            boolean wantsShot = burstLeft > 0
                    || (mode == FireMode.AUTO && attackDown)
                    || (mode == FireMode.SINGLE && newPress);

            if (wantsShot) {
                if (newPress && fireCredit < delay) fireCredit = delay;
                if (!newPress) fireCredit += 0.05f;
                int shots = 0;
                while (fireCredit >= delay - 1.0E-4f && shots < 4) {
                    if (GunItem.isReloading(stack)) {
                        burstLeft = 0;
                        break;
                    }
                    if (GunItem.getAmmo(stack) - shots <= 0) {
                        if (newPress) {
                            ClientNet.fire(aiming);
                            ClientNet.reload();
                        }
                        burstLeft = 0;
                        fireCredit = 0;
                        break;
                    }
                    ClientNet.fire(aiming);
                    addRecoil(p, gun, bg);
                    FirstPersonGun.onShoot(stack);
                    p.setSprinting(false);
                    fireCredit -= delay;
                    shots++;
                    if (burstLeft > 0 && --burstLeft == 0) {
                        fireCredit = 0;
                        break;
                    }
                }
                if (mode == FireMode.SINGLE) fireCredit = 0;
            } else {
                fireCredit = Math.min(fireCredit + 0.05f, delay);
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
        kick *= 0.45f;
        prevSprintAnim = sprintAnim;
        sprintAnim += ((p.isSprinting() && !aiming ? 1f : 0f) - sprintAnim) * 0.3f;
    }

    private static void addRecoil(ClientPlayerEntity p, GunItem g, BgPlayer bg) {
        float mul = aiming ? 0.75f : 1.0f;
        if (bg.bg$getStance() == BgPlayer.CROUCH) mul *= 0.85f;
        if (bg.bg$getStance() == BgPlayer.PRONE) mul *= 0.6f;
        float up = mods != null ? mods.recoilUp() : 1f;
        float side = mods != null ? mods.recoilSide() : 1f;
        pendingRecoilPitch += g.stats.recoilUp() * up * mul * (0.85f + p.getRandom().nextFloat() * 0.3f);
        pendingRecoilYaw += (p.getRandom().nextFloat() * 2f - 1f) * g.stats.recoilSide() * side * mul;
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

    public static float getSprint(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevSprintAnim, sprintAnim);
    }

    /** 0 → 1 за первые тики после доставания оружия. */
    public static float getEquip(float tickDelta) {
        return MathHelper.clamp((equipTicks + tickDelta) / 8f, 0f, 1f);
    }

    @Nullable
    public static Attachments.Mods getMods() {
        return mods;
    }

    /** Во сколько раз приближено изображение сейчас. */
    public static double getZoom(float tickDelta) {
        if (mods == null) return 1.0;
        return 1.0 + (mods.zoom() - 1.0) * getAimProgress(tickDelta);
    }

    private ClientController() {
    }
}
