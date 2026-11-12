package com.oidioomstudio.fastminer.shape;

import com.oidioomstudio.olib.util.world.OBlockFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ShapelessShape implements FastMinerShape {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("fastminer", "shapeless");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("fastminer.shape.shapeless");
    }

    @Override
    public boolean isIndeterminate() {
        return true;
    }

    @Override
    public List<BlockPos> getBlocks(Level level, Player player, BlockPos originPos, Direction face, BlockState originState, int maxBlocks) {
        return OBlockFinder.findConnected(
                level,
                originPos,
                maxBlocks,
                (pos, state) -> BlockMatcher.matches(originState, state, true),
                true // 26 adjacent neighbors (just like FTB Ultimine)
        );
    }
}
