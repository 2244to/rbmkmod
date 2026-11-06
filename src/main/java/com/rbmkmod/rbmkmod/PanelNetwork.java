package com.rbmkmod.rbmkmod;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = "rbmkmod", bus = EventBusSubscriber.Bus.MOD)
public class PanelNetwork {

    public record ClickPayload(BlockPos pos, int button, int value) implements CustomPacketPayload {
        public static final Type<ClickPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("rbmkmod", "panel_click"));

        public static final StreamCodec<ByteBuf, ClickPayload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ClickPayload::pos,
                ByteBufCodecs.VAR_INT, ClickPayload::button,
                ByteBufCodecs.VAR_INT, ClickPayload::value,
                ClickPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Jeśli masz już własny registrar("1"), przenieś tę jedną linijkę do niego.
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ClickPayload.TYPE, ClickPayload.CODEC, PanelNetwork::handleServer);
    }

    private static void handleServer(ClickPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        PanelButton button = PanelButton.byId(payload.button());
        if (button == null) return;

        // Walidacja zasięgu - panel ma 2 bloki szerokości, stąd zapas
        if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > 10 * 10) return;

        if (player.level().getBlockEntity(payload.pos()) instanceof ControlPanelBlockEntity panel) {
            panel.handleButton(button, payload.value(), player);
        }
    }
}
