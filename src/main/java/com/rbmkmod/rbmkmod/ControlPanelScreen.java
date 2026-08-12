package com.rbmkmod.rbmkmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class ControlPanelScreen extends AbstractContainerScreen<ControlPanelMenu> {
    private List<CoreChannelData> syncedChannels = new ArrayList<>();
    private int currentYOffset = 0;

    public ControlPanelScreen(ControlPanelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 240;
    }

    public void updateChannels(List<CoreChannelData> channels, int yOffset) {
        this.syncedChannels = channels;
        this.currentYOffset = yOffset;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Wysuń"), b -> sendButton(0))
                .bounds(x + 10, y + 170, 70, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Grafit"), b -> sendButton(1))
                .bounds(x + 10, y + 191, 70, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("AZ-5 (Bór)"), b -> sendButton(2))
                .bounds(x + 10, y + 212, 70, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Y +1"), b -> sendButton(3))
                .bounds(x + 10, y + 105, 33, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Y -1"), b -> sendButton(4))
                .bounds(x + 47, y + 105, 33, 18).build());
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF1E1E1E);

        int centerX = x + 160;
        int centerY = y + 115;
        int radius = 95;

        guiGraphics.fill(centerX - radius, centerY - radius, centerX + radius, centerY + radius, 0xFF111111);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.drawString(this.font, this.title, x + 10, y + 10, 0x00FF00, false);

        String yText = "Różnica Y: " + (currentYOffset >= 0 ? "+" : "") + currentYOffset;
        guiGraphics.drawString(this.font, yText, x + 10, y + 90, 0xFFFF55, false);

        int centerX = x + 160;
        int centerY = y + 115;
        int cellSize = 3;

        CoreChannelData hoveredChannel = null;

        BlockPos centerPos = menu.getBlockEntity() != null ? menu.getBlockEntity().getBlockPos() : null;

        for (CoreChannelData ch : syncedChannels) {
            int relX = centerPos != null ? ch.pos().getX() - centerPos.getX() : 0;
            int relZ = centerPos != null ? ch.pos().getZ() - centerPos.getZ() : 0;

            int px = centerX + (relX * cellSize);
            int py = centerY + (relZ * cellSize);

            int color = getChannelColor(ch);

            guiGraphics.fill(px, py, px + cellSize - 1, py + cellSize - 1, color);

            if (mouseX >= px && mouseX < px + cellSize && mouseY >= py && mouseY < py + cellSize) {
                hoveredChannel = ch;
            }
        }

        if (hoveredChannel != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§eKanał [" + hoveredChannel.pos().getX() + ", Y:" + hoveredChannel.pos().getY() + ", " + hoveredChannel.pos().getZ() + "]"));

            switch (hoveredChannel.type()) {
                case FUEL -> {
                    tooltip.add(Component.literal("§7Typ: §aBlok Paliwowy (Uran)"));
                    tooltip.add(Component.literal("§7Temp: " + String.format("%.1f°C", hoveredChannel.temperatureC())));
                    tooltip.add(Component.literal("§7Woda: §b" + hoveredChannel.waterMb() + " mB"));
                    tooltip.add(Component.literal("§7Para: §f" + hoveredChannel.steamMb() + " mB"));
                }
                case CONTROL_ROD -> {
                    tooltip.add(Component.literal("§7Typ: §dPręt Kontrolny"));
                    tooltip.add(Component.literal("§7Tryb: §f" + hoveredChannel.rodMode().getDisplayName()));
                }
                case GRAPHITE -> tooltip.add(Component.literal("§7Typ: §8Moderator Grafitowy"));
                case BERYLLIUM -> tooltip.add(Component.literal("§7Typ: §9Reflektor Berylowy"));
            }

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private int getChannelColor(CoreChannelData ch) {
        return switch (ch.type()) {
            case FUEL -> {
                double temp = ch.temperatureC();
                if (temp > 1000) yield 0xFFFF0000;
                if (temp > 400) yield 0xFFFF8800;
                if (temp > 100) yield 0xFFFFFF00;
                yield 0xFF00FF00;
            }
            case CONTROL_ROD -> switch (ch.rodMode()) {
                case BORON -> 0xFFAA00AA;
                case GRAPHITE -> 0xFF00FFFF;
                case RETRACTED -> 0xFF555555;
            };
            case GRAPHITE -> 0xFF888888;
            case BERYLLIUM -> 0xFF3333FF;
        };
    }
}