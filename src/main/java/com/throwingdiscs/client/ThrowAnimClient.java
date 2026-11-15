package com.throwingdiscs.client;

import java.util.HashMap;
import java.util.Map;

import com.throwingdiscs.ThrowingDiscItem;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

/**
 * Animação de arremesso por cima do ombro (estilo do addon Actions & Stuff): braço levantado atrás da cabeça,
 * chicote para frente na soltura, follow-through com o braço descendo, outro braço de equilíbrio e um passo à frente.
 *
 * Valores dos keyframes (todos em radianos, exceto TWIST em graus). Convenção "positivo = para fora"
 * para yaw/roll; o espelhamento para canhotos/braço esquerdo é feito em {@link #sample}/mixins.
 *
 * Colunas: 0 TWIST | 1 T.pitch 2 T.yaw 3 T.roll | 4 O.pitch 5 O.yaw 6 O.roll | 7 Tleg.pitch 8 Oleg.pitch 9 SPREAD
 * T = braço que arremessa, O = outro braço.
 */
public final class ThrowAnimClient {
    public static final int DURATION = 28;
    public static final int N = 10;

    // {tempo(ticks), 10 valores}  -- estilo "Actions & Stuff": arremesso por cima do ombro
    // t4 = pose de carga (braço atrás/acima da cabeça), t7 = soltura (braço chicoteia à frente), t10+ = follow-through
    private static final float[][] KEYS = {
        {  0,   0f,   0f,     0f,    0f,    0f,    0f,   0f,   0f,    0f,   0f },
        {  4,  20f, -2.75f, 0.15f, 0.25f, -1.15f, -0.10f, 0f,  0.30f, -0.30f, 0.08f },
        {  7, -10f, -1.55f, -0.05f, 0.05f, -0.40f,  0.10f, 0.2f, 0.25f, -0.45f, 0.10f },
        { 10, -25f, -0.70f, -0.25f, 0f,   -0.20f,  0.10f, 0.2f, 0.30f, -0.50f, 0.10f },
        { 18, -25f, -0.70f, -0.25f, 0f,   -0.20f,  0.10f, 0.2f, 0.30f, -0.50f, 0.10f },
        { 28,   0f,   0f,     0f,    0f,    0f,    0f,   0f,   0f,    0f,   0f },
    };

    private record Anim(int startAge, boolean rightArm) {}

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    /** Controle de push/pop da matriz na primeira pessoa (ver HeldItemRendererMixin). */
    public static boolean firstPersonPushed = false;

    private ThrowAnimClient() {}

    public static void start(PlayerEntity player, boolean rightArm) {
        // a animação de soltar começa em t=4 (a pose de carga), sem salto visual
        ANIMS.put(player.getId(), new Anim(player.age - 4, rightArm));
    }

    public static void clear() {
        ANIMS.clear();
    }

    private static boolean isCharging(PlayerEntity p) {
        return p.isUsingItem() && p.getActiveItem().getItem() instanceof ThrowingDiscItem;
    }

    /** Pose do jogador: animação de soltar, ou a pose de carga enquanto segura o botão. null = nenhuma. */
    public static float[] sample(PlayerEntity p, float ageProgress) {
        float[] anim = sample(p.getId(), ageProgress);
        if (anim != null) return anim;
        if (!isCharging(p)) return null;
        float used = p.getActiveItem().getMaxUseTime() - p.getItemUseTimeLeft();
        float u = Math.max(0f, Math.min(1f, used / 6f));
        u = u * u * (3f - 2f * u);
        float[] out = new float[N];
        for (int j = 0; j < N; j++) out[j] = KEYS[1][j + 1] * u;
        return out;
    }

    public static boolean isRightArm(PlayerEntity p) {
        if (ANIMS.containsKey(p.getId())) return isRightArm(p.getId());
        if (isCharging(p)) {
            return (p.getActiveHand() == Hand.MAIN_HAND) == (p.getMainArm() == Arm.RIGHT);
        }
        return true;
    }

    public static boolean isRightArm(int entityId) {
        Anim a = ANIMS.get(entityId);
        return a == null || a.rightArm();
    }

    /** @return valores interpolados da pose, ou null se o jogador não está animando. */
    public static float[] sample(int entityId, float ageProgress) {
        Anim a = ANIMS.get(entityId);
        if (a == null) return null;
        float t = ageProgress - a.startAge();
        if (t > DURATION) {
            ANIMS.remove(entityId);
            return null;
        }
        if (t < 0) return null;

        int i = 0;
        while (i < KEYS.length - 2 && t > KEYS[i + 1][0]) i++;
        float[] k0 = KEYS[i];
        float[] k1 = KEYS[i + 1];
        float u = (t - k0[0]) / (k1[0] - k0[0]);
        u = Math.max(0f, Math.min(1f, u));
        u = u * u * (3f - 2f * u); // smoothstep

        float[] out = new float[N];
        for (int j = 0; j < N; j++) {
            out[j] = k0[j + 1] + (k1[j + 1] - k0[j + 1]) * u;
        }
        return out;
    }
}
