package com.throwingdiscs;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<ThrowingDiscEntity> THROWING_DISC = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(ThrowingDiscsMod.MOD_ID, "throwing_disc"),
            FabricEntityTypeBuilder.<ThrowingDiscEntity>create(SpawnGroup.MISC, ThrowingDiscEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.2f))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .fireImmune()
                    .build());

    private ModEntities() {}

    public static void init() {}
}
