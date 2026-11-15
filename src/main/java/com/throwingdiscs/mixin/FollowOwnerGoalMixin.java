package com.throwingdiscs.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.throwingdiscs.WolfDiscGoal;

import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;

/**
 * Lobo domado envolvido com um disco do dono não segue/teletransporta até o dono (o FollowOwnerGoal vanilla
 * teleporta o lobo quando ele está a 12+ blocos do dono, o que atrapalhava a perseguição do disco).
 */
@Mixin(FollowOwnerGoal.class)
public abstract class FollowOwnerGoalMixin {
    @Shadow @Final private TameableEntity tameable;

    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void throwingdiscs$noStartWhileDisc(CallbackInfoReturnable<Boolean> cir) {
        if (tameable instanceof WolfEntity w && WolfDiscGoal.isBusy(w)) cir.setReturnValue(false);
    }

    @Inject(method = "shouldContinue", at = @At("HEAD"), cancellable = true)
    private void throwingdiscs$stopWhileDisc(CallbackInfoReturnable<Boolean> cir) {
        if (tameable instanceof WolfEntity w && WolfDiscGoal.isBusy(w)) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void throwingdiscs$noTeleportWhileDisc(CallbackInfo ci) {
        if (tameable instanceof WolfEntity w && WolfDiscGoal.isBusy(w)) ci.cancel();
    }
}
