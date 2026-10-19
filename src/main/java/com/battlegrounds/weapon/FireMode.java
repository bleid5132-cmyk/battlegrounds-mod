package com.battlegrounds.weapon;

public enum FireMode {
    AUTO("hud.battlegrounds.mode.auto"),
    BURST("hud.battlegrounds.mode.burst"),
    SINGLE("hud.battlegrounds.mode.single");

    public final String langKey;

    FireMode(String langKey) {
        this.langKey = langKey;
    }
}
