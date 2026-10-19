package com.battlegrounds.mixin;

import com.battlegrounds.player.BgPlayer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /** Во время перелезания тело рисуется вертикально, хотя хитбокс маленький. */
    @Inject(method = "getLeaningPitch", at = @At("HEAD"), cancellable = true)
    private void bg$noLeanWhileMantling(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof PlayerEntity player && BgPlayer.of(player).bg$isMantling()) {
            cir.setReturnValue(0f);
        }
    }
}
