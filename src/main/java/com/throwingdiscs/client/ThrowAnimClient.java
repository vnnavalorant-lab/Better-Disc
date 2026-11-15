package com.throwingdiscs.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.PlayerEntity;

/**
 * Animação de arremesso (baseada no vídeo): wind-up girando o corpo, varredura lateral,
 * follow-through com o braço cruzando o peito, outro braço atrás e pernas em avanço.
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

    // {tempo(ticks), 10 valores}
    private static final float[][] KEYS = {
        {  0,   0f,   0f,    0f,   0f,    0f,    0f,   0f,   0f,    0f,   0f },
        {  4,  55f, -1.40f, 1.10f, 0f,  -1.20f, -0.90f, 0f,  0.35f, -0.35f, 0.12f },
        {  7,  -5f, -1.55f, 0.10f, 0f,  -0.60f,  0.40f, 0.2f, 0.00f, -0.20f, 0.20f },
        { 10, -50f, -1.30f, -1.00f, 0f, -2.50f,  0.50f, 0.3f, 0.50f, -0.55f, 0.22f },
        { 20, -50f, -1.30f, -1.00f, 0f, -2.50f,  0.50f, 0.3f, 0.50f, -0.55f, 0.22f },
        { 28,   0f,   0f,    0f,   0f,    0f,    0f,   0f,   0f,    0f,   0f },
    };

    private record Anim(int startAge, boolean rightArm) {}

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    /** Controle de push/pop da matriz na primeira pessoa (ver HeldItemRendererMixin). */
    public static boolean firstPersonPushed = false;

    private ThrowAnimClient() {}

    public static void start(PlayerEntity player, boolean rightArm) {
        ANIMS.put(player.getId(), new Anim(player.age, rightArm));
    }

    public static void clear() {
        ANIMS.clear();
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
