package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ControlPanelBlockEntity extends BlockEntity implements MenuProvider {
    public static final List<ControlPanelBlockEntity> PANELS = new CopyOnWriteArrayList<>();

    private int selectedYOffset = 0;
    private int graphitePercent = 100;
    private float zoomFactor = 1.0f;

    // Nowe pola dla automatycznego wyłączenia awaryjnego (Auto AZ-5)
    private int autoScramTemp = 1500;
    private boolean autoScramEnabled = false;

    private List<CoreChannelData> clientSyncedChannels = new ArrayList<>();

    public ControlPanelBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CONTROL_PANEL.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            PANELS.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        PANELS.remove(this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ControlPanelBlockEntity blockEntity) {
        if (level.getGameTime() % 2 == 0) {
            blockEntity.clientSyncedChannels = blockEntity.scanCoreChannelsForY(blockEntity.selectedYOffset);

            // Automatyczny AZ-5: jeśli funkcja jest włączona i paliwo przekroczy limit temperatury
            if (blockEntity.autoScramEnabled && blockEntity.isAnyFuelOverheating()) {
                blockEntity.triggerAz5();
            }

            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /**
     * Sprawdza, czy którykolwiek blok paliwowy w zasięgu panelu osiągnął temperaturę alarmową.
     */
    public boolean isAnyFuelOverheating() {
        if (level == null) return false;

        // 1. Szybkie sprawdzenie aktualnie wyświetlanej warstwy Y
        for (CoreChannelData ch : clientSyncedChannels) {
            if (ch.type() == CoreChannelData.Type.FUEL && ch.temperatureC() >= autoScramTemp) {
                return true;
            }
        }

        // 2. Sprawdzenie pozostałych warstw Y w rdzeniu
        int radius = Config.PANEL_SCAN_RADIUS.get();
        int radiusSq = radius * radius;
        for (BlockPos p : BlockPos.betweenClosed(
                worldPosition.offset(-radius, -radius, -radius),
                worldPosition.offset(radius, radius, radius))) {
            if (worldPosition.distSqr(p) <= radiusSq && level.getBlockEntity(p) instanceof EnrichedUraniumBlockEntity u) {
                double tempC = u.getTemperature() - 273.15;
                if (tempC >= autoScramTemp) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<CoreChannelData> getClientSyncedChannels() {
        return clientSyncedChannels;
    }

    public int getSelectedYOffset() { return selectedYOffset; }
    public void setSelectedYOffset(int offset) { this.selectedYOffset = Math.max(-50, Math.min(50, offset)); setChanged(); }

    public int getGraphitePercent() { return graphitePercent; }

    public void setGraphitePercent(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        this.graphitePercent = clamped;
        setChanged();

        int radius = Config.PANEL_SCAN_RADIUS.get();
        if (level != null && !level.isClientSide()) {
            for (ControlPanelBlockEntity panel : PANELS) {
                if (panel.getLevel() == level && panel.getBlockPos().closerThan(this.worldPosition, radius)) {
                    if (panel.graphitePercent != clamped) {
                        panel.graphitePercent = clamped;
                        panel.setChanged();
                    }
                }
            }
        }
    }

    public float getZoomFactor() { return zoomFactor; }
    public void setZoomFactor(float zoom) { this.zoomFactor = Math.max(1.0f, Math.min(3.0f, zoom)); setChanged(); }

    // Gettery i settery dla Auto AZ-5
    public int getAutoScramTemp() { return autoScramTemp; }
    public void setAutoScramTemp(int temp) {
        this.autoScramTemp = Math.max(300, Math.min(2400, temp));
        setChanged();
    }

    public boolean isAutoScramEnabled() { return autoScramEnabled; }
    public void setAutoScramEnabled(boolean enabled) {
        this.autoScramEnabled = enabled;
        setChanged();
    }

    public void toggleSingleRodMode(BlockPos pos, boolean reverse) {
        if (level == null) return;
        if (level.getBlockEntity(pos) instanceof ControlRodBlockEntity rod) {
            ControlRodMode current = rod.getMode();
            ControlRodMode next = switch (current) {
                case RETRACTED -> reverse ? ControlRodMode.BORON : ControlRodMode.GRAPHITE;
                case GRAPHITE -> reverse ? ControlRodMode.RETRACTED : ControlRodMode.BORON;
                case BORON -> reverse ? ControlRodMode.GRAPHITE : ControlRodMode.RETRACTED;
            };
            rod.setColumnMode(next);
            setChanged();
        }
    }

    public List<CoreChannelData> scanCoreChannelsForY(int yOffset) {
        List<CoreChannelData> channels = new ArrayList<>();
        if (level == null) return channels;

        int radius = Config.PANEL_SCAN_RADIUS.get();
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
                    } else if (state.is(ModBlocks.BORON_BLOCK.get())) {
                        channels.add(new CoreChannelData(p, CoreChannelData.Type.BORON, 20.0, 0, 0, null));
                    }
                }
            }
        }
        return channels;
    }

    public void setAllRodsMode(ControlRodMode mode) {
        if (level == null) return;
        int radius = Config.PANEL_SCAN_RADIUS.get();
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

    /**
     * Procedura awaryjna AZ-5 (SCRAM): zrzuca wszystkie pręty na Bor i obniża skuteczność grafitu do 0%.
     */
    public void triggerAz5() {
        setAllRodsMode(ControlRodMode.BORON);
        setGraphitePercent(0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("SelectedYOffset", selectedYOffset);
        tag.putInt("GraphitePercent", graphitePercent);
        tag.putFloat("ZoomFactor", zoomFactor);
        tag.putInt("AutoScramTemp", autoScramTemp);
        tag.putBoolean("AutoScramEnabled", autoScramEnabled);

        ListTag list = new ListTag();
        for (CoreChannelData ch : clientSyncedChannels) {
            CompoundTag cTag = new CompoundTag();
            cTag.putInt("X", ch.pos().getX());
            cTag.putInt("Y", ch.pos().getY());
            cTag.putInt("Z", ch.pos().getZ());
            cTag.putInt("Type", ch.type().ordinal());
            cTag.putDouble("Temp", ch.temperatureC());
            if (ch.rodMode() != null) cTag.putInt("Rod", ch.rodMode().ordinal());
            list.add(cTag);
        }
        tag.put("SyncedChannels", list);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("SelectedYOffset")) this.selectedYOffset = tag.getInt("SelectedYOffset");
        if (tag.contains("GraphitePercent")) this.graphitePercent = tag.getInt("GraphitePercent");
        if (tag.contains("ZoomFactor")) this.zoomFactor = tag.getFloat("ZoomFactor");
        if (tag.contains("AutoScramTemp")) this.autoScramTemp = tag.getInt("AutoScramTemp");
        if (tag.contains("AutoScramEnabled")) this.autoScramEnabled = tag.getBoolean("AutoScramEnabled");

        if (tag.contains("SyncedChannels")) {
            ListTag list = tag.getList("SyncedChannels", Tag.TAG_COMPOUND);
            this.clientSyncedChannels.clear();
            CoreChannelData.Type[] types = CoreChannelData.Type.values();
            ControlRodMode[] modes = ControlRodMode.values();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag cTag = list.getCompound(i);
                BlockPos p = new BlockPos(cTag.getInt("X"), cTag.getInt("Y"), cTag.getInt("Z"));
                int typeIdx = Math.abs(cTag.getInt("Type")) % types.length;
                CoreChannelData.Type type = types[typeIdx];
                double temp = cTag.getDouble("Temp");
                ControlRodMode rodMode = cTag.contains("Rod") ? modes[Math.abs(cTag.getInt("Rod")) % modes.length] : null;
                this.clientSyncedChannels.add(new CoreChannelData(p, type, temp, 0, 0, rodMode));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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