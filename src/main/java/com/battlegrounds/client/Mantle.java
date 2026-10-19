package com.battlegrounds.client;

import com.battlegrounds.player.BgPlayer;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Залезание на уступы до 2 блоков и перелезание через окна/заборы, как в PUBG.
 * Нажми прыжок, идя вперёд, вплотную к препятствию.
 */
public final class Mantle {
    private static Vec3d start, mid, end;
    private static int riseTicks, moveTicks, tick;
    private static boolean active;
    private static Vec3d exitVelocity = Vec3d.ZERO;

    public static boolean isActive() {
        return active;
    }

    private static boolean solid(World w, BlockPos pos) {
        BlockState st = w.getBlockState(pos);
        return !st.getCollisionShape(w, pos).isEmpty();
    }

    private static double top(World w, BlockPos pos) {
        VoxelShape shape = w.getBlockState(pos).getCollisionShape(w, pos);
        return pos.getY() + (shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y));
    }

    public static boolean tryStart(ClientPlayerEntity p) {
        if (p.isSpectator() || p.hasVehicle() || p.getAbilities().flying || p.isTouchingWater()
                || p.isClimbing() || p.isFallFlying() || !p.input.pressingForward) {
            return false;
        }
        World w = p.getWorld();
        Direction dir = Direction.fromRotation(p.getYaw());
        BlockPos feet = BlockPos.ofFloored(p.getX(), p.getY() + 0.01, p.getZ());
        BlockPos front = feet.offset(dir);

        // игрок должен стоять вплотную к препятствию
        double center = dir.getAxis() == Direction.Axis.X ? p.getX() : p.getZ();
        int frontCoord = dir.getAxis() == Direction.Axis.X ? front.getX() : front.getZ();
        double face = dir.getDirection() == Direction.AxisDirection.POSITIVE ? frontCoord : frontCoord + 1;
        if (Math.abs(face - center) - 0.3 > 0.55) return false;

        // сколько блоков препятствия снизу
        int k = 0;
        while (k < 3 && solid(w, front.up(k))) k++;
        if (k == 0 || k == 3) return false;

        double topY = top(w, front.up(k - 1));
        double rel = topY - p.getY();
        if (rel < 0.55 || rel > 2.1) return false;
        if (solid(w, front.up(k))) return false;          // нет проёма
        if (solid(w, feet.up(k)) && k >= 2) return false; // над головой потолок

        boolean free2 = !solid(w, front.up(k + 1));
        BlockPos beyond = front.offset(dir);
        boolean vault = !solid(w, beyond.up(k)) && !solid(w, beyond.up(k - 1));

        Vec3d s = p.getPos();
        double y = topY + 0.02;
        double targetAlong;
        if (vault) {
            targetAlong = (dir.getAxis() == Direction.Axis.X ? beyond.getX() : beyond.getZ()) + 0.5;
        } else if (free2) {
            targetAlong = frontCoord + 0.5;
        } else {
            return false;
        }

        Vec3d m = new Vec3d(s.x, y, s.z);
        Vec3d e = dir.getAxis() == Direction.Axis.X ? new Vec3d(targetAlong, y, s.z) : new Vec3d(s.x, y, targetAlong);

        // проверяем, что путь свободен в позе ползком
        EntityDimensions crawl = p.getDimensions(EntityPose.SWIMMING);
        for (int i = 0; i <= 4; i++) {
            Vec3d a = m.lerp(e, i / 4.0);
            if (!w.isSpaceEmpty(p, crawl.getBoxAt(a).contract(1.0E-3))) return false;
        }
        if (!w.isSpaceEmpty(p, crawl.getBoxAt(m).union(crawl.getBoxAt(s.add(0, 0.05, 0))).contract(1.0E-3))) return false;

        start = s;
        mid = m;
        end = e;
        riseTicks = Math.max(3, MathHelper.ceil(rel / 0.3));
        moveTicks = vault ? 6 : 4;
        tick = 0;
        active = true;
        exitVelocity = vault ? Vec3d.of(dir.getVector()).multiply(0.12) : Vec3d.ZERO;

        BgPlayer.of(p).bg$setMantling(true);
        p.setPose(EntityPose.SWIMMING);
        ClientNet.mantle(true);
        return true;
    }

    public static void tick(ClientPlayerEntity p) {
        if (!active) return;
        if (p.isRemoved() || p.isDead()) {
            stop(p);
            return;
        }
        tick++;
        Vec3d pos;
        if (tick <= riseTicks) {
            pos = start.lerp(mid, smooth((float) tick / riseTicks));
        } else {
            pos = mid.lerp(end, smooth((float) (tick - riseTicks) / moveTicks));
        }
        p.setPosition(pos);
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0;
        if (tick >= riseTicks + moveTicks) {
            p.setVelocity(exitVelocity);
            stop(p);
        }
    }

    private static void stop(ClientPlayerEntity p) {
        active = false;
        BgPlayer.of(p).bg$setMantling(false);
        ClientNet.mantle(false);
    }

    private static float smooth(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private Mantle() {
    }
}
