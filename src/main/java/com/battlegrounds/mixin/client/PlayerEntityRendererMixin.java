package com.battlegrounds.mixin.client;

import com.battlegrounds.item.GunItem;
import com.battlegrounds.player.BgPlayer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    /** Оружие держится двумя руками перед собой. */
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void bg$armPose(AbstractClientPlayerEntity player, Hand hand, CallbackInfoReturnable<BipedEntityModel.ArmPose> cir) {
        if (player.getMainHandStack().getItem() instanceof GunItem && !BgPlayer.of(player).bg$isMantling()) {
            cir.setReturnValue(hand == Hand.MAIN_HAND ? BipedEntityModel.ArmPose.CROSSBOW_HOLD : BipedEntityModel.ArmPose.EMPTY);
        }
    }

    /** Наклон тела влево/вправо — видно другим игрокам. */
    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",
            at = @At("TAIL"))
    private void bg$lean(AbstractClientPlayerEntity player, MatrixStack matrices, float animationProgress, float bodyYaw,
                         float tickDelta, CallbackInfo ci) {
        float lean = BgPlayer.of(player).bg$getLeanAnim(tickDelta);
        if (Math.abs(lean) < 0.01f) return;
        float pivot = player.isInSneakingPose() ? 0.65f : 0.9f;
        matrices.translate(0f, pivot, 0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-lean * 20f));
        matrices.translate(0f, -pivot, 0f);
    }
}
