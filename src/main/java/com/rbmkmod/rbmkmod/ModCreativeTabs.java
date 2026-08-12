package com.rbmkmod.rbmkmod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RbmkMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RBMK_TAB =
            CREATIVE_MODE_TABS.register("rbmk_tab", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.GRAPHITE.get()))
                    .title(Component.translatable("creativetab.rbmkmod.rbmk_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.GRAPHITE.get());
                        output.accept(ModItems.GRAPHITE_BLOCK_ITEM.get());
                        output.accept(ModItems.PURIFIED_GRAPHITE_BLOCK_ITEM.get());
                        output.accept(ModItems.BERYLLIUM_BLOCK_ITEM.get());
                        output.accept(ModItems.BORON_INGOT.get());
                        output.accept(ModItems.BORON_BLOCK_ITEM.get());
                        output.accept(ModItems.URANIUM_ORE_ITEM.get());
                        output.accept(ModItems.URANIUM_INGOT.get());
                        output.accept(ModItems.ENRICHED_URANIUM.get());
                        output.accept(ModItems.URANIUM_BLOCK_ITEM.get());
                        output.accept(ModItems.ENRICHED_URANIUM_BLOCK_ITEM.get());
                        output.accept(ModItems.NEUTRON_GUN.get());
                        output.accept(ModItems.CONTROL_ROD_BLOCK_ITEM.get());
                        output.accept(ModItems.CONTROL_PANEL_BLOCK_ITEM.get());
                    }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}