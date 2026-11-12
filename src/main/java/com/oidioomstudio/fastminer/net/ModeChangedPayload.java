package com.oidioomstudio.fastminer.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ModeChangedPayload(boolean next) implements CustomPacketPayload {
    public static final Type<ModeChangedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fastminer", "mode_changed"));
    public static final StreamCodec<FriendlyByteBuf, ModeChangedPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            ModeChangedPayload::next,
            ModeChangedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
