package com.throwingdiscs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.throwingdiscs.WolfDiscGoal;
import com.throwingdiscs.WolfSaliva;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.world.World;

/** Lobos domados ganham o comportamento de buscar/agarrar o disco do dono (só soma uma meta de IA). */
@Mixin(WolfEntity.class)
public abstract class WolfEntityMixin extends TameableEntity {
    private WolfEntityMixin(EntityType<? extends TameableEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void throwingdiscs$discGoal(CallbackInfo ci) {
        this.goalSelector.add(3, new WolfDiscGoal((WolfEntity) (Object) this));
    }

    /** Baba (partículas do mod, só no cliente) quando o lobo está com o disco na boca. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void throwingdiscs$drool(CallbackInfo ci) {
        WolfSaliva.tick((WolfEntity) (Object) this);
    }
}
