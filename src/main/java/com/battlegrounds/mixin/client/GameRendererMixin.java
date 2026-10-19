package com.battlegrounds.mixin.client;

import com.battlegrounds.client.ClientController;
import com.battlegrounds.client.geo.FirstPersonGun;
import com.battlegrounds.client.geo.GeoModel;
import com.battlegrounds.player.BgPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /** Приближение мира при прицеливании; поле зрения для рук сужается до 48°, как в эталоне. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void bg$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (changingFov) {
            double zoom = ClientController.getZoom(tickDelta);
            if (zoom != 1.0) {
                cir.setReturnValue(cir.getReturnValue() / zoom);
            }
        } else if (FirstPersonGun.active()) {
            float a = MathHelper.clamp(FirstPersonGun.aim(), 0f, 1f);
            cir.setReturnValue(MathHelper.lerp(a, cir.getReturnValue(), 48.0));
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void bg$handStart(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        FirstPersonGun.setRenderingHand(true);
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void bg$handEnd(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        FirstPersonGun.setRenderingHand(false);
    }

    /** Ванильное покачивание рук отключаем: у оружия свои анимации ходьбы. */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void bg$noBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (FirstPersonGun.isRenderingHand() && FirstPersonGun.active()) {
            ci.cancel();
        }
    }

    /** Наклон головы и тряска камеры от анимаций оружия. */
    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;getPitch()F", ordinal = 0))
    private void bg$roll(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!mc.options.getPerspective().isFirstPerson()) return;
        if (!(mc.getCameraEntity() instanceof PlayerEntity player)) return;
        float lean = BgPlayer.of(player).bg$getLeanAnim(tickDelta);
        if (Math.abs(lean) > 0.001f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(lean * 9f));
        }
        if (FirstPersonGun.active()) {
            float[] c = FirstPersonGun.cameraRotation();
            if (c[0] != 0 || c[1] != 0 || c[2] != 0) {
                matrices.multiply(GeoModel.rotation(c[0], c[1], c[2]));
            }
        }
    }
}
