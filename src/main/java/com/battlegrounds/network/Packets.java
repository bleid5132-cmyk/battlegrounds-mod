package com.battlegrounds.network;

import com.battlegrounds.Battlegrounds;
import net.minecraft.util.Identifier;

public final class Packets {
    public static final Identifier FIRE = Battlegrounds.id("fire");
    public static final Identifier RELOAD = Battlegrounds.id("reload");
    public static final Identifier FIRE_MODE = Battlegrounds.id("fire_mode");
    public static final Identifier STANCE = Battlegrounds.id("stance");
    public static final Identifier LEAN = Battlegrounds.id("lean");
    public static final Identifier MANTLE = Battlegrounds.id("mantle");
    public static final Identifier AIM = Battlegrounds.id("aim");
    public static final Identifier OPEN_ATTACHMENTS = Battlegrounds.id("open_attachments");
    /** Сервер → клиенты: эффекты выстрела (трассер, вспышка, попадание). */
    public static final Identifier SHOT_FX = Battlegrounds.id("shot_fx");

    private Packets() {
    }
}
