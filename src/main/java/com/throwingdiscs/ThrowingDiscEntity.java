package com.throwingdiscs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.AbstractGlassBlock;
import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class ThrowingDiscEntity extends ProjectileEntity {
    private static final TrackedData<ItemStack> STACK =
            DataTracker.registerData(ThrowingDiscEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final TrackedData<Integer> STUCK_SIDE =
            DataTracker.registerData(ThrowingDiscEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final int OUTBOUND_TICKS = 600;
    private static final double RETURN_SPEED = 2.2;
    private static final int MAX_PATH = 2048;
    private static final double MAX_RANGE = 22.0;

    private boolean returning = false;
    private boolean refund = true;
    private int flightTicks = 0;
    private int spinTicks = 0;
    private int returnIdx = -1;
    private final List<Vec3d> path = new ArrayList<>();
    private final Map<Integer, Integer> hitCooldown = new HashMap<>();

    private boolean dispensed = false;
    private Vec3d homePos = null;
    private static final int MAX_DISPENSED_BOUNCES = 1;
    private int bounces = 0;

    private static final double DISC_CLASH_DIST = 0.55;
    private static final int DISC_CLASH_COOLDOWN = 8;
    private int discClashCooldown = 0;
    private Vec3d moveStart = null;
    private Vec3d moveEnd = null;
    private long moveStamp = -1;

    private static final double CROP_RADIUS = 0.45;
    private static final double CROP_REACH_BELOW = 1.7;
    private static final double CROP_REACH_ABOVE = 0.3;

    public ThrowingDiscEntity(EntityType<? extends ThrowingDiscEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public ThrowingDiscEntity(World world, LivingEntity owner, ItemStack stack, boolean refund) {
        this(ModEntities.THROWING_DISC, world);
        this.setOwner(owner);
        this.setStack(stack);
        this.refund = refund;
        this.setPosition(owner.getEyePos());
    }

    public ThrowingDiscEntity(World world, ItemStack stack, Vec3d pos, Vec3d direction) {
        this(ModEntities.THROWING_DISC, world);
        this.setStack(stack);
        this.refund = true;
        this.dispensed = true;
        this.homePos = pos;
        this.setPosition(pos);
        this.setVelocity(direction.normalize().multiply(getTier().speed));
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(STACK, ItemStack.EMPTY);
        this.dataTracker.startTracking(STUCK_SIDE, -1);
    }

    public int getStuckSide() {
        return this.dataTracker.get(STUCK_SIDE);
    }

    public boolean isRefund() {
        return refund;
    }

    public boolean isCatchable() {
        return isAlive() && getStuckSide() < 0 && !isWornOut();
    }

    public ItemStack getStack() {
        return this.dataTracker.get(STACK);
    }

    public void setStack(ItemStack stack) {
        this.dataTracker.set(STACK, stack);
    }

    public DiscTier getTier() {
        ItemStack s = getStack();
        return s.getItem() instanceof ThrowingDiscItem d ? d.getTier() : DiscTier.IRON;
    }

    public int getSpinTicks() {
        return spinTicks;
    }

    @Override
    public void tick() {
        super.tick();
        spinTicks++;
        if (getWorld().isClient) {
            clientTick();
        } else {
            serverTick((ServerWorld) getWorld());
        }
    }

    private void clientTick() {
        if (getStuckSide() >= 0) {
            spawnHoneyEffects();
            return;
        }
        setPosition(getPos().add(getVelocity()));
        spawnSpinParticles();
    }

    private void spawnHoneyEffects() {
        Direction face = Direction.byId(getStuckSide());
        net.minecraft.util.math.random.Random rnd = getWorld().random;
        Vec3d c = getPos();
        Vec3d u;
        Vec3d w;
        switch (face.getAxis()) {
            case X -> { u = new Vec3d(0, 0, 1); w = new Vec3d(0, 1, 0); }
            case Z -> { u = new Vec3d(1, 0, 0); w = new Vec3d(0, 1, 0); }
            default -> { u = new Vec3d(1, 0, 0); w = new Vec3d(0, 0, 1); }
        }
        Vec3d n = new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
        final double radius = 0.24;

        if (rnd.nextInt(3) == 0) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(rnd.nextDouble()) * radius;
            Vec3d p = c.add(u.multiply(Math.cos(a) * r)).add(w.multiply(Math.sin(a) * r)).add(n.multiply(0.05));
            getWorld().addParticle(ModParticles.HONEY_GLINT, p.x, p.y, p.z,
                    n.x * 0.006, n.y * 0.006 + 0.004, n.z * 0.006);
        }
        if (rnd.nextInt(9) == 0) {
            double a = face.getAxis() == Direction.Axis.Y
                    ? rnd.nextDouble() * Math.PI * 2.0
                    : Math.PI + rnd.nextDouble() * Math.PI;
            Vec3d p = c.add(u.multiply(Math.cos(a) * radius)).add(w.multiply(Math.sin(a) * radius)).add(n.multiply(0.07));
            getWorld().addParticle(ModParticles.HONEY_DRIP, p.x, p.y, p.z, 0.0, 0.0, 0.0);
        }
    }

    private void spawnSpinParticles() {
        Vec3d prev = new Vec3d(prevX, prevY, prevZ);
        Vec3d cur = getPos();
        Vec3d v = getVelocity();
        if (v.lengthSquared() < 1.0E-4) v = cur.subtract(prev);
        Vec3d dir = v.lengthSquared() > 1.0E-6 ? v.normalize() : new Vec3d(0, 0, 1);

        int steps = 3;
        for (int i = 0; i < steps; i++) {
            double t = (i + 1) / (double) steps;
            Vec3d p = prev.lerp(cur, t);
            double angle = (spinTicks * steps + i) * 0.75;
            for (int k = 0; k < 2; k++) {
                double a = angle + k * Math.PI;
                double ca = Math.cos(a), sa = Math.sin(a);
                double ox = ca * 0.34, oz = sa * 0.34;
                double vx = -dir.x * 0.16 - sa * 0.03;
                double vy = -dir.y * 0.16;
                double vz = -dir.z * 0.16 + ca * 0.03;
                getWorld().addParticle(ModParticles.WIND_STREAK, p.x + ox, p.y + 0.04, p.z + oz, vx, vy, vz);
            }
        }
        getWorld().addParticle(ModParticles.WIND_RING, cur.x, cur.y + 0.03, cur.z, spinTicks * 0.9, 0, 0);
    }

    private void serverTick(ServerWorld world) {
        Entity owner = getOwner();
        if (path.isEmpty() && !returning) path.add(getPos());
        if (discClashCooldown > 0) discClashCooldown--;
        if (getStuckSide() >= 0) {
            tickStuck(world);
            return;
        }
        Vec3d from = getPos();
        flightTicks++;

        if (!returning) {
            boolean outOfWorld = getY() < world.getBottomY() - 8 || getY() > world.getTopY() + 64;
            boolean unloaded = !world.isChunkLoaded(BlockPos.ofFloored(getPos().add(getVelocity())));
            boolean farFromLast = path.get(0).squaredDistanceTo(getPos()) > MAX_RANGE * MAX_RANGE;
            if (flightTicks >= OUTBOUND_TICKS || path.size() >= MAX_PATH || outOfWorld || unloaded || farFromLast) {
                beginReturn();
            }
        }

        if (!returning) {
            moveOutbound(world, owner);
            if (isRemoved()) return;
        } else if (!moveReturn(world, owner)) {
            return;
        }
        moveStart = from;
        moveEnd = getPos();
        moveStamp = world.getTime();
        if (!returning) checkDiscCollisions(world);
        checkEntityHits(world, owner, from, getPos());
    }

    private boolean canDiscCollide() {
        return isAlive() && !returning && getStuckSide() < 0
                && discClashCooldown == 0 && flightTicks >= 3;
    }

    private void checkDiscCollisions(ServerWorld world) {
        if (!canDiscCollide() || moveStart == null) return;
        long now = world.getTime();
        for (ThrowingDiscEntity o : world.getEntitiesByClass(ThrowingDiscEntity.class,
                getBoundingBox().expand(3.0), e -> e != this)) {
            if (o.moveStamp != now || o.moveStart == null || !o.canDiscCollide()) continue;
            Vec3d w0 = moveStart.subtract(o.moveStart);
            Vec3d dw = moveEnd.subtract(moveStart).subtract(o.moveEnd.subtract(o.moveStart));
            double dd = dw.lengthSquared();
            double tau = dd > 1.0E-9 ? MathHelper.clamp(-w0.dotProduct(dw) / dd, 0.0, 1.0) : 0.0;
            Vec3d rel = w0.add(dw.multiply(tau));
            if (rel.length() > DISC_CLASH_DIST) continue;
            Vec3d pa = moveStart.add(moveEnd.subtract(moveStart).multiply(tau));
            Vec3d pb = o.moveStart.add(o.moveEnd.subtract(o.moveStart).multiply(tau));
            collideWithDisc(world, o, pa.add(pb).multiply(0.5), rel);
            return;
        }
    }

    private void collideWithDisc(ServerWorld world, ThrowingDiscEntity o, Vec3d point, Vec3d rel) {
        Vec3d va = getVelocity();
        Vec3d vb = o.getVelocity();
        double sa = getTier().speed, sb = o.getTier().speed;
        if (va.lengthSquared() < 1.0E-6) va = new Vec3d(0, 0, 1).multiply(sa);
        if (vb.lengthSquared() < 1.0E-6) vb = new Vec3d(0, 0, 1).multiply(sb);

        Vec3d n = rel;
        if (n.lengthSquared() < 1.0E-6) n = va.subtract(vb);
        if (n.lengthSquared() < 1.0E-6) n = new Vec3d(0, 1, 0);
        n = n.normalize();
        double approach = va.subtract(vb).dotProduct(n);
        if (approach > 0) {
            n = n.negate();
            approach = -approach;
        }
        Vec3d dv = n.multiply(approach);
        Vec3d na = va.subtract(dv).add(n.multiply(0.4 * sa));
        Vec3d nb = vb.add(dv).subtract(n.multiply(0.4 * sb));
        if (na.lengthSquared() < 1.0E-6) na = n;
        if (nb.lengthSquared() < 1.0E-6) nb = n.negate();
        this.setVelocity(na.normalize().multiply(sa));
        o.setVelocity(nb.normalize().multiply(sb));
        this.velocityDirty = true;
        o.velocityDirty = true;

        if (path.size() < MAX_PATH) path.add(getPos());
        if (o.path.size() < MAX_PATH) o.path.add(o.getPos());
        this.discClashCooldown = DISC_CLASH_COOLDOWN;
        o.discClashCooldown = DISC_CLASH_COOLDOWN;

        discSparks(world, point, n);
    }

    private static void discSparks(ServerWorld world, Vec3d p, Vec3d n) {
        world.spawnParticles(ModParticles.DISC_FLASH, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 18; i++) {
            Vec3d r = new Vec3d(world.random.nextGaussian(), world.random.nextGaussian(), world.random.nextGaussian());
            Vec3d t = r.subtract(n.multiply(r.dotProduct(n)));
            if (t.lengthSquared() < 1.0E-6) continue;
            double speed = 0.18 + world.random.nextDouble() * 0.5;
            Vec3d v = t.normalize().multiply(speed)
                    .add(n.multiply((world.random.nextDouble() - 0.5) * 0.25))
                    .add(0, 0.05, 0);
            world.spawnParticles(ModParticles.DISC_SPARK, p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0); // count 0: dx/dy/dz viram a velocidade
        }
        world.playSound(null, p.x, p.y, p.z, SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS,
                0.7f, 1.8f + world.random.nextFloat() * 0.3f);
        world.playSound(null, p.x, p.y, p.z, SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.PLAYERS,
                0.5f, 1.9f);
    }

    private void beginReturn() {
        returning = true;
        returnIdx = path.size() - 1;
        setVelocity(Vec3d.ZERO);
    }

    private void moveOutbound(ServerWorld world, Entity owner) {
        Vec3d start = getPos();
        Vec3d vel = getVelocity();
        double speed = getTier().speed;
        if (vel.lengthSquared() < 1.0E-6) {
            vel = new Vec3d(0, 0, 1).multiply(speed);
        }
        Vec3d end = start.add(vel);

        BlockHitResult hit = world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));

        breakCrops(world, owner, start, hit.getType() == HitResult.Type.MISS ? end : hit.getPos());

        if (hit.getType() == HitResult.Type.MISS) {
            setPosition(end);
            setVelocity(vel);
            return;
        }

        BlockPos hitPos = hit.getBlockPos();
        BlockState hitState = world.getBlockState(hitPos);
        if (hitState.isOf(Blocks.HONEY_BLOCK)) {
            stickToHoney(world, hit);
            return;
        }
        if (isBreakable(world, hitPos, hitState, owner)) {
            world.breakBlock(hitPos, true, this);
            Vec3d hp = hit.getPos();
            world.spawnParticles(ParticleTypes.CRIT, hp.x, hp.y, hp.z, 8, 0.15, 0.15, 0.15, 0.3);
            setPosition(hp.add(vel.normalize().multiply(0.25)));
            setVelocity(vel);
            return;
        }

        Direction side = hit.getSide();
        Vec3d n = new Vec3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());

        if (dispensed && bounces >= MAX_DISPENSED_BOUNCES) {
            Vec3d dropPos = hit.getPos().add(n.multiply(0.2));
            setPosition(dropPos);
            onBounce(world, hit, dropPos);
            dropAsItem(world);
            return;
        }
        bounces++;

        Vec3d reflected = vel.subtract(n.multiply(2.0 * vel.dotProduct(n)));
        if (reflected.lengthSquared() < 1.0E-6) reflected = n;
        reflected = reflected.normalize().multiply(speed);

        Vec3d bouncePos = hit.getPos().add(n.multiply(0.12));
        setPosition(bouncePos);
        setVelocity(reflected);
        path.add(bouncePos);

        onBounce(world, hit, bouncePos);
    }

    private static boolean isCrop(BlockState state) {
        return state.isIn(BlockTags.CROPS)
                || state.getBlock() instanceof StemBlock
                || state.getBlock() instanceof AttachedStemBlock
                || state.getBlock() instanceof NetherWartBlock
                || state.getBlock() instanceof SweetBerryBushBlock
                || state.isOf(Blocks.BAMBOO_SAPLING);
    }

    private static boolean isTallPlant(BlockState state) {
        return state.isOf(Blocks.SUGAR_CANE) || state.isOf(Blocks.BAMBOO);
    }

    private void breakCrops(ServerWorld world, Entity owner, Vec3d from, Vec3d to) {
        int steps = Math.max(1, Math.min(24, (int) Math.ceil(from.distanceTo(to) / 0.4)));
        for (int i = 0; i <= steps; i++) {
            Vec3d p = from.lerp(to, i / (double) steps);
            BlockPos min = BlockPos.ofFloored(p.x - CROP_RADIUS, p.y - CROP_REACH_BELOW, p.z - CROP_RADIUS);
            BlockPos max = BlockPos.ofFloored(p.x + CROP_RADIUS, p.y + CROP_REACH_ABOVE, p.z + CROP_RADIUS);
            for (BlockPos bp : BlockPos.iterate(min, max)) {
                BlockState st = world.getBlockState(bp);
                boolean tall = isTallPlant(st);
                if (!tall && !isCrop(st)) continue;
                if (tall) {
                    boolean inCell = p.y >= bp.getY() - 0.1 && p.y <= bp.getY() + 1.1;
                    if (!inCell && !world.getBlockState(bp.down()).isOf(st.getBlock())) continue;
                }
                if (owner instanceof PlayerEntity pl && !world.canPlayerModifyAt(pl, bp)) continue;
                world.breakBlock(bp.toImmutable(), true, this);
            }
        }
    }

    private void stickToHoney(ServerWorld world, BlockHitResult hit) {
        Direction side = hit.getSide();
        Vec3d n = new Vec3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
        Vec3d pos = hit.getPos().add(n.multiply(0.06));
        setPosition(pos);
        setVelocity(Vec3d.ZERO);
        this.dataTracker.set(STUCK_SIDE, side.getId());
        world.spawnParticles(ModParticles.HONEY_DRIP, pos.x, pos.y, pos.z, 10, 0.12, 0.12, 0.12, 0.10);
        world.spawnParticles(ModParticles.HONEY_GLINT, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.02);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_HONEY_BLOCK_PLACE,
                SoundCategory.BLOCKS, 1.0f, 0.9f);
    }

    private void tickStuck(ServerWorld world) {
        setVelocity(Vec3d.ZERO);
        Direction side = Direction.byId(getStuckSide());
        BlockPos support = BlockPos.ofFloored(getPos().subtract(
                side.getOffsetX() * 0.1, side.getOffsetY() * 0.1, side.getOffsetZ() * 0.1));
        if (!world.getBlockState(support).isOf(Blocks.HONEY_BLOCK)) {
            this.dataTracker.set(STUCK_SIDE, -1);
            dropAsItem(world);
            return;
        }
        PlayerEntity player = world.getClosestPlayer(this, 1.6);
        if (player != null && !player.isSpectator()) {
            if (returnsItem(world)) {
                ItemStack stack = getStack().copy();
                if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ITEM_PICKUP,
                    SoundCategory.PLAYERS, 0.5f, 1.5f);
            discard();
        }
    }

    private boolean isBreakable(ServerWorld world, BlockPos pos, BlockState state, Entity owner) {
        boolean glass = state.isIn(BlockTags.IMPERMEABLE)
                || state.getBlock() instanceof AbstractGlassBlock
                || (state.getBlock() instanceof PaneBlock && !state.isOf(Blocks.IRON_BARS));
        boolean vase = state.getBlock() instanceof FlowerPotBlock || state.getBlock() instanceof DecoratedPotBlock;
        boolean bamboo = state.isOf(Blocks.BAMBOO) || state.isOf(Blocks.BAMBOO_SAPLING);
        if (!glass && !vase && !bamboo) return false;
        if (state.getHardness(world, pos) < 0) return false;
        if (owner instanceof PlayerEntity p && !world.canPlayerModifyAt(p, pos)) return false;
        return true;
    }

    private void onBounce(ServerWorld world, BlockHitResult hit, Vec3d pos) {
        BlockState state = world.getBlockState(hit.getBlockPos());
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                pos.x, pos.y, pos.z, 14, 0.15, 0.15, 0.15, 0.08);
        world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 10, 0.12, 0.12, 0.12, 0.25);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.3);
        world.spawnParticles(new DustParticleEffect(Vec3d.unpackRgb(getTier().color).toVector3f(), 1.2f),
                pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.02);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_TRIDENT_HIT_GROUND,
                SoundCategory.PLAYERS, 0.6f, 1.4f + world.random.nextFloat() * 0.4f);
    }

    private boolean moveReturn(ServerWorld world, Entity owner) {
        Vec3d startPos = getPos();
        Vec3d pos = startPos;
        double remaining = RETURN_SPEED;

        for (int guard = 0; guard < 64 && remaining > 1.0E-6; guard++) {
            Vec3d target;
            boolean toOwner = returnIdx < 1;
            if (!toOwner) {
                target = path.get(returnIdx);
            } else if (dispensed) {
                target = homePos != null ? homePos : path.get(0);
            } else {
                if (owner == null || !owner.isAlive() || owner.getWorld() != getWorld()) {
                    dropAsItem(world);
                    return false;
                }
                target = owner.getPos().add(0, owner.getHeight() * 0.6, 0);
            }

            Vec3d delta = target.subtract(pos);
            double dist = delta.length();
            if (dist <= remaining) {
                pos = target;
                remaining -= dist;
                if (toOwner) {
                    setPosition(pos);
                    if (dispensed) {
                        dropAsItem(world);
                        return false;
                    }
                    pickUp(world, owner);
                    return false;
                }
                returnIdx--;
            } else {
                pos = pos.add(delta.multiply(remaining / dist));
                remaining = 0;
            }
        }

        breakCrops(world, owner, startPos, pos);
        setPosition(pos);
        setVelocity(pos.subtract(startPos));
        return true;
    }

    private void pickUp(ServerWorld world, Entity owner) {
        if (returnsItem(world)) {
            ItemStack stack = getStack().copy();
            if (owner instanceof PlayerEntity player) {
                if (!player.getInventory().insertStack(stack)) {
                    player.dropItem(stack, false);
                }
            } else {
                dropStack(stack);
            }
        }
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.PLAYERS, 0.5f, 1.5f);
        world.spawnParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.2);
        discard();
    }

    public boolean isWornOut() {
        ItemStack s = getStack();
        return s.isDamageable() && s.getDamage() >= s.getMaxDamage();
    }

    private boolean returnsItem(ServerWorld world) {
        if (!refund) return false;
        if (!isWornOut()) return true;
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, getStack()),
                getX(), getY(), getZ(), 16, 0.15, 0.15, 0.15, 0.12);
        world.spawnParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.3);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ITEM_BREAK,
                SoundCategory.PLAYERS, 0.9f, 1.0f);
        return false;
    }

    private void dropAsItem(ServerWorld world) {
        if (returnsItem(world)) {
            ItemEntity item = new ItemEntity(world, getX(), getY(), getZ(), getStack().copy());
            item.setVelocity(Vec3d.ZERO);
            world.spawnEntity(item);
        }
        discard();
    }

    private boolean canDiscHit(Entity e) {
        if (!e.isAlive() || e.isSpectator() || !e.isAttackable() || e instanceof ThrowingDiscEntity) return false;
        if (e == getOwner()) return false;
        if (e instanceof WolfEntity w && w.isTamed()) return false;
        return hitCooldown.getOrDefault(e.getId(), 0) <= flightTicks;
    }

    private void checkEntityHits(ServerWorld world, Entity owner, Vec3d from, Vec3d to) {
        EntityHitResult result = ProjectileUtil.getEntityCollision(world, this, from, to,
                getBoundingBox().stretch(to.subtract(from)).expand(0.6), this::canDiscHit);
        if (result == null) return;

        Entity target = result.getEntity();
        hitCooldown.put(target.getId(), flightTicks + 10);
        target.damage(world.getDamageSources().thrown(this, owner), getTier().damage);
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.8f, 1.2f);
        world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(),
                10, 0.2, 0.3, 0.2, 0.3);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("Item", getStack().writeNbt(new NbtCompound()));
        nbt.putBoolean("Returning", returning);
        nbt.putBoolean("Refund", refund);
        nbt.putInt("FlightTicks", flightTicks);
        nbt.putInt("ReturnIdx", returnIdx);
        nbt.putInt("StuckSide", getStuckSide());
        nbt.putBoolean("Dispensed", dispensed);
        nbt.putInt("Bounces", bounces);
        if (homePos != null) {
            nbt.putDouble("HomeX", homePos.x);
            nbt.putDouble("HomeY", homePos.y);
            nbt.putDouble("HomeZ", homePos.z);
        }
        NbtList list = new NbtList();
        for (Vec3d v : path) {
            NbtCompound c = new NbtCompound();
            c.putDouble("x", v.x);
            c.putDouble("y", v.y);
            c.putDouble("z", v.z);
            list.add(c);
        }
        nbt.put("Path", list);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setStack(ItemStack.fromNbt(nbt.getCompound("Item")));
        returning = nbt.getBoolean("Returning");
        refund = !nbt.contains("Refund") || nbt.getBoolean("Refund");
        flightTicks = nbt.getInt("FlightTicks");
        returnIdx = nbt.getInt("ReturnIdx");
        this.dataTracker.set(STUCK_SIDE, nbt.contains("StuckSide") ? nbt.getInt("StuckSide") : -1);
        dispensed = nbt.getBoolean("Dispensed");
        bounces = nbt.getInt("Bounces");
        homePos = nbt.contains("HomeX") ? new Vec3d(nbt.getDouble("HomeX"), nbt.getDouble("HomeY"), nbt.getDouble("HomeZ")) : null;
        path.clear();
        NbtList list = nbt.getList("Path", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            path.add(new Vec3d(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")));
        }
    }
}
