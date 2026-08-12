package com.rbmkmod.rbmkmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetGraphitePercentPayload(int percent) implements CustomPacketPayload {
    public static final Type<SetGraphitePercentPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RbmkMod.MODID, "set_graphite_percent"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetGraphitePercentPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SetGraphitePercentPayload::percent,
                    SetGraphitePercentPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}