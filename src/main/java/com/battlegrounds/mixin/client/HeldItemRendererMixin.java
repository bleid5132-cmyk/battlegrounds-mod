package com.battlegrounds.mixin.client;

import com.battlegrounds.client.ClientController;
import com.battlegrounds.client.geo.FirstPersonGun;
import com.battlegrounds.client.render.GunMounts;
import com.battlegrounds.item.GunItem;
import com.battlegrounds.player.BgPlayer;
import com.battlegrounds.weapon.AttachmentType;
import com.battlegrounds.weapon.Attachments;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Свои анимации оружия от первого лица: покачивание при ходьбе, дыхание,
 * опущенное оружие при беге, наклон при перезарядке, подъём при доставании,
 * отдача и вывод прицела точно в центр экрана.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void bg$pre(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                        ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                        int light, CallbackInfo ci) {
        if (hand != Hand.MAIN_HAND || !(item.getItem() instanceof GunItem gun)) {
            matrices.push();
            return;
        }
        // оружие с костной моделью: свой вид от первого лица по точкам камеры
        if (FirstPersonGun.handles(item) && player instanceof net.minecraft.client.network.ClientPlayerEntity cp) {
            FirstPersonGun.render(cp, tickDelta, item, matrices, vertexConsumers, light);
            ci.cancel();
            return;
        }
        Attachments.Mods mods = Attachments.compute(item, gun.stats);
        float aim = ClientController.getAimProgress(tickDelta);
        // в оптике с приближением оружие не рисуем — виден только прицел
        if (mods.reticle() == AttachmentType.Reticle.SCOPE && aim > 0.92f) {
            ci.cancel();
            return;
        }
        matrices.push();

        Arm arm = player.getMainArm();
        float side = arm == Arm.RIGHT ? 1f : -1f;
        GunMounts.Mount mount = GunMounts.get(gun.stats.id());
        float sightY = mods.scope() != null ? mount.railY() + mods.scope().sightHeight() : mount.ironY();
        float aimY = 0.52f - 0.09375f - (sightY - 8f) / 16f * 0.75f;

        float kick = ClientController.getKick(tickDelta);
        float sprint = ClientController.getSprint(tickDelta);
        float equip = ClientController.getEquip(tickDelta);
        float free = 1f - aim * 0.85f;

        // ходьба и дыхание
        float limbPos = player.limbAnimator.getPos(tickDelta);
        float limbSpeed = MathHelper.clamp(player.limbAnimator.getSpeed(tickDelta) * 1.3f, 0f, 1f);
        float ph = limbPos * 0.6662f;
        float t = player.age + tickDelta;
        float bobX = MathHelper.sin(ph) * 0.014f * limbSpeed * free;
        float bobY = -Math.abs(MathHelper.cos(ph)) * 0.02f * limbSpeed * free + MathHelper.sin(t * 0.08f) * 0.004f * free;

        // вывод в прицел
        matrices.translate(-side * 0.56f * aim + bobX * side, aimY * aim + bobY, 0.30f * aim + kick * 0.07f);

        // лёгкое покачивание в прицеле
        if (aim > 0f) {
            float sway = aim * 0.25f;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.sin(t * 0.05f) * sway));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.sin(t * 0.1f) * sway * 0.5f));
        }

        // отдача — ствол подбрасывает
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(kick * 2.5f * (1f - aim * 0.5f)));

        // бег: оружие опущено и развёрнуто
        if (sprint > 0.001f) {
            matrices.translate(-0.06f * sprint * side, -0.07f * sprint, 0.06f * sprint);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(38f * sprint * side));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-22f * sprint));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-12f * sprint * side));
            float runBob = MathHelper.sin(ph) * 0.03f * sprint * limbSpeed;
            matrices.translate(runBob, -Math.abs(runBob), 0f);
        }

        // перезарядка: оружие наклоняется и уходит вниз
        if (GunItem.isReloading(item) && player.getWorld() != null) {
            long start = GunItem.getReloadStart(item);
            long end = GunItem.getReloadEnd(item);
            float r = end > start ? MathHelper.clamp((player.getWorld().getTime() + tickDelta - start) / (float) (end - start), 0f, 1f) : 1f;
            float tilt = MathHelper.sin(MathHelper.clamp(r * 1.12f, 0f, 1f) * (float) Math.PI);
            float jolt = r > 0.55f && r < 0.65f ? MathHelper.sin((r - 0.55f) / 0.1f * (float) Math.PI) : 0f;
            matrices.translate(0f, -0.09f * tilt + 0.02f * jolt, 0.02f * tilt);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(32f * tilt * side));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(12f * tilt + 4f * jolt));
        }

        // доставание: оружие поднимается снизу
        if (equip < 1f) {
            float e = 1f - equip;
            e = e * e;
            matrices.translate(0f, -0.35f * e, 0.1f * e);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-55f * e));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(20f * e * side));
        }

        // перелезание: оружие уходит вниз
        if (BgPlayer.of(player).bg$isMantling()) {
            matrices.translate(0f, -0.45f, 0.1f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-40f));
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void bg$post(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                         ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                         int light, CallbackInfo ci) {
        matrices.pop();
    }
}
