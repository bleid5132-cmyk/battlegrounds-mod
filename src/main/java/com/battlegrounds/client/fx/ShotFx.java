package com.battlegrounds.client.fx;

import com.battlegrounds.Battlegrounds;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Эффекты выстрела на клиенте: дульная вспышка, трассер, искры, пыль и обломки,
 * дырки от пуль на поверхности блоков.
 */
public final class ShotFx {
    public static final Identifier TEX_FRONT = Battlegrounds.id("textures/fx/muzzle_front.png");
    public static final Identifier TEX_SIDE = Battlegrounds.id("textures/fx/muzzle_side.png");
    public static final Identifier TEX_TRACER = Battlegrounds.id("textures/fx/tracer.png");
    public static final Identifier TEX_SPARK = Battlegrounds.id("textures/fx/spark.png");
    public static final Identifier TEX_HOLE = Battlegrounds.id("textures/fx/bullet_hole.png");

    public static final int HIT_MISS = 0, HIT_BLOCK = 1, HIT_ENTITY = 2;

    private static final Random RND = new Random();
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    // ------------------------------------------------------------ вспышка от первого лица
    private static final Matrix4f MUZZLE = new Matrix4f();
    /** Поле зрения мира и рук (рисуются с разными проекциями) — нужно, чтобы трассер шёл точно из дула. */
    public static double worldFov = 70, handFov = 70;
    private static float muzzleForward = 2.7f;
    private static boolean muzzleValid;
    private static long fpFlashUntil = -1;
    private static boolean fpFlashShown = true;
    private static float fpFlashRoll, fpFlashScale = 1f;

    /** Положение дульного среза в кадре (пиксели модели, пространство камеры). */
    public static void captureMuzzle(Matrix4f m) {
        MUZZLE.set(m);
        muzzleValid = true;
    }

    /** Вспышка от первого лица в момент выстрела. small — пламегаситель (вспышка меньше), none — глушитель. */
    public static void firstPersonFlash(boolean none, boolean small) {
        if (none) return;
        fpFlashUntil = System.currentTimeMillis() + 45;
        fpFlashShown = false;
        fpFlashRoll = RND.nextFloat() * 360f;
        fpFlashScale = (small ? 0.55f : 1f) * (0.8f + RND.nextFloat() * 0.45f);
    }

    /** Рисуем вспышку у дульного среза (вызывается из рендера оружия от первого лица). */
    public static void renderFirstPersonFlash(VertexConsumerProvider vcp, float muzzleForwardPx) {
        muzzleForward = muzzleForwardPx;
        if (!muzzleValid || fpFlashUntil < 0) return;
        long now = System.currentTimeMillis();
        if (now > fpFlashUntil && fpFlashShown) return;
        fpFlashShown = true;
        MatrixStack m = new MatrixStack();
        m.peek().getPositionMatrix().set(MUZZLE);
        m.translate(0, 0, -muzzleForwardPx);
        m.multiply(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(fpFlashRoll)));
        float s = fpFlashScale;
        // звезда поперёк ствола
        VertexConsumer front = vcp.getBuffer(RenderLayer.getEyes(TEX_FRONT));
        quadXY(m, front, 4.2f * s, 1f);
        // два лепестка вдоль ствола (крест)
        VertexConsumer side = vcp.getBuffer(RenderLayer.getEyes(TEX_SIDE));
        for (int k = 0; k < 2; k++) {
            m.push();
            m.multiply(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(90 * k)));
            sidePetal(m, side, 2.6f * s, 9f * s);
            m.pop();
        }
    }

    // ------------------------------------------------------------ события выстрела (пакет с сервера)
    private static final class Streak {
        Vec3d from, to;           // трассер: голова летит от from к to
        double head, length, speed, total;
        float width, r, g, b;
        boolean spark;
        Vec3d pos, vel;           // искра
        float age, life;
    }

    private static final class Flash {
        Vec3d pos, dir;
        float scale, roll;
        long until;
        boolean shown;
    }

    private static final class Decal {
        BlockPos block;
        int stateId;
        Vec3d pos;
        Direction face;
        float rot, size;
        long born;
    }

    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Decal> DECALS = new ArrayList<>();
    private static final int MAX_DECALS = 400;
    private static final long DECAL_LIFE = 60_000, DECAL_FADE = 6_000;
    private static long lastNanos = -1;

    public static void onShot(int shooterId, Vec3d origin, Vec3d hit, int type, int faceId, int stateId,
                              boolean suppressed, boolean flashHidden, boolean heavy) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) return;
        Entity shooter = world.getEntityById(shooterId);
        Vec3d dir = hit.subtract(origin).normalize();
        boolean localFp = shooter == mc.player && mc.options.getPerspective().isFirstPerson();

        Vec3d muzzle = shooter instanceof PlayerEntity p ? muzzleWorld(p, localFp) : origin.add(dir);
        GunSounds.shot(shooter == mc.player ? mc.player.getEyePos() : muzzle, shooter == mc.player, suppressed, heavy);
        // вспышка для вида со стороны
        if (!localFp && !suppressed) {
            Flash f = new Flash();
            f.pos = muzzle;
            f.dir = dir;
            f.scale = (flashHidden ? 0.55f : 1f) * (0.8f + RND.nextFloat() * 0.45f);
            f.roll = RND.nextFloat() * 360f;
            f.until = System.currentTimeMillis() + 45;
            FLASHES.add(f);
        }
        // пороховой дымок у ствола (от первого лица не нужен — он оказывается прямо перед камерой)
        int smoke = localFp ? 0 : 1;
        for (int i = 0; i < smoke; i++) {
            world.addParticle(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z,
                    dir.x * 0.03 + RND.nextGaussian() * 0.008, 0.012 + dir.y * 0.03, dir.z * 0.03 + RND.nextGaussian() * 0.008);
        }
        // трассер
        if (!suppressed) {
            Vec3d from = localFp ? muzzle : muzzle.add(dir.multiply(0.6));
            double total = from.distanceTo(hit);
            if (total > 1.0) {
                Streak s = new Streak();
                s.from = from;
                s.to = hit;
                s.total = total;
                s.speed = 260 + RND.nextDouble() * 40;
                s.length = Math.min(7.0, total * 0.6);
                s.head = 0;
                s.width = 0.035f;
                s.r = 1f; s.g = 0.78f; s.b = 0.45f;
                STREAKS.add(s);
            }
        }
        // попадание
        if (type == HIT_BLOCK) impactBlock(world, hit, Direction.byId(faceId), Block.getStateFromRawId(stateId), dir);
        else if (type == HIT_ENTITY) impactEntity(world, hit, dir);
    }

    /** Примерное положение дульного среза в мире. */
    private static Vec3d muzzleWorld(PlayerEntity p, boolean localFp) {
        float td = MinecraftClient.getInstance().getTickDelta();
        Vec3d eye = p.getCameraPosVec(td);
        Vec3d fwd = p.getRotationVec(td);
        float yaw = p.getYaw(td) * MathHelper.RADIANS_PER_DEGREE;
        Vec3d right = new Vec3d(-MathHelper.cos(yaw), 0, -MathHelper.sin(yaw));
        Vec3d up = right.crossProduct(fwd).normalize().multiply(-1);
        if (localFp && muzzleValid) {
            // дуло в пространстве рук → точка на экране → та же точка экрана в мире (у мира другое поле зрения)
            Vector3f q = MUZZLE.transformPosition(new Vector3f(0, 0, -muzzleForward));
            if (q.z < -0.01f) {
                double th = Math.tan(Math.toRadians(handFov) / 2), tw = Math.tan(Math.toRadians(worldFov) / 2);
                double sx = q.x / -q.z / th, sy = q.y / -q.z / th;
                double depth = Math.max(0.6, -q.z);
                Vec3d r = fwd.crossProduct(new Vec3d(0, 1, 0));
                if (r.lengthSquared() < 1e-6) r = right; else r = r.normalize();
                Vec3d u = r.crossProduct(fwd).normalize();
                return eye.add(fwd.multiply(depth)).add(r.multiply(sx * tw * depth)).add(u.multiply(sy * tw * depth));
            }
        }
        return eye.add(fwd.multiply(0.95)).add(right.multiply(0.22)).add(up.multiply(-0.2));
    }

    private static void impactBlock(ClientWorld world, Vec3d p, Direction face, BlockState state, Vec3d dir) {
        Vec3d n = Vec3d.of(face.getVector());
        // обломки блока отлетают от поверхности
        if (!state.isAir()) {
            for (int i = 0; i < 9; i++) {
                Vec3d v = n.multiply(0.08 + RND.nextDouble() * 0.12)
                        .add(RND.nextGaussian() * 0.06, 0.04 + RND.nextDouble() * 0.08, RND.nextGaussian() * 0.06);
                world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                        p.x + n.x * 0.05, p.y + n.y * 0.05, p.z + n.z * 0.05, v.x, v.y, v.z);
            }
            // облачко пыли цвета блока
            int c = state.getMapColor(world, BlockPos.ofFloored(p.subtract(n.multiply(0.01)))).color;
            Vector3f col = new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
            col.lerp(new Vector3f(0.55f, 0.53f, 0.5f), 0.35f);
            for (int i = 0; i < 5; i++) {
                Vec3d q = p.add(n.multiply(0.06 + RND.nextDouble() * 0.15));
                world.addParticle(new DustParticleEffect(col, 1.4f + RND.nextFloat() * 0.8f), q.x, q.y, q.z,
                        n.x * 0.02, 0.01, n.z * 0.02);
            }
            world.addParticle(ParticleTypes.SMOKE, p.x + n.x * 0.08, p.y + n.y * 0.08, p.z + n.z * 0.08, n.x * 0.02, 0.02, n.z * 0.02);
        }
        // искры от твёрдых материалов
        if (hard(state)) {
            int k = 4 + RND.nextInt(5);
            Vec3d refl = dir.subtract(n.multiply(2 * dir.dotProduct(n)));
            for (int i = 0; i < k; i++) {
                Streak s = new Streak();
                s.spark = true;
                s.pos = p.add(n.multiply(0.02));
                Vec3d v = refl.multiply(0.5).add(n.multiply(0.6)).add(RND.nextGaussian() * 0.45, RND.nextGaussian() * 0.45 + 0.2, RND.nextGaussian() * 0.45);
                s.vel = v.normalize().multiply(5 + RND.nextDouble() * 6);
                s.life = 0.12f + RND.nextFloat() * 0.2f;
                s.width = 0.012f;
                s.r = 1f; s.g = 0.85f; s.b = 0.5f;
                STREAKS.add(s);
            }
        }
        // дырка от пули
        BlockPos bp = BlockPos.ofFloored(p.subtract(n.multiply(0.01)));
        boolean glass = state.isIn(net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags.GLASS_BLOCKS)
                || state.isIn(net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags.GLASS_PANES);
        if (!state.isAir() && !glass && (state.isOpaque() || state.isSideSolidFullSquare(world, bp, face))) {
            Decal d = new Decal();
            d.block = bp;
            d.stateId = Block.getRawIdFromState(world.getBlockState(d.block));
            d.pos = p;
            d.face = face;
            d.rot = RND.nextFloat() * 360f;
            d.size = 0.1f + RND.nextFloat() * 0.03f;
            d.born = System.currentTimeMillis();
            DECALS.add(d);
            if (DECALS.size() > MAX_DECALS) DECALS.remove(0);
        }
    }

    private static boolean hard(BlockState s) {
        BlockSoundGroup g = s.getSoundGroup();
        return g == BlockSoundGroup.STONE || g == BlockSoundGroup.METAL || g == BlockSoundGroup.DEEPSLATE
                || g == BlockSoundGroup.DEEPSLATE_BRICKS || g == BlockSoundGroup.DEEPSLATE_TILES || g == BlockSoundGroup.POLISHED_DEEPSLATE
                || g == BlockSoundGroup.TUFF || g == BlockSoundGroup.CALCITE || g == BlockSoundGroup.NETHERITE
                || g == BlockSoundGroup.COPPER || g == BlockSoundGroup.ANVIL || g == BlockSoundGroup.CHAIN
                || g == BlockSoundGroup.LANTERN || g == BlockSoundGroup.NETHER_BRICKS || g == BlockSoundGroup.BASALT || g == BlockSoundGroup.MUD_BRICKS;
    }

    private static void impactEntity(ClientWorld world, Vec3d p, Vec3d dir) {
        DustParticleEffect blood = new DustParticleEffect(new Vector3f(0.45f, 0.02f, 0.02f), 1.1f);
        for (int i = 0; i < 10; i++) {
            Vec3d v = dir.multiply(0.12 + RND.nextDouble() * 0.15).add(RND.nextGaussian() * 0.05, RND.nextGaussian() * 0.05, RND.nextGaussian() * 0.05);
            world.addParticle(blood, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    // ------------------------------------------------------------ обновление и рисование в мире
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            STREAKS.clear();
            FLASHES.clear();
            DECALS.clear();
            return;
        }
        // дырки пропадают вместе с блоком
        long now = System.currentTimeMillis();
        for (Iterator<Decal> it = DECALS.iterator(); it.hasNext(); ) {
            Decal d = it.next();
            if (now - d.born > DECAL_LIFE || Block.getRawIdFromState(mc.world.getBlockState(d.block)) != d.stateId) it.remove();
        }
    }

    public static void renderWorld(WorldRenderContext ctx) {
        GunSounds.update();
        VertexConsumerProvider vcp = ctx.consumers();
        ClientWorld world = MinecraftClient.getInstance().world;
        if (vcp == null || world == null) return;
        long nowN = System.nanoTime();
        float dt = lastNanos < 0 ? 0f : Math.min(0.1f, (nowN - lastNanos) / 1.0e9f);
        lastNanos = nowN;
        long now = System.currentTimeMillis();
        Vec3d cam = ctx.camera().getPos();
        MatrixStack m = ctx.matrixStack();

        // дырки от пуль
        if (!DECALS.isEmpty()) {
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEX_HOLE));
            int idx = 0;
            for (Decal d : DECALS) {
                long age = now - d.born;
                float alpha = age > DECAL_LIFE - DECAL_FADE ? (DECAL_LIFE - age) / (float) DECAL_FADE : 1f;
                int light = WorldRenderer.getLightmapCoordinates(world, d.block.offset(d.face));
                m.push();
                Vec3d n = Vec3d.of(d.face.getVector());
                Vec3d p = d.pos.add(n.multiply(0.0025 + (idx++ % 4) * 0.0006));
                m.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
                m.multiply(d.face.getRotationQuaternion());               // +Y → нормаль грани
                m.multiply(new org.joml.Quaternionf().rotateY((float) Math.toRadians(d.rot)));
                float h = d.size / 2;
                Matrix4f pm = m.peek().getPositionMatrix();
                Matrix3f nm = m.peek().getNormalMatrix();
                int a = (int) (alpha * 255);
                vert(vc, pm, nm, -h, 0, -h, 0, 0, 255, 255, 255, a, light, 0, 1, 0);
                vert(vc, pm, nm, -h, 0, h, 0, 1, 255, 255, 255, a, light, 0, 1, 0);
                vert(vc, pm, nm, h, 0, h, 1, 1, 255, 255, 255, a, light, 0, 1, 0);
                vert(vc, pm, nm, h, 0, -h, 1, 0, 255, 255, 255, a, light, 0, 1, 0);
                m.pop();
            }
        }

        // трассеры и искры
        if (!STREAKS.isEmpty()) {
            VertexConsumer tr = vcp.getBuffer(RenderLayer.getEyes(TEX_TRACER));
            VertexConsumer sp = vcp.getBuffer(RenderLayer.getEyes(TEX_SPARK));
            for (Iterator<Streak> it = STREAKS.iterator(); it.hasNext(); ) {
                Streak s = it.next();
                Vec3d a, b;
                if (s.spark) {
                    s.age += dt;
                    if (s.age > s.life) { it.remove(); continue; }
                    s.vel = s.vel.add(0, -18 * dt, 0).multiply(1 - 2.5 * dt);
                    Vec3d prev = s.pos;
                    s.pos = s.pos.add(s.vel.multiply(dt));
                    a = s.pos;
                    b = s.pos.subtract(s.vel.multiply(0.025));
                    if (prev.equals(s.pos)) b = s.pos.subtract(0, 0.02, 0);
                    float fade = 1f - s.age / s.life;
                    streak(m, sp, cam, a, b, s.width, fade);
                } else {
                    s.head += s.speed * dt;
                    double tail = s.head - s.length;
                    if (tail >= s.total) { it.remove(); continue; }
                    Vec3d d = s.to.subtract(s.from).normalize();
                    a = s.from.add(d.multiply(Math.min(s.head, s.total)));
                    b = s.from.add(d.multiply(Math.max(0, tail)));
                    if (a.squaredDistanceTo(b) < 1e-4) continue;
                    // постоянная видимая толщина: у дула тонкий, вдали не теряется
                    double dist = a.add(b).multiply(0.5).distanceTo(cam);
                    float w = (float) Math.max(0.003, 0.0028 * dist);
                    streak(m, tr, cam, a, b, w, 1f);
                }
            }
        }

        // вспышки у ствола (вид со стороны)
        if (!FLASHES.isEmpty()) {
            VertexConsumer front = vcp.getBuffer(RenderLayer.getEyes(TEX_FRONT));
            VertexConsumer side = vcp.getBuffer(RenderLayer.getEyes(TEX_SIDE));
            for (Iterator<Flash> it = FLASHES.iterator(); it.hasNext(); ) {
                Flash f = it.next();
                if (now > f.until && f.shown) { it.remove(); continue; }
                f.shown = true;
                Vec3d p = f.pos;
                // звезда — лицом к камере
                m.push();
                m.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
                m.multiply(ctx.camera().getRotation());
                m.multiply(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(f.roll)));
                m.scale(1f / 16f, 1f / 16f, 1f / 16f);
                quadXY(m, front, 4.5f * f.scale, 1f);
                m.pop();
                // язык пламени вдоль ствола
                Vec3d tip = p.add(f.dir.multiply(0.55 * f.scale));
                streakTex(m, side, cam, p, tip, 0.17f * f.scale);
            }
        }
    }

    /** Полоса от a (голова, v=0) к b (хвост, v=1), развёрнутая к камере. */
    private static void streak(MatrixStack m, VertexConsumer vc, Vec3d cam, Vec3d a, Vec3d b, float w, float bright) {
        streakTex(m, vc, cam, a, b, w, bright);
    }

    private static void streakTex(MatrixStack m, VertexConsumer vc, Vec3d cam, Vec3d a, Vec3d b, float w) {
        streakTex(m, vc, cam, a, b, w, 1f);
    }

    private static void streakTex(MatrixStack m, VertexConsumer vc, Vec3d cam, Vec3d a, Vec3d b, float w, float bright) {
        Vec3d ra = a.subtract(cam), rb = b.subtract(cam);
        Vec3d axis = rb.subtract(ra);
        Vec3d side = axis.crossProduct(ra.add(rb).multiply(0.5));
        if (side.lengthSquared() < 1e-10) return;
        side = side.normalize().multiply(w);
        Matrix4f pm = m.peek().getPositionMatrix();
        Matrix3f nm = m.peek().getNormalMatrix();
        int c = (int) (MathHelper.clamp(bright, 0f, 1f) * 255);
        vert(vc, pm, nm, (float) (ra.x - side.x), (float) (ra.y - side.y), (float) (ra.z - side.z), 0, 0, c, c, c, 255, FULL, 0, 1, 0);
        vert(vc, pm, nm, (float) (ra.x + side.x), (float) (ra.y + side.y), (float) (ra.z + side.z), 1, 0, c, c, c, 255, FULL, 0, 1, 0);
        vert(vc, pm, nm, (float) (rb.x + side.x), (float) (rb.y + side.y), (float) (rb.z + side.z), 1, 1, c, c, c, 255, FULL, 0, 1, 0);
        vert(vc, pm, nm, (float) (rb.x - side.x), (float) (rb.y - side.y), (float) (rb.z - side.z), 0, 1, c, c, c, 255, FULL, 0, 1, 0);
        // обратная сторона (слой без отключения отсечения граней)
        vert(vc, pm, nm, (float) (rb.x - side.x), (float) (rb.y - side.y), (float) (rb.z - side.z), 0, 1, c, c, c, 255, FULL, 0, -1, 0);
        vert(vc, pm, nm, (float) (rb.x + side.x), (float) (rb.y + side.y), (float) (rb.z + side.z), 1, 1, c, c, c, 255, FULL, 0, -1, 0);
        vert(vc, pm, nm, (float) (ra.x + side.x), (float) (ra.y + side.y), (float) (ra.z + side.z), 1, 0, c, c, c, 255, FULL, 0, -1, 0);
        vert(vc, pm, nm, (float) (ra.x - side.x), (float) (ra.y - side.y), (float) (ra.z - side.z), 0, 0, c, c, c, 255, FULL, 0, -1, 0);
    }

    /** Квадрат в плоскости XY с центром в 0 (для звезды вспышки), с обеих сторон. */
    private static void quadXY(MatrixStack m, VertexConsumer vc, float half, float bright) {
        Matrix4f pm = m.peek().getPositionMatrix();
        Matrix3f nm = m.peek().getNormalMatrix();
        int c = (int) (bright * 255);
        float h = half;
        vert(vc, pm, nm, -h, -h, 0, 0, 1, c, c, c, 255, FULL, 0, 0, 1);
        vert(vc, pm, nm, h, -h, 0, 1, 1, c, c, c, 255, FULL, 0, 0, 1);
        vert(vc, pm, nm, h, h, 0, 1, 0, c, c, c, 255, FULL, 0, 0, 1);
        vert(vc, pm, nm, -h, h, 0, 0, 0, c, c, c, 255, FULL, 0, 0, 1);
        vert(vc, pm, nm, -h, h, 0, 0, 0, c, c, c, 255, FULL, 0, 0, -1);
        vert(vc, pm, nm, h, h, 0, 1, 0, c, c, c, 255, FULL, 0, 0, -1);
        vert(vc, pm, nm, h, -h, 0, 1, 1, c, c, c, 255, FULL, 0, 0, -1);
        vert(vc, pm, nm, -h, -h, 0, 0, 1, c, c, c, 255, FULL, 0, 0, -1);
    }

    /** Лепесток пламени в плоскости XZ: от дульного среза (z=0) вперёд по −Z. */
    private static void sidePetal(MatrixStack m, VertexConsumer vc, float halfW, float len) {
        Matrix4f pm = m.peek().getPositionMatrix();
        Matrix3f nm = m.peek().getNormalMatrix();
        vert(vc, pm, nm, -halfW, 0, 0, 0, 0, 255, 255, 255, 255, FULL, 0, 1, 0);
        vert(vc, pm, nm, halfW, 0, 0, 1, 0, 255, 255, 255, 255, FULL, 0, 1, 0);
        vert(vc, pm, nm, halfW, 0, -len, 1, 1, 255, 255, 255, 255, FULL, 0, 1, 0);
        vert(vc, pm, nm, -halfW, 0, -len, 0, 1, 255, 255, 255, 255, FULL, 0, 1, 0);
        vert(vc, pm, nm, -halfW, 0, -len, 0, 1, 255, 255, 255, 255, FULL, 0, -1, 0);
        vert(vc, pm, nm, halfW, 0, -len, 1, 1, 255, 255, 255, 255, FULL, 0, -1, 0);
        vert(vc, pm, nm, halfW, 0, 0, 1, 0, 255, 255, 255, 255, FULL, 0, -1, 0);
        vert(vc, pm, nm, -halfW, 0, 0, 0, 0, 255, 255, 255, 255, FULL, 0, -1, 0);
    }

    private static void vert(VertexConsumer vc, Matrix4f pm, Matrix3f nm, float x, float y, float z, float u, float v,
                             int r, int g, int b, int a, int light, float nx, float ny, float nz) {
        vc.vertex(pm, x, y, z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(nm, nx, ny, nz).next();
    }

    private ShotFx() {
    }
}
