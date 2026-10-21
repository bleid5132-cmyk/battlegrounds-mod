package com.battlegrounds.client.fx;

import com.battlegrounds.client.geo.GeoAssets;
import com.battlegrounds.client.geo.GeoModel;
import com.battlegrounds.client.geo.GeoRenderer;
import com.battlegrounds.registry.ModSounds;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Гильзы, вылетающие из окна выброса.
 * От первого лица — в пространстве камеры (как в TACZ), для остальных — в мире с падением и отскоком.
 */
public final class Shells {
    private static final Random RND = new Random();

    // ---------------- от первого лица ----------------
    private static final class ViewShell {
        GeoModel model;
        final Vector3f pos = new Vector3f();      // блоки, пространство камеры
        final Vector3f vel = new Vector3f();      // блоки/с
        final Quaternionf rot = new Quaternionf();
        final Vector3f spin = new Vector3f();     // градусы/с вокруг осей гильзы
        float age;
    }

    private static final List<ViewShell> VIEW = new ArrayList<>();
    private static final Matrix4f PORT = new Matrix4f();
    private static boolean portValid;
    private static long lastViewNanos = -1;

    /** Запоминаем положение окна выброса в кадре (матрица в пикселях модели, пространство камеры). */
    public static void capturePort(Matrix4f m) {
        PORT.set(m);
        portValid = true;
    }

    public static void spawnFirstPerson(String gunId) {
        GeoAssets.Entry e = GeoAssets.get(gunId);
        if (e == null || e.model().shell == null || !portValid) return;
        GeoModel.ShellInfo si = e.model().shell;
        ViewShell s = new ViewShell();
        s.model = e.model();
        PORT.getTranslation(s.pos);
        // матрица порта уже включает масштаб 1/16: скорость в px/с переводится в блоки
        Vector3f v = new Vector3f(
                si.velocity.x + (RND.nextFloat() * 2 - 1) * si.random.x,
                si.velocity.y + (RND.nextFloat() * 2 - 1) * si.random.y,
                si.velocity.z + (RND.nextFloat() * 2 - 1) * si.random.z);
        PORT.transformDirection(v);
        s.vel.set(v);
        Matrix3f r = new Matrix3f();
        PORT.get3x3(r);
        r.getNormalizedRotation(s.rot);
        s.spin.set(si.spin).mul(0.8f + RND.nextFloat() * 0.4f);
        VIEW.add(s);
        if (VIEW.size() > 24) VIEW.remove(0);
    }

    /** Рисуем гильзы от первого лица. matrices — в пространстве камеры (блоки). */
    public static void renderFirstPerson(MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        long now = System.nanoTime();
        float dt = lastViewNanos < 0 ? 0f : Math.min(0.1f, (now - lastViewNanos) / 1.0e9f);
        lastViewNanos = now;
        for (Iterator<ViewShell> it = VIEW.iterator(); it.hasNext(); ) {
            ViewShell s = it.next();
            GeoModel.ShellInfo si = s.model.shell;
            s.age += dt;
            if (s.age > si.life) {
                it.remove();
                continue;
            }
            s.vel.y += si.gravity / 16f * dt;
            s.pos.add(s.vel.x * dt, s.vel.y * dt, s.vel.z * dt);
            s.rot.rotateXYZ((float) Math.toRadians(s.spin.x * dt), (float) Math.toRadians(s.spin.y * dt), (float) Math.toRadians(s.spin.z * dt));
            matrices.push();
            matrices.translate(s.pos.x, s.pos.y, s.pos.z);
            matrices.multiply(s.rot);
            matrices.scale(1f / 16f, 1f / 16f, 1f / 16f);
            draw(s.model, matrices, vcp, light);
            matrices.pop();
        }
    }

    public static void clearFirstPerson() {
        VIEW.clear();
        portValid = false;
    }

    // ---------------- в мире ----------------
    private static final class WorldShell {
        final GeoModel model;
        Vec3d pos, prev;
        Vec3d vel;
        final Quaternionf rot = new Quaternionf();
        final Vector3f spin = new Vector3f();
        int age;
        boolean landed;

        WorldShell(GeoModel model) {
            this.model = model;
        }
    }

    private static final List<WorldShell> WORLD = new ArrayList<>();

    /** Гильза у стреляющего игрока (вид от третьего лица и для других игроков). */
    public static void spawnWorld(PlayerEntity player, String gunId) {
        GeoAssets.Entry e = GeoAssets.get(gunId);
        GeoModel model = e != null ? e.model() : null;
        if (model == null || model.shell == null) return;
        float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d right = new Vec3d(-MathHelper.cos(yaw), 0, -MathHelper.sin(yaw));
        Vec3d fwd = player.getRotationVector();
        Vec3d pos = player.getEyePos().add(fwd.multiply(0.45)).add(right.multiply(0.3)).add(0, -0.3, 0);
        WorldShell s = new WorldShell(model);
        s.pos = pos;
        s.prev = pos;
        s.vel = right.multiply(1.8 + RND.nextDouble() * 0.6).add(0, 1.4 + RND.nextDouble() * 0.5, 0)
                .add(fwd.multiply(-0.3)).add(player.getVelocity().multiply(20));
        s.rot.rotationYXZ(-yaw, (float) Math.toRadians(-player.getPitch()), 0);
        s.spin.set(model.shell.spin).mul(0.8f + RND.nextFloat() * 0.4f);
        WORLD.add(s);
        if (WORLD.size() > 96) WORLD.remove(0);
    }

    public static void tickWorld() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) {
            WORLD.clear();
            return;
        }
        for (Iterator<WorldShell> it = WORLD.iterator(); it.hasNext(); ) {
            WorldShell s = it.next();
            s.age++;
            if (s.age > 60) {
                it.remove();
                continue;
            }
            s.prev = s.pos;
            if (s.landed) continue;
            s.vel = s.vel.add(0, -9.8 * 0.05, 0);
            Vec3d next = s.pos.add(s.vel.multiply(0.05));
            BlockPos bp = BlockPos.ofFloored(next);
            BlockState st = world.getBlockState(bp);
            VoxelShape shape = st.getCollisionShape(world, bp);
            if (!shape.isEmpty() && next.y < bp.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y)) {
                double top = bp.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y) + 0.01;
                if (Math.abs(s.vel.y) > 1.0) {
                    world.playSound(next.x, top, next.z, ModSounds.SHELL_DROP, SoundCategory.PLAYERS,
                            0.35f, 0.9f + RND.nextFloat() * 0.3f, false);
                    s.vel = new Vec3d(s.vel.x * 0.45, -s.vel.y * 0.3, s.vel.z * 0.45);
                    s.spin.mul(0.5f);
                    next = new Vec3d(next.x, top, next.z);
                } else {
                    s.landed = true;
                    next = new Vec3d(next.x, top, next.z);
                    s.rot.identity().rotateY(RND.nextFloat() * 6.28f).rotateX((float) Math.PI / 2f);
                }
            }
            s.pos = next;
            if (!s.landed) {
                s.rot.rotateXYZ((float) Math.toRadians(s.spin.x * 0.05), (float) Math.toRadians(s.spin.y * 0.05),
                        (float) Math.toRadians(s.spin.z * 0.05));
            }
        }
    }

    public static void renderWorld(WorldRenderContext ctx) {
        if (WORLD.isEmpty() || ctx.consumers() == null) return;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;
        Vec3d cam = ctx.camera().getPos();
        MatrixStack m = ctx.matrixStack();
        float td = ctx.tickDelta();
        for (WorldShell s : WORLD) {
            Vec3d p = s.prev.lerp(s.pos, td);
            int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(p));
            m.push();
            m.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            m.multiply(s.rot);
            m.scale(1f / 16f, 1f / 16f, 1f / 16f);
            draw(s.model, m, ctx.consumers(), light);
            m.pop();
        }
    }

    private static void draw(@Nullable GeoModel model, MatrixStack m, VertexConsumerProvider vcp, int light) {
        if (model == null || model.shell == null) return;
        GeoModel.ShellInfo si = model.shell;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(model.texture));
        float hx = si.sx / 2f, hy = si.sy / 2f, hz = si.sz / 2f;
        // корпус и донце (донце сзади, ближе к затвору)
        GeoRenderer.drawBox(model, m, vc, -hx, -hy, -hz, hx, hy, hz - 0.25f, si.swatch, light);
        GeoRenderer.drawBox(model, m, vc, -hx - 0.03f, -hy - 0.03f, hz - 0.25f, hx + 0.03f, hy + 0.03f, hz, si.swatchEnd, light);
    }

    private Shells() {
    }
}
