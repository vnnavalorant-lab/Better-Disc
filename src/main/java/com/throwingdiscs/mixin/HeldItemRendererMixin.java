package com.throwingdiscs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.throwingdiscs.ClashState;
import com.throwingdiscs.ThrowingDiscItem;
import com.throwingdiscs.client.ClashRender;
import com.throwingdiscs.client.ThrowAnimClient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * 1ª pessoa:
 *  - arremesso: braço + disco desenhados por nós (disco na ponta da mão, braço mais perto da câmera);
 *  - defesa com espada: pose 1.8 + (durante o clash) tremor e o disco girando/balançando na lâmina.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    /** Desenha o braço do jogador em 1ª pessoa (o mesmo usado quando a mão está vazia). */
    @Shadow
    protected abstract void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                 int light, float equipProgress, float swingProgress, Arm arm);

    /** Aproxima o braço da câmera no arremesso (antes ele ficava ~1,3 bloco à frente). Aumente para chegar mais perto. */
    private static final float THROW_PULL_Z = 0.50f;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void throwingdiscs$head(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                    float swingProgress, ItemStack item, float equipProgress,
                                    MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                    CallbackInfo ci) {
        ThrowAnimClient.firstPersonPushed = false;

        boolean handIsRight = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
        float side = handIsRight ? 1f : -1f;

        ThrowAnimClient.Pose p = ThrowAnimClient.sample(player, player.age + tickDelta);
        boolean throwing = p != null && handIsRight == ThrowAnimClient.isRightArm(player);

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

            matrices.push();
            matrices.translate(
                    (float) (dx * k) * side + ps[0] / 16f * 2f * side,
                    (float) ((dy + 1.0) * k) + ps[1] / 16f * 2f,
                    (float) (dz * 0.3) + ps[2] / 16f * 2f + THROW_PULL_Z);

            // O Minecraft não desenha o braço quando há item na mão, então desenhamos nós: braço vanilla (sem a pose
            // de arremesso no modelo) e, preso à ponta dele, o disco (ver PlayerEntityRendererMixin).
            ThrowAnimClient.firstPersonArm = true;
            ThrowAnimClient.armDisc = item.getItem() instanceof ThrowingDiscItem ? item : ItemStack.EMPTY;
            try {
                this.renderArmHoldingItem(matrices, vertexConsumers, light, equipProgress, 0.0f,
                        handIsRight ? Arm.RIGHT : Arm.LEFT);
            } finally {
                ThrowAnimClient.firstPersonArm = false;
                ThrowAnimClient.armDisc = ItemStack.EMPTY;
            }
            matrices.pop();
            ci.cancel();   // o disco já foi desenhado na mão; não desenha o item vanilla (ficava de lado e longe)
            return;
        }

        boolean blocking = player.isUsingItem() && player.getActiveHand() == hand
                && item.getItem() instanceof SwordItem;
        if (!blocking) return;

        matrices.push();
        ThrowAnimClient.firstPersonPushed = true;

        // pose de defesa da 1.8: espada inclinada à frente do peito, com entrada suave
        float e = Math.min(1f, (72000 - player.getItemUseTimeLeft() + tickDelta) / 4f);
        matrices.translate(side * -0.22f * e, 0.12f * e, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 40f * e));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30f * e));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -25f * e));

        ClashState.Info info = ClashState.get(player);
        if (info != null) {
            // disco preso: espada levemente erguida e tremendo (fraca = treme cada vez mais)
            float t = player.age + tickDelta;
            float s = ClashState.shake(info.weak, info.progress(tickDelta));
            matrices.translate(MathHelper.sin(t * 2.3f) * 0.012f * s, 0.05f + MathHelper.sin(t * 3.1f + 1f) * 0.012f * s, 0f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(t * 2.7f + 2f) * 3.5f * s));
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void throwingdiscs$tail(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                    float swingProgress, ItemStack item, float equipProgress,
                                    MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                    CallbackInfo ci) {
        if (!ThrowAnimClient.firstPersonPushed) return;

        ClashState.Info info = ClashState.get(player);
        if (info != null && !info.disc.isEmpty()) {
            boolean handIsRight = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
            float side = handIsRight ? 1f : -1f;

            // disco no meio da lâmina (mesma origem que o vanilla usa para a espada), perpendicular a ela
            matrices.push();
            matrices.translate(side * ClashState.FP_X, ClashState.FP_Y - equipProgress * 0.6f, ClashState.FP_Z);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-45.0f));
            ClashRender.wobble(matrices, player.age + tickDelta, info.weak, info.progress(tickDelta));
            MinecraftClient.getInstance().getItemRenderer().renderItem(info.disc, ModelTransformationMode.FIXED,
                    light, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, player.getWorld(), player.getId());
            matrices.pop();
        }

        matrices.pop();
        ThrowAnimClient.firstPersonPushed = false;
    }
}
