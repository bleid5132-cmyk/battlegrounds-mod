package com.battlegrounds.client.anim;

import com.battlegrounds.item.GunItem;
import com.battlegrounds.player.BgPlayer;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Свои процедурные анимации всего тела вместо стандартных анимаций Minecraft.
 * Руки сгибаются в локтях, ноги в коленях, спина — вперёд и вбок.
 */
public class BodyAnimator implements IAnimation {
    /** Знаки изгиба. Если сустав гнётся «не в ту сторону» — поменять знак здесь. */
    public static final float KNEE_SIGN = 1f;
    public static final float ELBOW_SIGN = -1f;

    private static final float PI = (float) Math.PI;
    private static final float HALF_PI = PI / 2f;

    private final AbstractClientPlayerEntity player;

    // плавные веса состояний: [0] — прошлый тик, [1] — текущий
    private final float[] crouch = new float[2];
    private final float[] prone = new float[2];
    private final float[] air = new float[2];
    private final float[] sprint = new float[2];
    private final float[] gun = new float[2];
    private final float[] aim = new float[2];
    private final float[] mantle = new float[2];
    private final float[] kick = new float[2];

    private int mantleTicks;
    private int lastAmmo = -1;
    private int age;

    private final BodyPose pose = new BodyPose();
    private final BodyPose tmp = new BodyPose();

    public BodyAnimator(AbstractClientPlayerEntity player) {
        this.player = player;
    }

    @Override
    public boolean isActive() {
        AbstractClientPlayerEntity p = player;
        if (p.isSpectator() || p.isSleeping() || p.hasVehicle() || p.isFallFlying() || p.isUsingRiptide()) return false;
        if (p.isSwimming() || (p.isTouchingWater() && !p.isOnGround())) return false;
        if (p.isClimbing() && !p.isOnGround()) return false;
        boolean holdingGun = p.getMainHandStack().getItem() instanceof GunItem;
        return !(p.isUsingItem() && !holdingGun);
    }

    private static void approach(float[] w, float target, float rate) {
        w[0] = w[1];
        w[1] += (target - w[1]) * rate;
    }

    private static float at(float[] w, float td) {
        return MathHelper.lerp(td, w[0], w[1]);
    }

    @Override
    public void tick() {
        AbstractClientPlayerEntity p = player;
        BgPlayer bg = BgPlayer.of(p);
        age++;
        boolean crawling = p.getPose() == EntityPose.SWIMMING && !p.isTouchingWater() && !bg.bg$isMantling();
        boolean crouching = !crawling && (p.isInSneakingPose() || bg.bg$getStance() == BgPlayer.CROUCH);
        ItemStack stack = p.getMainHandStack();
        boolean holdingGun = stack.getItem() instanceof GunItem;

        approach(prone, crawling ? 1f : 0f, 0.3f);
        approach(crouch, crouching ? 1f : 0f, 0.35f);
        approach(air, !p.isOnGround() && !crawling ? 1f : 0f, 0.3f);
        approach(sprint, p.isSprinting() ? 1f : 0f, 0.25f);
        approach(gun, holdingGun ? 1f : 0f, 0.4f);
        approach(aim, holdingGun && bg.bg$isAiming() ? 1f : 0f, 0.35f);

        if (bg.bg$isMantling()) mantleTicks++;
        else mantleTicks = 0;
        approach(mantle, bg.bg$isMantling() ? 1f : 0f, 0.5f);

        // отдача: заметна по уменьшению патронов (работает и для чужих игроков)
        kick[0] = kick[1];
        kick[1] *= 0.45f;
        if (holdingGun) {
            int ammo = GunItem.getAmmo(stack);
            if (lastAmmo >= 0 && ammo < lastAmmo) kick[1] = 1f;
            lastAmmo = ammo;
        } else {
            lastAmmo = -1;
        }
    }

    @Override
    public void setupAnim(float td) {
        AbstractClientPlayerEntity p = player;
        BodyPose ps = pose;

        float limbPos = p.limbAnimator.getPos(td);
        float limbSpeed = MathHelper.clamp(p.limbAnimator.getSpeed(td) * 1.25f, 0f, 1f);
        float ph = limbPos * 0.6662f;
        float t = age + td;

        float wCrouch = at(crouch, td);
        float wProne = at(prone, td);
        float wAir = at(air, td);
        float wSprint = at(sprint, td);
        float wGun = at(gun, td);
        float wAim = at(aim, td);
        float wMantle = at(mantle, td);
        float wKick = at(kick, td);

        float headPitch = p.getPitch(td) * MathHelper.RADIANS_PER_DEGREE;
        float headYaw = MathHelper.wrapDegrees(MathHelper.lerpAngleDegrees(td, p.prevHeadYaw, p.headYaw)
                - MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw)) * MathHelper.RADIANS_PER_DEGREE;
        float lean = BgPlayer.of(p).bg$getLeanAnim(td);

        // ================= стоя / шаг / бег =================
        float a = limbSpeed;
        float swing = (0.7f + 0.4f * wSprint) * a;
        float cos = MathHelper.cos(ph);
        float sin = MathHelper.sin(ph);
        float breathe = MathHelper.sin(t * 0.08f);

        ps.rLegX = cos * swing;
        ps.lLegX = -cos * swing;
        ps.rLegY = 0; ps.lLegY = 0;
        ps.rLegZ = 0.02f; ps.lLegZ = -0.02f;
        float kneeAmp = (0.9f + 0.8f * wSprint) * a;
        ps.rKnee = 0.06f + kneeAmp * Math.max(0f, sin);
        ps.lKnee = 0.06f + kneeAmp * Math.max(0f, -sin);

        // руки без оружия: противоход ногам, локти слегка согнуты
        float armSwing = (0.55f + 0.5f * wSprint) * a;
        ps.rArmX = -cos * armSwing;
        ps.lArmX = cos * armSwing;
        ps.rArmY = 0; ps.lArmY = 0;
        ps.rArmZ = 0.07f + 0.02f * breathe;
        ps.lArmZ = -0.07f - 0.02f * breathe;
        ps.rElbow = 0.18f + 0.25f * a + 1.0f * wSprint + 0.3f * a * Math.max(0f, -cos);
        ps.lElbow = 0.18f + 0.25f * a + 1.0f * wSprint + 0.3f * a * Math.max(0f, cos);

        ps.spineForward = 0.02f + 0.05f * a + 0.22f * wSprint + 0.01f * breathe;
        ps.spineSide = 0f;
        ps.bodyY = -0.035f * Math.abs(sin) * a * (1f + wSprint);
        ps.itemRoll = 0f;
        ps.itemPitch = 0f;

        // удар рукой / копание
        float swingProg = p.getHandSwingProgress(td);
        if (swingProg > 0f && wGun < 0.5f) {
            float s = MathHelper.sin(MathHelper.sqrt(swingProg) * PI);
            ps.rArmX -= s * 1.3f;
            ps.rArmY += MathHelper.sin(swingProg * PI) * -0.4f;
            ps.rElbow += s * 0.6f;
            ps.spineForward += s * 0.1f;
        }

        // ================= присед =================
        if (wCrouch > 0f) {
            tmp.set(ps);
            tmp.rLegX = ps.rLegX * 0.6f - 0.75f;
            tmp.lLegX = ps.lLegX * 0.6f - 0.75f;
            tmp.rKnee = ps.rKnee * 0.6f + 1.25f;
            tmp.lKnee = ps.lKnee * 0.6f + 1.25f;
            tmp.spineForward = 0.5f + 0.05f * a;
            tmp.rArmX = ps.rArmX * 0.5f - 0.25f;
            tmp.lArmX = ps.lArmX * 0.5f - 0.25f;
            tmp.rElbow = ps.rElbow + 0.3f;
            tmp.lElbow = ps.lElbow + 0.3f;
            tmp.bodyY = -0.02f + ps.bodyY * 0.5f;
            ps.blend(tmp, wCrouch);
        }

        // ================= в воздухе =================
        if (wAir > 0f) {
            tmp.set(ps);
            tmp.rLegX = -0.65f; tmp.rKnee = 1.05f;
            tmp.lLegX = 0.25f; tmp.lKnee = 0.55f;
            tmp.rArmX = -0.45f; tmp.lArmX = -0.3f;
            tmp.rArmZ = 0.35f; tmp.lArmZ = -0.35f;
            tmp.rElbow = 0.5f; tmp.lElbow = 0.4f;
            tmp.spineForward = 0.12f;
            tmp.bodyY = 0f;
            ps.blend(tmp, wAir * 0.85f);
        }

        // ================= с оружием в руках =================
        if (wGun > 0f) {
            ItemStack stack = p.getMainHandStack();
            tmp.set(ps);
            float forward = -HALF_PI + headPitch;          // предплечье смотрит туда же, куда голова
            // от бедра
            float rUpper = -0.6f + headPitch * 0.6f;
            float lUpper = -1.15f + headPitch * 0.85f;
            tmp.rArmX = rUpper;
            tmp.rArmY = -0.18f + headYaw;
            tmp.rArmZ = 0f;
            tmp.rElbow = rUpper - forward;                 // локоть доворачивает ствол вперёд
            tmp.lArmX = lUpper;
            tmp.lArmY = 0.55f + headYaw;
            tmp.lArmZ = 0f;
            tmp.lElbow = 0.4f;
            tmp.spineForward = Math.max(ps.spineForward, 0.08f);

            // прицеливание: оружие поднято к глазам
            if (wAim > 0f) {
                float rUp2 = -1.05f + headPitch * 0.8f;
                float lUp2 = -1.4f + headPitch * 0.95f;
                BodyPose aimPose = new BodyPose().set(tmp);
                aimPose.rArmX = rUp2;
                aimPose.rArmY = -0.05f + headYaw;
                aimPose.rElbow = rUp2 - forward;
                aimPose.lArmX = lUp2;
                aimPose.lArmY = 0.68f + headYaw;
                aimPose.lElbow = 0.5f;
                tmp.blendArms(aimPose, wAim);
            }

            // бег: оружие опущено поперёк груди
            float lowReady = wSprint * (1f - wAim);
            if (lowReady > 0f) {
                BodyPose run = new BodyPose().set(tmp);
                run.rArmX = -0.35f - cos * 0.15f * a;
                run.rArmY = -0.35f;
                run.rElbow = 0.75f;
                run.lArmX = -0.85f + cos * 0.15f * a;
                run.lArmY = 0.7f;
                run.lElbow = 0.9f;
                run.itemRoll = 0.6f;
                tmp.blendArms(run, lowReady);
            }

            // перезарядка: левая рука к магазину, оружие наклонено
            if (GunItem.isReloading(stack) && p.getWorld() != null) {
                long start = GunItem.getReloadStart(stack);
                long end = GunItem.getReloadEnd(stack);
                float r = end > start ? MathHelper.clamp((p.getWorld().getTime() + td - start) / (float) (end - start), 0f, 1f) : 1f;
                float tilt = MathHelper.sin(MathHelper.clamp(r * 1.15f, 0f, 1f) * PI);
                BodyPose rel = new BodyPose().set(tmp);
                float reach;                               // 0 — у цевья, 1 — у магазина, 2 — у затвора
                if (r < 0.15f) reach = r / 0.15f;
                else if (r < 0.45f) reach = 1f + MathHelper.sin(r * 40f) * 0.05f;
                else if (r < 0.6f) reach = 1f;
                else if (r < 0.8f) reach = 1f + (r - 0.6f) / 0.2f;
                else reach = 2f - (r - 0.8f) / 0.2f * 2f;
                float toMag = Math.min(reach, 1f);
                float toBolt = Math.max(0f, reach - 1f);
                rel.lArmX = MathHelper.lerp(toMag, tmp.lArmX, -0.55f + headPitch * 0.5f);
                rel.lArmY = MathHelper.lerp(toMag, tmp.lArmY, 0.35f);
                rel.lElbow = MathHelper.lerp(toMag, tmp.lElbow, 1.35f);
                rel.lArmX = MathHelper.lerp(toBolt, rel.lArmX, -1.0f);
                rel.lArmY = MathHelper.lerp(toBolt, rel.lArmY, 0.15f);
                rel.lElbow = MathHelper.lerp(toBolt, rel.lElbow, 1.6f);
                rel.itemRoll = 0.55f * tilt;
                rel.rArmX = tmp.rArmX + 0.25f * tilt;
                tmp.blendArms(rel, 1f - wAim * 0.5f);
            }

            // отдача
            tmp.rArmX -= 0.12f * wKick;
            tmp.lArmX -= 0.1f * wKick;
            tmp.spineForward -= 0.04f * wKick;

            ps.blendArms(tmp, wGun);
            ps.spineForward = MathHelper.lerp(wGun, ps.spineForward, tmp.spineForward);
        }

        // ================= лёжа =================
        if (wProne > 0f) {
            tmp.set(ps);
            float c = a;
            float s1 = Math.max(0f, sin), s2 = Math.max(0f, -sin);
            tmp.rLegX = -0.55f * s1 * c;
            tmp.lLegX = -0.55f * s2 * c;
            tmp.rLegY = 0f; tmp.lLegY = 0f;
            tmp.rLegZ = 0.12f + 0.3f * s1 * c;
            tmp.lLegZ = -0.12f - 0.3f * s2 * c;
            tmp.rKnee = 0.15f + 1.2f * s1 * c;
            tmp.lKnee = 0.15f + 1.2f * s2 * c;
            tmp.spineForward = 0f;
            tmp.bodyY = 0f;
            if (wGun > 0.5f) {
                float forward = -PI + headPitch;
                tmp.rArmX = -2.55f + headPitch;
                tmp.rArmY = -0.2f + headYaw * 0.5f;
                tmp.rArmZ = 0f;
                tmp.rElbow = tmp.rArmX - forward;
                tmp.lArmX = -2.9f + headPitch;
                tmp.lArmY = 0.35f + headYaw * 0.5f;
                tmp.lArmZ = 0f;
                tmp.lElbow = 0.35f;
                tmp.itemRoll = 0f;
            } else {
                tmp.rArmX = -2.5f + 0.5f * cos * c;
                tmp.lArmX = -2.5f - 0.5f * cos * c;
                tmp.rArmY = 0f; tmp.lArmY = 0f;
                tmp.rArmZ = 0.2f; tmp.lArmZ = -0.2f;
                tmp.rElbow = 0.4f + 0.6f * s2 * c;
                tmp.lElbow = 0.4f + 0.6f * s1 * c;
            }
            ps.blend(tmp, wProne);
        }

        // ================= перелезание =================
        if (wMantle > 0f) {
            float m = MathHelper.clamp((mantleTicks + td) / 10f, 0f, 1f);
            float reachUp = m < 0.4f ? 1f : 1f - (m - 0.4f) / 0.6f;
            tmp.set(ps);
            tmp.rArmX = MathHelper.lerp(reachUp, -0.35f, -2.75f);
            tmp.lArmX = MathHelper.lerp(reachUp, -0.35f, -2.75f);
            tmp.rArmY = -0.1f; tmp.lArmY = 0.1f;
            tmp.rArmZ = 0.1f; tmp.lArmZ = -0.1f;
            tmp.rElbow = MathHelper.lerp(reachUp, 0.25f, 0.35f);
            tmp.lElbow = tmp.rElbow;
            tmp.rLegX = -1.35f; tmp.rKnee = 1.9f;
            tmp.lLegX = 0.35f; tmp.lKnee = 0.6f;
            tmp.spineForward = 0.5f;
            tmp.bodyY = 0f;
            ps.blend(tmp, wMantle);
        }

        // наклон влево/вправо — изгиб позвоночника вбок
        ps.spineSide = lean * 0.38f;
        ps.spineForward += Math.abs(lean) * 0.05f;
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        BodyPose ps = pose;
        switch (modelName) {
            case "head":
                if (type == TransformType.POSITION) return new Vec3f(0f, 0f, 0f);
                return value0;
            case "torso":
                if (type == TransformType.POSITION) return new Vec3f(0f, 0f, 0f);
                if (type == TransformType.ROTATION) return new Vec3f(0f, 0f, 0f);
                return new Vec3f(0f, 0f, 0f);
            case "body":
                if (type == TransformType.POSITION) return new Vec3f(0f, ps.bodyY, 0f);
                if (type == TransformType.ROTATION) return value0;
                return spineBend(ps.spineForward, ps.spineSide);
            case "rightArm":
                if (type == TransformType.POSITION) return new Vec3f(-5f, 2f, 0f);
                if (type == TransformType.ROTATION) return new Vec3f(ps.rArmX, ps.rArmY, ps.rArmZ);
                return new Vec3f(0f, ps.rElbow * ELBOW_SIGN, 0f);
            case "leftArm":
                if (type == TransformType.POSITION) return new Vec3f(5f, 2f, 0f);
                if (type == TransformType.ROTATION) return new Vec3f(ps.lArmX, ps.lArmY, ps.lArmZ);
                return new Vec3f(0f, ps.lElbow * ELBOW_SIGN, 0f);
            case "rightLeg":
                if (type == TransformType.POSITION) return new Vec3f(-1.9f, 12f, 0.1f);
                if (type == TransformType.ROTATION) return new Vec3f(ps.rLegX, ps.rLegY, ps.rLegZ);
                return new Vec3f(0f, ps.rKnee * KNEE_SIGN, 0f);
            case "leftLeg":
                if (type == TransformType.POSITION) return new Vec3f(1.9f, 12f, 0.1f);
                if (type == TransformType.ROTATION) return new Vec3f(ps.lLegX, ps.lLegY, ps.lLegZ);
                return new Vec3f(0f, ps.lKnee * KNEE_SIGN, 0f);
            case "rightItem":
                if (type == TransformType.ROTATION) return new Vec3f(ps.itemPitch, 0f, ps.itemRoll);
                return value0;
            default:
                return value0;
        }
    }

    private static Vec3f spineBend(float forward, float side) {
        float mag = MathHelper.sqrt(forward * forward + side * side);
        if (mag < 1.0E-4f) return new Vec3f(0f, 0f, 0f);
        float axis = (float) Math.atan2(side, forward);
        return new Vec3f(axis, mag, 0f);
    }
}
