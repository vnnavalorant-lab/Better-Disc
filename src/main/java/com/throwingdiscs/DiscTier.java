package com.throwingdiscs;

public enum DiscTier {
    IRON("iron", 6.0f, 0.95, 0xDADADA, 120),
    GOLD("gold", 5.0f, 1.15, 0xFFD83C, 60),
    DIAMOND("diamond", 8.0f, 1.00, 0x3BEFD0, 320),
    NETHERITE("netherite", 10.0f, 1.10, 0x6B4E71, 600);

    public final String id;
    public final float damage;
    public final double speed;
    public final int color;
    /** Durabilidade: cada lançamento (jogador ou ejetor) gasta 1; ao zerar, o disco quebra. */
    public final int durability;

    DiscTier(String id, float damage, double speed, int color, int durability) {
        this.id = id;
        this.damage = damage;
        this.speed = speed;
        this.color = color;
        this.durability = durability;
    }

    public String itemName() {
        return id + "_throwing_disc";
    }
}
