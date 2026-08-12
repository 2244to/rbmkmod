package com.rbmkmod.rbmkmod;

import mekanism.common.capabilities.Capabilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@EventBusSubscriber(modid = RbmkMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class MekanismHeatCapabilities {

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Rejestracja rur z wodą (NeoForge FluidHandler)
        event.registerBlockEntity(
                FluidHandler.BLOCK,
                ModBlockEntities.ENRICHED_URANIUM_REACTOR.get(),
                (be, side) -> be.getWaterTank()
        );

        // Rejestracja gazociągów z parą (Mekanism Chemical)
        event.registerBlockEntity(
                Capabilities.CHEMICAL.block(),
                ModBlockEntities.ENRICHED_URANIUM_REACTOR.get(),
                (be, side) -> be
        );
    }
}