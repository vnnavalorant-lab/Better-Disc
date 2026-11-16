package com.throwingdiscs.client;

import com.throwingdiscs.ThrowingDiscEntity;

import net.minecraft.util.math.Direction;

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
        int stuck = entity.getStuckSide();
        if (stuck >= 0) {
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
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.getSpinTicks() + tickDelta) * 55.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(12.0f));
        this.itemRenderer.renderItem(entity.getStack(), ModelTransformationMode.FIXED, light,
                OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, entity.getWorld(), entity.getId());
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(ThrowingDiscEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
