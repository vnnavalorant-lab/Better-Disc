package com.throwingdiscs;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModParticles {
    /** Risco de vento alinhado ao trajeto (ar sendo cortado). */
    public static final DefaultParticleType WIND_STREAK = FabricParticleTypes.simple();
    /** Arcos de vento deitados no plano do disco, girando e se expandindo. */
    public static final DefaultParticleType WIND_RING = FabricParticleTypes.simple();

    private ModParticles() {}

    public static void init() {
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(ThrowingDiscsMod.MOD_ID, "wind_streak"), WIND_STREAK);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(ThrowingDiscsMod.MOD_ID, "wind_ring"), WIND_RING);
    }
}
