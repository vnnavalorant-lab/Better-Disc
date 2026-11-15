package com.throwingdiscs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.throwingdiscs.ThrowingDiscEntity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * Defesa com espada estilo 1.8 (botão direito), mas SÓ quando há um disco de outro jogador por perto.
 * Sem disco por perto a espada se comporta de forma 100% vanilla.
 */
@Mixin(Item.class)
public abstract class ItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void throwingdiscs$swordUse(World world, PlayerEntity user, Hand hand,
                                        CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if ((Object) this instanceof SwordItem && ThrowingDiscEntity.hasThreat(world, user)) {
            user.setCurrentHand(hand);
            cir.setReturnValue(TypedActionResult.consume(user.getStackInHand(hand)));
        }
    }

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void throwingdiscs$swordAction(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
        if ((Object) this instanceof SwordItem) cir.setReturnValue(UseAction.BLOCK);
    }

    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void throwingdiscs$swordTime(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof SwordItem) cir.setReturnValue(72000);
    }

    /** Perdeu a ameaça (disco foi embora)? Para de defender depois de uma pequena carência. */
    @Inject(method = "usageTick", at = @At("HEAD"))
    private void throwingdiscs$swordTick(World world, LivingEntity user, ItemStack stack,
                                         int remainingUseTicks, CallbackInfo ci) {
        if ((Object) this instanceof SwordItem
                && 72000 - remainingUseTicks > 10
                && !ThrowingDiscEntity.hasThreat(world, user)) {
            user.stopUsingItem();
        }
    }
}
