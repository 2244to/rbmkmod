package com.rbmkmod.rbmkmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class ControlPanelScreen extends AbstractContainerScreen<ControlPanelMenu> {
    private List<CoreChannelData> syncedChannels = new ArrayList<>();
    private int currentYOffset = 0;
    private int graphitePercent = 100;
    private float zoomFactor = 1.0f;

    private ModSlider graphiteSlider;
    private ModSlider zoomSlider;

    public ControlPanelScreen(ControlPanelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 280;
        this.imageHeight = 230;
        if (menu.getBlockEntity() != null) {
            this.graphitePercent = menu.getBlockEntity().getGraphitePercent();
            this.currentYOffset = menu.getBlockEntity().getSelectedYOffset();
            this.zoomFactor = menu.getBlockEntity().getZoomFactor();
        }
    }

    public void updateChannels(List<CoreChannelData> channels, int yOffset, int graphitePercent, float zoomFactor) {
        this.syncedChannels = channels;
        this.currentYOffset = yOffset;
        this.graphitePercent = graphitePercent;
        this.zoomFactor = zoomFactor;

        if (graphiteSlider != null && !graphiteSlider.isDragging()) {
            graphiteSlider.setSliderValue(graphitePercent / 100.0);
        }
        if (zoomSlider != null && !zoomSlider.isDragging()) {
            zoomSlider.setSliderValue((zoomFactor - 1.0f) / 2.0f);
        }
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        double initialGraphiteVal = this.graphitePercent / 100.0;
        this.graphiteSlider = new ModSlider(x + 12, y + 24, 80, 18, initialGraphiteVal) {
            @Override
            protected void updateMessage() {
                int val = (int) Math.round(this.value * 100.0);
                setMessage(Component.literal("Grafit: " + val + "%"));
            }

            @Override
            protected void applyValue() {
                graphitePercent = (int) Math.round(this.value * 100.0);
                PacketDistributor.sendToServer(new SetPanelSettingPayload(0, (float) graphitePercent));
            }
        };
        this.addRenderableWidget(this.graphiteSlider);

        double initialZoomVal = (this.zoomFactor - 1.0) / 2.0;
        this.zoomSlider = new ModSlider(x + 12, y + 44, 80, 18, initialZoomVal) {
            @Override
            protected void updateMessage() {
                float z = 1.0f + (float) (this.value * 2.0);
                setMessage(Component.literal(String.format("Zoom: %.1fx", z)));
            }

            @Override
            protected void applyValue() {
                zoomFactor = 1.0f + (float) (this.value * 2.0);
                PacketDistributor.sendToServer(new SetPanelSettingPayload(1, zoomFactor));
            }
        };
        this.addRenderableWidget(this.zoomSlider);

        this.addRenderableWidget(Button.builder(Component.literal("Y +1"), b -> sendButton(3))
                .bounds(x + 12, y + 76, 38, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Y -1"), b -> sendButton(4))
                .bounds(x + 54, y + 76, 38, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Wysuń"), b -> sendButton(0))
                .bounds(x + 12, y + 110, 80, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Grafit"), b -> sendButton(1))
                .bounds(x + 12, y + 130, 80, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Bor"), b -> sendButton(2))
                .bounds(x + 12, y + 150, 80, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("§c§lAZ-5 (SCRAM)"), b -> sendButton(5))
                .bounds(x + 12, y + 174, 80, 20).build());
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        int mapX = x + 100;
        int mapY = y + 26;
        int mapW = 170;
        int mapH = 192;
        float mapCenterX = mapX + mapW / 2.0f;
        float mapCenterY = mapY + mapH / 2.0f;

        BlockPos centerPos = menu.getBlockEntity() != null ? menu.getBlockEntity().getBlockPos() : null;

        if (!syncedChannels.isEmpty() && centerPos != null) {
            int minRelX = Integer.MAX_VALUE, maxRelX = Integer.MIN_VALUE;
            int minRelZ = Integer.MAX_VALUE, maxRelZ = Integer.MIN_VALUE;

            for (CoreChannelData ch : syncedChannels) {
                int relX = ch.pos().getX() - centerPos.getX();
                int relZ = ch.pos().getZ() - centerPos.getZ();
                if (relX < minRelX) minRelX = relX;
                if (relX > maxRelX) maxRelX = relX;
                if (relZ < minRelZ) minRelZ = relZ;
                if (relZ > maxRelZ) maxRelZ = relZ;
            }

            int gridW = Math.max(1, maxRelX - minRelX + 1);
            int gridH = Math.max(1, maxRelZ - minRelZ + 1);

            float availableW = mapW - 12.0f;
            float availableH = mapH - 12.0f;
            float cellSize = Math.min(availableW / gridW, availableH / gridH) * zoomFactor;
            cellSize = Math.min(cellSize, 24.0f);
            cellSize = Math.max(cellSize, 1.0f);

            float drawSize = Math.max(1.0f, cellSize - (cellSize >= 3.0f ? 1.0f : 0.0f));
            float centerGridX = (minRelX + maxRelX) / 2.0f;
            float centerGridZ = (minRelZ + maxRelZ) / 2.0f;

            for (CoreChannelData ch : syncedChannels) {
                if (ch.type() == CoreChannelData.Type.CONTROL_ROD) {
                    int relX = ch.pos().getX() - centerPos.getX();
                    int relZ = ch.pos().getZ() - centerPos.getZ();

                    float px = mapCenterX + (relX - centerGridX) * cellSize - (drawSize / 2.0f);
                    float py = mapCenterY + (relZ - centerGridZ) * cellSize - (drawSize / 2.0f);

                    if (mouseX >= px && mouseX < px + cellSize && mouseY >= py && mouseY < py + cellSize) {
                        boolean reverse = (button == 1);
                        PacketDistributor.sendToServer(new ToggleRodModePayload(ch.pos(), reverse));
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF141814);
        guiGraphics.renderOutline(x, y, this.imageWidth, this.imageHeight, 0xFF2A3A2A);

        guiGraphics.fill(x + 10, y + 20, x + this.imageWidth - 10, y + 21, 0xFF2A3A2A);

        int mapX = x + 100;
        int mapY = y + 26;
        int mapW = 170;
        int mapH = 192;

        guiGraphics.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF0A0D0A);
        guiGraphics.renderOutline(mapX, mapY, mapW, mapH, 0xFF00AA55);

        int centerX = mapX + mapW / 2;
        int centerY = mapY + mapH / 2;
        guiGraphics.fill(centerX - 1, mapY + 4, centerX + 1, mapY + mapH - 4, 0x1500FF55);
        guiGraphics.fill(mapX + 4, centerY - 1, mapX + mapW - 4, centerY + 1, 0x1500FF55);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.drawString(this.font, "SYSTEM SKALA - MONIT RBMK", x + 12, y + 8, 0x00FF55, false);

        String yText = "Poziom Y: " + (currentYOffset >= 0 ? "+" : "") + currentYOffset;
        guiGraphics.drawString(this.font, yText, x + 12, y + 65, 0xFFFF55, false);

        guiGraphics.drawString(this.font, "PRĘTY STERUJĄCE", x + 12, y + 98, 0xAAAAAA, false);

        // Podsumowanie telemetrii na dole po lewej
        double maxTemp = 20.0;
        int fuelCount = 0;
        for (CoreChannelData ch : syncedChannels) {
            if (ch.type() == CoreChannelData.Type.FUEL) {
                fuelCount++;
                if (ch.temperatureC() > maxTemp) maxTemp = ch.temperatureC();
            }
        }
        int tempColor = maxTemp > 1000 ? 0xFF3333 : (maxTemp > 400 ? 0xFFAA00 : 0x00FF55);
        guiGraphics.drawString(this.font, String.format("T max: %.0f°C", maxTemp), x + 12, y + 198, tempColor, false);
        guiGraphics.drawString(this.font, "Paliwo: " + fuelCount, x + 12, y + 210, 0xAAAAAA, false);

        int mapX = x + 100;
        int mapY = y + 26;
        int mapW = 170;
        int mapH = 192;
        float mapCenterX = mapX + mapW / 2.0f;
        float mapCenterY = mapY + mapH / 2.0f;

        CoreChannelData hoveredChannel = null;
        BlockPos centerPos = menu.getBlockEntity() != null ? menu.getBlockEntity().getBlockPos() : null;

        if (!syncedChannels.isEmpty()) {
            int minRelX = Integer.MAX_VALUE, maxRelX = Integer.MIN_VALUE;
            int minRelZ = Integer.MAX_VALUE, maxRelZ = Integer.MIN_VALUE;

            for (CoreChannelData ch : syncedChannels) {
                int relX = centerPos != null ? ch.pos().getX() - centerPos.getX() : 0;
                int relZ = centerPos != null ? ch.pos().getZ() - centerPos.getZ() : 0;
                if (relX < minRelX) minRelX = relX;
                if (relX > maxRelX) maxRelX = relX;
                if (relZ < minRelZ) minRelZ = relZ;
                if (relZ > maxRelZ) maxRelZ = relZ;
            }

            int gridW = Math.max(1, maxRelX - minRelX + 1);
            int gridH = Math.max(1, maxRelZ - minRelZ + 1);

            float availableW = mapW - 12.0f;
            float availableH = mapH - 12.0f;
            float cellSize = Math.min(availableW / gridW, availableH / gridH) * zoomFactor;
            cellSize = Math.min(cellSize, 24.0f);
            cellSize = Math.max(cellSize, 1.0f);

            float gap = cellSize >= 3.0f ? 1.0f : 0.0f;
            float drawSize = Math.max(1.0f, cellSize - gap);

            float centerGridX = (minRelX + maxRelX) / 2.0f;
            float centerGridZ = (minRelZ + maxRelZ) / 2.0f;

            for (CoreChannelData ch : syncedChannels) {
                int relX = centerPos != null ? ch.pos().getX() - centerPos.getX() : 0;
                int relZ = centerPos != null ? ch.pos().getZ() - centerPos.getZ() : 0;

                float px = mapCenterX + (relX - centerGridX) * cellSize - (drawSize / 2.0f);
                float py = mapCenterY + (relZ - centerGridZ) * cellSize - (drawSize / 2.0f);

                if (px >= mapX + 2 && px + drawSize <= mapX + mapW - 2 && py >= mapY + 2 && py + drawSize <= mapY + mapH - 2) {
                    int color = getChannelColor(ch);
                    guiGraphics.fill((int) px, (int) py, (int) (px + drawSize), (int) (py + drawSize), color);

                    if (mouseX >= px && mouseX < px + cellSize && mouseY >= py && mouseY < py + cellSize) {
                        hoveredChannel = ch;
                    }
                }
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
                    tooltip.add(Component.literal("§7Typ: §dPręt Kontrolny (Kolumna)"));
                    tooltip.add(Component.literal("§7Tryb: §f" + hoveredChannel.rodMode().getDisplayName()));
                    tooltip.add(Component.literal("§8[Kliknij LPM/PPM, aby przełączyć kolumnę]"));
                }
                case GRAPHITE -> tooltip.add(Component.literal("§7Typ: §8Moderator Grafitowy"));
                case BERYLLIUM -> tooltip.add(Component.literal("§7Typ: §9Reflektor Berylowy"));
                case BORON -> tooltip.add(Component.literal("§7Typ: §5Stały Absorber Borowy"));
            }

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private int getChannelColor(CoreChannelData ch) {
        return switch (ch.type()) {
            case FUEL -> {
                double temp = ch.temperatureC();
                if (temp > 1000) yield 0xFFFF2222;
                if (temp > 400) yield 0xFFFF8800;
                if (temp > 100) yield 0xFFFFEE00;
                yield 0xFF00FF55;
            }
            case CONTROL_ROD -> switch (ch.rodMode()) {
                case BORON -> 0xFFCC00FF;
                case GRAPHITE -> 0xFF00E5FF;
                case RETRACTED -> 0xFF666666;
            };
            case GRAPHITE -> 0xFF4A4A4A;
            case BERYLLIUM -> 0xFF2255FF;
            case BORON -> 0xFF770099;
        };
    }

    private static abstract class ModSlider extends AbstractSliderButton {
        private boolean dragging = false;

        public ModSlider(int x, int y, int width, int height, double initialValue) {
            super(x, y, width, height, Component.empty(), initialValue);
            this.updateMessage();
        }

        public void setSliderValue(double val) {
            this.value = Math.max(0.0, Math.min(1.0, val));
            this.updateMessage();
        }

        public boolean isDragging() {
            return this.dragging;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            this.dragging = true;
            super.onClick(mouseX, mouseY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            this.dragging = false;
            super.onRelease(mouseX, mouseY);
        }
    }
}