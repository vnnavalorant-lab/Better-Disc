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

    /** Gota de mel: forma-se na borda do disco melado, estica, cai e vira poça. */
    public static final DefaultParticleType HONEY_DRIP = FabricParticleTypes.simple();
    /** Poça de mel deitada no chão onde a gota caiu. */
    public static final DefaultParticleType HONEY_SPLAT = FabricParticleTypes.simple();
    /** Brilho dourado que cintila na superfície do disco melado. */
    public static final DefaultParticleType HONEY_GLINT = FabricParticleTypes.simple();

    /** Gota de saliva do lobo com o disco na boca: fio que estica, solta e cai. */
    public static final DefaultParticleType SALIVA_DRIP = FabricParticleTypes.simple();
    /** Ondinha de saliva onde a gota caiu. */
    public static final DefaultParticleType SALIVA_SPLAT = FabricParticleTypes.simple();
    /** Faísca quente (risco brilhante que esfria de branco para vermelho) do choque entre dois discos. */
    public static final DefaultParticleType DISC_SPARK = FabricParticleTypes.simple();
    /** Clarão em estrela no ponto exato do choque entre dois discos. */
    public static final DefaultParticleType DISC_FLASH = FabricParticleTypes.simple();

    private ModParticles() {}

    public static void init() {
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(ThrowingDiscsMod.MOD_ID, "wind_streak"), WIND_STREAK);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(ThrowingDiscsMod.MOD_ID, "wind_ring"), WIND_RING);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(ThrowingDiscsMod.MOD_ID, "honey_drip"), HONEY_DRIP);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(ThrowingDiscsMod.MOD_ID, "honey_splat"), HONEY_SPLAT);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(ThrowingDiscsMod.MOD_ID, "honey_glint"), HONEY_GLINT);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(ThrowingDiscsMod.MOD_ID, "saliva_drip"), SALIVA_DRIP);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(ThrowingDiscsMod.MOD_ID, "saliva_splat"), SALIVA_SPLAT);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(ThrowingDiscsMod.MOD_ID, "disc_spark"), DISC_SPARK);
        Registry.register(Registries.PARTICLE_TYPE, new Identifier(ThrowingDiscsMod.MOD_ID, "disc_flash"), DISC_FLASH);
    }
}
