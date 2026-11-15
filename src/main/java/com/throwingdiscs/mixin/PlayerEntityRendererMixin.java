package com.throwingdiscs.mixin;

import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.throwingdiscs.client.ThrowAnimClient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Desenha o disco NA PONTA DA MÃO em 1ª pessoa. Roda no fim de renderRightArm/renderLeftArm, quando a matriz
 * ainda está no espaço do braço, então o disco acompanha o braço exatamente (sem depender da transformação de item
 * do vanilla, que deixava o disco de lado e longe da mão).
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin
        extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    /** Ponto do disco no espaço do braço, em pixels do modelo (y = 10 é a ponta da mão; z negativo = palma). */
    @Unique private static final float DISC_Y = 9.0f, DISC_Z = -3.5f;
    /** Inclinação do disco para a câmera (graus), para não ficar de lado/fino. */
    @Unique private static final float DISC_TILT = 35.0f;

    private PlayerEntityRendererMixin(EntityRendererFactory.Context ctx,
                                      PlayerEntityModel<AbstractClientPlayerEntity> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "renderRightArm", at = @At("TAIL"))
    private void throwingdiscs$rightArmDisc(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                            AbstractClientPlayerEntity player, CallbackInfo ci) {
        throwingdiscs$disc(matrices, vertexConsumers, light, player, true);
    }

    @Inject(method = "renderLeftArm", at = @At("TAIL"))
    private void throwingdiscs$leftArmDisc(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                           AbstractClientPlayerEntity player, CallbackInfo ci) {
        throwingdiscs$disc(matrices, vertexConsumers, light, player, false);
    }

    @Unique
    private void throwingdiscs$disc(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                    AbstractClientPlayerEntity player, boolean right) {
        ItemStack stack = ThrowAnimClient.armDisc;
        if (stack == null || stack.isEmpty()) return;

        PlayerEntityModel<AbstractClientPlayerEntity> model = this.getModel();
        ModelPart arm = right ? model.rightArm : model.leftArm;
        boolean slim = "slim".equals(player.getModel());
        float cx = (right ? -1f : 1f) * (slim ? 0.5f : 1.0f);   // centro do braço em x (px) em relação ao pivô

        matrices.push();
        matrices.translate((arm.pivotX + cx) / 16f, (arm.pivotY + DISC_Y) / 16f, (arm.pivotZ + DISC_Z) / 16f);
        // desfaz a rotação do braço: a partir daqui os eixos são os da câmera (X direita, Y cima, -Z frente)
        Quaternionf inv = new Quaternionf().setFromUnnormalized(matrices.peek().getNormalMatrix()).invert();
        matrices.multiply(inv);
        // deitado na palma, inclinado para a câmera
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(DISC_TILT));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        MinecraftClient.getInstance().getItemRenderer().renderItem(stack, ModelTransformationMode.FIXED, light,
                OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, player.getWorld(), player.getId());
        matrices.pop();
    }
}
