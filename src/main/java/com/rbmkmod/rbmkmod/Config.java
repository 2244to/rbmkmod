package com.rbmkmod.rbmkmod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue EXPLOSION_TEMPERATURE = BUILDER
            .comment("Temperatura (w Kelwinach), przy której blok paliwowy eksploduje (domyślnie 2773.15 K = 2500°C)")
            .defineInRange("explosionTemperature", 2773.15, 500.0, 10000.0);

    public static final ModConfigSpec.IntValue PANEL_SCAN_RADIUS = BUILDER
            .comment("Promień skanowania reaktora przez panel SKALA (w blokach)")
            .defineInRange("panelScanRadius", 50, 10, 128);

    public static final ModConfigSpec.DoubleValue RADIATION_DAMAGE = BUILDER
            .comment("Obrażenia zadawane graczowi przez pojedynczą cząstkę promieniowania")
            .defineInRange("radiationDamage", 0.5, 0.0, 20.0);

    public static final ModConfigSpec.IntValue STEAM_PER_WATER_MB = BUILDER
            .comment("Ilość mB pary Mekanism produkowanej z 1 mB wody")
            .defineInRange("steamPerWaterMb", 10, 1, 100);

    static final ModConfigSpec SPEC = BUILDER.build();
}