package com.throwingdiscs.client;

import com.throwingdiscs.ClashState;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Movimento do disco preso na lâmina: gira deitado ao redor da espada e balança (como no vídeo de referência). */
public final class ClashRender {
    private ClashRender() {}

    /**
     * Aplica ao MatrixStack (origem = centro do disco, Y = eixo da lâmina) o balanço, o giro e deixa o disco deitado.
     * @param t   tempo em ticks (com parcial)
     * @param p   progresso 0..1 da defesa
     */
    public static void wobble(MatrixStack m, float t, boolean weak, float p) {
        float s = ClashState.shake(weak, p);
        // tremor da lâmina (só ganha força quando a espada vai quebrar)
        m.translate(MathHelper.sin(t * 2.9f) * 0.012f * s,
                MathHelper.sin(t * 3.7f + 1f) * 0.008f * s,
                MathHelper.sin(t * 3.1f + 2f) * 0.012f * s);
        // balanço: o plano do disco precessiona enquanto ele gira
        float amp = 5f + (weak ? 20f * s : 0f);
        float phase = t * 0.35f;
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(amp * MathHelper.sin(phase)));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(amp * MathHelper.cos(phase)));
        // giro ao redor da lâmina
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * ClashState.SPIN_DEG_PER_TICK));
        // deita o disco (o item é desenhado no plano XY)
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
    }
}
