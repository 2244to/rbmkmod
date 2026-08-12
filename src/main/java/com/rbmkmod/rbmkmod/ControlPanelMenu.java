package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class ControlPanelMenu extends AbstractContainerMenu {
    private final ControlPanelBlockEntity blockEntity;

    public ControlPanelMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos(), null);
    }

    public ControlPanelMenu(int containerId, Inventory playerInventory, BlockPos pos, ControlPanelBlockEntity blockEntity) {
        super(ModMenuTypes.CONTROL_PANEL_MENU.get(), containerId);
        if (blockEntity == null && playerInventory.player.level().getBlockEntity(pos) instanceof ControlPanelBlockEntity be) {
            this.blockEntity = be;
        } else {
            this.blockEntity = blockEntity;
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null || player.level().isClientSide()) return true;

        switch (id) {
            case 0 -> blockEntity.setAllRodsMode(ControlRodMode.RETRACTED);
            case 1 -> blockEntity.setAllRodsMode(ControlRodMode.GRAPHITE);
            case 2 -> blockEntity.setAllRodsMode(ControlRodMode.BORON);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public ControlPanelBlockEntity getBlockEntity() {
        return blockEntity;
    }
}