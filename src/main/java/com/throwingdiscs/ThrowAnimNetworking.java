package com.throwingdiscs;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ThrowAnimNetworking {
    public static final Identifier ID = new Identifier(ThrowingDiscsMod.MOD_ID, "throw_anim");
    /** Ticks entre o fim do carregamento e a soltura (a animação de soltar continua da pose de carga em 0.375s; o disco sai ~0.78s no clipe, com o braço varrendo à frente). */
    public static final int RELEASE_DELAY = 8;

    private ThrowAnimNetworking() {}

    private static PacketByteBuf buf(ServerPlayerEntity player, boolean rightArm) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(player.getId());
        buf.writeBoolean(rightArm);
        return buf;
    }

    /** Avisa quem está vendo o jogador (e ele mesmo) para tocar a animação de arremesso. */
    public static void broadcast(ServerPlayerEntity player, boolean rightArm) {
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            ServerPlayNetworking.send(other, ID, buf(player, rightArm));
        }
        ServerPlayNetworking.send(player, ID, buf(player, rightArm));
    }
}
