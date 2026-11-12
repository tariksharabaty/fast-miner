package com.oidioomstudio.fastminer.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public abstract class DiagonalTunnelShape implements FastMinerShape {
    protected abstract int yStep();

    @Override
    public List<BlockPos> getBlocks(Level level, Player player, BlockPos originPos, Direction face, BlockState originState, int maxBlocks) {
        Direction forwardDir;
        if (face != null && face.getAxis().isVertical()) {
            forwardDir = player != null ? player.getDirection() : Direction.NORTH;
        } else if (face != null) {
            forwardDir = face.getOpposite();
        } else {
            forwardDir = player != null ? player.getDirection() : Direction.NORTH;
        }

        List<BlockPos> list = new ArrayList<>(maxBlocks);
        for (int i = 0; i < maxBlocks; i++) {
            int dx = forwardDir.getStepX() * i;
            int dy = yStep() * i;
            int dz = forwardDir.getStepZ() * i;
            BlockPos target = originPos.offset(dx, dy, dz);

            if (!BlockMatcher.matches(originState, level.getBlockState(target), false)) {
                break;
            }
            list.add(target);
        }
        return list;
    }
}
