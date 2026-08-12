package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;

public record UraniumStat(BlockPos pos, double temperatureCelcius, int waterMb, long steamMb) {
}