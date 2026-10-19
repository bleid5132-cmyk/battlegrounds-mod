package com.battlegrounds.weapon;

public enum AttachmentSlot {
    SCOPE("scope"),
    MUZZLE("muzzle"),
    GRIP("grip"),
    MAGAZINE("magazine"),
    STOCK("stock");

    public final String key;

    AttachmentSlot(String key) {
        this.key = key;
    }
}
