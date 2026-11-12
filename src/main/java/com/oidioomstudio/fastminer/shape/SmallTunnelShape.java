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

public class SmallTunnelShape implements FastMinerShape {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("fastminer", "small_tunnel");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("fastminer.shape.small_tunnel");
    }

    @Override
    public List<BlockPos> getBlocks(Level level, Player player, BlockPos originPos, Direction face, BlockState originState, int maxBlocks) {
        List<BlockPos> blocks = new ArrayList<>(maxBlocks);
        Direction dir = (face != null) ? face.getOpposite() : (player != null ? player.getDirection() : Direction.NORTH);

        for (int i = 0; i < maxBlocks; i++) {
            BlockPos pos = originPos.relative(dir, i);
            BlockState state = level.getBlockState(pos);
            if (!BlockMatcher.matches(originState, state, false)) {
                break;
            }
            blocks.add(pos);
        }
        return blocks;
    }
}
