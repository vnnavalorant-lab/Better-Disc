package com.throwingdiscs;

import net.fabricmc.api.ModInitializer;

public class ThrowingDiscsMod implements ModInitializer {
    public static final String MOD_ID = "throwingdiscs";

    @Override
    public void onInitialize() {
        ModItems.init();
        ModEntities.init();
        ModParticles.init();
        PendingThrows.init();
    }
}
