package com.rbmkmod.rbmkmod;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = RbmkMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(RbmkMod.MODID);
        registrar.playToClient(
                SyncCoreChannelsPayload.TYPE,
                SyncCoreChannelsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (Minecraft.getInstance().screen instanceof ControlPanelScreen screen) {
                        screen.updateChannels(payload.channels(), payload.yOffset());
                    }
                })
        );
    }
}