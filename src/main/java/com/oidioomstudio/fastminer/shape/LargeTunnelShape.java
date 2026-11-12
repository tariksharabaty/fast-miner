package com.oidioomstudio.fastminer.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class LargeTunnelShape implements FastMinerShape {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("fastminer", "large_tunnel");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("fastminer.shape.large_tunnel");
    }

    protected int maxDepth() {
        return Integer.MAX_VALUE;
    }

    @Override
    public List<BlockPos> getBlocks(Level level, Player player, BlockPos originPos, Direction face, BlockState originState, int maxBlocks) {
        List<BlockPos> result = new ArrayList<>(Math.min(maxBlocks, 9));
        Direction faceDir = (face != null) ? face : Direction.UP;
        Direction stepDir = faceDir.getOpposite();
        Direction.Axis axis = faceDir.getAxis();

        BlockPos center = originPos;
        int depthLimit = maxDepth();

        for (int depth = 0; depth < depthLimit && result.size() < maxBlocks; depth++) {
            int prevSize = result.size();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos target;
                    if (axis == Direction.Axis.Y) {
                        target = center.offset(dx, 0, dy);
                    } else if (axis == Direction.Axis.X) {
                        target = center.offset(0, dx, dy);
                    } else {
                        target = center.offset(dx, dy, 0);
                    }

                    if (BlockMatcher.matches(originState, level.getBlockState(target), false)) {
                        if (!result.contains(target)) {
                            result.add(target);
                            if (result.size() >= maxBlocks) break;
                        }
                    }
                }
                if (result.size() >= maxBlocks) break;
            }

            if (result.size() == prevSize) {
                // If nothing was added in this slice, stop digging further
                break;
            }

            center = center.relative(stepDir);
        }

        return result;
    }
}
