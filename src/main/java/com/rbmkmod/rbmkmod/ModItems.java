package com.rbmkmod.rbmkmod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RbmkMod.MODID);

    public static final DeferredItem<Item> GRAPHITE = ITEMS.register("graphite",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> URANIUM_INGOT = ITEMS.register("uranium_ingot",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> ENRICHED_URANIUM = ITEMS.register("enriched_uranium",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> BORON_INGOT = ITEMS.register("boron_ingot",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<NeutronGunItem> NEUTRON_GUN = ITEMS.register("neutron_gun",
            () -> new NeutronGunItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> GRAPHITE_BLOCK_ITEM = ITEMS.registerItem("graphite_block",
            properties -> new BlockItem(ModBlocks.GRAPHITE_BLOCK.get(), properties));

    public static final DeferredItem<Item> PURIFIED_GRAPHITE_BLOCK_ITEM = ITEMS.registerItem("purified_graphite_block",
            properties -> new BlockItem(ModBlocks.PURIFIED_GRAPHITE_BLOCK.get(), properties));

    public static final DeferredItem<Item> URANIUM_ORE_ITEM = ITEMS.registerItem("uranium_ore",
            properties -> new BlockItem(ModBlocks.URANIUM_ORE.get(), properties));

    public static final DeferredItem<Item> URANIUM_BLOCK_ITEM = ITEMS.registerItem("uranium_block",
            properties -> new BlockItem(ModBlocks.URANIUM_BLOCK.get(), properties));

    public static final DeferredItem<Item> ENRICHED_URANIUM_BLOCK_ITEM = ITEMS.registerItem("enriched_uranium_block",
            properties -> new BlockItem(ModBlocks.ENRICHED_URANIUM_BLOCK.get(), properties));

    public static final DeferredItem<Item> BERYLLIUM_BLOCK_ITEM = ITEMS.registerItem("beryllium_block",
            properties -> new BlockItem(ModBlocks.BERYLLIUM_BLOCK.get(), properties));

    public static final DeferredItem<Item> BORON_BLOCK_ITEM = ITEMS.registerItem("boron_block",
            properties -> new BlockItem(ModBlocks.BORON_BLOCK.get(), properties));

    public static final DeferredItem<Item> CONTROL_ROD_BLOCK_ITEM = ITEMS.registerItem("control_rod",
            properties -> new BlockItem(ModBlocks.CONTROL_ROD_BLOCK.get(), properties));
    public static final DeferredItem<Item> CONTROL_PANEL_BLOCK_ITEM = ITEMS.registerItem("control_panel",
            properties -> new BlockItem(ModBlocks.CONTROL_PANEL_BLOCK.get(), properties));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}