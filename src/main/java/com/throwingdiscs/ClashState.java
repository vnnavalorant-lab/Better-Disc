package com.throwingdiscs;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Estado (só no cliente) da animação de defesa: quem está defendendo, há quantos ticks e se a espada vai quebrar.
 * Alimentado pelo próprio disco preso na lâmina (ThrowingDiscEntity#clientTick) e lido pelos renderers/mixins.
 * Não importa nada de client para poder ser carregado em servidor dedicado sem problemas.
 */
public final class ClashState {
    // ---- ajustes rápidos da animação (1ª pessoa: espaço da câmera, a partir da posição vanilla da mão) ----
    /** Posição do disco na lâmina em 1ª pessoa (x é multiplicado pelo lado da mão). */
    public static final float FP_X = 0.63f, FP_Y = -0.19f, FP_Z = -0.78f;
    /** Posição do disco na lâmina em 3ª pessoa, relativa ao corpo de quem defende. */
    public static final float TP_FORWARD = 0.72f, TP_SIDE = 0.10f, TP_HEIGHT = 1.30f;
    /** Giro do disco ao redor da lâmina (graus por tick), como no vídeo de referência. */
    public static final float SPIN_DEG_PER_TICK = 30f;

    public static final class Info {
        public int ticks;
        public boolean weak;
        public ItemStack disc = ItemStack.EMPTY;
        int discId;
        long lastSeen;

        /** 0..1 ao longo dos 5 segundos. */
        public float progress(float tickDelta) {
            return MathHelper.clamp((ticks + tickDelta) / ThrowingDiscEntity.CLASH_TICKS, 0f, 1f);
        }
    }

    private static final Map<Integer, Info> MAP = new HashMap<>();

    private ClashState() {}

    /** Chamado todo tick (cliente) pelo disco que está preso na espada do alvo. */
    public static void mark(World world, int targetId, ThrowingDiscEntity disc) {
        Info i = MAP.get(targetId);
        if (i == null || i.discId != disc.getId()) {
            i = new Info();
            i.discId = disc.getId();
            MAP.put(targetId, i);
        } else {
            i.ticks++;
        }
        i.weak = disc.isClashWeak();
        i.disc = disc.getStack();
        i.lastSeen = world.getTime();
    }

    /** Info da defesa em andamento desta entidade, ou null. */
    public static Info get(Entity e) {
        Info i = MAP.get(e.getId());
        if (i == null) return null;
        if (e.getWorld().getTime() - i.lastSeen > 2) {
            MAP.remove(e.getId());
            return null;
        }
        return i;
    }

    public static void clear() {
        MAP.clear();
    }

    /**
     * Intensidade do tremor (0..1) — é a "dica" visual do desfecho:
     * espada fraca treme cada vez mais forte (rachando) até o fim; espada boa continua firme.
     */
    public static float shake(boolean weak, float p) {
        return weak ? 0.12f + 0.88f * p * p * p : 0.12f + 0.08f * p;
    }
}
