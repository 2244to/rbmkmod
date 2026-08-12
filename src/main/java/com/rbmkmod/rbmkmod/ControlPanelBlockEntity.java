package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ControlPanelBlockEntity extends BlockEntity implements MenuProvider {
    private int selectedYOffset = 0;

    public ControlPanelBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CONTROL_PANEL.get(), pos, blockState);
    }

    public int getSelectedYOffset() {
        return selectedYOffset;
    }

    public void setSelectedYOffset(int offset) {
        this.selectedYOffset = Math.max(-50, Math.min(50, offset));
        setChanged();
    }

    public List<CoreChannelData> scanCoreChannelsForY(int yOffset) {
        List<CoreChannelData> channels = new ArrayList<>();
        if (level == null) return channels;

        int radius = 50;
        int radiusSq = radius * radius;
        int targetY = worldPosition.getY() + yOffset;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radiusSq) {
                    BlockPos p = new BlockPos(worldPosition.getX() + x, targetY, worldPosition.getZ() + z);
                    BlockEntity be = level.getBlockEntity(p);
                    BlockState state = level.getBlockState(p);

                    if (be instanceof EnrichedUraniumBlockEntity u) {
                        double tempC = u.getTemperature() - 273.15;
                        int water = u.getWaterTank().getFluidAmount();
                        long steam = u.getSteamTank().getStack().getAmount();
                        channels.add(new CoreChannelData(p, CoreChannelData.Type.FUEL, tempC, water, steam, null));
                    } else if (be instanceof ControlRodBlockEntity rod) {
                        channels.add(new CoreChannelData(p, CoreChannelData.Type.CONTROL_ROD, 20.0, 0, 0, rod.getMode()));
                    } else if (state.is(ModBlocks.PURIFIED_GRAPHITE_BLOCK.get()) || state.is(ModBlocks.GRAPHITE_BLOCK.get())) {
                        channels.add(new CoreChannelData(p, CoreChannelData.Type.GRAPHITE, 20.0, 0, 0, null));
                    } else if (state.is(ModBlocks.BERYLLIUM_BLOCK.get())) {
                        channels.add(new CoreChannelData(p, CoreChannelData.Type.BERYLLIUM, 20.0, 0, 0, null));
                    }
                }
            }
        }

        return channels;
    }

    public void setAllRodsMode(ControlRodMode mode) {
        if (level == null) return;
        int radius = 50;
        int radiusSq = radius * radius;

        BlockPos.betweenClosedStream(
                worldPosition.offset(-radius, -radius, -radius),
                worldPosition.offset(radius, radius, radius)
        ).forEach(p -> {
            if (worldPosition.distSqr(p) <= radiusSq && level.getBlockEntity(p) instanceof ControlRodBlockEntity rod) {
                rod.setMode(mode);
            }
        });
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("SelectedYOffset", selectedYOffset);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("SelectedYOffset")) {
            this.selectedYOffset = tag.getInt("SelectedYOffset");
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("System SKALA - Mnemokomputer RBMK");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ControlPanelMenu(containerId, playerInventory, this.worldPosition, this);
    }
}