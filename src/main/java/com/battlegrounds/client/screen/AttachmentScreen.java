package com.battlegrounds.client.screen;

import com.battlegrounds.screen.AttachmentScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/** Меню обвесов: оружие крупно посередине, вокруг ячейки для обвесов. */
public class AttachmentScreen extends HandledScreen<AttachmentScreenHandler> {
    private static final int BG = 0xF0202326;
    private static final int PANEL = 0xFF2C3036;
    private static final int EDGE = 0xFF4A5058;
    private static final int SLOT = 0xFF15171A;
    private static final int ACCENT = 0xFFD6A94A;

    public AttachmentScreen(AttachmentScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 184;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;
        ctx.fill(x - 1, y - 1, x + backgroundWidth + 1, y + backgroundHeight + 1, EDGE);
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, BG);
        ctx.fill(x + 6, y + 14, x + backgroundWidth - 6, y + 86, PANEL);
        ctx.fill(x + 6, y + 14, x + backgroundWidth - 6, y + 15, ACCENT);

        // оружие крупно
        ItemStack gun = handler.getGun();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 88 - 30, y + 46 - 30, 0);
        ctx.getMatrices().scale(3.75f, 3.75f, 1f);
        ctx.drawItem(gun, 0, 0);
        ctx.getMatrices().pop();

        // ячейки
        for (Slot slot : handler.slots) {
            boolean att = slot instanceof AttachmentScreenHandler.AttSlot;
            if (!slot.isEnabled() && !att) continue;
            int sx = x + slot.x;
            int sy = y + slot.y;
            if (att) {
                AttachmentScreenHandler.AttSlot a = (AttachmentScreenHandler.AttSlot) slot;
                boolean allowed = a.isAllowedForGun();
                ctx.fill(sx - 2, sy - 2, sx + 18, sy + 18, allowed ? ACCENT : 0xFF3A3030);
                ctx.fill(sx - 1, sy - 1, sx + 17, sy + 17, allowed ? SLOT : 0xFF241C1C);
                if (!allowed) {
                    ctx.drawCenteredTextWithShadow(textRenderer, "×", sx + 8, sy + 4, 0xFF6A5050);
                }
            } else {
                ctx.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT);
            }
        }
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(textRenderer, this.title, this.titleX, this.titleY, 0xFFE8E2D0, false);
        ctx.drawText(textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0xFFB0B0B0, false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        if (this.focusedSlot instanceof AttachmentScreenHandler.AttSlot a && !a.hasStack()) {
            Text name = Text.translatable("slot.battlegrounds." + a.type.key);
            if (!a.isAllowedForGun()) {
                name = Text.translatable("slot.battlegrounds.unavailable", name);
            }
            ctx.drawTooltip(textRenderer, name, mouseX, mouseY);
        } else {
            drawMouseoverTooltip(ctx, mouseX, mouseY);
        }
    }
}
