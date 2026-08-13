package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToggleRodModePayload(BlockPos pos, boolean reverse) implements CustomPacketPayload {
    public static final Type<ToggleRodModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RbmkMod.MODID, "toggle_rod_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleRodModePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    ToggleRodModePayload::pos,
                    net.minecraft.network.codec.ByteBufCodecs.BOOL,
                    ToggleRodModePayload::reverse,
                    ToggleRodModePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}