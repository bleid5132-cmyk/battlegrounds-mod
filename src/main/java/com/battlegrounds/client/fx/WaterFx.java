package com.battlegrounds.client.fx;

import com.battlegrounds.Battlegrounds;
import com.battlegrounds.registry.ModSounds;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Попадание пули в воду: тонкий столб брызг и «корона» капель, водяная пыль,
 * расходящиеся круги на поверхности, пузыри по пути пули под водой и звук всплеска.
 */
public final class WaterFx {
    private static final Identifier TEX_DROP = Battlegrounds.id("textures/fx/droplet.png");
    private static final Identifier TEX_RIPPLE = Battlegrounds.id("textures/fx/ripple.png");
    private static final Random RND = new Random();

    private static final class Drop {
        Vec3d pos, vel;
        float size, age, life, alpha;
        double floorY;          // уровень воды: ниже — капля исчезает
        boolean mist;
    }

    private static final class Ripple {
        Vec3d pos;
        float delay, age, life, radius;
    }

    private static final List<Drop> DROPS = new ArrayList<>();
    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static long lastNanos = -1;

    /** surface — точка входа в воду, dir — направление пули, end — где пуля остановилась/попала. */
    public static void impact(ClientWorld world, Vec3d surface, Vec3d dir, Vec3d end, boolean heavy) {
        double k = heavy ? 1.25 : 1.0;
        // чем более отвесно вошла пуля, тем выше и тоньше столб; по касательной — брызги летят вперёд
        double steep = Math.min(1.0, Math.abs(dir.y) * 1.6 + 0.25);
        Vec3d flat = new Vec3d(dir.x, 0, dir.z);
        flat = flat.lengthSquared() > 1e-6 ? flat.normalize() : Vec3d.ZERO;
        // столб
        int column = (int) (22 * k);
        for (int i = 0; i < column; i++) {
            Drop d = new Drop();
            d.pos = surface.add(RND.nextGaussian() * 0.03, 0.01, RND.nextGaussian() * 0.03);
            double up = (3.5 + RND.nextDouble() * 4.5) * k * (0.55 + 0.45 * steep);
            d.vel = new Vec3d(RND.nextGaussian() * 0.35, up, RND.nextGaussian() * 0.35)
                    .add(flat.multiply((1 - steep) * (1.5 + RND.nextDouble() * 2)));
            d.size = 0.025f + RND.nextFloat() * 0.03f;
            d.life = 1.4f;
            d.alpha = 0.85f;
            d.floorY = surface.y;
            DROPS.add(d);
        }
        // корона — капли расходятся в стороны под углом
        int crown = (int) (14 * k);
        for (int i = 0; i < crown; i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            double sp = 1.2 + RND.nextDouble() * 1.6;
            Drop d = new Drop();
            d.pos = surface.add(Math.cos(a) * 0.04, 0.01, Math.sin(a) * 0.04);
            d.vel = new Vec3d(Math.cos(a) * sp, 1.6 + RND.nextDouble() * 1.8, Math.sin(a) * sp).add(flat.multiply(1 - steep));
            d.size = 0.018f + RND.nextFloat() * 0.02f;
            d.life = 1.0f;
            d.alpha = 0.8f;
            d.floorY = surface.y;
            DROPS.add(d);
        }
        // водяная пыль — мягкие полупрозрачные облачка, медленно поднимаются и тают
        for (int i = 0; i < 5; i++) {
            Drop d = new Drop();
            d.mist = true;
            d.pos = surface.add(RND.nextGaussian() * 0.08, 0.05 + RND.nextDouble() * 0.25, RND.nextGaussian() * 0.08);
            d.vel = new Vec3d(RND.nextGaussian() * 0.15, 0.4 + RND.nextDouble() * 0.5, RND.nextGaussian() * 0.15);
            d.size = 0.12f + RND.nextFloat() * 0.12f;
            d.life = 0.55f + RND.nextFloat() * 0.3f;
            d.alpha = 0.28f;
            d.floorY = surface.y - 1;
            DROPS.add(d);
        }
        // круги на воде
        float[][] rs = {{0f, 0.75f, 0.55f}, {0.08f, 1.0f, 0.85f}, {0.22f, 1.3f, 1.2f}};
        for (float[] r : rs) {
            Ripple rp = new Ripple();
            rp.pos = new Vec3d(surface.x, surface.y + 0.004, surface.z);
            rp.delay = r[0];
            rp.life = r[1];
            rp.radius = (float) (r[2] * k);
            RIPPLES.add(rp);
        }
        // классические брызги Minecraft у самой поверхности
        for (int i = 0; i < 10; i++) {
            world.addParticle(ParticleTypes.SPLASH, surface.x + RND.nextGaussian() * 0.15, surface.y + 0.05,
                    surface.z + RND.nextGaussian() * 0.15, 0, 0, 0);
        }
        // пузыри по пути пули под водой
        double len = surface.distanceTo(end);
        for (double t = 0.05; t < len; t += 0.12) {
            Vec3d p = surface.add(dir.multiply(t));
            world.addParticle(ParticleTypes.BUBBLE, p.x + RND.nextGaussian() * 0.02, p.y, p.z + RND.nextGaussian() * 0.02,
                    RND.nextGaussian() * 0.01, 0.02 + RND.nextDouble() * 0.04, RND.nextGaussian() * 0.01);
        }
        for (int i = 0; i < 6; i++) {
            world.addParticle(ParticleTypes.BUBBLE, end.x + RND.nextGaussian() * 0.05, end.y, end.z + RND.nextGaussian() * 0.05,
                    0, 0.05 + RND.nextDouble() * 0.05, 0);
        }
        world.playSound(surface.x, surface.y, surface.z, ModSounds.BULLET_WATER, SoundCategory.BLOCKS,
                heavy ? 1.0f : 0.85f, 0.92f + RND.nextFloat() * 0.16f, false);
    }

    public static void render(WorldRenderContext ctx) {
        long nowN = System.nanoTime();
        float dt = lastNanos < 0 ? 0f : Math.min(0.1f, (nowN - lastNanos) / 1.0e9f);
        lastNanos = nowN;
        VertexConsumerProvider vcp = ctx.consumers();
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            DROPS.clear();
            RIPPLES.clear();
            return;
        }
        if (vcp == null || (DROPS.isEmpty() && RIPPLES.isEmpty())) return;
        Vec3d cam = ctx.camera().getPos();
        MatrixStack m = ctx.matrixStack();

        if (!RIPPLES.isEmpty()) {
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEX_RIPPLE));
            for (Iterator<Ripple> it = RIPPLES.iterator(); it.hasNext(); ) {
                Ripple r = it.next();
                r.age += dt;
                float t = (r.age - r.delay) / r.life;
                if (t >= 1f) { it.remove(); continue; }
                if (t < 0f) continue;
                float ease = 1f - (1f - t) * (1f - t);                // быстро расходится, потом замедляется
                float rad = 0.04f + r.radius * ease;
                int a = (int) (255 * (1f - t) * (1f - t) * 0.9f);
                int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(r.pos).up());
                m.push();
                m.translate(r.pos.x - cam.x, r.pos.y - cam.y, r.pos.z - cam.z);
                Matrix4f pm = m.peek().getPositionMatrix();
                Matrix3f nm = m.peek().getNormalMatrix();
                vert(vc, pm, nm, -rad, 0, -rad, 0, 0, a, light, 0, 1, 0);
                vert(vc, pm, nm, -rad, 0, rad, 0, 1, a, light, 0, 1, 0);
                vert(vc, pm, nm, rad, 0, rad, 1, 1, a, light, 0, 1, 0);
                vert(vc, pm, nm, rad, 0, -rad, 1, 0, a, light, 0, 1, 0);
                m.pop();
            }
        }

        if (!DROPS.isEmpty()) {
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEX_DROP));
            Matrix4f pm = m.peek().getPositionMatrix();
            Matrix3f nm = m.peek().getNormalMatrix();
            for (Iterator<Drop> it = DROPS.iterator(); it.hasNext(); ) {
                Drop d = it.next();
                d.age += dt;
                if (d.age > d.life) { it.remove(); continue; }
                if (d.mist) {
                    d.vel = d.vel.multiply(1 - 2.5 * dt);
                    d.size += dt * 0.35f;
                } else {
                    d.vel = d.vel.add(0, -16 * dt, 0).multiply(1 - 0.6 * dt);
                }
                d.pos = d.pos.add(d.vel.multiply(dt));
                if (!d.mist && d.vel.y < 0 && d.pos.y < d.floorY) {    // капля упала обратно в воду
                    if (RND.nextFloat() < 0.15f) {
                        world.addParticle(ParticleTypes.SPLASH, d.pos.x, d.floorY + 0.02, d.pos.z, 0, 0, 0);
                    }
                    it.remove();
                    continue;
                }
                float fade = d.mist ? (1f - d.age / d.life) : Math.min(1f, (d.life - d.age) / 0.3f);
                int a = (int) (255 * d.alpha * fade);
                int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(d.pos));
                Vec3d p = d.pos.subtract(cam);
                // капля вытянута по скорости — так брызги выглядят в движении
                Vec3d tail = d.mist ? p.add(0, -0.001, 0) : p.subtract(d.vel.multiply(0.018));
                quadAlong(vc, pm, nm, p, tail, d.size, a, light);
            }
        }
    }

    /** Полоса от a к b шириной w, развёрнутая к камере; при коротком отрезке — почти круг. */
    private static void quadAlong(VertexConsumer vc, Matrix4f pm, Matrix3f nm, Vec3d a, Vec3d b, float w, int alpha, int light) {
        Vec3d axis = b.subtract(a);
        double len = axis.length();
        Vec3d mid = a.add(b).multiply(0.5);
        Vec3d view = mid.normalize();
        Vec3d along = len < 1e-4 ? new Vec3d(0, 1, 0) : axis.multiply(1 / len);
        Vec3d side = along.crossProduct(view);
        if (side.lengthSquared() < 1e-8) side = new Vec3d(1, 0, 0);
        side = side.normalize().multiply(w);
        // удлиняем на радиус капли с обоих концов
        Vec3d ext = along.multiply(w);
        Vec3d A = a.subtract(ext), B = b.add(ext);
        vert(vc, pm, nm, (float) (A.x - side.x), (float) (A.y - side.y), (float) (A.z - side.z), 0, 0, alpha, light, 0, 1, 0);
        vert(vc, pm, nm, (float) (A.x + side.x), (float) (A.y + side.y), (float) (A.z + side.z), 1, 0, alpha, light, 0, 1, 0);
        vert(vc, pm, nm, (float) (B.x + side.x), (float) (B.y + side.y), (float) (B.z + side.z), 1, 1, alpha, light, 0, 1, 0);
        vert(vc, pm, nm, (float) (B.x - side.x), (float) (B.y - side.y), (float) (B.z - side.z), 0, 1, alpha, light, 0, 1, 0);
    }

    private static void vert(VertexConsumer vc, Matrix4f pm, Matrix3f nm, float x, float y, float z, float u, float v,
                             int a, int light, float nx, float ny, float nz) {
        vc.vertex(pm, x, y, z).color(255, 255, 255, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(nm, nx, ny, nz).next();
    }

    private WaterFx() {
    }
}
