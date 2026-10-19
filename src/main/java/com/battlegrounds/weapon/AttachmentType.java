package com.battlegrounds.weapon;

/**
 * Все обвесы и их влияние на оружие.
 * Множители < 1 уменьшают отдачу / время перезарядки.
 */
public enum AttachmentType {
    // прицелы: приближение, тип сетки, высота линии прицела над планкой (в пикселях модели)
    RED_DOT(AttachmentSlot.SCOPE, "red_dot"),
    HOLO(AttachmentSlot.SCOPE, "holo"),
    SCOPE_2X(AttachmentSlot.SCOPE, "scope_2x"),
    SCOPE_3X(AttachmentSlot.SCOPE, "scope_3x"),
    SCOPE_4X(AttachmentSlot.SCOPE, "scope_4x"),
    SCOPE_6X(AttachmentSlot.SCOPE, "scope_6x"),
    // дульные
    COMPENSATOR(AttachmentSlot.MUZZLE, "compensator"),
    FLASH_HIDER(AttachmentSlot.MUZZLE, "flash_hider"),
    SUPPRESSOR(AttachmentSlot.MUZZLE, "suppressor"),
    // рукоятки
    VERTICAL_GRIP(AttachmentSlot.GRIP, "vertical_grip"),
    ANGLED_GRIP(AttachmentSlot.GRIP, "angled_grip"),
    HALF_GRIP(AttachmentSlot.GRIP, "half_grip"),
    THUMB_GRIP(AttachmentSlot.GRIP, "thumb_grip"),
    LIGHT_GRIP(AttachmentSlot.GRIP, "light_grip"),
    // магазины
    EXTENDED_MAG(AttachmentSlot.MAGAZINE, "extended_mag"),
    QUICKDRAW_MAG(AttachmentSlot.MAGAZINE, "quickdraw_mag"),
    EXTENDED_QUICKDRAW_MAG(AttachmentSlot.MAGAZINE, "extended_quickdraw_mag"),
    // приклад
    TACTICAL_STOCK(AttachmentSlot.STOCK, "tactical_stock");

    public final AttachmentSlot slot;
    public final String id;

    AttachmentType(AttachmentSlot slot, String id) {
        this.slot = slot;
        this.id = id;
    }

    public static AttachmentType byId(String id) {
        for (AttachmentType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    public enum Reticle { NONE, DOT, HOLO, SCOPE }

    public float zoom() {
        return switch (this) {
            case RED_DOT, HOLO -> 1.35f;
            case SCOPE_2X -> 2f;
            case SCOPE_3X -> 3f;
            case SCOPE_4X -> 4f;
            case SCOPE_6X -> 6f;
            default -> 1f;
        };
    }

    public Reticle reticle() {
        return switch (this) {
            case RED_DOT -> Reticle.DOT;
            case HOLO -> Reticle.HOLO;
            case SCOPE_2X, SCOPE_3X, SCOPE_4X, SCOPE_6X -> Reticle.SCOPE;
            default -> Reticle.NONE;
        };
    }

    /** Высота линии прицеливания над верхом планки, пиксели модели. */
    public float sightHeight() {
        return switch (this) {
            case RED_DOT -> 1.6f;
            case HOLO -> 1.9f;
            case SCOPE_2X, SCOPE_3X -> 1.9f;
            case SCOPE_4X -> 1.8f;
            case SCOPE_6X -> 2.1f;
            default -> 0f;
        };
    }
}
