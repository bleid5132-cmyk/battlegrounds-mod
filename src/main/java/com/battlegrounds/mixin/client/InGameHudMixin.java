package com.battlegrounds.mixin.client;

import com.battlegrounds.client.ClientController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    /** В прицеле обычное перекрестие не рисуется — только сетка прицела. */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void bg$hideCrosshair(DrawContext context, CallbackInfo ci) {
        if (ClientController.getAimProgress(1f) > 0.5f) {
            ci.cancel();
        }
    }
}
