package com.battlegrounds.mixin.client;

import com.battlegrounds.client.ClientController;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** В прицеле с приближением мышь двигается медленнее, как в PUBG. */
@Mixin(Mouse.class)
public abstract class MouseMixin {

    @ModifyArg(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"), index = 0)
    private double bg$scaleX(double dx) {
        return dx / ClientController.getZoom(1f);
    }

    @ModifyArg(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"), index = 1)
    private double bg$scaleY(double dy) {
        return dy / ClientController.getZoom(1f);
    }
}
