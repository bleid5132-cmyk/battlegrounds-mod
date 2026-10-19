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

    private Packets() {
    }
}
