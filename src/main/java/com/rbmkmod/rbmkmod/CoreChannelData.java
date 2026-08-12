package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record CoreChannelData(
        BlockPos pos,
        Type type,
        double temperatureC,
        int waterMb,
        long steamMb,
        ControlRodMode rodMode
) {
    public enum Type {
        FUEL,
        CONTROL_ROD,
        GRAPHITE,
        BERYLLIUM
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CoreChannelData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeBlockPos(data.pos());
                buf.writeEnum(data.type());
                buf.writeDouble(data.temperatureC());
                buf.writeInt(data.waterMb());
                buf.writeLong(data.steamMb());
                buf.writeEnum(data.rodMode() != null ? data.rodMode() : ControlRodMode.RETRACTED);
            },
            buf -> new CoreChannelData(
                    buf.readBlockPos(),
                    buf.readEnum(Type.class),
                    buf.readDouble(),
                    buf.readInt(),
                    buf.readLong(),
                    buf.readEnum(ControlRodMode.class)
            )
    );
}