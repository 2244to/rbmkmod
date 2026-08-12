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

        // Synchronizacja z serwera do klienta
        registrar.playToClient(
                SyncCoreChannelsPayload.TYPE,
                SyncCoreChannelsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (Minecraft.getInstance().screen instanceof ControlPanelScreen screen) {
                        screen.updateChannels(payload.channels(), payload.yOffset(), payload.graphitePercent(), payload.zoomFactor());
                    }
                })
        );

        // Synchronizacja suwaków z klienta do serwera
        registrar.playToServer(
                SetPanelSettingPayload.TYPE,
                SetPanelSettingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof ControlPanelMenu menu && menu.getBlockEntity() != null) {
                        if (payload.settingId() == 0) {
                            menu.getBlockEntity().setGraphitePercent((int) payload.value());
                        } else if (payload.settingId() == 1) {
                            menu.getBlockEntity().setZoomFactor(payload.value());
                        }
                    }
                })
        );
    }
}