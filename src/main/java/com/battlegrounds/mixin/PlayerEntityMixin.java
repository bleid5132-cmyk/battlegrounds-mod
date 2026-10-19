package com.battlegrounds.mixin;

import com.battlegrounds.player.BgPlayer;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements BgPlayer {
    @Unique
    private static final TrackedData<Byte> BG_STANCE = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BYTE);
    @Unique
    private static final TrackedData<Byte> BG_LEAN = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BYTE);
    @Unique
    private static final TrackedData<Boolean> BG_MANTLE = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Unique
    private float bg$leanAnim;
    @Unique
    private float bg$prevLeanAnim;
    @Unique
    private int bg$mantleTimeout;

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void bg$initData(CallbackInfo ci) {
        this.dataTracker.startTracking(BG_STANCE, (byte) 0);
        this.dataTracker.startTracking(BG_LEAN, (byte) 0);
        this.dataTracker.startTracking(BG_MANTLE, false);
    }

    @Override
    public int bg$getStance() {
        return this.dataTracker.get(BG_STANCE);
    }

    @Override
    public void bg$setStance(int stance) {
        this.dataTracker.set(BG_STANCE, (byte) stance);
    }

    @Override
    public int bg$getLean() {
        return this.dataTracker.get(BG_LEAN);
    }

    @Override
    public void bg$setLean(int lean) {
        this.dataTracker.set(BG_LEAN, (byte) MathHelper.clamp(lean, -1, 1));
    }

    @Override
    public boolean bg$isMantling() {
        return this.dataTracker.get(BG_MANTLE);
    }

    @Override
    public void bg$setMantling(boolean mantling) {
        this.dataTracker.set(BG_MANTLE, mantling);
        this.bg$mantleTimeout = mantling ? 40 : 0;
        if (mantling) {
            this.setPose(EntityPose.SWIMMING);
        }
    }

    @Override
    public float bg$getLeanAnim(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.bg$prevLeanAnim, this.bg$leanAnim);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void bg$tick(CallbackInfo ci) {
        // плавный наклон для отрисовки
        this.bg$prevLeanAnim = this.bg$leanAnim;
        float target = bg$getLean();
        this.bg$leanAnim += (target - this.bg$leanAnim) * 0.4f;

        // защита: перелезание не может длиться вечно
        if (!this.getWorld().isClient && bg$isMantling()) {
            if (--this.bg$mantleTimeout <= 0) {
                this.dataTracker.set(BG_MANTLE, false);
            }
        }
    }

    @Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
    private void bg$updatePose(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.isSpectator() || self.hasVehicle() || self.isSleeping() || self.isFallFlying()
                || self.getAbilities().flying || self.isTouchingWater()) {
            return;
        }
        if (bg$isMantling()) {
            this.setPose(EntityPose.SWIMMING);
            ci.cancel();
            return;
        }
        int stance = bg$getStance();
        if (stance == PRONE) {
            this.setPose(EntityPose.SWIMMING);
            ci.cancel();
        } else if (stance == CROUCH) {
            this.setPose(bg$fits(EntityPose.CROUCHING) ? EntityPose.CROUCHING : EntityPose.SWIMMING);
            ci.cancel();
        }
    }

    @Unique
    private boolean bg$fits(EntityPose pose) {
        return this.getWorld().isSpaceEmpty(this, this.getDimensions(pose).getBoxAt(this.getPos()).contract(1.0E-7));
    }
}
