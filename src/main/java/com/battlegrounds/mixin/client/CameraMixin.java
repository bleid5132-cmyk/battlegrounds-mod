package com.battlegrounds.mixin.client;

import com.battlegrounds.player.BgPlayer;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Камера «через плечо» как в PUBG и смещение камеры при наклоне. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float yaw;

    @Shadow
    public abstract Vec3d getPos();

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @ModifyConstant(method = "update", constant = @Constant(doubleValue = 4.0))
    private double bg$distance(double original) {
        return 2.6;
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void bg$shoulder(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        double side = 0;
        double down = 0;
        if (thirdPerson && !inverseView) side += 0.7;
        if (focused instanceof PlayerEntity player) {
            float lean = BgPlayer.of(player).bg$getLeanAnim(tickDelta);
            side += lean * (thirdPerson ? 0.6 : 0.45);
            down += Math.abs(lean) * 0.1;
        }
        if (Math.abs(side) < 1.0E-3 && down < 1.0E-3) return;

        float yr = this.yaw * MathHelper.RADIANS_PER_DEGREE;
        Vec3d right = new Vec3d(-MathHelper.cos(yr), 0, -MathHelper.sin(yr));
        Vec3d from = getPos();
        Vec3d to = from.add(right.multiply(side)).add(0, -down, 0);
        BlockHitResult hit = area.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, focused));
        if (hit.getType() != HitResult.Type.MISS) {
            to = from.add(hit.getPos().subtract(from).multiply(0.75));
        }
        setPos(to.x, to.y, to.z);
    }
}
