package com.battlegrounds.client.geo;

import com.battlegrounds.client.ClientController;
import com.battlegrounds.client.fx.Shells;
import com.battlegrounds.client.render.GeoGunItemRenderer;
import com.battlegrounds.item.GunItem;
import com.battlegrounds.weapon.AttachmentSlot;
import com.battlegrounds.weapon.AttachmentType;
import com.battlegrounds.weapon.Attachments;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Random;
import java.util.Set;

/**
 * Вид оружия от первого лица по схеме TACZ:
 * камера стоит в точке idle_view модели и плавно переходит в iron_view (или прицел) при прицеливании,
 * руки игрока рисуются в костях righthand/lefthand, анимации идут дорожками и складываются.
 */
public final class FirstPersonGun {
    // ---------------- дорожки анимаций ----------------
    static final class Track {
        GeoAnimation cur, prev;
        float t, prevT, speed = 1f, fade = 1f, fadeTime = 0.2f;
        int nextSound;

        void play(@Nullable GeoAnimation a, float fadeSeconds, float speed) {
            if (a == cur) { this.speed = speed; return; }
            prev = cur;
            prevT = t;
            cur = a;
            t = 0f;
            nextSound = 0;
            this.speed = speed;
            fadeTime = fadeSeconds;
            fade = fadeSeconds <= 0f || prev == null ? 1f : 0f;
        }

        void restart(GeoAnimation a, float speed) {
            prev = null;
            cur = a;
            t = 0f;
            nextSound = 0;
            fade = 1f;
            this.speed = speed;
        }

        void stop(float fadeSeconds) {
            play(null, fadeSeconds, 1f);
        }

        boolean finished() {
            return cur == null || (!cur.loop && t >= cur.length);
        }

        void advance(float dt) {
            if (cur != null) {
                t += dt * speed;
                if (cur.loop && cur.length > 0) t %= cur.length;
                else t = Math.min(t, cur.length);
                // звуки по ходу анимации (затвор, магазин, ремень)
                while (!cur.loop && nextSound < cur.soundTimes.length && cur.soundTimes[nextSound] <= t) {
                    com.battlegrounds.client.fx.GunSounds.play(cur.soundNames[nextSound]);
                    nextSound++;
                }
            }
            if (prev != null) {
                prevT += dt * speed;
                if (prev.loop && prev.length > 0) prevT %= prev.length;
                else prevT = Math.min(prevT, prev.length);
            }
            if (fade < 1f) {
                fade = Math.min(1f, fade + dt / Math.max(0.01f, fadeTime));
                if (fade >= 1f) prev = null;
            }
        }

        void apply(GeoPose pose, float weight) {
            float f = smooth(fade);
            if (prev != null) prev.apply(pose, prevT, weight * (1f - f));
            if (cur != null) cur.apply(pose, t, weight * f);
        }
    }

    private static final Track BASE = new Track();     // покой / ходьба / бег
    private static final Track MAIN = new Track();     // доставание / перезарядка
    private static final Track SHOOT = new Track();    // выстрел
    private static final Track ADS = new Track();      // подъём к прицелу

    private enum Run { NONE, START, LOOP, END }

    private static Run run = Run.NONE;

    /** Оружие ещё опущено после бега (бег или первая половина выхода из него). */
    public static boolean runBlocksFire() {
        return run == Run.START || run == Run.LOOP || (run == Run.END && BASE.cur != null && BASE.t < BASE.cur.length * 0.45f);
    }
    private static String modelId;
    private static GeoPose pose;
    private static long lastFrame = -1;
    private static boolean wasReloading;
    private static boolean wasAiming;

    private static final SecondOrderDynamics AIM = new SecondOrderDynamics(1.2f, 1.2f, 0.5f, 0f);
    private static final SecondOrderDynamics JUMP = new SecondOrderDynamics(0.28f, 1f, 0.65f, 0f);
    private static float aimValue;
    private static float jumpProgress;
    private static boolean lastOnGround = true;
    private static long shootTime = -1;
    private static float shootSwayX, shootSwayYaw;
    private static final Random RANDOM = new Random();
    private static final float[] CAMERA = new float[3];
    private static boolean renderingHand;

    // ---------------- события ----------------

    public static boolean handles(ItemStack stack) {
        return stack.getItem() instanceof GunItem g && GeoAssets.get(g.stats.id()) != null;
    }

    public static void onEquip(ItemStack stack) {
        Shells.clearFirstPerson();
        if (!handles(stack)) {
            modelId = null;
            return;
        }
        GeoAssets.Entry e = entry(stack);
        BASE.restart(e.anim("idle"), 1f);
        run = Run.NONE;
        GeoAnimation draw = e.anim("draw");
        if (draw != null) MAIN.restart(draw, 1f);
        SHOOT.restart(null, 1f);
        wasReloading = GunItem.isReloading(stack);
    }

    public static float drawLength(ItemStack stack) {
        GeoAssets.Entry e = handles(stack) ? entry(stack) : null;
        GeoAnimation d = e != null ? e.anim("draw") : null;
        return d != null ? d.length : 0.3f;
    }

    public static void onShoot(ItemStack stack) {
        if (!handles(stack)) return;
        GeoAnimation s = entry(stack).anim("shoot");
        if (s != null) SHOOT.restart(s, 1f);
        shootTime = System.currentTimeMillis();
        Shells.spawnFirstPerson(((GunItem) stack.getItem()).stats.id());
        Attachments.Mods fm = Attachments.compute(stack, ((GunItem) stack.getItem()).stats);
        com.battlegrounds.client.fx.ShotFx.firstPersonFlash(fm.suppressed(), fm.flashHidden());
        shootSwayX = (RANDOM.nextFloat() * 2f - 1f) * 0.2f;
        shootSwayYaw = (RANDOM.nextFloat() * 2f - 1f) * 0.78f;
    }

    private static GeoAssets.Entry entry(ItemStack stack) {
        return GeoAssets.get(((GunItem) stack.getItem()).stats.id());
    }

    /** Значение подъёма к прицелу с пружинной кривой (0..1), считается раз в кадр. */
    public static float aim() {
        return aimValue;
    }

    public static boolean isRenderingHand() {
        return renderingHand;
    }

    public static void setRenderingHand(boolean v) {
        renderingHand = v;
    }

    /** Поворот камеры от анимаций (градусы), с ослаблением в оптике. */
    public static float[] cameraRotation() {
        return CAMERA;
    }

    public static boolean active() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.options.getPerspective().isFirstPerson() && handles(mc.player.getMainHandStack());
    }

    // ---------------- обновление ----------------

    private static void update(ClientPlayerEntity player, ItemStack stack, GeoAssets.Entry e, float tickDelta) {
        long now = System.nanoTime();
        float dt = lastFrame < 0 ? 0f : MathHelper.clamp((now - lastFrame) / 1.0e9f, 0f, 0.1f);
        lastFrame = now;

        GunItem gun = (GunItem) stack.getItem();
        if (!gun.stats.id().equals(modelId) || pose == null || pose.model != e.model()) {
            modelId = gun.stats.id();
            pose = new GeoPose(e.model());
        }

        // перезарядка: длительность берём из сервера (с учётом быстрого магазина)
        boolean reloading = GunItem.isReloading(stack);
        if (reloading && !wasReloading) {
            boolean empty = GunItem.getAmmo(stack) == 0;
            GeoAnimation r = e.anim(empty ? "reload_empty" : "reload_tactical");
            if (r == null) r = e.anim("reload_tactical");
            if (r != null) {
                long ticks = Math.max(1, GunItem.getReloadEnd(stack) - GunItem.getReloadStart(stack));
                MAIN.restart(r, r.length / (ticks * 0.05f));
            }
        } else if (!reloading && wasReloading && MAIN.cur != null && MAIN.cur.name.startsWith("reload") && !MAIN.finished()) {
            MAIN.stop(0.15f);   // перезарядку прервали
        }
        wasReloading = reloading;

        // прицеливание
        boolean aiming = ClientController.isAiming();
        if (aiming && !wasAiming && e.anim("ads_up") != null) ADS.restart(e.anim("ads_up"), 1f);
        if (!aiming && wasAiming) com.battlegrounds.client.fx.GunSounds.play("aim_out");
        wasAiming = aiming;
        aimValue = MathHelper.clamp(AIM.update(ClientController.getAimProgress(tickDelta)), 0f, 1.05f);

        // базовая дорожка: покой / ходьба / бег
        double dx = player.getX() - player.prevX;
        double dz = player.getZ() - player.prevZ;
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        boolean busy = MAIN.cur != null && !MAIN.finished();
        boolean sprint = player.isSprinting() && !aiming && !busy && player.isOnGround();
        switch (run) {
            case NONE -> {
                if (sprint && e.anim("run_start") != null) {
                    run = Run.START;
                    BASE.play(e.anim("run_start"), 0.08f, 1f);
                }
            }
            case START -> {
                if (!sprint) {
                    run = Run.END;
                    BASE.play(e.anim("run_end"), 0.1f, 1f);
                } else if (BASE.finished()) {
                    run = Run.LOOP;
                    BASE.play(e.anim("run"), 0.05f, 1f);
                }
            }
            case LOOP -> {
                BASE.speed = MathHelper.clamp(speed / 0.28f, 0.7f, 1.3f);
                if (!sprint) {
                    run = Run.END;
                    BASE.play(e.anim("run_end"), 0.12f, 1f);
                }
            }
            case END -> {
                if (sprint) {
                    run = Run.START;
                    BASE.play(e.anim("run_start"), 0.12f, 1f);
                } else if (BASE.finished()) {
                    run = Run.NONE;
                }
            }
        }
        if (run == Run.NONE) {
            boolean walking = speed > 0.015f && player.isOnGround();
            GeoAnimation want = walking ? e.anim(aiming ? "walk_aiming" : "walk") : e.anim("idle");
            float spd = walking ? MathHelper.clamp(speed / 0.2f, 0.5f, 1.4f) : 1f;
            BASE.play(want, 0.25f, spd);
        }

        BASE.advance(dt);
        MAIN.advance(dt);
        SHOOT.advance(dt);
        ADS.advance(dt);

        // складываем позу
        pose.reset();
        BASE.apply(pose, 1f);
        MAIN.apply(pose, 1f);
        SHOOT.apply(pose, 1f);
        ADS.apply(pose, 1f);

        // левая рука на рукоятке, если она стоит (плавно отпускается, когда рука занята анимацией)
        float[] lh = pose.get("lefthand");
        AttachmentType grip = Attachments.get(stack, AttachmentSlot.GRIP);
        if (lh != null && grip != null) {
            float away = (float) Math.sqrt(lh[0] * lh[0] + lh[1] * lh[1] + lh[2] * lh[2]);
            float w = MathHelper.clamp(1f - away / 3f, 0f, 1f);
            float drop = grip == AttachmentType.VERTICAL_GRIP || grip == AttachmentType.LIGHT_GRIP ? 2.6f : 1.0f;
            lh[1] -= drop * w;
            lh[2] += 0.4f * w;
            lh[3] -= 12f * w;
        }

        // процедурные движения корпуса оружия: запаздывание за взглядом, отдача от бедра, прыжок
        float[] root = pose.get("root");
        if (root != null) {
            float xRot = player.getPitch(tickDelta) - MathHelper.lerp(tickDelta, player.lastRenderPitch, player.renderPitch);
            float yRot = player.getYaw(tickDelta) - MathHelper.lerp(tickDelta, player.lastRenderYaw, player.renderYaw);
            xRot = (float) Math.tanh(xRot / 25f) * 25f;
            yRot = (float) Math.tanh(yRot / 25f) * 25f;
            root[0] -= yRot * 0.1f / 3f;
            root[1] += xRot * 0.1f / 3f;
            root[3] += xRot * 0.05f;
            root[4] += yRot * 0.05f;

            float sp = shootTime < 0 ? 0f : 1f - (System.currentTimeMillis() - shootTime) / 300f;
            if (sp > 0f) {
                float k = 1f - (1f - sp) * (1f - sp) * (1f - sp);
                float hip = 1f - MathHelper.clamp(aimValue, 0f, 1f);
                root[0] += shootSwayX * k * hip;
                root[1] += 0.1f * k * hip;
                root[4] += shootSwayYaw * k;
            }

            boolean onGround = player.isOnGround();
            float vy = (float) (player.getY() - player.prevY);
            if (onGround != lastOnGround) {
                jumpProgress = MathHelper.clamp(onGround ? vy / -0.1f : vy / 0.42f, 0f, 1f);
                lastOnGround = onGround;
            } else {
                jumpProgress = Math.max(0f, jumpProgress - dt / (onGround ? 0.15f : 0.3f));
            }
            root[1] += JUMP.update(-2f * jumpProgress);
        }

        // камера
        float mul = 1f - aimValue + aimValue / (float) Math.sqrt(Math.max(1.0, ClientController.getZoom(1f)));
        CAMERA[0] = pose.camera[3] * mul;
        CAMERA[1] = pose.camera[4] * mul;
        CAMERA[2] = pose.camera[5] * mul;
    }

    // ---------------- рисование ----------------

    public static void render(ClientPlayerEntity player, float tickDelta, ItemStack stack, MatrixStack matrices,
                              VertexConsumerProvider vcp, int light) {
        GeoAssets.Entry e = entry(stack);
        update(player, stack, e, tickDelta);
        GeoModel model = e.model();
        GunItem gun = (GunItem) stack.getItem();
        Attachments.Mods mods = Attachments.compute(stack, gun.stats);

        matrices.push();
        // убираем ванильное запаздывание руки — оно уже заложено в корень модели
        float h = MathHelper.lerp(tickDelta, player.lastRenderPitch, player.renderPitch);
        float i = MathHelper.lerp(tickDelta, player.lastRenderYaw, player.renderYaw);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-(player.getYaw(tickDelta) - i) * 0.1f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-(player.getPitch(tickDelta) - h) * 0.1f));
        // тряска камеры от анимации действует и на оружие
        matrices.multiply(GeoModel.rotation(CAMERA[0], CAMERA[1], CAMERA[2]));

        matrices.scale(1f / 16f, 1f / 16f, 1f / 16f);
        Vector3f idle = model.locator("idle_view");
        Vector3f aimAt = aimPoint(model, mods);
        float a = aimValue;
        if (idle != null && aimAt != null) {
            matrices.translate(-(idle.x + (aimAt.x - idle.x) * a), -(idle.y + (aimAt.y - idle.y) * a),
                    -(idle.z + (aimAt.z - idle.z) * a));
        }

        Set<String> hidden = GeoGunItemRenderer.hiddenBones(stack);
        GeoRenderer.render(model, pose, matrices, vcp, light, GeoRenderer.defaultOverlay(), hidden,
                (bone, m) -> {
                    switch (bone.name) {
                        case "shell_pos" -> Shells.capturePort(new org.joml.Matrix4f(m.peek().getPositionMatrix()));
                        case "muzzle_pos" -> {
                            com.battlegrounds.client.fx.ShotFx.captureMuzzle(new org.joml.Matrix4f(m.peek().getPositionMatrix()));
                            GeoGunItemRenderer.attachmentAt(stack, bone.name, m, vcp, light);
                        }
                        case "righthand" -> arm(player, true, m, vcp, light);
                        case "lefthand" -> arm(player, false, m, vcp, light);
                        default -> GeoGunItemRenderer.attachmentAt(stack, bone.name, m, vcp, light);
                    }
                });
        matrices.pop();
        // дульная вспышка: без насадки — на срезе штатного пламегасителя, с насадкой — дальше
        com.battlegrounds.client.fx.ShotFx.renderFirstPersonFlash(vcp, hidden.contains("muzzle_default") ? 4.5f : 2.7f);
        // гильзы летят в пространстве камеры, отдельно от оружия
        Shells.renderFirstPerson(matrices, vcp, light);
    }

    /** Точка глаза при прицеливании: мушка/целик или установленный прицел. */
    @Nullable
    private static Vector3f aimPoint(GeoModel model, Attachments.Mods mods) {
        Vector3f iron = model.locator("iron_view");
        if (mods.scope() == null) return iron;
        Vector3f s = model.locator("scope_pos");
        if (s == null || iron == null) return iron;
        return new Vector3f(s.x, s.y + mods.scope().sightHeight(), iron.z);
    }

    /** Рука игрока: кисть в точке кости, рука тянется вдоль +Y кости. */
    private static void arm(AbstractClientPlayerEntity player, boolean right, MatrixStack m, VertexConsumerProvider vcp, int light) {
        EntityRenderer<? super AbstractClientPlayerEntity> r = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (!(r instanceof PlayerEntityRenderer pr)) return;
        m.scale(16f, 16f, 16f);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f));
        if (right) {
            m.translate(6f / 16f, -11f / 16f, 0f);
            pr.renderRightArm(m, vcp, light, player);
        } else {
            m.translate(-6f / 16f, -11f / 16f, 0f);
            pr.renderLeftArm(m, vcp, light, player);
        }
    }

    private static float smooth(float x) {
        return x * x * (3f - 2f * x);
    }

    private FirstPersonGun() {
    }
}
