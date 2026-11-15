package com.throwingdiscs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.throwingdiscs.client.ThrowAnimClient;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** Giro do corpo inteiro durante o arremesso (terceira pessoa). */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",
            at = @At("TAIL"))
    private void throwingdiscs$twist(AbstractClientPlayerEntity player, MatrixStack matrices,
                                     float animationProgress, float bodyYaw, float tickDelta,
                                     CallbackInfo ci) {
        float[] p = ThrowAnimClient.sample(player, animationProgress);
        if (p == null) return;
        float s = ThrowAnimClient.isRightArm(player) ? 1f : -1f;
        // + = virar para a direita (equivale a somar ao bodyYaw)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-p[0] * s));
    }
}
