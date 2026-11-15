package com.throwingdiscs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.throwingdiscs.DiscDispenser;
import com.throwingdiscs.ThrowingDiscItem;

import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPointer;

/**
 * O ejetor (e o dispensador, que usa o mesmo comportamento padrão) lança o disco em linha reta em vez de só largá-lo.
 * Só mexe no comportamento PADRÃO (classe exata ItemDispenserBehavior) e só para os discos; o resto é vanilla.
 */
@Mixin(ItemDispenserBehavior.class)
public abstract class DispenserBehaviorMixin {
    @Inject(method = "dispenseSilently", at = @At("HEAD"), cancellable = true)
    private void throwingdiscs$launch(BlockPointer pointer, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (stack.getItem() instanceof ThrowingDiscItem && ((Object) this).getClass() == ItemDispenserBehavior.class) {
            cir.setReturnValue(DiscDispenser.launch(pointer, stack));
        }
    }
}
