package com.rbmkmod.rbmkmod;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class EnrichedUraniumBlock extends BaseEntityBlock {
    public static final MapCodec<EnrichedUraniumBlock> CODEC = simpleCodec(EnrichedUraniumBlock::new);

    public EnrichedUraniumBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnrichedUraniumBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EnrichedUraniumBlockEntity reactor) {
            double tempC = reactor.getTemperature() - 273.15;
            int water = reactor.getWaterTank().getFluidAmount();
            long steam = reactor.getSteamTank().getStack().getAmount();
            player.displayClientMessage(Component.translatable(
                    "chat.rbmkmod.fuel_channel.status", String.format("%.1f", tempC), water, steam
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.ENRICHED_URANIUM_REACTOR.get(),
                EnrichedUraniumBlockEntity::serverTick);
    }
}
