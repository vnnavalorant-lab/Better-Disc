package com.throwingdiscs.client;

import com.throwingdiscs.ModEntities;
import com.throwingdiscs.ThrowAnimNetworking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class ThrowingDiscsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.THROWING_DISC, ThrowingDiscRenderer::new);

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

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ThrowAnimClient.clear());
    }
}
