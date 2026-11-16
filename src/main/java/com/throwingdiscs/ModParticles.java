package com.throwingdiscs;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModParticles {
    public static final DefaultParticleType WIND_STREAK = FabricParticleTypes.simple();
    public static final DefaultParticleType WIND_RING = FabricParticleTypes.simple();

    public static final DefaultParticleType HONEY_DRIP = FabricParticleTypes.simple();
    public static final DefaultParticleType HONEY_SPLAT = FabricParticleTypes.simple();
    public static final DefaultParticleType HONEY_GLINT = FabricParticleTypes.simple();

    public static final DefaultParticleType SALIVA_DRIP = FabricParticleTypes.simple();
    public static final DefaultParticleType SALIVA_SPLAT = FabricParticleTypes.simple();
    public static final DefaultParticleType DISC_SPARK = FabricParticleTypes.simple();
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
