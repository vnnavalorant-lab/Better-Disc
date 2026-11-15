package com.throwingdiscs;

public enum DiscTier {
    COPPER("copper", 5.0f, 1.25, 0xC77B4A),
    IRON("iron", 6.0f, 1.30, 0xDADADA),
    GOLD("gold", 5.0f, 1.60, 0xFFD83C),
    DIAMOND("diamond", 8.0f, 1.40, 0x3BEFD0),
    NETHERITE("netherite", 10.0f, 1.50, 0x6B4E71);

    public final String id;
    public final float damage;
    public final double speed;
    public final int color;

    DiscTier(String id, float damage, double speed, int color) {
        this.id = id;
        this.damage = damage;
        this.speed = speed;
        this.color = color;
    }

    public String itemName() {
        return id + "_throwing_disc";
    }
}
