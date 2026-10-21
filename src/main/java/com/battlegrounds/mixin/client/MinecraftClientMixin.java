package com.battlegrounds.mixin.client;

import com.battlegrounds.client.ClientController;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** С оружием в руках: ЛКМ стреляет, ПКМ целится, Q/E — наклоны вместо выброса и инвентаря. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void bg$drainKeys(CallbackInfo ci) {
        MinecraftClient self = (MinecraftClient) (Object) this;
        if (ClientController.holdingGun(self.player)) {
            while (self.options.dropKey.wasPressed()) {
            }
            while (self.options.inventoryKey.wasPressed()) {
            }
            while (self.options.swapHandsKey.wasPressed()) {
            }
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void bg$noAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient self = (MinecraftClient) (Object) this;
        if (ClientController.holdingGun(self.player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void bg$noUse(CallbackInfo ci) {
        MinecraftClient self = (MinecraftClient) (Object) this;
        if (ClientController.holdingGun(self.player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void bg$noBreak(boolean breaking, CallbackInfo ci) {
        MinecraftClient self = (MinecraftClient) (Object) this;
        if (ClientController.holdingGun(self.player)) {
            if (self.interactionManager != null) self.interactionManager.cancelBlockBreaking();
            ci.cancel();
        }
    }
}
