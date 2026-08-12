package com.rbmkmod.rbmkmod;

public enum ControlRodMode {
    RETRACTED("Wysunięty (Przelotny)"),
    GRAPHITE("Moderator (Grafit)"),
    BORON("Absorber (Bór)");

    private final String displayName;

    ControlRodMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ControlRodMode next() {
        ControlRodMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}