package com.battlegrounds.client.fx;

import com.battlegrounds.registry.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Звуки оружия на клиенте: по кадрам анимаций и выстрелы (ближний/дальний, с задержкой скорости звука). */
public final class GunSounds {
    private static final Random RND = new Random();

    /** Звук из анимации своего оружия — слышит только сам игрок, у его позиции. */
    public static void play(String name) {
        SoundEvent ev = switch (name) {
            case "mag_out" -> ModSounds.MAG_OUT;
            case "mag_in" -> ModSounds.MAG_IN;
            case "mag_tap" -> ModSounds.MAG_TAP;
            case "charge_pull" -> ModSounds.CHARGE_PULL;
            case "charge_release" -> ModSounds.CHARGE_RELEASE;
            case "gun_rattle" -> ModSounds.GUN_RATTLE;
            case "gun_draw" -> ModSounds.GUN_DRAW;
            case "gun_holster" -> ModSounds.GUN_HOLSTER;
            case "aim_in" -> ModSounds.AIM_IN;
            case "aim_out" -> ModSounds.AIM_OUT;
            default -> null;
        };
        if (ev == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        float vol = name.startsWith("aim") || name.equals("gun_rattle") ? 0.55f : 0.9f;
        mc.world.playSound(p.getX(), p.getEyeY() - 0.3, p.getZ(), ev, SoundCategory.PLAYERS, vol,
                0.97f + RND.nextFloat() * 0.06f, false);
    }

    // ------------------------------------------------------------ выстрелы
    private record Pending(long at, SoundEvent ev, Vec3d pos, float vol, float pitch) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final double FAR = 48;

    /** Звук выстрела у слушателя: вблизи — резкий с хвостом эха, вдали — глухой раскат, с задержкой по расстоянию. */
    public static void shot(Vec3d at, boolean own, boolean suppressed, boolean heavy) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        float pitch = 0.96f + RND.nextFloat() * 0.08f;
        if (own) {
            SoundEvent ev = suppressed ? ModSounds.SHOT_SUPPRESSED : heavy ? ModSounds.SHOT_762 : ModSounds.SHOT_556;
            mc.world.playSound(at.x, at.y, at.z, ev, SoundCategory.PLAYERS, 1.0f, pitch, false);
            return;
        }
        double d = mc.player.getEyePos().distanceTo(at);
        long delay = d > 16 ? (long) (d / 343.0 * 1000) : 0;         // 1 блок = 1 м, звук идёт 343 м/с
        long now = System.currentTimeMillis();
        if (suppressed) {
            if (d < 40) PENDING.add(new Pending(now + delay, ModSounds.SHOT_SUPPRESSED, at, 1.0f, pitch));
        } else if (d < FAR) {
            SoundEvent ev = heavy ? ModSounds.SHOT_762 : ModSounds.SHOT_556;
            PENDING.add(new Pending(now + delay, ev, at, 1.0f, pitch));
        } else {
            SoundEvent ev = heavy ? ModSounds.SHOT_FAR_762 : ModSounds.SHOT_FAR;
            // плавный переход: на границе ещё немного слышен ближний звук
            PENDING.add(new Pending(now + delay, ev, at, 1.0f, pitch));
        }
    }

    /** Вызывается каждый кадр: проигрывает отложенные звуки. */
    public static void update() {
        if (PENDING.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            PENDING.clear();
            return;
        }
        long now = System.currentTimeMillis();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.at() > now) continue;
            mc.world.playSound(p.pos().x, p.pos().y, p.pos().z, p.ev(), SoundCategory.PLAYERS, p.vol(), p.pitch(), false);
            it.remove();
        }
    }

    private GunSounds() {
    }
}
