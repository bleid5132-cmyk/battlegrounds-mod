package com.battlegrounds.client.render;

import com.battlegrounds.client.geo.GeoAssets;
import com.battlegrounds.client.geo.GeoModel;
import com.battlegrounds.client.geo.GeoRenderer;
import com.battlegrounds.item.GunItem;
import com.battlegrounds.weapon.AttachmentSlot;
import com.battlegrounds.weapon.AttachmentType;
import com.battlegrounds.weapon.Attachments;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/** Оружие с костной моделью вне вида от первого лица: в руке от третьего лица, в инвентаре, на земле, в рамке. */
public final class GeoGunItemRenderer {

    /** Детали, которые скрываются обвесами. */
    public static Set<String> hiddenBones(ItemStack stack) {
        Set<String> hidden = new HashSet<>();
        hidden.add("camera");
        hidden.add("idle_view");
        hidden.add("iron_view");
        if (Attachments.get(stack, AttachmentSlot.SCOPE) != null) {
            hidden.add("sight_front");
            hidden.add("sight_rear");
        }
        if (Attachments.get(stack, AttachmentSlot.MUZZLE) != null) hidden.add("muzzle_default");
        if (Attachments.get(stack, AttachmentSlot.STOCK) != null) hidden.add("stock_default");
        return hidden;
    }

    /** На точке крепления рисуем установленный обвес (модели обвесов: точка крепления в (8,8,8)). */
    public static void attachmentAt(ItemStack stack, String locator, MatrixStack m, VertexConsumerProvider vcp, int light) {
        AttachmentSlot slot = switch (locator) {
            case "scope_pos" -> AttachmentSlot.SCOPE;
            case "muzzle_pos" -> AttachmentSlot.MUZZLE;
            case "grip_pos" -> AttachmentSlot.GRIP;
            case "mag_pos" -> AttachmentSlot.MAGAZINE;
            case "stock_pos" -> AttachmentSlot.STOCK;
            default -> null;
        };
        if (slot == null) return;
        AttachmentType type = Attachments.get(stack, slot);
        if (type == null || !(stack.getItem() instanceof GunItem g) || !g.stats.slots().contains(slot)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        BakedModel model = mc.getBakedModelManager().getModel(GunRenderer.attachmentModelId(type));
        m.push();
        m.scale(16f, 16f, 16f);
        mc.getItemRenderer().renderItem(stack, ModelTransformationMode.NONE, false, m, vcp, light,
                GeoRenderer.defaultOverlay(), model);
        m.pop();
    }

    /**
     * Матрица уже в пространстве предмета 0..1 (после трансформа из json и сдвига -0.5).
     * От третьего лица ставим так, чтобы рукоять оказалась там же, где была у старой модели.
     */
    public static void render(String gunId, ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                              VertexConsumerProvider vcp, int light, int overlay) {
        GeoAssets.Entry e = GeoAssets.get(gunId);
        if (e == null) return;
        GeoModel model = e.model();
        matrices.push();
        matrices.translate(0.5f, 0.5f, 0.5f);
        boolean hand = mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
        if (hand) {
            // в json предмета стоит масштаб 0.75 — приводим к масштабу модели
            float k = model.thirdPersonScale / 0.75f;
            matrices.translate(0f, (5.3f - 8f) / 16f, (10.9f - 8f) / 16f);
            matrices.scale(k / 16f, k / 16f, k / 16f);
            Vector3f grip = model.locator("righthand");
            if (grip != null) matrices.translate(-grip.x, -grip.y, -grip.z);
        } else {
            float k = mode == ModelTransformationMode.GUI ? model.guiScale / 0.5f : model.guiScale / 0.5f * 0.9f;
            matrices.scale(k / 16f, k / 16f, k / 16f);
            Vector3f c = new Vector3f(model.min).add(model.max).mul(0.5f);
            matrices.translate(-c.x, -c.y, -c.z);
        }
        Set<String> hidden = hiddenBones(stack);
        hidden.add("righthand");
        hidden.add("lefthand");
        GeoRenderer.render(model, null, matrices, vcp, light, overlay, hidden,
                (bone, m) -> attachmentAt(stack, bone.name, m, vcp, light));
        matrices.pop();
    }

    private GeoGunItemRenderer() {
    }
}
