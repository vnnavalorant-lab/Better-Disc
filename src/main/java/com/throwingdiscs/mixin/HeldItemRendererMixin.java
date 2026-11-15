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
import net.minecraft.item.SwordItem;
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

        boolean handIsRight = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
        float side = handIsRight ? 1f : -1f;

        float[] p = ThrowAnimClient.sample(player.getId(), player.age + tickDelta);
        boolean throwing = p != null && handIsRight == ThrowAnimClient.isRightArm(player.getId());
        boolean blocking = player.isUsingItem() && player.getActiveHand() == hand
                && item.getItem() instanceof SwordItem;
        if (!throwing && !blocking) return;

        matrices.push();
        ThrowAnimClient.firstPersonPushed = true;

        if (throwing) {
            float tw = p[0]; // graus: + = wind-up, - = follow-through
            float x = tw > 0 ? tw / 55f * 0.20f : tw / 50f * 0.95f;
            float y = tw > 0 ? tw / 55f * 0.18f : -tw / 50f * 0.05f;
            float z = -Math.abs(tw) / 55f * 0.25f;
            matrices.translate(side * x, y, z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side * tw * 0.7f));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * tw * 0.45f));
        }

        if (blocking) {
            // pose de defesa da 1.8: espada inclinada à frente do peito, com entrada suave
            float e = Math.min(1f, (72000 - player.getItemUseTimeLeft() + tickDelta) / 4f);
            matrices.translate(side * -0.22f * e, 0.12f * e, 0.0f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 40f * e));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30f * e));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -25f * e));
        }
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
