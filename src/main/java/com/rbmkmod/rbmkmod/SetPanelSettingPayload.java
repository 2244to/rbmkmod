package com.rbmkmod.rbmkmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetPanelSettingPayload(int settingId, float value) implements CustomPacketPayload {
    public static final Type<SetPanelSettingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RbmkMod.MODID, "set_panel_setting"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPanelSettingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SetPanelSettingPayload::settingId,
                    ByteBufCodecs.FLOAT,
                    SetPanelSettingPayload::value,
                    SetPanelSettingPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}