package com.throwingdiscs.client;

import com.throwingdiscs.ModEntities;
import com.throwingdiscs.ModParticles;
import com.throwingdiscs.ThrowAnimNetworking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.WolfEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class ThrowingDiscsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.THROWING_DISC, ThrowingDiscRenderer::new);

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof WolfEntityRenderer wolfRenderer) {
                helper.register(new WolfDiscFeatureRenderer(wolfRenderer, ctx.getHeldItemRenderer()));
            }
        });

        ParticleFactoryRegistry.getInstance().register(ModParticles.WIND_STREAK, WindStreakParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.WIND_RING, WindRingParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.HONEY_DRIP, HoneyDripParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.HONEY_SPLAT, HoneySplatParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.HONEY_GLINT, HoneyGlintParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.SALIVA_DRIP, SalivaDripParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.SALIVA_SPLAT, SalivaSplatParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.DISC_SPARK, DiscSparkParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.DISC_FLASH, DiscFlashParticle.Factory::new);

        ClientPlayNetworking.registerGlobalReceiver(ThrowAnimNetworking.ID, (client, handler, buf, sender) -> {
            int id = buf.readInt();
            boolean rightArm = buf.readBoolean();
            client.execute(() -> {
                if (client.world == null) return;
                Entity e = client.world.getEntityById(id);
                if (e instanceof PlayerEntity player) {
                    ThrowAnimClient.start(player, rightArm);
                }
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ThrowAnimClient.clear();
            com.throwingdiscs.ClashState.clear();
        });
    }
}
