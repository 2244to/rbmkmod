package com.rbmkmod.rbmkmod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RbmkMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnrichedUraniumBlockEntity>> ENRICHED_URANIUM_REACTOR =
            BLOCK_ENTITIES.register("enriched_uranium_reactor", () ->
                    BlockEntityType.Builder.of(EnrichedUraniumBlockEntity::new,
                            ModBlocks.ENRICHED_URANIUM_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControlRodBlockEntity>> CONTROL_ROD =
            BLOCK_ENTITIES.register("control_rod", () ->
                    BlockEntityType.Builder.of(ControlRodBlockEntity::new,
                            ModBlocks.CONTROL_ROD_BLOCK.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
