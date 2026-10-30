package com.rbmkmod.rbmkmod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
@Mod(RbmkMod.MODID)
public class RbmkMod {
    public static final String MODID = "rbmkmod";

    public RbmkMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        NeoForge.EVENT_BUS.register(NuclearShockwaveManager.class);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}