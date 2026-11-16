package com.throwingdiscs;

import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public final class WolfSaliva {
    private WolfSaliva() {}

    public static void tick(WolfEntity wolf) {
        World world = wolf.getWorld();
        if (!world.isClient || !(wolf.getMainHandStack().getItem() instanceof ThrowingDiscItem)) return;
        Random rnd = world.random;

        float scale = wolf.isBaby() ? 0.5f : 1.0f;
        double bodyYaw = Math.toRadians(wolf.bodyYaw);
        double headYaw = Math.toRadians(wolf.headYaw);
        double pitch = Math.toRadians(wolf.getPitch());
        double hx = -Math.sin(headYaw) * Math.cos(pitch);
        double hy = -Math.sin(pitch);
        double hz = Math.cos(headYaw) * Math.cos(pitch);
        double mx = wolf.getX() + (-Math.sin(bodyYaw) * 0.4375 + hx * 0.30) * scale;
        double my = wolf.getY() + (0.656 + hy * 0.30 - 0.12) * scale;
        double mz = wolf.getZ() + (Math.cos(bodyYaw) * 0.4375 + hz * 0.30) * scale;

        double side = (rnd.nextBoolean() ? 1 : -1) * (0.05 + rnd.nextDouble() * 0.05) * scale;
        double sx = mx + (-Math.cos(headYaw)) * side;
        double sz = mz + (-Math.sin(headYaw)) * side;

        Vec3d v = wolf.getVelocity();
        boolean moving = v.x * v.x + v.z * v.z > 0.004;
        if (moving) {
            if (rnd.nextInt(3) == 0) {
                world.addParticle(ModParticles.SALIVA_DRIP, sx, my, sz,
                        v.x * 0.5 + (rnd.nextDouble() - 0.5) * 0.04,
                        0.02 + rnd.nextDouble() * 0.03,
                        v.z * 0.5 + (rnd.nextDouble() - 0.5) * 0.04);
            }
        } else if (rnd.nextInt(9) == 0) {
            world.addParticle(ModParticles.SALIVA_DRIP, sx, my - 0.02 * scale, sz, 0.0, 0.0, 0.0);
        }
    }
}
