package com.throwingdiscs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.throwingdiscs.client.ThrowAnimClient;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

/** Animação de arremesso em primeira pessoa: a mão/disco faz o movimento lateral cruzando a tela. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void throwingdiscs$head(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                    float swingProgress, ItemStack item, float equipProgress,
                                    MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                    CallbackInfo ci) {
        ThrowAnimClient.firstPersonPushed = false;
        float[] p = ThrowAnimClient.sample(player.getId(), player.age + tickDelta);
        if (p == null) return;

        boolean handIsRight = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
        if (handIsRight != ThrowAnimClient.isRightArm(player.getId())) return;

        float side = handIsRight ? 1f : -1f;
        float tw = p[0]; // graus: + = wind-up, - = follow-through

        float x = tw > 0 ? tw / 55f * 0.20f : tw / 50f * 0.95f;
        float y = tw > 0 ? tw / 55f * 0.18f : -tw / 50f * 0.05f;
        float z = -Math.abs(tw) / 55f * 0.25f;

        matrices.push();
        ThrowAnimClient.firstPersonPushed = true;
        matrices.translate(side * x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side * tw * 0.7f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * tw * 0.45f));
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void throwingdiscs$tail(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                    float swingProgress, ItemStack item, float equipProgress,
                                    MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                    CallbackInfo ci) {
        if (ThrowAnimClient.firstPersonPushed) {
            matrices.pop();
            ThrowAnimClient.firstPersonPushed = false;
        }
    }
}
