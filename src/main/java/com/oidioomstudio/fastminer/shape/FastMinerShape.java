package com.oidioomstudio.fastminer.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public interface FastMinerShape {
    Identifier getId();

    Component getDisplayName();

    List<BlockPos> getBlocks(Level level, Player player, BlockPos originPos, Direction face, BlockState originState, int maxBlocks);

    default boolean isIndeterminate() {
        return false;
    }
}
