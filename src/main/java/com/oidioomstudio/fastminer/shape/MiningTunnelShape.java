package com.oidioomstudio.fastminer.shape;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class MiningTunnelShape extends DiagonalTunnelShape {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("fastminer", "mining_tunnel");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("fastminer.shape.mining_tunnel");
    }

    @Override
    protected int yStep() {
        return -1; // downward staircase
    }
}
