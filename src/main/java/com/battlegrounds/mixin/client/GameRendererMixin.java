package com.battlegrounds.mixin.client;

import com.battlegrounds.client.ClientController;
import com.battlegrounds.player.BgPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /** Приближение при прицеливании. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void bg$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (!changingFov) return;
        double zoom = ClientController.getZoom(tickDelta);
        if (zoom != 1.0) {
            cir.setReturnValue(cir.getReturnValue() / zoom);
        }
    }

    /** Наклон головы при наклоне влево/вправо (вид от первого лица). */
    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;getPitch()F", ordinal = 0))
    private void bg$roll(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!mc.options.getPerspective().isFirstPerson()) return;
        if (!(mc.getCameraEntity() instanceof PlayerEntity player)) return;
        float lean = BgPlayer.of(player).bg$getLeanAnim(tickDelta);
        if (Math.abs(lean) > 0.001f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(lean * 9f));
        }
    }
}
