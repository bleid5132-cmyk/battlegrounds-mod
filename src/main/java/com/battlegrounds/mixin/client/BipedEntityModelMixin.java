package com.battlegrounds.mixin.client;

import com.battlegrounds.player.BgPlayer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Руки тянутся к уступу во время перелезания. */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
    @Shadow
    @Final
    public ModelPart rightArm;
    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void bg$mantleArms(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                               float headYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player && BgPlayer.of(player).bg$isMantling()) {
            float wave = (float) Math.sin(animationProgress * 0.9f) * 0.25f;
            rightArm.pitch = -2.7f + wave;
            leftArm.pitch = -2.7f - wave;
            rightArm.yaw = -0.15f;
            leftArm.yaw = 0.15f;
            rightArm.roll = 0f;
            leftArm.roll = 0f;
        }
    }
}
