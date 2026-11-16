package com.throwingdiscs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.throwingdiscs.ThrowingDiscItem;
import com.throwingdiscs.client.ThrowAnimClient;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Shadow
    protected abstract void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                 int light, float equipProgress, float swingProgress, Arm arm);

    private static final float THROW_PULL_Z = 0.50f;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void throwingdiscs$head(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                    float swingProgress, ItemStack item, float equipProgress,
                                    MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                    CallbackInfo ci) {
        boolean handIsRight = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
        float side = handIsRight ? 1f : -1f;

        ThrowAnimClient.Pose p = ThrowAnimClient.sample(player, player.age + tickDelta);
        boolean throwing = p != null && handIsRight == ThrowAnimClient.isRightArm(player);

        if (throwing) {
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
            ci.cancel();
        }
    }
}
