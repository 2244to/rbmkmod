package com.rbmkmod.rbmkmod;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class NeutronReflectorBlock extends Block {

    // Używamy pełnej ścieżki dla Properties, żeby uniknąć błędu kompilacji
    public NeutronReflectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public float getReflectionRate() {
        return 0.8f;
    }
}