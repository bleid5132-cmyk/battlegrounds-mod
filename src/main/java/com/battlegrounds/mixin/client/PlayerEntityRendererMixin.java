package com.battlegrounds.mixin.client;

import com.battlegrounds.item.GunItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    /** Запасной вариант, когда наш аниматор выключен (в воде, верхом и т.п.): оружие двумя руками. */
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void bg$armPose(AbstractClientPlayerEntity player, Hand hand, CallbackInfoReturnable<BipedEntityModel.ArmPose> cir) {
        if (player.getMainHandStack().getItem() instanceof GunItem) {
            cir.setReturnValue(hand == Hand.MAIN_HAND ? BipedEntityModel.ArmPose.CROSSBOW_HOLD : BipedEntityModel.ArmPose.EMPTY);
        }
    }
}
