package com.rbmkmod.rbmkmod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(RbmkMod.MODID)
public class RbmkMod {
    public static final String MODID = "rbmkmod";

    public RbmkMod(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        // TA LINIA JEST WYMAGANA:
        ModMenuTypes.register(modEventBus);
    }
}