package com.oidioomstudio.fastminer.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record KeyPressedPayload(boolean pressed) implements CustomPacketPayload {
    public static final Type<KeyPressedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fastminer", "key_pressed"));
    public static final StreamCodec<FriendlyByteBuf, KeyPressedPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            KeyPressedPayload::pressed,
            KeyPressedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
