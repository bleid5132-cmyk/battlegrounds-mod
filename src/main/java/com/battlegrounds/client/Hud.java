package com.battlegrounds.client;

import com.battlegrounds.item.GunItem;
import com.battlegrounds.player.BgPlayer;
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
        Text mode = Text.translatable(gun.isAutoMode(stack) ? "hud.battlegrounds.mode.auto" : "hud.battlegrounds.mode.single");
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

    private Hud() {
    }
}
