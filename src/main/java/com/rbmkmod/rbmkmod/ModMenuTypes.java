package com.rbmkmod.rbmkmod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, RbmkMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ControlPanelMenu>> CONTROL_PANEL_MENU =
            MENUS.register("control_panel_menu", () -> IMenuTypeExtension.create(ControlPanelMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}