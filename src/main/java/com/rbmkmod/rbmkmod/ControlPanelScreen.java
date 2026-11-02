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
    private static final int MIN_SCRAM_TEMP = 300;
    private static final int MAX_SCRAM_TEMP = 2400;

    private List<CoreChannelData> syncedChannels = new ArrayList<>();
    private int currentYOffset = 0;
    private int graphitePercent = 100;
    private float zoomFactor = 1.0f;

    // Nowe pola do automatycznego wyłączenia awaryjnego (Auto AZ-5)
    private int autoScramTemp = 1500;
    private boolean autoScramEnabled = false;

    private ModSlider graphiteSlider;
    private ModSlider zoomSlider;
    private ModSlider scramTempSlider;
    private Button autoScramButton;

    public ControlPanelScreen(ControlPanelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 280;
        this.imageHeight = 256; // Powiększone miejsce na suwak i przycisk Auto AZ-5
        if (menu.getBlockEntity() != null) {
            this.graphitePercent = menu.getBlockEntity().getGraphitePercent();
            this.currentYOffset = menu.getBlockEntity().getSelectedYOffset();
            this.zoomFactor = menu.getBlockEntity().getZoomFactor();
            this.autoScramTemp = menu.getBlockEntity().getAutoScramTemp();
            this.autoScramEnabled = menu.getBlockEntity().isAutoScramEnabled();
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

    // Przeciążona metoda, jeśli chcesz również synchronizować stan Auto-SCRAM z serwera w pakiecie
    public void updateChannels(List<CoreChannelData> channels, int yOffset, int graphitePercent, float zoomFactor, int autoScramTemp, boolean autoScramEnabled) {
        updateChannels(channels, yOffset, graphitePercent, zoomFactor);
        this.autoScramTemp = autoScramTemp;
        this.autoScramEnabled = autoScramEnabled;

        if (scramTempSlider != null && !scramTempSlider.isDragging()) {
            double norm = (double) (autoScramTemp - MIN_SCRAM_TEMP) / (MAX_SCRAM_TEMP - MIN_SCRAM_TEMP);
            scramTempSlider.setSliderValue(norm);
        }
        if (autoScramButton != null) {
            autoScramButton.setMessage(getAutoScramButtonText());
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
                setMessage(Component.translatable("gui.rbmkmod.control_panel.graphite_slider", val));
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
                setMessage(Component.translatable("gui.rbmkmod.control_panel.zoom_slider", String.format("%.1f", z)));
            }

            @Override
            protected void applyValue() {
                zoomFactor = 1.0f + (float) (this.value * 2.0);
                PacketDistributor.sendToServer(new SetPanelSettingPayload(1, zoomFactor));
            }
        };
        this.addRenderableWidget(this.zoomSlider);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.rbmkmod.control_panel.y_up"), b -> sendButton(3))
                .bounds(x + 12, y + 74, 38, 16).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.rbmkmod.control_panel.y_down"), b -> sendButton(4))
                .bounds(x + 54, y + 74, 38, 16).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.rbmkmod.control_panel.retract"), b -> sendButton(0))
                .bounds(x + 12, y + 104, 80, 16).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.rbmkmod.control_panel.mode_graphite"), b -> sendButton(1))
                .bounds(x + 12, y + 122, 80, 16).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.rbmkmod.control_panel.mode_boron"), b -> sendButton(2))
                .bounds(x + 12, y + 140, 80, 16).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.rbmkmod.control_panel.scram"), b -> sendButton(5))
                .bounds(x + 12, y + 158, 80, 18).build());

        // NOWY SUWAK: Maksymalna temperatura paliwa do awaryjnego wyłączenia
        double initialScramTempVal = (double) (this.autoScramTemp - MIN_SCRAM_TEMP) / (MAX_SCRAM_TEMP - MIN_SCRAM_TEMP);
        initialScramTempVal = Math.max(0.0, Math.min(1.0, initialScramTempVal));

        this.scramTempSlider = new ModSlider(x + 12, y + 180, 80, 18, initialScramTempVal) {
            @Override
            protected void updateMessage() {
                int rawTemp = MIN_SCRAM_TEMP + (int) Math.round(this.value * (MAX_SCRAM_TEMP - MIN_SCRAM_TEMP));
                int roundedTemp = (rawTemp / 10) * 10; // Zaokrąglanie co 10°C
                setMessage(Component.translatable("gui.rbmkmod.control_panel.scram_temp_slider", roundedTemp));
            }

            @Override
            protected void applyValue() {
                int rawTemp = MIN_SCRAM_TEMP + (int) Math.round(this.value * (MAX_SCRAM_TEMP - MIN_SCRAM_TEMP));
                autoScramTemp = (rawTemp / 10) * 10;
                // ID 2 = ustawienie temperatury alarmowej Auto-SCRAM
                PacketDistributor.sendToServer(new SetPanelSettingPayload(2, (float) autoScramTemp));
            }
        };
        this.addRenderableWidget(this.scramTempSlider);

        // NOWY PRZYCISK: Włącznik / wyłącznik automatycznego wyłączenia awaryjnego
        this.autoScramButton = Button.builder(getAutoScramButtonText(), b -> {
            this.autoScramEnabled = !this.autoScramEnabled;
            b.setMessage(getAutoScramButtonText());
            // Wysyłamy zarówno przez handleInventoryButtonClick(6), jak i SetPanelSettingPayload(3)
            sendButton(6);
            PacketDistributor.sendToServer(new SetPanelSettingPayload(3, this.autoScramEnabled ? 1.0f : 0.0f));
        }).bounds(x + 12, y + 200, 80, 18).build();
        this.addRenderableWidget(this.autoScramButton);
    }

    private Component getAutoScramButtonText() {
        return this.autoScramEnabled
                ? Component.translatable("gui.rbmkmod.control_panel.auto_scram_on")
                : Component.translatable("gui.rbmkmod.control_panel.auto_scram_off");
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
        int mapH = 218;
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
        int mapH = 218;

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

        guiGraphics.drawString(this.font, Component.translatable("gui.rbmkmod.control_panel.header"), x + 12, y + 8, 0x00FF55, false);

        String yFormatted = (currentYOffset >= 0 ? "+" : "") + currentYOffset;
        guiGraphics.drawString(this.font, Component.translatable("gui.rbmkmod.control_panel.y_level", yFormatted), x + 12, y + 64, 0xFFFF55, false);

        guiGraphics.drawString(this.font, Component.translatable("gui.rbmkmod.control_panel.control_rods_label"), x + 12, y + 93, 0xAAAAAA, false);

        // Podsumowanie telemetrii na dole po lewej
        double maxTemp = 20.0;
        int fuelCount = 0;
        for (CoreChannelData ch : syncedChannels) {
            if (ch.type() == CoreChannelData.Type.FUEL) {
                fuelCount++;
                if (ch.temperatureC() > maxTemp) maxTemp = ch.temperatureC();
            }
        }
        int tempColor = (maxTemp > 1200) ? (int) 0xFFFF2222L
                : (maxTemp > 500)  ? (int) 0xFFFF8800L
                : (maxTemp > 150)  ? (int) 0xFFFFEE00L
                : (int) 0xFF00FF55L;

        guiGraphics.drawString(this.font, Component.translatable("gui.rbmkmod.control_panel.max_temp", String.format("%.0f", maxTemp)), x + 12, y + 224, tempColor, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.rbmkmod.control_panel.fuel_count", fuelCount), x + 12, y + 236, 0xAAAAAA, false);

        int mapX = x + 100;
        int mapY = y + 26;
        int mapW = 170;
        int mapH = 218;
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
            tooltip.add(Component.translatable("tooltip.rbmkmod.channel", hoveredChannel.pos().getX(), hoveredChannel.pos().getY(), hoveredChannel.pos().getZ()));

            switch (hoveredChannel.type()) {
                case FUEL -> {
                    tooltip.add(Component.translatable("tooltip.rbmkmod.type.fuel"));
                    tooltip.add(Component.translatable("tooltip.rbmkmod.temp", String.format("%.1f", hoveredChannel.temperatureC())));
                    tooltip.add(Component.translatable("tooltip.rbmkmod.water", hoveredChannel.waterMb()));
                    tooltip.add(Component.translatable("tooltip.rbmkmod.steam", hoveredChannel.steamMb()));
                }
                case CONTROL_ROD -> {
                    tooltip.add(Component.translatable("tooltip.rbmkmod.type.control_rod"));
                    tooltip.add(Component.translatable("tooltip.rbmkmod.mode", hoveredChannel.rodMode().getDisplayName()));
                    tooltip.add(Component.translatable("tooltip.rbmkmod.click_instruction"));
                }
                case GRAPHITE -> tooltip.add(Component.translatable("tooltip.rbmkmod.type.graphite"));
                case BERYLLIUM -> tooltip.add(Component.translatable("tooltip.rbmkmod.type.beryllium"));
                case BORON -> tooltip.add(Component.translatable("tooltip.rbmkmod.type.boron"));
            }

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private int getChannelColor(CoreChannelData ch) {
        return switch (ch.type()) {
            case FUEL -> {
                double temp = ch.temperatureC();
                if (temp > 1200) yield 0xFFFF2222;
                if (temp > 500) yield 0xFFFF8800;
                if (temp > 150) yield 0xFFFFEE00;
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