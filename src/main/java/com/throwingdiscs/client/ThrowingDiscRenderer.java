package com.throwingdiscs.client;

import com.throwingdiscs.ClashState;
import com.throwingdiscs.ThrowingDiscEntity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class ThrowingDiscRenderer extends EntityRenderer<ThrowingDiscEntity> {
    private final ItemRenderer itemRenderer;

    public ThrowingDiscRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.shadowRadius = 0.1f;
    }

    @Override
    public void render(ThrowingDiscEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        int clashTarget = entity.getClashTargetId();
        if (clashTarget >= 0 && renderOnSword(entity, clashTarget, tickDelta, matrices, vertexConsumers, light)) {
            return;
        }
        int stuck = entity.getStuckSide();
        if (stuck >= 0) {
            // grudado no mel: parado, deitado na face do bloco
            Direction face = Direction.byId(stuck);
            matrices.push();
            if (face.getAxis() == Direction.Axis.Y) {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
            } else if (face.getAxis() == Direction.Axis.X) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f));
            }
            this.itemRenderer.renderItem(entity.getStack(), ModelTransformationMode.FIXED, light,
                    OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, entity.getWorld(), entity.getId());
            matrices.pop();
            return;
        }
        matrices.push();
        matrices.translate(0.0, 0.1, 0.0);
        // gira como um frisbee, levemente inclinado
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.getSpinTicks() + tickDelta) * 55.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(12.0f));
        matrices.scale(1.0f, 1.0f, 1.0f); // FIXED(0.5) * 1.0 = 0.5 blocos (menor que o item em 1ª pessoa)
        this.itemRenderer.renderItem(entity.getStack(), ModelTransformationMode.FIXED, light,
                OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, entity.getWorld(), entity.getId());
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /**
     * Disco preso na espada de quem defende: segue o corpo/mão do defensor (calculado no cliente, sem o atraso do
     * pacote de posição), gira deitado ao redor da lâmina e balança. Em 1ª pessoa do próprio defensor o disco é
     * desenhado na mão (HeldItemRendererMixin), então aqui não desenha.
     * @return true se tratou o desenho
     */
    private boolean renderOnSword(ThrowingDiscEntity disc, int targetId, float tickDelta, MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers, int light) {
        Entity t = disc.getWorld().getEntityById(targetId);
        if (!(t instanceof LivingEntity target)) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (t == mc.player && mc.options.getPerspective().isFirstPerson()) return true;

        ClashState.Info info = ClashState.get(target);
        float p = info != null ? info.progress(tickDelta) : 0f;

        Hand hand = target.isUsingItem() ? target.getActiveHand() : Hand.MAIN_HAND;
        float side = (hand == Hand.MAIN_HAND) == (target.getMainArm() == Arm.RIGHT) ? 1f : -1f;
        double yaw = Math.toRadians(MathHelper.lerp(tickDelta, target.prevBodyYaw, target.bodyYaw));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);       // frente do corpo
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);      // direita do corpo

        double tx = MathHelper.lerp(tickDelta, target.lastRenderX, target.getX());
        double ty = MathHelper.lerp(tickDelta, target.lastRenderY, target.getY());
        double tz = MathHelper.lerp(tickDelta, target.lastRenderZ, target.getZ());
        double ex = MathHelper.lerp(tickDelta, disc.lastRenderX, disc.getX());
        double ey = MathHelper.lerp(tickDelta, disc.lastRenderY, disc.getY());
        double ez = MathHelper.lerp(tickDelta, disc.lastRenderZ, disc.getZ());

        double h = ClashState.TP_HEIGHT * (target.getHeight() / 1.8);
        double wx = tx + fx * ClashState.TP_FORWARD + rx * side * ClashState.TP_SIDE;
        double wy = ty + h;
        double wz = tz + fz * ClashState.TP_FORWARD + rz * side * ClashState.TP_SIDE;

        matrices.push();
        matrices.translate(wx - ex, wy - ey, wz - ez);
        ClashRender.wobble(matrices, disc.getSpinTicks() + tickDelta, disc.isClashWeak(), p);
        this.itemRenderer.renderItem(disc.getStack(), ModelTransformationMode.FIXED, light,
                OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, disc.getWorld(), disc.getId());
        matrices.pop();
        return true;
    }

    @Override
    public Identifier getTexture(ThrowingDiscEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
