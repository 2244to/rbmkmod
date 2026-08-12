package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
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

    public ControlPanelBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CONTROL_PANEL.get(), pos, blockState);
    }

    public List<ControlRodBlockEntity> findNearbyRods() {
        List<ControlRodBlockEntity> rods = new ArrayList<>();
        if (level == null) return rods;

        int radius = 50;
        int radiusSq = radius * radius;

        BlockPos.betweenClosedStream(
                worldPosition.offset(-radius, -radius, -radius),
                worldPosition.offset(radius, radius, radius)
        ).forEach(p -> {
            if (worldPosition.distSqr(p) <= radiusSq) {
                if (level.getBlockEntity(p) instanceof ControlRodBlockEntity rod) {
                    rods.add(rod);
                }
            }
        });

        return rods;
    }

    public void setAllRodsMode(ControlRodMode mode) {
        for (ControlRodBlockEntity rod : findNearbyRods()) {
            rod.setMode(mode);
        }
    }

    public int getFoundRodsCount() {
        return findNearbyRods().size();
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Panel Sterowania Reaktorem");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ControlPanelMenu(containerId, playerInventory, this.worldPosition, this);
    }
}