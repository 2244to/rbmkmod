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
        if (this.mode == mode) return;
        this.mode = mode;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Ustawia tryb dla tego bloku oraz wszystkich połączonych w pionie bloków prętów sterujących.
     */
    public void setColumnMode(ControlRodMode mode) {
        setMode(mode);
        if (level == null || level.isClientSide) return;

        // Propagacja w górę
        BlockPos.MutableBlockPos cursor = worldPosition.mutable().move(0, 1, 0);
        while (level.getBlockEntity(cursor) instanceof ControlRodBlockEntity rodAbove) {
            rodAbove.setMode(mode);
            cursor.move(0, 1, 0);
        }

        // Propagacja w dół
        cursor.set(worldPosition).move(0, -1, 0);
        while (level.getBlockEntity(cursor) instanceof ControlRodBlockEntity rodBelow) {
            rodBelow.setMode(mode);
            cursor.move(0, -1, 0);
        }
    }

    public void cycleMode() {
        setColumnMode(this.mode.next());
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