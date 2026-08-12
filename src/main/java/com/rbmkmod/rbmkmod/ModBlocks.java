package com.rbmkmod.rbmkmod;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.Blocks.createBlocks(RbmkMod.MODID);

    public static final DeferredBlock<Block> GRAPHITE_BLOCK = BLOCKS.registerBlock("graphite_block",
            properties -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> PURIFIED_GRAPHITE_BLOCK = BLOCKS.registerBlock("purified_graphite_block",
            properties -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(4.0F, 8.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> URANIUM_ORE = BLOCKS.registerBlock("uranium_ore",
            properties -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 3.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> URANIUM_BLOCK = BLOCKS.registerBlock("uranium_block",
            properties -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.EMERALD)
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<EnrichedUraniumBlock> ENRICHED_URANIUM_BLOCK = BLOCKS.registerBlock("enriched_uranium_block",
            properties -> new EnrichedUraniumBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIAMOND)
                    .strength(6.0F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    // Blok Berylu - reflektor neutronów
    public static final DeferredBlock<Block> BERYLLIUM_BLOCK = BLOCKS.registerBlock("beryllium_block",
            properties -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 10.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    // Blok Boru - absorber neutronów (pręty kontrolne)
    public static final DeferredBlock<Block> BORON_BLOCK = BLOCKS.registerBlock("boron_block",
            properties -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(4.0F, 8.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}