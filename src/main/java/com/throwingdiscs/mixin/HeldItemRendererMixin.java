package com.throwingdiscs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    /** Desenha o braço do jogador em 1ª pessoa (o mesmo usado quando a mão está vazia). */
    @Shadow
    protected abstract void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                 int light, float equipProgress, float swingProgress, Arm arm);

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void throwingdiscs$head(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                    float swingProgress, ItemStack item, float equipProgress,
                                    MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                    CallbackInfo ci) {
        ThrowAnimClient.firstPersonPushed = false;

        boolean handIsRight = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
        float side = handIsRight ? 1f : -1f;

        ThrowAnimClient.Pose p = ThrowAnimClient.sample(player, player.age + tickDelta);
        boolean throwing = p != null && handIsRight == ThrowAnimClient.isRightArm(player);
        boolean blocking = player.isUsingItem() && player.getActiveHand() == hand
                && item.getItem() instanceof SwordItem;
        if (!throwing && !blocking) return;

        matrices.push();
        ThrowAnimClient.firstPersonPushed = true;

        if (throwing) {
            // posição da mão = direção do braço da animação (Euler ZYX do Blockbench) + keyframes de posição
            float[] r = p.rot[ThrowAnimClient.R_ARM];
            float[] ps = p.pos[ThrowAnimClient.R_ARM];
            double rad = Math.PI / 180.0;
            double sx = Math.sin(r[0] * rad), cx = Math.cos(r[0] * rad);
            double sy = Math.sin(r[1] * rad), cy = Math.cos(r[1] * rad);
            double sz = Math.sin(r[2] * rad), cz = Math.cos(r[2] * rad);
            double dx = -sx * sy * cz + cx * sz;
            double dy = -sx * sy * sz - cx * cz;
            double dz = -sx * cy;
            float k = 0.55f;
            matrices.translate(
                    (float) (dx * k) * side + ps[0] / 16f * 2f * side,
                    (float) ((dy + 1.0) * k) + ps[1] / 16f * 2f,
                    (float) (dz * k) + ps[2] / 16f * 2f);
        }

        if (throwing) {
            // mostra o braço segurando o disco (o Minecraft não desenha o braço quando há item na mão)
            matrices.push();
            this.renderArmHoldingItem(matrices, vertexConsumers, light, equipProgress, 0.0f,
                    handIsRight ? Arm.RIGHT : Arm.LEFT);
            matrices.pop();
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
