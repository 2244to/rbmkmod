package com.rbmkmod.rbmkmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ControlPanelScreen extends AbstractContainerScreen<ControlPanelMenu> {

    public ControlPanelScreen(ControlPanelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 146;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Wysuń wszystkie"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            }
        }).bounds(x + 10, y + 35, 156, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Ustaw Grafit (Moderator)"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
            }
        }).bounds(x + 10, y + 60, 156, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("AZ-5: Ustaw Bór (Absorber)"), b -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 2);
            }
        }).bounds(x + 10, y + 85, 156, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF2B2B2B);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.drawString(this.font, this.title, x + 10, y + 8, 0xFFFFFF, false);

        int count = menu.getBlockEntity() != null ? menu.getBlockEntity().getFoundRodsCount() : 0;
        guiGraphics.drawString(this.font, "Wykryte pręty (r=50): " + count, x + 10, y + 22, 0x00FF00, false);

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}