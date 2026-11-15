package com.throwingdiscs;

import net.minecraft.block.DispenserBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;

/** Ejetor/dispensador lançando o disco em linha reta (ver DispenserBehaviorMixin). */
public final class DiscDispenser {
    private DiscDispenser() {}

    public static ItemStack launch(BlockPointer pointer, ItemStack stack) {
        ServerWorld world = pointer.getWorld();
        Direction dir = pointer.getBlockState().get(DispenserBlock.FACING);
        Position out = DispenserBlock.getOutputLocation(pointer);
        Vec3d pos = new Vec3d(out.getX(), out.getY(), out.getZ());
        ItemStack one = stack.split(1);
        world.spawnEntity(new ThrowingDiscEntity(world, one, pos,
                new Vec3d(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ())));
        return stack;
    }
}
