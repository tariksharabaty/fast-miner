package com.oidioomstudio.fastminer.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record SyncShapePayload(int shapeIdx, List<BlockPos> blocks) implements CustomPacketPayload {
    public static final Type<SyncShapePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fastminer", "sync_shape"));

    public static final StreamCodec<FriendlyByteBuf, SyncShapePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SyncShapePayload::shapeIdx,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()),
            SyncShapePayload::blocks,
            SyncShapePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
