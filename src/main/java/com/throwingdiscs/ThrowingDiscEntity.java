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
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import java.util.UUID;
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
    /** Id de quem está defendendo (-1 = sem defesa). Sincronizado para o cliente animar a espada. */
    private static final TrackedData<Integer> CLASH_TARGET =
            DataTracker.registerData(ThrowingDiscEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** A espada que defende vai quebrar? (o cliente usa para mostrar o tremor crescente) */
    private static final TrackedData<Boolean> CLASH_WEAK =
            DataTracker.registerData(ThrowingDiscEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Face do bloco de mel em que o disco está grudado (Direction id; -1 = não está grudado). */
    private static final TrackedData<Integer> STUCK_SIDE =
            DataTracker.registerData(ThrowingDiscEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final int OUTBOUND_TICKS = 600;      // 30 segundos
    private static final double RETURN_SPEED = 2.2;     // blocos/tick na volta
    private static final int MAX_PATH = 2048;           // limite de ricochetes guardados
    private static final double MAX_RANGE = 22.0;       // distância máxima do ponto de lançamento; passou, volta

    private boolean returning = false;
    private boolean refund = true;       // devolve o item ao dono (false no criativo)
    private int flightTicks = 0;
    private int spinTicks = 0;
    private int returnIdx = -1;
    private final List<Vec3d> path = new ArrayList<>();   // path[0] = ponto de lançamento
    private final Map<Integer, Integer> hitCooldown = new HashMap<>();

    // --- defesa com espada (clash) ---
    public static final int CLASH_TICKS = 100;         // 5 segundos
    private int clashTicks = 0;
    private UUID clashTarget = null;
    private UUID parrier = null;
    private boolean parried = false;                    // cada disco só sofre um clash
    private boolean ownerClashed = false;              // clash com a espada do próprio dono (1x)
    private boolean hostileReturn = false;              // volta machucando quem lançou

    // --- lançado por ejetor/dispensador: não tem dono para pegar o disco de volta ---
    private boolean dispensed = false;
    private Vec3d homePos = null;                       // ponto à frente do ejetor onde o disco é largado ao "voltar"

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

    /** Lançamento por ejetor/dispensador: sem dono; ao voltar, o disco cai no chão à frente do bloco. */
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
        this.dataTracker.startTracking(CLASH_TARGET, -1);
        this.dataTracker.startTracking(CLASH_WEAK, false);
        this.dataTracker.startTracking(STUCK_SIDE, -1);
    }

    /** Face do bloco de mel em que está grudado, ou -1. */
    public int getStuckSide() {
        return this.dataTracker.get(STUCK_SIDE);
    }

    public boolean isRefund() {
        return refund;
    }

    /** Pode ser agarrado por um lobo: voando (ida/volta), sem estar preso no mel nem num clash de espada. */
    public boolean isCatchable() {
        return isAlive() && getStuckSide() < 0 && getClashTargetId() < 0 && clashTicks == 0;
    }

    public int getClashTargetId() {
        return this.dataTracker.get(CLASH_TARGET);
    }

    public boolean isClashWeak() {
        return this.dataTracker.get(CLASH_WEAK);
    }

    /** Espada de madeira ou com "copper" no id (de outro mod) quebra. */
    private static boolean isWeakSword(ItemStack sword) {
        return sword.getItem() instanceof SwordItem si
                && (si.getMaterial() == ToolMaterials.WOOD
                || Registries.ITEM.getId(si).getPath().contains("copper"));
    }

    public ItemStack getStack() {
        return this.dataTracker.get(STACK);
    }

    public void setStack(ItemStack stack) {
        this.dataTracker.set(STACK, stack);
    }

    /** Há algum disco de outro jogador por perto? Só então as espadas podem "defender" (animação 1.8). */
    public static boolean hasThreat(World world, LivingEntity user) {
        return !world.getEntitiesByClass(ThrowingDiscEntity.class,
                user.getBoundingBox().expand(16.0), d -> true).isEmpty();
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
        int clashTarget = getClashTargetId();
        if (clashTarget >= 0) {
            // preso na espada: a animação (giro/balanço) é desenhada pelo renderer; sem rastro de vento
            ClashState.mark(getWorld(), clashTarget, this);
            return;
        }
        if (getStuckSide() >= 0) return;   // grudado no mel: parado, sem rastro de vento
        setPosition(getPos().add(getVelocity()));
        spawnSpinParticles();
    }

    /**
     * Partículas constantes enquanto voa: o disco "corta o vento".
     * Riscos brancos translúcidos nascem na borda do disco e ficam para trás formando duas hélices,
     * e arcos de vento deitados giram ao redor dele. Nada colorido.
     */
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
                // nasce na borda do disco (plano horizontal)
                double ox = ca * 0.34, oz = sa * 0.34;
                // fica para trás do movimento, com uma leve deriva tangencial (espiral)
                double vx = -dir.x * 0.16 - sa * 0.03;
                double vy = -dir.y * 0.16;
                double vz = -dir.z * 0.16 + ca * 0.03;
                getWorld().addParticle(ModParticles.WIND_STREAK, p.x + ox, p.y + 0.04, p.z + oz, vx, vy, vz);
            }
        }
        // arcos de vento girando no plano do disco
        getWorld().addParticle(ModParticles.WIND_RING, cur.x, cur.y + 0.03, cur.z, spinTicks * 0.9, 0, 0);
    }

    private void serverTick(ServerWorld world) {
        Entity owner = getOwner();
        if (path.isEmpty() && !returning) path.add(getPos());
        if (getStuckSide() >= 0) {
            tickStuck(world);
            return;
        }
        Vec3d from = getPos();
        flightTicks++;

        if (clashTicks > 0) {
            tickClash(world);
            return;
        }

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

        // plantações não têm colisão: quebra as que o disco atravessa neste tick
        breakCrops(world, owner, start, hit.getType() == HitResult.Type.MISS ? end : hit.getPos());

        if (hit.getType() == HitResult.Type.MISS) {
            setPosition(end);
            setVelocity(vel);
            return;
        }

        // vidros e vasos quebram e o disco segue em frente (sem ricochete)
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
        Vec3d reflected = vel.subtract(n.multiply(2.0 * vel.dotProduct(n)));
        if (reflected.lengthSquared() < 1.0E-6) reflected = n;
        reflected = reflected.normalize().multiply(speed);

        Vec3d bouncePos = hit.getPos().add(n.multiply(0.12));
        setPosition(bouncePos);
        setVelocity(reflected);
        path.add(bouncePos);

        onBounce(world, hit, bouncePos);
    }

    // ------------------------------------------------------------------ PLANTAÇÕES

    private static boolean isCrop(BlockState state) {
        return state.isIn(BlockTags.CROPS)
                || state.getBlock() instanceof StemBlock
                || state.getBlock() instanceof AttachedStemBlock
                || state.getBlock() instanceof NetherWartBlock
                || state.getBlock() instanceof SweetBerryBushBlock;
    }

    /** Quebra (com drops) as plantações que o disco atravessa entre dois pontos. */
    private void breakCrops(ServerWorld world, Entity owner, Vec3d from, Vec3d to) {
        int steps = Math.max(1, Math.min(24, (int) Math.ceil(from.distanceTo(to) / 0.5)));
        for (int i = 0; i <= steps; i++) {
            Vec3d p = from.lerp(to, i / (double) steps);
            BlockPos min = BlockPos.ofFloored(p.x - 0.3, p.y - 0.15, p.z - 0.3);
            BlockPos max = BlockPos.ofFloored(p.x + 0.3, p.y + 0.15, p.z + 0.3);
            for (BlockPos bp : BlockPos.iterate(min, max)) {
                if (!isCrop(world.getBlockState(bp))) continue;
                if (owner instanceof PlayerEntity pl && !world.canPlayerModifyAt(pl, bp)) continue;
                world.breakBlock(bp.toImmutable(), true, this);
            }
        }
    }

    // ------------------------------------------------------------------ BLOCO DE MEL

    /** Gruda no bloco de mel: sem ricochete, fica parado até um jogador chegar perto. */
    private void stickToHoney(ServerWorld world, BlockHitResult hit) {
        Direction side = hit.getSide();
        Vec3d n = new Vec3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
        Vec3d pos = hit.getPos().add(n.multiply(0.06));
        setPosition(pos);
        setVelocity(Vec3d.ZERO);
        this.dataTracker.set(STUCK_SIDE, side.getId());
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.HONEY_BLOCK.getDefaultState()),
                pos.x, pos.y, pos.z, 10, 0.15, 0.15, 0.15, 0.05);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_HONEY_BLOCK_PLACE,
                SoundCategory.BLOCKS, 1.0f, 0.9f);
    }

    private void tickStuck(ServerWorld world) {
        setVelocity(Vec3d.ZERO);
        Direction side = Direction.byId(getStuckSide());
        BlockPos support = BlockPos.ofFloored(getPos().subtract(
                side.getOffsetX() * 0.1, side.getOffsetY() * 0.1, side.getOffsetZ() * 0.1));
        if (!world.getBlockState(support).isOf(Blocks.HONEY_BLOCK)) {   // o mel sumiu: o disco cai como item
            this.dataTracker.set(STUCK_SIDE, -1);
            dropAsItem(world);
            return;
        }
        PlayerEntity player = world.getClosestPlayer(this, 1.6);
        if (player != null && !player.isSpectator()) {
            if (refund) {
                ItemStack stack = getStack().copy();
                if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ITEM_PICKUP,
                    SoundCategory.PLAYERS, 0.5f, 1.5f);
            discard();
        }
    }

    /** Vidros (blocos e painéis), vasos de planta e vasos decorados. */
    private boolean isBreakable(ServerWorld world, BlockPos pos, BlockState state, Entity owner) {
        boolean glass = state.isIn(BlockTags.IMPERMEABLE)
                || state.getBlock() instanceof AbstractGlassBlock
                || (state.getBlock() instanceof PaneBlock && !state.isOf(Blocks.IRON_BARS));
        boolean vase = state.getBlock() instanceof FlowerPotBlock || state.getBlock() instanceof DecoratedPotBlock;
        if (!glass && !vase) return false;
        if (state.getHardness(world, pos) < 0) return false;                 // inquebrável
        if (owner instanceof PlayerEntity p && !world.canPlayerModifyAt(p, pos)) return false; // proteção de spawn etc.
        return true;
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
                    if (dispensed) {                      // ejetor não pega o disco: ele cai no chão à frente
                        dropAsItem(world);
                        return false;
                    }
                    // o dono também pode se defender do próprio disco rebatido com a espada
                    if (hostileReturn && !ownerClashed && owner instanceof LivingEntity le
                            && le.isUsingItem() && le.getActiveItem().getItem() instanceof SwordItem) {
                        ownerClashed = true;
                        startClash(world, le);
                        return true;
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
        if (hostileReturn && owner instanceof LivingEntity victim) {
            Entity attacker = parrier == null ? null : world.getEntity(parrier);
            victim.damage(world.getDamageSources().thrown(this, attacker), getTier().damage);
            world.spawnParticles(ParticleTypes.CRIT, victim.getX(), victim.getBodyY(0.5), victim.getZ(),
                    12, 0.25, 0.3, 0.25, 0.3);
        }
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
            item.setVelocity(Vec3d.ZERO);
            world.spawnEntity(item);
        }
        discard();
    }

    // ------------------------------------------------------------------ DANO

    private boolean canDiscHit(Entity e) {
        if (!e.isAlive() || e.isSpectator() || !e.isAttackable() || e instanceof ThrowingDiscEntity) return false;
        if (e == getOwner()) return false;
        if (e instanceof WolfEntity w && w.isTamed()) return false;   // lobos domados nunca levam dano (podem agarrar o disco)
        return hitCooldown.getOrDefault(e.getId(), 0) <= flightTicks;
    }

    private void checkEntityHits(ServerWorld world, Entity owner, Vec3d from, Vec3d to) {
        EntityHitResult result = ProjectileUtil.getEntityCollision(world, this, from, to,
                getBoundingBox().stretch(to.subtract(from)).expand(0.6), this::canDiscHit);
        if (result == null) return;

        Entity target = result.getEntity();
        if (!parried && !returning && target instanceof LivingEntity le
                && le.isUsingItem() && le.getActiveItem().getItem() instanceof SwordItem) {
            startClash(world, le);
            return;
        }
        hitCooldown.put(target.getId(), flightTicks + 10);
        target.damage(world.getDamageSources().thrown(this, owner), getTier().damage);
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.8f, 1.2f);
        world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(),
                10, 0.2, 0.3, 0.2, 0.3);
    }


    // ------------------------------------------------------------------ CLASH COM ESPADA

    private void startClash(ServerWorld world, LivingEntity target) {
        parried = true;
        clashTicks = CLASH_TICKS;
        clashTarget = target.getUuid();
        parrier = target.getUuid();
        setVelocity(Vec3d.ZERO);
        Hand h = target.isUsingItem() ? target.getActiveHand() : Hand.MAIN_HAND;
        this.dataTracker.set(CLASH_WEAK, isWeakSword(target.getStackInHand(h)));
        this.dataTracker.set(CLASH_TARGET, target.getId());
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.6f);
    }

    private void tickClash(ServerWorld world) {
        Entity e = clashTarget == null ? null : world.getEntity(clashTarget);
        if (!(e instanceof LivingEntity target) || !target.isAlive()) {
            endClash(false);
            return;
        }
        boolean still = target.isUsingItem() && target.getActiveItem().getItem() instanceof SwordItem;
        if (getClashTargetId() != target.getId()) {      // ex.: mundo recarregado no meio do clash
            this.dataTracker.set(CLASH_TARGET, target.getId());
            this.dataTracker.set(CLASH_WEAK, isWeakSword(target.getActiveItem()));
        }
        int elapsed = CLASH_TICKS - clashTicks;
        float progress = elapsed / (float) CLASH_TICKS;
        boolean weak = isClashWeak();

        // o disco fica preso na lâmina, na frente do peito de quem defende
        Vec3d look = target.getRotationVec(1.0f);
        Vec3d pos = target.getPos().add(0, target.getHeight() * 0.62, 0).add(look.multiply(0.7));
        setPosition(pos);
        setVelocity(Vec3d.ZERO);
        sparks(world, pos, 1.0f + (weak ? 1.5f * progress : 0f));
        clashTells(world, target, pos, elapsed, progress, weak);

        if (clashTicks % 6 == 0) {
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GRINDSTONE_USE,
                    SoundCategory.PLAYERS, 0.5f, 1.7f + world.random.nextFloat() * 0.3f);
        }

        clashTicks--;
        if (clashTicks <= 0 || !still) {
            resolveClash(world, target);
        }
    }

    /**
     * "Dicas" durante os 5s para dar para perceber o desfecho:
     * espada fraca racha (lascas do próprio item + estalos cada vez mais frequentes, junto com o tremor do cliente);
     * espada boa só tine, firme, de tempos em tempos.
     */
    private void clashTells(ServerWorld world, LivingEntity target, Vec3d pos, int elapsed, float progress, boolean weak) {
        if (weak) {
            int period = progress < 0.35f ? 0 : progress < 0.6f ? 14 : progress < 0.85f ? 7 : 3;
            if (period > 0 && elapsed % period == 0) {
                Hand hand = target.isUsingItem() ? target.getActiveHand() : Hand.MAIN_HAND;
                world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, target.getStackInHand(hand)),
                        pos.x, pos.y, pos.z, 4 + (int) (progress * 6), 0.12, 0.12, 0.12, 0.06);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ITEM_BREAK,
                        SoundCategory.PLAYERS, 0.35f, 0.7f + progress * 0.9f);
            }
        } else if (elapsed % 25 == 12) {
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_SHIELD_BLOCK,
                    SoundCategory.PLAYERS, 0.35f, 1.9f);
        }
    }

    /** Faíscas realistas: centelhas brancas, brasas laranja, fagulhas de fogos e pequenas chamas. */
    private void sparks(ServerWorld world, Vec3d p, float intensity) {
        int n = Math.max(1, Math.round(6 * intensity));
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, n, 0.12, 0.12, 0.12, 0.45);
        world.spawnParticles(ParticleTypes.FIREWORK, p.x, p.y, p.z, Math.max(1, n / 2), 0.05, 0.05, 0.05, 0.22);
        world.spawnParticles(new DustParticleEffect(Vec3d.unpackRgb(0xFFB020).toVector3f(), 0.6f),
                p.x, p.y, p.z, n, 0.2, 0.2, 0.2, 0.05);
        world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.5);
        world.spawnParticles(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.06);
        if (world.random.nextInt(3) == 0) {
            world.spawnParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    private void endClash(boolean hostile) {
        clashTicks = 0;
        this.dataTracker.set(CLASH_TARGET, -1);
        hostileReturn = hostile;
        if (ownerClashed) {          // clash com o dono: segue direto para ele, sem refazer o trajeto
            beginReturn();
            returnIdx = 0;
        } else {
            path.add(getPos());
            beginReturn();
        }
    }

    /** Passados os 5s: espada fraca quebra e fere quem defendeu; espada boa rebate o disco contra quem lançou. */
    private void resolveClash(ServerWorld world, LivingEntity target) {
        Vec3d pos = getPos();
        Hand hand = target.isUsingItem() ? target.getActiveHand() : Hand.MAIN_HAND;
        ItemStack sword = target.getStackInHand(hand);
        if (!(sword.getItem() instanceof SwordItem si)) {
            endClash(false);
            return;
        }

        boolean weak = isWeakSword(sword);

        sparks(world, pos, 3.0f);
        if (weak) {
            target.stopUsingItem();
            target.sendToolBreakStatus(hand);
            target.setStackInHand(hand, ItemStack.EMPTY);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ITEM_BREAK,
                    SoundCategory.PLAYERS, 1.0f, 0.9f);
            target.damage(world.getDamageSources().thrown(this, getOwner()), getTier().damage);
            endClash(false);              // volta ao dono sem machucá-lo
        } else {
            sword.damage(1, target, t -> t.sendToolBreakStatus(hand));
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_SHIELD_BLOCK,
                    SoundCategory.PLAYERS, 1.0f, 1.2f);
            endClash(!ownerClashed);      // rebatido: volta contra quem lançou (se foi o próprio dono, o disco é só barrado)
        }
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
        nbt.putInt("ClashTicks", clashTicks);
        nbt.putBoolean("Parried", parried);
        nbt.putBoolean("OwnerClashed", ownerClashed);
        nbt.putBoolean("Hostile", hostileReturn);
        nbt.putInt("StuckSide", getStuckSide());
        nbt.putBoolean("Dispensed", dispensed);
        if (homePos != null) {
            nbt.putDouble("HomeX", homePos.x);
            nbt.putDouble("HomeY", homePos.y);
            nbt.putDouble("HomeZ", homePos.z);
        }
        if (clashTarget != null) nbt.putUuid("ClashTarget", clashTarget);
        if (parrier != null) nbt.putUuid("Parrier", parrier);
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
        clashTicks = nbt.getInt("ClashTicks");
        parried = nbt.getBoolean("Parried");
        ownerClashed = nbt.getBoolean("OwnerClashed");
        hostileReturn = nbt.getBoolean("Hostile");
        this.dataTracker.set(STUCK_SIDE, nbt.contains("StuckSide") ? nbt.getInt("StuckSide") : -1);
        dispensed = nbt.getBoolean("Dispensed");
        homePos = nbt.contains("HomeX") ? new Vec3d(nbt.getDouble("HomeX"), nbt.getDouble("HomeY"), nbt.getDouble("HomeZ")) : null;
        clashTarget = nbt.containsUuid("ClashTarget") ? nbt.getUuid("ClashTarget") : null;
        parrier = nbt.containsUuid("Parrier") ? nbt.getUuid("Parrier") : null;
        path.clear();
        NbtList list = nbt.getList("Path", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            path.add(new Vec3d(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")));
        }
    }
}
