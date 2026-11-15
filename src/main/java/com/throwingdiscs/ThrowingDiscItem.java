package com.throwingdiscs;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class ThrowingDiscItem extends Item {
    private final DiscTier tier;

    public ThrowingDiscItem(DiscTier tier, Settings settings) {
        super(settings);
        this.tier = tier;
    }

    public DiscTier getTier() {
        return tier;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        user.getItemCooldownManager().set(this, 20);
        user.incrementStat(Stats.USED.getOrCreateStat(this));

        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            boolean rightArm = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
            ThrowAnimNetworking.broadcast(player, rightArm);
            PendingThrows.add(player, hand, ThrowAnimNetworking.RELEASE_DELAY);
        }
        // CONSUME = sem o balanço de mão padrão do Minecraft (a animação própria faz isso)
        return TypedActionResult.consume(stack);
    }
}
