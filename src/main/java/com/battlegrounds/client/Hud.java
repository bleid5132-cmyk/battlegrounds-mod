package com.battlegrounds.client;

import com.battlegrounds.item.GunItem;
import com.battlegrounds.player.BgPlayer;
import com.battlegrounds.weapon.AttachmentType;
import com.battlegrounds.weapon.Attachments;
import com.battlegrounds.weapon.GunLogic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class Hud {

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // стойка
        int stance = BgPlayer.of(p).bg$getStance();
        if (stance != BgPlayer.STAND) {
            Text t = Text.translatable(stance == BgPlayer.CROUCH ? "hud.battlegrounds.crouch" : "hud.battlegrounds.prone");
            ctx.drawTextWithShadow(tr, t, 8, h - 20, 0xFFFFFF);
        }

        ItemStack stack = p.getMainHandStack();
        if (!(stack.getItem() instanceof GunItem gun)) return;

        // сетка прицела
        Attachments.Mods mods = Attachments.compute(stack, gun.stats);
        float aim = ClientController.getAimProgress(tickDelta);
        if (mods.reticle() == AttachmentType.Reticle.SCOPE && aim > 0.92f) {
            drawScope(ctx, w, h, mods.zoom());
        } else if (mods.reticle() == AttachmentType.Reticle.DOT && aim > 0.6f) {
            ctx.fill(w / 2 - 1, h / 2 - 1, w / 2 + 1, h / 2 + 1, 0xFFFF2020);
        } else if (mods.reticle() == AttachmentType.Reticle.HOLO && aim > 0.6f) {
            drawRing(ctx, w / 2, h / 2, 7, 0xE0FF3030);
            ctx.fill(w / 2, h / 2, w / 2 + 1, h / 2 + 1, 0xFFFF3030);
        }

        // патроны: в магазине | в запасе
        int mag = GunItem.getAmmo(stack);
        int reserve = p.isCreative() ? -1 : GunLogic.countAmmo(p.getInventory(), gun.stats.ammo());
        String magStr = String.valueOf(mag);
        String resStr = reserve < 0 ? "∞" : String.valueOf(reserve);
        int x = w - 110;
        int y = h - 48;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(2f, 2f, 1f);
        ctx.drawTextWithShadow(tr, magStr, 0, 0, mag == 0 ? 0xFF5555 : 0xFFFFFF);
        ctx.getMatrices().pop();
        ctx.drawTextWithShadow(tr, "| " + resStr, x + tr.getWidth(magStr) * 2 + 4, y + 6, 0xBBBBBB);
        Text mode = Text.translatable(gun.getFireMode(stack).langKey);
        ctx.drawTextWithShadow(tr, mode, x, y + 20, 0xAAAAAA);

        // перезарядка
        if (GunItem.isReloading(stack) && mc.world != null) {
            long start = GunItem.getReloadStart(stack);
            long end = GunItem.getReloadEnd(stack);
            float prog = end > start ? (mc.world.getTime() + tickDelta - start) / (float) (end - start) : 1f;
            prog = Math.max(0f, Math.min(1f, prog));
            int bw = 60;
            int bx = w / 2 - bw / 2;
            int by = h / 2 + 14;
            ctx.fill(bx, by, bx + bw, by + 3, 0x80000000);
            ctx.fill(bx, by, bx + (int) (bw * prog), by + 3, 0xFFFFFFFF);
            Text t = Text.translatable("hud.battlegrounds.reloading");
            ctx.drawTextWithShadow(tr, t, w / 2 - tr.getWidth(t) / 2, by + 6, 0xFFFFFF);
        }

        // прицел в виде от третьего лица
        if (!mc.options.getPerspective().isFirstPerson()) {
            int cx = w / 2;
            int cy = h / 2;
            int gap = 3;
            int len = 4;
            int col = 0xDDFFFFFF;
            ctx.fill(cx - gap - len, cy, cx - gap, cy + 1, col);
            ctx.fill(cx + gap + 1, cy, cx + gap + 1 + len, cy + 1, col);
            ctx.fill(cx, cy - gap - len, cx + 1, cy - gap, col);
            ctx.fill(cx, cy + gap + 1, cx + 1, cy + gap + 1 + len, col);
            ctx.fill(cx, cy, cx + 1, cy + 1, col);
        }
    }

    /** Чёрная рамка оптики с сеткой. */
    private static void drawScope(DrawContext ctx, int w, int h, float zoom) {
        int cx = w / 2;
        int cy = h / 2;
        int r = (int) (h * 0.46f);
        int black = 0xFF000000;
        for (int y = 0; y < h; y++) {
            int dy = y - cy;
            if (Math.abs(dy) >= r) {
                ctx.fill(0, y, w, y + 1, black);
            } else {
                int half = (int) Math.sqrt((double) r * r - (double) dy * dy);
                ctx.fill(0, y, cx - half, y + 1, black);
                ctx.fill(cx + half, y, w, y + 1, black);
            }
        }
        // тонкая тень по краю
        drawRing(ctx, cx, cy, r - 1, 0x80000000);
        drawRing(ctx, cx, cy, r - 2, 0x40000000);
        int line = 0xE0101010;
        // толстые линии от края
        ctx.fill(cx - r, cy - 1, cx - r / 3, cy + 1, line);
        ctx.fill(cx + r / 3, cy - 1, cx + r, cy + 1, line);
        ctx.fill(cx - 1, cy + r / 3, cx + 1, cy + r, line);
        // тонкое перекрестие в центре
        ctx.fill(cx - r / 3, cy, cx + r / 3, cy + 1, line);
        ctx.fill(cx, cy - r / 3, cx + 1, cy + r / 3, line);
        // деления дальности
        int step = Math.max(4, (int) (r / (zoom * 3f)));
        for (int i = 1; i <= 4; i++) {
            ctx.fill(cx - 3 + i / 4, cy + i * step, cx + 4 - i / 4, cy + i * step + 1, line);
        }
        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFF2020);
        Text t = Text.literal(Math.round(zoom) + "x");
        ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, t, cx + r / 2, cy + r / 2, 0xFFAAAAAA);
    }

    private static void drawRing(DrawContext ctx, int cx, int cy, int r, int color) {
        int n = Math.max(24, r * 4);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int x = cx + (int) Math.round(Math.cos(a) * r);
            int y = cy + (int) Math.round(Math.sin(a) * r);
            ctx.fill(x, y, x + 1, y + 1, color);
        }
    }

    private Hud() {
    }
}
