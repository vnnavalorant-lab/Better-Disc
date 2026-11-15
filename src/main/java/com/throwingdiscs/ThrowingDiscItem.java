package com.throwingdiscs;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** Segure o botão direito para carregar (mesmo tempo do arco: 20 ticks = força máxima) e solte para arremessar. */
public class ThrowingDiscItem extends Item {
    private final DiscTier tier;

    public ThrowingDiscItem(DiscTier tier, Settings settings) {
        super(settings);
        this.tier = tier;
    }

    public DiscTier getTier() {
        return tier;
    }

    /** Reparo na bigorna com o material do disco (ferro, ouro, diamante ou netherite). */
    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return switch (tier) {
            case IRON -> ingredient.isOf(Items.IRON_INGOT);
            case GOLD -> ingredient.isOf(Items.GOLD_INGOT);
            case DIAMOND -> ingredient.isOf(Items.DIAMOND);
            case NETHERITE -> ingredient.isOf(Items.NETHERITE_INGOT);
        };
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE; // a pose de carregar é feita pela animação própria do mod
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return;
        int used = getMaxUseTime(stack) - remainingUseTicks;
        float power = BowItem.getPullProgress(used);   // idêntico ao arco
        if (power < 0.1f) return;

        player.getItemCooldownManager().set(this, 4);
        player.incrementStat(Stats.USED.getOrCreateStat(this));

        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            Hand hand = sp.getActiveHand();
            boolean rightArm = (hand == Hand.MAIN_HAND) == (sp.getMainArm() == Arm.RIGHT);
            ThrowAnimNetworking.broadcast(sp, rightArm);
            PendingThrows.add(sp, hand, ThrowAnimNetworking.RELEASE_DELAY, power);
        }
    }
}
