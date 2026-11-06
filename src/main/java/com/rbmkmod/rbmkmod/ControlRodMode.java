package com.rbmkmod.rbmkmod;

public enum ControlRodMode {
    RETRACTED("item.rbmkmod.control_rod_mode.retracted"),
    GRAPHITE("item.rbmkmod.control_rod_mode.graphite"),
    BORON("item.rbmkmod.control_rod_mode.boron");

    private final String displayName;

    ControlRodMode(String displayName) {
        this.displayName = displayName;
    }

    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable(displayName);
    }

    public ControlRodMode next() {
        ControlRodMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
