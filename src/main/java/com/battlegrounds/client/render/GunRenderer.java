package com.battlegrounds.client.render;

import com.battlegrounds.Battlegrounds;
import com.battlegrounds.weapon.AttachmentSlot;
import com.battlegrounds.weapon.AttachmentType;
import com.battlegrounds.weapon.Attachments;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Рисует оружие вместе с установленными обвесами. */
public class GunRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private final String gunId;
    private final Identifier baseModel;

    public GunRenderer(String gunId) {
        this.gunId = gunId;
        this.baseModel = baseModelId(gunId);
    }

    public static Identifier baseModelId(String gunId) {
        return Battlegrounds.id("item/gun/" + gunId);
    }

    public static Identifier attachmentModelId(AttachmentType type) {
        return Battlegrounds.id("item/att/" + type.id);
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ItemRenderer ir = mc.getItemRenderer();
        matrices.push();
        matrices.translate(0.5f, 0.5f, 0.5f);
        part(ir, mc.getBakedModelManager().getModel(baseModel), stack, matrices, vertexConsumers, light, overlay);

        GunMounts.Mount m = GunMounts.get(gunId);
        for (AttachmentSlot slot : AttachmentSlot.values()) {
            AttachmentType type = Attachments.get(stack, slot);
            if (type == null) continue;
            matrices.push();
            switch (slot) {
                case SCOPE -> at(matrices, m.railY(), m.scopeZ());
                case MUZZLE -> at(matrices, m.muzzleY(), m.muzzleZ());
                case GRIP -> at(matrices, m.gripY(), m.gripZ());
                case MAGAZINE -> {
                    at(matrices, m.magY(), m.magZ());
                    if (m.magAngle() != 0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(m.magAngle()));
                }
                case STOCK -> at(matrices, m.stockY(), m.stockZ());
            }
            part(ir, mc.getBakedModelManager().getModel(attachmentModelId(type)), stack, matrices, vertexConsumers, light, overlay);
            matrices.pop();
        }
        matrices.pop();
    }

    private static void at(MatrixStack matrices, float y, float z) {
        matrices.translate(0f, (y - 8f) / 16f, (z - 8f) / 16f);
    }

    private static void part(ItemRenderer ir, BakedModel model, ItemStack stack, MatrixStack matrices,
                             VertexConsumerProvider vcp, int light, int overlay) {
        ir.renderItem(stack, ModelTransformationMode.NONE, false, matrices, vcp, light, overlay, model);
    }
}
