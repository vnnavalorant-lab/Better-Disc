package com.throwingdiscs.client;

import com.throwingdiscs.ThrowingDiscItem;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.WolfEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/** Disco na boca do lobo (igual à raposa com item na boca). */
public class WolfDiscFeatureRenderer extends FeatureRenderer<WolfEntity, WolfEntityModel<WolfEntity>> {
    private final HeldItemRenderer heldItemRenderer;

    public WolfDiscFeatureRenderer(FeatureRendererContext<WolfEntity, WolfEntityModel<WolfEntity>> context,
                                   HeldItemRenderer heldItemRenderer) {
        super(context);
        this.heldItemRenderer = heldItemRenderer;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, WolfEntity wolf,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        ItemStack stack = wolf.getEquippedStack(EquipmentSlot.MAINHAND);
        if (!(stack.getItem() instanceof ThrowingDiscItem)) return;

        matrices.push();
        // pivô da cabeça do lobo (px do modelo) e, a partir dele, a boca
        matrices.translate(-1.0f / 16f, 13.5f / 16f, -7.0f / 16f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(headYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(headPitch));
        matrices.translate(0.0625f, 0.10f, -0.30f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        matrices.scale(0.7f, 0.7f, 0.7f);
        this.heldItemRenderer.renderItem(wolf, stack, ModelTransformationMode.GROUND, false, matrices,
                vertexConsumers, light);
        matrices.pop();
    }
}
