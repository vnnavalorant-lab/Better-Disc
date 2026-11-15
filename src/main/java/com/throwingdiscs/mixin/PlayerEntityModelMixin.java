package com.throwingdiscs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.throwingdiscs.ClashState;
import com.throwingdiscs.client.ThrowAnimClient;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

/**
 * Pose dos membros durante o arremesso (terceira pessoa: você e outros jogadores).
 *
 * Compatibilidade: só injeta em HEAD/TAIL (sem @Overwrite/@Redirect/cancel), só mexe nas peças durante o
 * arremesso, e devolve os pivots ao valor anterior no próximo frame para não vazar para outras animações.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {
    @Shadow public ModelPart leftSleeve;
    @Shadow public ModelPart rightSleeve;
    @Shadow public ModelPart leftPants;
    @Shadow public ModelPart rightPants;
    @Shadow public ModelPart jacket;

    @Unique private float[] throwingdiscs$saved;
    @Unique private boolean throwingdiscs$dirty;

    private PlayerEntityModelMixin(ModelPart root) {
        super(root);
    }

    @Unique
    private ModelPart[] throwingdiscs$parts() {
        return new ModelPart[] { this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg };
    }

    /** Restaura os pivots que alteramos no frame anterior (antes de qualquer outro código rodar). */
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void throwingdiscs$restore(T entity, float limbAngle, float limbDistance, float animationProgress,
                                       float headYaw, float headPitch, CallbackInfo ci) {
        if (!throwingdiscs$dirty || throwingdiscs$saved == null) return;
        ModelPart[] parts = throwingdiscs$parts();
        for (int i = 0; i < parts.length; i++) {
            parts[i].pivotX = throwingdiscs$saved[i * 3];
            parts[i].pivotY = throwingdiscs$saved[i * 3 + 1];
            parts[i].pivotZ = throwingdiscs$saved[i * 3 + 2];
        }
        throwingdiscs$dirty = false;
    }

    @Unique
    private static void throwingdiscs$apply(ModelPart part, float[] rot, float[] pos, float m, boolean rotate) {
        float deg = (float) (Math.PI / 180.0);
        if (rotate) {
            part.pitch = -rot[0] * deg;
            part.yaw = -rot[1] * m * deg;
            part.roll = rot[2] * m * deg;
        }
        part.pivotX -= pos[0] * m;
        part.pivotY -= pos[1];
        part.pivotZ += pos[2];
    }

    /**
     * Defesa com o disco preso na lâmina: braço da espada levantado (lâmina quase na vertical, como no vídeo de
     * referência) e tremendo. Só o tremor muda com o desfecho: espada fraca vibra cada vez mais.
     */
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void throwingdiscs$applyClash(T entity, float limbAngle, float limbDistance,
                                          float animationProgress, float headYaw, float headPitch,
                                          CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player) || ThrowAnimClient.firstPersonArm) return;
        ClashState.Info info = ClashState.get(player);
        if (info == null || !player.isUsingItem()) return;

        boolean right = (player.getActiveHand() == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
        ModelPart arm = right ? this.rightArm : this.leftArm;
        ModelPart sleeve = right ? this.rightSleeve : this.leftSleeve;
        float side = right ? 1f : -1f;
        float p = info.progress(animationProgress - player.age);
        float s = ClashState.shake(info.weak, p);
        float t = animationProgress;

        arm.pitch = -1.25f + MathHelper.sin(t * 2.1f) * 0.06f * s;
        arm.yaw = side * -0.30f + MathHelper.sin(t * 2.9f + 1f) * 0.08f * s;
        arm.roll = MathHelper.sin(t * 3.3f + 2f) * 0.07f * s;
        sleeve.copyTransform(arm);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void throwingdiscs$applyThrow(T entity, float limbAngle, float limbDistance,
                                          float animationProgress, float headYaw, float headPitch,
                                          CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player)) return;
        if (ThrowAnimClient.firstPersonArm) return;   // braço de 1ª pessoa: pose vanilla (a posição vem do HeldItemRendererMixin)
        ThrowAnimClient.Pose pose = ThrowAnimClient.sample(player, animationProgress);
        if (pose == null) return;

        boolean right = ThrowAnimClient.isRightArm(player);
        float m = right ? 1f : -1f;

        ModelPart[] parts = throwingdiscs$parts();
        if (throwingdiscs$saved == null) throwingdiscs$saved = new float[parts.length * 3];
        for (int i = 0; i < parts.length; i++) {
            throwingdiscs$saved[i * 3] = parts[i].pivotX;
            throwingdiscs$saved[i * 3 + 1] = parts[i].pivotY;
            throwingdiscs$saved[i * 3 + 2] = parts[i].pivotZ;
        }
        throwingdiscs$dirty = true;

        // canhoto: troca os lados (dados do braço/perna direita vão para a esquerda) e espelha
        ModelPart rArm = right ? this.rightArm : this.leftArm;
        ModelPart lArm = right ? this.leftArm : this.rightArm;
        ModelPart rLeg = right ? this.rightLeg : this.leftLeg;
        ModelPart lLeg = right ? this.leftLeg : this.rightLeg;

        throwingdiscs$apply(this.head, pose.rot[ThrowAnimClient.HEAD], pose.pos[ThrowAnimClient.HEAD], m, false); // cabeça segue o olhar
        throwingdiscs$apply(this.body, pose.rot[ThrowAnimClient.BODY], pose.pos[ThrowAnimClient.BODY], m, true);
        throwingdiscs$apply(rArm, pose.rot[ThrowAnimClient.R_ARM], pose.pos[ThrowAnimClient.R_ARM], m, true);
        throwingdiscs$apply(lArm, pose.rot[ThrowAnimClient.L_ARM], pose.pos[ThrowAnimClient.L_ARM], m, true);
        throwingdiscs$apply(rLeg, pose.rot[ThrowAnimClient.R_LEG], pose.pos[ThrowAnimClient.R_LEG], m, true);
        throwingdiscs$apply(lLeg, pose.rot[ThrowAnimClient.L_LEG], pose.pos[ThrowAnimClient.L_LEG], m, true);

        this.hat.copyTransform(this.head);
        this.jacket.copyTransform(this.body);
        this.leftSleeve.copyTransform(this.leftArm);
        this.rightSleeve.copyTransform(this.rightArm);
        this.leftPants.copyTransform(this.leftLeg);
        this.rightPants.copyTransform(this.rightLeg);
    }
}
