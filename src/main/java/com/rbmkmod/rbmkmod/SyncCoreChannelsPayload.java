package com.rbmkmod.rbmkmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record SyncCoreChannelsPayload(List<CoreChannelData> channels, int yOffset) implements CustomPacketPayload {
    public static final Type<SyncCoreChannelsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RbmkMod.MODID, "sync_core_channels"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCoreChannelsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    CoreChannelData.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    SyncCoreChannelsPayload::channels,
                    ByteBufCodecs.INT,
                    SyncCoreChannelsPayload::yOffset,
                    SyncCoreChannelsPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}