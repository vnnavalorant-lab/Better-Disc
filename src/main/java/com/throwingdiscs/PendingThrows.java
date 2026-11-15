package com.throwingdiscs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/** Atrasa a soltura do disco para coincidir com o momento certo da animação. */
public final class PendingThrows {
    private static final class Pending {
        final UUID player;
        final Hand hand;
        int ticks;

        Pending(UUID player, Hand hand, int ticks) {
            this.player = player;
            this.hand = hand;
            this.ticks = ticks;
        }
    }

    private static final List<Pending> LIST = new ArrayList<>();

    private PendingThrows() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Pending> it = LIST.iterator();
            while (it.hasNext()) {
                Pending p = it.next();
                if (--p.ticks > 0) continue;
                it.remove();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(p.player);
                if (player == null || !player.isAlive()) continue;
                release(player, p.hand);
            }
        });
    }

    public static void add(ServerPlayerEntity player, Hand hand, int delay) {
        LIST.add(new Pending(player.getUuid(), hand, delay));
    }

    private static void release(ServerPlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (!(held.getItem() instanceof ThrowingDiscItem disc)) return;

        ServerWorld world = player.getServerWorld();
        boolean creative = player.getAbilities().creativeMode;
        ItemStack single = held.copyWithCount(1);

        ThrowingDiscEntity entity = new ThrowingDiscEntity(world, player, single, !creative);
        Vec3d look = player.getRotationVec(1.0f);
        entity.setPosition(player.getEyePos().add(0, -0.25, 0).add(look.multiply(0.4)));
        entity.setVelocity(look.multiply(disc.getTier().speed));
        world.spawnEntity(entity);

        if (!creative) held.decrement(1);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.3f);
    }
}
