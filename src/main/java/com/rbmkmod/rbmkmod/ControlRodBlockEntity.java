package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ControlRodBlockEntity extends BlockEntity {
    private ControlRodMode mode = ControlRodMode.RETRACTED;

    public ControlRodBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CONTROL_ROD.get(), pos, blockState);
    }

    public ControlRodMode getMode() {
        return mode;
    }

    public void setMode(ControlRodMode mode) {
        this.mode = mode;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void cycleMode() {
        setMode(this.mode.next());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("RodMode", mode.ordinal());
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("RodMode")) {
            int ordinal = tag.getInt("RodMode");
            ControlRodMode[] values = ControlRodMode.values();
            this.mode = values[Math.abs(ordinal) % values.length];
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}