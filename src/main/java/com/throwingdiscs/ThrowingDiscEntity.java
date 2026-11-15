package com.throwingdiscs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Disco boomerang:
 *  - IDA: voa em linha reta e ricocheteia (reflexão) em cada bloco, guardando os pontos de ricochete.
 *  - Após 30s (600 ticks) rebatendo, entra em VOLTA: refaz os ricochetes ao contrário e volta ao dono.
 */
public class ThrowingDiscEntity extends ProjectileEntity {
    private static final TrackedData<ItemStack> STACK =
            DataTracker.registerData(ThrowingDiscEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

    public static final int OUTBOUND_TICKS = 600;      // 30 segundos
    private static final double RETURN_SPEED = 2.2;     // blocos/tick na volta
    private static final int MAX_PATH = 2048;           // limite de ricochetes guardados
    private static final double MAX_LEG = 96.0;         // se voar longe demais sem bater, volta

    private boolean returning = false;
    private boolean refund = true;       // devolve o item ao dono (false no criativo)
    private int flightTicks = 0;
    private int spinTicks = 0;
    private int returnIdx = -1;
    private final List<Vec3d> path = new ArrayList<>();   // path[0] = ponto de lançamento
    private final Map<Integer, Integer> hitCooldown = new HashMap<>();

    public ThrowingDiscEntity(EntityType<? extends ThrowingDiscEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public ThrowingDiscEntity(World world, net.minecraft.entity.LivingEntity owner, ItemStack stack, boolean refund) {
        this(ModEntities.THROWING_DISC, world);
        this.setOwner(owner);
        this.setStack(stack);
        this.refund = refund;
        this.setPosition(owner.getEyePos());
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(STACK, ItemStack.EMPTY);
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

    // ------------------------------------------------------------------ TICK

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
        // predição simples; o servidor corrige a posição/velocidade todo tick
        setPosition(getPos().add(getVelocity()));
        spawnSpinParticles();
    }

    /** Partículas constantes de giro: anel de poeira colorida girando ao redor do disco. */
    private void spawnSpinParticles() {
        Vec3d prev = new Vec3d(prevX, prevY, prevZ);
        Vec3d cur = getPos();
        DustParticleEffect dust = new DustParticleEffect(Vec3d.unpackRgb(getTier().color).toVector3f(), 0.9f);
        int steps = 4;
        for (int i = 0; i < steps; i++) {
            double t = (i + 1) / (double) steps;
            Vec3d p = prev.lerp(cur, t);
            double angle = (spinTicks * steps + i) * 0.55;
            for (int k = 0; k < 2; k++) {
                double a = angle + k * Math.PI;
                getWorld().addParticle(dust,
                        p.x + Math.cos(a) * 0.5, p.y + 0.08, p.z + Math.sin(a) * 0.5,
                        0, 0, 0);
            }
        }
        if (spinTicks % 2 == 0) {
            getWorld().addParticle(ParticleTypes.CRIT, cur.x, cur.y + 0.05, cur.z, 0, 0, 0);
        }
    }

    private void serverTick(ServerWorld world) {
        Entity owner = getOwner();
        if (path.isEmpty() && !returning) path.add(getPos());
        Vec3d from = getPos();
        flightTicks++;

        if (!returning) {
            boolean outOfWorld = getY() < world.getBottomY() - 8 || getY() > world.getTopY() + 64;
            boolean unloaded = !world.isChunkLoaded(BlockPos.ofFloored(getPos().add(getVelocity())));
            boolean farFromLast = path.get(path.size() - 1).squaredDistanceTo(getPos()) > MAX_LEG * MAX_LEG;
            if (flightTicks >= OUTBOUND_TICKS || path.size() >= MAX_PATH || outOfWorld || unloaded || farFromLast) {
                beginReturn();
            }
        }

        if (!returning) {
            moveOutbound(world, owner);
        } else if (!moveReturn(world, owner)) {
            return; // entidade removida
        }
        checkEntityHits(world, owner, from, getPos());
    }

    private void beginReturn() {
        returning = true;
        returnIdx = path.size() - 1;   // índice 0 (lançamento) é ignorado: volta direto ao jogador
        setVelocity(Vec3d.ZERO);
    }

    // ------------------------------------------------------------------ IDA (ricochete)

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

        if (hit.getType() == HitResult.Type.MISS) {
            setPosition(end);
            setVelocity(vel);
            return;
        }

        Direction side = hit.getSide();
        Vec3d n = new Vec3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
        Vec3d reflected = vel.subtract(n.multiply(2.0 * vel.dotProduct(n)));
        if (reflected.lengthSquared() < 1.0E-6) reflected = n;
        reflected = reflected.normalize().multiply(speed);

        Vec3d bouncePos = hit.getPos().add(n.multiply(0.12));
        setPosition(bouncePos);
        setVelocity(reflected);
        path.add(bouncePos);

        onBounce(world, hit, bouncePos);
    }

    /** Partículas + som ao colidir com blocos. */
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

    // ------------------------------------------------------------------ VOLTA (refaz o trajeto)

    /** @return false se a entidade foi removida. */
    private boolean moveReturn(ServerWorld world, Entity owner) {
        Vec3d startPos = getPos();
        Vec3d pos = startPos;
        double remaining = RETURN_SPEED;

        for (int guard = 0; guard < 64 && remaining > 1.0E-6; guard++) {
            Vec3d target;
            boolean toOwner = returnIdx < 1;
            if (!toOwner) {
                target = path.get(returnIdx);
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
                    pickUp(world, owner);
                    return false;
                }
                returnIdx--;
            } else {
                pos = pos.add(delta.multiply(remaining / dist));
                remaining = 0;
            }
        }

        setPosition(pos);
        setVelocity(pos.subtract(startPos));
        return true;
    }

    private void pickUp(ServerWorld world, Entity owner) {
        if (refund) {
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

    private void dropAsItem(ServerWorld world) {
        if (refund) {
            ItemEntity item = new ItemEntity(world, getX(), getY(), getZ(), getStack().copy());
            world.spawnEntity(item);
        }
        discard();
    }

    // ------------------------------------------------------------------ DANO

    private boolean canDiscHit(Entity e) {
        if (!e.isAlive() || e.isSpectator() || !e.isAttackable() || e instanceof ThrowingDiscEntity) return false;
        if (e == getOwner()) return false;
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

    // ------------------------------------------------------------------ NBT

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("Item", getStack().writeNbt(new NbtCompound()));
        nbt.putBoolean("Returning", returning);
        nbt.putBoolean("Refund", refund);
        nbt.putInt("FlightTicks", flightTicks);
        nbt.putInt("ReturnIdx", returnIdx);
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
        path.clear();
        NbtList list = nbt.getList("Path", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            path.add(new Vec3d(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")));
        }
    }
}
