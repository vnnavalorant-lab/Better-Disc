package com.throwingdiscs.client;

import java.util.HashMap;
import java.util.Map;

import com.throwingdiscs.ThrowingDiscItem;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

/**
 * Animação de arremesso importada de animation_disc_player.bbmodel (animação "attack", 1.125s).
 * Os keyframes abaixo são os do Blockbench (graus / pixels, convenção Bedrock, interpolação catmull-rom).
 * Conversão para ModelPart do Java feita nos mixins: pitch = -x, yaw = -y, roll = z; pivotX -= x, pivotY -= y, pivotZ += z.
 *
 * Carga (segurar botão direito): toca o começo da animação até 0.375s e segura essa pose.
 * Soltar: continua dali até o fim.
 */
public final class ThrowAnimClient {
    public static final float CHARGE_TIME = 0.375f;           // segundos (pose de carga)
    public static final float DURATION_TICKS = 1.125f * 20f;  // 22.5 ticks

    public static final int HEAD = 0, BODY = 1, R_ARM = 2, L_ARM = 3, R_LEG = 4, L_LEG = 5;

    /** Pose amostrada (convenção do arremesso com a mão DIREITA; espelhar para canhotos). */
    public static final class Pose {
        public final float[][] rot = new float[6][3];
        public final float[][] pos = new float[6][3];
    }

    private record Track(float[] t, float[][] v) {
        void at(float time, float[] out) {
            int n = t.length;
            if (time <= t[0]) { System.arraycopy(v[0], 0, out, 0, 3); return; }
            if (time >= t[n - 1]) { System.arraycopy(v[n - 1], 0, out, 0, 3); return; }
            int i = 0;
            while (i < n - 2 && time >= t[i + 1]) i++;
            float u = (time - t[i]) / (t[i + 1] - t[i]);
            float[] p0 = v[Math.max(i - 1, 0)], p1 = v[i], p2 = v[i + 1], p3 = v[Math.min(i + 2, n - 1)];
            float u2 = u * u, u3 = u2 * u;
            for (int c = 0; c < 3; c++) {
                out[c] = 0.5f * ((2f * p1[c]) + (-p0[c] + p2[c]) * u
                        + (2f * p0[c] - 5f * p1[c] + 4f * p2[c] - p3[c]) * u2
                        + (-p0[c] + 3f * p1[c] - 3f * p2[c] + p3[c]) * u3);
            }
        }
    }

    private static Track tr(float[] t, float[]... v) { return new Track(t, v); }
    private static float[] v3(float x, float y, float z) { return new float[] { x, y, z }; }

    private static final Track[] ROT = new Track[6];
    private static final Track[] POS = new Track[6];

    static {
        POS[HEAD] = tr(new float[] {0, .25f, .5f, .875f, 1.125f},
                v3(0,0,0), v3(0,0,0), v3(-1,0,-2), v3(-1,0,-2), v3(0,0,0));

        ROT[BODY] = tr(new float[] {0, .25f, .375f, .75f, 1.04167f, 1.125f},
                v3(0,0,0), v3(0,20,0), v3(-21,20,0), v3(-10.1292f,-21.9033f,7.2639f), v3(-18.13f,-21.9f,7.26f), v3(0,0,0));
        POS[BODY] = tr(new float[] {0, .25f, .375f, .75f, 1.04167f, 1.125f},
                v3(0,0,0), v3(0,0,0), v3(-1,0,-3), v3(-1,0,-1), v3(-1,0,-2), v3(0,0,0));

        ROT[R_ARM] = tr(new float[] {0, .375f, .58333f, 1.04167f, 1.125f},
                v3(0,0,0), v3(90,52.5f,0), v3(-90,-22.5f,180), v3(90,2.5f,0), v3(0,0,0));
        POS[R_ARM] = tr(new float[] {0, .375f, .5f, .58333f, 1.04167f, 1.125f},
                v3(0,0,0), v3(-1,0,-2), v3(-1,0,-2), v3(0,0,-1), v3(-2,0,3), v3(0,0,0));

        ROT[L_ARM] = tr(new float[] {0, .25f, .5f, .75f, 1.125f},
                v3(0,0,0), v3(0,0,-22.5f), v3(0,0,-22.5f), v3(0,-67.5f,-22.5f), v3(0,0,0));
        POS[L_ARM] = tr(new float[] {0, .25f, .5f, .75f, 1.125f},
                v3(0,0,0), v3(0,0,2), v3(-1,0,-2), v3(0,0,-3), v3(0,0,0));

        ROT[R_LEG] = tr(new float[] {0, .25f, .5f, .875f, 1.125f},
                v3(0,0,0), v3(0,-45,0), v3(-20.941f,-40.7895f,30.3612f), v3(-20.941f,-40.7895f,30.3612f), v3(0,0,0));
        POS[R_LEG] = tr(new float[] {.875f, 1f, 1.125f},
                v3(0,0,0), v3(0,2,-2), v3(0,0,0));

        ROT[L_LEG] = tr(new float[] {0, .125f, .25f, 1f, 1.125f},
                v3(0,0,0), v3(-22.5f,0,0), v3(22.5f,0,0), v3(22.5f,0,0), v3(0,0,0));
        POS[L_LEG] = tr(new float[] {0, .125f, .25f, 1f, 1.125f},
                v3(0,0,0), v3(0,2,-3), v3(0,0,-3), v3(0,0,-3), v3(0,0,0));
    }

    private record Anim(float startAge, boolean rightArm) {}

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    /** Controle de push/pop da matriz na primeira pessoa (ver HeldItemRendererMixin). */
    public static boolean firstPersonPushed = false;

    private ThrowAnimClient() {}

    public static void start(PlayerEntity player, boolean rightArm) {
        // continua exatamente da pose de carga (0.375s = 7.5 ticks)
        ANIMS.put(player.getId(), new Anim(player.age - CHARGE_TIME * 20f, rightArm));
    }

    public static void clear() {
        ANIMS.clear();
    }

    private static boolean isCharging(PlayerEntity p) {
        return p.isUsingItem() && p.getActiveItem().getItem() instanceof ThrowingDiscItem;
    }

    public static boolean isRightArm(PlayerEntity p) {
        Anim a = ANIMS.get(p.getId());
        if (a != null) return a.rightArm();
        if (isCharging(p)) {
            return (p.getActiveHand() == Hand.MAIN_HAND) == (p.getMainArm() == Arm.RIGHT);
        }
        return true;
    }

    /**
     * Pose atual do jogador (animação de soltar ou pose de carga) ou null.
     * Compatibilidade: não anima em poses especiais (nadando, elytra, dormindo...) nem em espectador,
     * para não brigar com o vanilla/outros mods nessas situações.
     */
    public static Pose sample(PlayerEntity p, float ageProgress) {
        Anim a = ANIMS.get(p.getId());
        float timeSec = -1f;
        if (a != null) {
            float ticks = ageProgress - a.startAge();
            if (ticks > DURATION_TICKS) {
                ANIMS.remove(p.getId());
            } else if (ticks >= 0f) {
                timeSec = ticks / 20f;
            }
        }
        if (timeSec < 0f) {
            if (!isCharging(p)) return null;
            float used = p.getActiveItem().getMaxUseTime() - p.getItemUseTimeLeft() + (ageProgress - p.age);
            timeSec = Math.max(0f, Math.min(CHARGE_TIME, used / 20f));
        }
        if (p.isSpectator() || p.getPose() != EntityPose.STANDING) return null;

        Pose pose = new Pose();
        for (int b = 0; b < 6; b++) {
            if (ROT[b] != null) ROT[b].at(timeSec, pose.rot[b]);
            if (POS[b] != null) POS[b].at(timeSec, pose.pos[b]);
        }
        return pose;
    }
}
