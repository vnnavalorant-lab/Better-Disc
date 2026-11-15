package com.throwingdiscs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.throwingdiscs.client.ThrowAnimClient;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Pose dos membros durante o arremesso (terceira pessoa: você e outros jogadores). */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {
    @Shadow public ModelPart leftSleeve;
    @Shadow public ModelPart rightSleeve;
    @Shadow public ModelPart leftPants;
    @Shadow public ModelPart rightPants;
    @Shadow public ModelPart jacket;

    private PlayerEntityModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void throwingdiscs$applyThrow(T entity, float limbAngle, float limbDistance,
                                          float animationProgress, float headYaw, float headPitch,
                                          CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) return;
        float[] p = ThrowAnimClient.sample(entity.getId(), animationProgress);
        if (p == null) return;

        boolean right = ThrowAnimClient.isRightArm(entity.getId());
        float sT = right ? 1f : -1f; // lado do braço que arremessa
        float sO = -sT;
        float deg = (float) (Math.PI / 180.0);

        // o corpo inteiro gira (PlayerEntityRendererMixin); a cabeça compensa para continuar olhando para frente
        this.head.yaw -= p[0] * sT * deg;

        ModelPart tArm = right ? this.rightArm : this.leftArm;
        ModelPart oArm = right ? this.leftArm : this.rightArm;
        ModelPart tLeg = right ? this.rightLeg : this.leftLeg;
        ModelPart oLeg = right ? this.leftLeg : this.rightLeg;

        tArm.pitch = p[1];
        tArm.yaw = sT * p[2];
        tArm.roll = sT * p[3];

        oArm.pitch = p[4];
        oArm.yaw = sO * p[5];
        oArm.roll = sO * p[6];

        tLeg.pitch = p[7];
        tLeg.yaw = 0f;
        tLeg.roll = sT * p[9];
        oLeg.pitch = p[8];
        oLeg.yaw = 0f;
        oLeg.roll = sO * p[9];

        this.hat.copyTransform(this.head);
        this.jacket.copyTransform(this.body);
        this.leftSleeve.copyTransform(this.leftArm);
        this.rightSleeve.copyTransform(this.rightArm);
        this.leftPants.copyTransform(this.leftLeg);
        this.rightPants.copyTransform(this.rightLeg);
    }
}
