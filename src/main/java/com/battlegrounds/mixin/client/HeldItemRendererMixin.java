package com.battlegrounds.mixin.client;

import com.battlegrounds.client.ClientController;
import com.battlegrounds.item.GunItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Оружие уходит в центр экрана при прицеливании и дёргается при выстреле. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    private static final float AIM_Y = 0.25f;
    private static final float AIM_Z = 0.30f;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void bg$pre(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                        ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                        int light, CallbackInfo ci) {
        matrices.push();
        if (hand == Hand.MAIN_HAND && item.getItem() instanceof GunItem) {
            Arm arm = player.getMainArm();
            float sign = arm == Arm.RIGHT ? -1f : 1f;
            float a = ClientController.getAimProgress(tickDelta);
            float kick = ClientController.getKick(tickDelta);
            matrices.translate(sign * 0.56f * a, AIM_Y * a, AIM_Z * a + kick * 0.06f);
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void bg$post(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                         ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                         int light, CallbackInfo ci) {
        matrices.pop();
    }
}
