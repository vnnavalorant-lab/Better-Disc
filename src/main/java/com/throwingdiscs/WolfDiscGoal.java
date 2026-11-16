package com.throwingdiscs;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class WolfDiscGoal extends Goal {
    private static final double SEE_RANGE = 16.0;
    private static final float CHASE_CHANCE = 0.6f;
    private static final float CATCH_CHANCE = 0.45f;
    private static final double CATCH_REACH = 1.5;
    private static final double MAX_JUMP = 2.95;
    private static final int MAX_CHASE_TICKS = 240;
    private static final int MAX_DELIVER_TICKS = 400;

    private static final double BUSY_OWNER_DIST = 32.0;
    private static final double BUSY_DISC_RANGE = 24.0;

    private final WolfEntity wolf;
    private ThrowingDiscEntity target;
    private final Map<Integer, Integer> ignoreUntil = new HashMap<>();
    private int chaseTicks, deliverTicks, jumpCooldown, repath;

    public WolfDiscGoal(WolfEntity wolf) {
        this.wolf = wolf;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    public static boolean isBusy(WolfEntity wolf) {
        if (wolf.getWorld().isClient || !wolf.isTamed()) return false;
        LivingEntity owner = wolf.getOwner();
        if (owner == null) return false;
        double d2 = wolf.squaredDistanceTo(owner);
        if (d2 < 81.0 || d2 > BUSY_OWNER_DIST * BUSY_OWNER_DIST) return false;
        if (wolf.getMainHandStack().getItem() instanceof ThrowingDiscItem) return true;
        return !wolf.getWorld().getEntitiesByClass(ThrowingDiscEntity.class,
                wolf.getBoundingBox().expand(BUSY_DISC_RANGE, 12.0, BUSY_DISC_RANGE),
                d -> d.getOwner() == owner && d.isCatchable()).isEmpty();
    }

    private boolean holdsDisc() {
        return wolf.getMainHandStack().getItem() instanceof ThrowingDiscItem;
    }

    @Override
    public boolean canStart() {
        if (wolf.getWorld().isClient || !wolf.isTamed() || wolf.isInSittingPose()) return false;
        LivingEntity owner = wolf.getOwner();
        if (holdsDisc()) {
            if (owner == null) {
                wolf.dropStack(wolf.getMainHandStack().copy());
                wolf.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                return false;
            }
            return true;
        }
        if (owner == null || (wolf.age + wolf.getId()) % 5 != 0) return false;
        target = findDisc(owner);
        return target != null;
    }

    private ThrowingDiscEntity findDisc(LivingEntity owner) {
        if (ignoreUntil.size() > 32) ignoreUntil.clear();
        List<ThrowingDiscEntity> list = wolf.getWorld().getEntitiesByClass(ThrowingDiscEntity.class,
                wolf.getBoundingBox().expand(SEE_RANGE, 8.0, SEE_RANGE),
                d -> d.getOwner() == owner && d.isCatchable() && ignoreUntil.getOrDefault(d.getId(), 0) <= wolf.age);
        ThrowingDiscEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (ThrowingDiscEntity d : list) {
            double dist = wolf.squaredDistanceTo(d);
            if (dist < bestD) { bestD = dist; best = d; }
        }
        if (best == null) return null;
        if (wolf.getRandom().nextFloat() > CHASE_CHANCE) {
            ignoreUntil.put(best.getId(), wolf.age + 100);
            return null;
        }
        return best;
    }

    @Override
    public boolean shouldContinue() {
        if (!wolf.isTamed() || wolf.isInSittingPose()) return false;
        if (holdsDisc()) return wolf.getOwner() != null;
        return target != null && target.isAlive() && target.isCatchable()
                && chaseTicks < MAX_CHASE_TICKS && wolf.squaredDistanceTo(target) < 24.0 * 24.0;
    }

    @Override
    public void start() {
        chaseTicks = 0;
        deliverTicks = 0;
        jumpCooldown = 0;
        repath = 0;
    }

    @Override
    public void stop() {
        target = null;
        wolf.getNavigation().stop();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (holdsDisc()) {
            tickDeliver();
            return;
        }
        if (target == null) return;
        chaseTicks++;
        if (jumpCooldown > 0) jumpCooldown--;

        Vec3d dp = target.getPos();
        wolf.getLookControl().lookAt(dp.x, dp.y, dp.z);
        if (--repath <= 0) {
            repath = 4;
            wolf.getNavigation().startMovingTo(dp.x, dp.y, dp.z, 1.5);
        }

        double dx = dp.x - wolf.getX(), dz = dp.z - wolf.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double rise = dp.y - (wolf.getY() + wolf.getHeight() * 0.8);
        if (jumpCooldown == 0 && wolf.isOnGround() && horiz < 3.2 && rise > 0.3 && rise <= MAX_JUMP) {
            double h = MathHelper.clamp(rise + 0.3, 0.5, MAX_JUMP);
            // gravidade 0.08/tick; 1.14 compensa o arrasto
            double vy = Math.sqrt(0.16 * h / 1.14);
            Vec3d v = wolf.getVelocity();
            double nx = horiz > 1.0E-4 ? dx / horiz : 0, nz = horiz > 1.0E-4 ? dz / horiz : 0;
            wolf.setVelocity(v.x + nx * 0.2, vy, v.z + nz * 0.2);
            wolf.velocityDirty = true;
            jumpCooldown = 20;
        }

        Vec3d mouth = wolf.getPos().add(Vec3d.fromPolar(0f, wolf.headYaw).multiply(0.5))
                .add(0, wolf.getHeight() * 0.8, 0);
        Vec3d a = new Vec3d(target.prevX, target.prevY, target.prevZ);
        if (distanceToSegment(mouth, a, dp) <= CATCH_REACH) {
            if (wolf.getRandom().nextFloat() < CATCH_CHANCE) {
                catchDisc();
            } else {
                ignoreUntil.put(target.getId(), wolf.age + 40);
                target = null;
            }
        }
    }

    private void catchDisc() {
        ItemStack stack = target.getStack().copy();
        boolean refund = target.isRefund();
        target.discard();
        target = null;
        deliverTicks = 0;
        wolf.playSound(SoundEvents.ENTITY_FOX_BITE, 1.0f, 1.0f);
        if (refund) {
            wolf.equipStack(EquipmentSlot.MAINHAND, stack);
            wolf.setEquipmentDropChance(EquipmentSlot.MAINHAND, 2.0f);
        }
    }

    private void tickDeliver() {
        LivingEntity owner = wolf.getOwner();
        if (owner == null) return;
        deliverTicks++;
        wolf.getLookControl().lookAt(owner, 30.0f, 30.0f);
        if (--repath <= 0) {
            repath = 10;
            wolf.getNavigation().startMovingTo(owner, 1.3);
        }
        if (wolf.squaredDistanceTo(owner) < 6.25 || deliverTicks >= MAX_DELIVER_TICKS) {
            ItemStack stack = wolf.getMainHandStack().copy();
            wolf.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (owner instanceof PlayerEntity p) {
                if (!p.getInventory().insertStack(stack)) p.dropItem(stack, false);
            } else {
                wolf.dropStack(stack);
            }
            wolf.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
        }
    }

    private static double distanceToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double len2 = ab.lengthSquared();
        double t = len2 < 1.0E-8 ? 0 : MathHelper.clamp(p.subtract(a).dotProduct(ab) / len2, 0.0, 1.0);
        return p.distanceTo(a.add(ab.multiply(t)));
    }
}
