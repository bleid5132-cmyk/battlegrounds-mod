package com.battlegrounds.weapon;

public enum AmmoType {
    A556("5.56"),
    A762("7.62"),
    A9MM("9mm"),
    A45("45 ACP"),
    G12("12 Gauge"),
    A300("300 Magnum");

    public final String label;

    AmmoType(String label) {
        this.label = label;
    }
}
