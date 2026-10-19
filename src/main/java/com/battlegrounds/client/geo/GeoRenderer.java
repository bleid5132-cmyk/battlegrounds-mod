package com.battlegrounds.client.geo;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Set;

/** Рисует GeoModel. Позиции в пикселях, поэтому перед вызовом матрицу нужно уменьшить в 16 раз. */
public final class GeoRenderer {

    /** Вызывается на кости-локаторе: матрица уже стоит в точке pivot с поворотом кости. */
    public interface LocatorHook {
        void at(GeoModel.Bone bone, MatrixStack matrices);
    }

    public static void render(GeoModel model, @Nullable GeoPose pose, MatrixStack matrices, VertexConsumerProvider vcp,
                              int light, int overlay, Set<String> hidden, @Nullable LocatorHook hook) {
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(model.texture));
        for (int root : model.roots) {
            bone(model, model.bones.get(root), pose, matrices, vc, vcp, light, overlay, hidden, hook);
        }
    }

    private static void bone(GeoModel model, GeoModel.Bone b, @Nullable GeoPose pose, MatrixStack matrices,
                             VertexConsumer vc, VertexConsumerProvider vcp, int light, int overlay,
                             Set<String> hidden, @Nullable LocatorHook hook) {
        if (hidden.contains(b.name)) return;
        matrices.push();
        float[] p = pose != null ? pose.get(b.index) : null;
        float px = b.pivot.x, py = b.pivot.y, pz = b.pivot.z;
        float sx = b.scale.x, sy = b.scale.y, sz = b.scale.z;
        if (p != null) {
            px += p[0]; py += p[1]; pz += p[2];
            sx *= p[6]; sy *= p[7]; sz *= p[8];
        }
        matrices.translate(px, py, pz);
        matrices.multiply(GeoModel.rotation(b.rest.x, b.rest.y, b.rest.z));
        if (p != null && (p[3] != 0 || p[4] != 0 || p[5] != 0)) {
            matrices.multiply(GeoModel.rotation(p[3], p[4], p[5]));
        }
        matrices.scale(sx, sy, sz);

        if (hook != null && b.cubes.isEmpty()) {
            // локатор: матрица в точке pivot
            matrices.push();
            hook.at(b, matrices);
            matrices.pop();
            // после внешнего рендера (руки, обвесы) нужно снова взять наш буфер
            vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(model.texture));
        }

        matrices.translate(-b.pivot.x, -b.pivot.y, -b.pivot.z);
        if (!b.cubes.isEmpty()) {
            MatrixStack.Entry e = matrices.peek();
            for (GeoModel.Cube c : b.cubes) cube(model, c, e.getPositionMatrix(), e.getNormalMatrix(), vc, light, overlay);
        }
        for (int child : b.children) {
            bone(model, model.bones.get(child), pose, matrices, vc, vcp, light, overlay, hidden, hook);
            vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(model.texture));
        }
        matrices.pop();
    }

    private static void cube(GeoModel model, GeoModel.Cube c, Matrix4f pos, Matrix3f nrm, VertexConsumer vc, int light, int overlay) {
        int cols = model.texWidth / model.swatch;
        float sw = model.swatch;
        float u0 = ((c.swatch % cols) * sw + 1f) / model.texWidth;
        float v0 = ((c.swatch / cols) * sw + 1f) / model.texHeight;
        float u1 = ((c.swatch % cols) * sw + sw - 1f) / model.texWidth;
        float v1 = ((c.swatch / cols) * sw + sw - 1f) / model.texHeight;
        float x0 = c.x0, y0 = c.y0, z0 = c.z0, x1 = c.x1, y1 = c.y1, z1 = c.z1;
        // -X
        quad(vc, pos, nrm, light, overlay, u0, v0, u1, v1, -1, 0, 0,
                x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
        // +X
        quad(vc, pos, nrm, light, overlay, u0, v0, u1, v1, 1, 0, 0,
                x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);
        // -Y
        quad(vc, pos, nrm, light, overlay, u0, v0, u1, v1, 0, -1, 0,
                x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
        // +Y
        quad(vc, pos, nrm, light, overlay, u0, v0, u1, v1, 0, 1, 0,
                x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        // -Z
        quad(vc, pos, nrm, light, overlay, u0, v0, u1, v1, 0, 0, -1,
                x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);
        // +Z
        quad(vc, pos, nrm, light, overlay, u0, v0, u1, v1, 0, 0, 1,
                x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
    }

    private static void quad(VertexConsumer vc, Matrix4f pos, Matrix3f nrm, int light, int overlay,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vert(vc, pos, nrm, ax, ay, az, u0, v0, light, overlay, nx, ny, nz);
        vert(vc, pos, nrm, bx, by, bz, u1, v0, light, overlay, nx, ny, nz);
        vert(vc, pos, nrm, cx, cy, cz, u1, v1, light, overlay, nx, ny, nz);
        vert(vc, pos, nrm, dx, dy, dz, u0, v1, light, overlay, nx, ny, nz);
    }

    private static void vert(VertexConsumer vc, Matrix4f pos, Matrix3f nrm, float x, float y, float z,
                             float u, float v, int light, int overlay, float nx, float ny, float nz) {
        vc.vertex(pos, x, y, z).color(1f, 1f, 1f, 1f).texture(u, v).overlay(overlay).light(light).normal(nrm, nx, ny, nz).next();
    }

    public static int defaultOverlay() {
        return OverlayTexture.DEFAULT_UV;
    }

    private GeoRenderer() {
    }
}
