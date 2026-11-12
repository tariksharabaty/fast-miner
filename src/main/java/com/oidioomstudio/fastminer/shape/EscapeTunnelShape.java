package com.oidioomstudio.fastminer.shape;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class EscapeTunnelShape extends DiagonalTunnelShape {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("fastminer", "escape_tunnel");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("fastminer.shape.escape_tunnel");
    }

    @Override
    protected int yStep() {
        return 1; // upward staircase
    }
}
