package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Lightweight simulation particle used by the reactor, not a persistent world entity. */
public class NuclearParticle {
    public enum Type {
        NEUTRON,
        ALPHA,
        GAMMA
    }

    private final Type type;
    private Vec3 position;
    private Vec3 velocity;
    private int remainingTicks;
    private double neutronEnergyEv;
    private BlockPos lastInteraction;
    private BlockPos originPos;
    private boolean damagedPlayer;

    public NuclearParticle(Type type, Vec3 position, Vec3 velocity, int remainingTicks) {
        this(type, position, velocity, remainingTicks, BlockPos.containing(position.x, position.y, position.z));
    }

    public NuclearParticle(Type type, Vec3 position, Vec3 velocity, int remainingTicks, BlockPos sourcePos) {
        this.type = type;
        this.position = position;
        this.velocity = velocity;
        this.remainingTicks = remainingTicks;
        this.neutronEnergyEv = type == Type.NEUTRON ? 2_000_000 : 0;
        this.originPos = sourcePos;
    }

    public boolean tick(Level level) {
        position = position.add(velocity);
        remainingTicks--;

        if (type == Type.NEUTRON || type == Type.GAMMA) {
            checkPlayerCollision(level);
        }

        if (type == Type.NEUTRON) {
            BlockPos blockPos = BlockPos.containing(position.x, position.y, position.z);

            if (!blockPos.equals(originPos)) {
                BlockState hitBlock = level.getBlockState(blockPos);

                int graphitePercent = getNearbyGraphitePercent(level, blockPos);
                double graphiteEffect = graphitePercent / 100.0;

                // 1. Grafit stacjonarny = moderator: spowalnia i rozprasza neutron
                if (hitBlock.is(ModBlocks.PURIFIED_GRAPHITE_BLOCK.get()) || hitBlock.is(ModBlocks.GRAPHITE_BLOCK.get())) {
                    if (!blockPos.equals(lastInteraction)) {
                        if (level.random.nextDouble() < graphiteEffect) {
                            neutronEnergyEv = Math.max(1.0, neutronEnergyEv * 1e-8);
                        }
                        double speed = velocity.length();
                        if (speed > 1.0e-6) {
                            Vec3 scatter = new Vec3(
                                    level.random.nextDouble() - 0.5,
                                    level.random.nextDouble() - 0.5,
                                    level.random.nextDouble() - 0.5
                            ).scale(0.4);
                            velocity = velocity.normalize().add(scatter).normalize().scale(speed);
                        }
                        lastInteraction = blockPos;
                    }

                    // 2. Beryl = reflektor: odbicie zwierciadlane
                } else if (hitBlock.is(ModBlocks.BERYLLIUM_BLOCK.get())) {
                    if (!blockPos.equals(lastInteraction)) {
                        Vec3 prevPos = position.subtract(velocity);
                        BlockPos prevBlockPos = BlockPos.containing(prevPos.x, prevPos.y, prevPos.z);

                        int dx = blockPos.getX() - prevBlockPos.getX();
                        int dy = blockPos.getY() - prevBlockPos.getY();
                        int dz = blockPos.getZ() - prevBlockPos.getZ();

                        double vx = (dx != 0) ? -velocity.x : velocity.x;
                        double vy = (dy != 0) ? -velocity.y : velocity.y;
                        double vz = (dz != 0) ? -velocity.z : velocity.z;

                        if (dx == 0 && dy == 0 && dz == 0) {
                            velocity = velocity.scale(-1.0);
                        } else {
                            velocity = new Vec3(vx, vy, vz);
                        }

                        lastInteraction = blockPos;
                    }

                    // 3. Uran = blok paliwowy: rozszczepienie jąder
                } else if (hitBlock.is(ModBlocks.ENRICHED_URANIUM_BLOCK.get())
                        && level instanceof ServerLevel serverLevel
                        && level.getBlockEntity(blockPos) instanceof EnrichedUraniumBlockEntity reactor) {

                    if (!blockPos.equals(originPos)) {
                        boolean isThermal = neutronEnergyEv < 1000.0;

                        if (isThermal || level.random.nextFloat() < 0.05f) {
                            reactor.receiveNeutron(neutronEnergyEv, serverLevel);
                            return false;
                        } else {
                            Vec3 scatter = new Vec3(
                                    level.random.nextDouble() - 0.5,
                                    level.random.nextDouble() - 0.5,
                                    level.random.nextDouble() - 0.5
                            ).scale(0.4);
                            velocity = velocity.normalize().add(scatter).normalize().scale(velocity.length());
                        }
                    }

                    // 4. Pręt sterujący = pręt z wyborem trybu
                } else if (hitBlock.is(ModBlocks.CONTROL_ROD_BLOCK.get())
                        && level.getBlockEntity(blockPos) instanceof ControlRodBlockEntity controlRod) {

                    if (!blockPos.equals(lastInteraction)) {
                        switch (controlRod.getMode()) {
                            case BORON -> {
                                return false;
                            }
                            case GRAPHITE -> {
                                if (level.random.nextDouble() < graphiteEffect) {
                                    neutronEnergyEv = Math.max(1.0, neutronEnergyEv * 1e-8);
                                }
                                double speed = velocity.length();
                                if (speed > 1.0e-6) {
                                    Vec3 scatter = new Vec3(
                                            level.random.nextDouble() - 0.5,
                                            level.random.nextDouble() - 0.5,
                                            level.random.nextDouble() - 0.5
                                    ).scale(0.4);
                                    velocity = velocity.normalize().add(scatter).normalize().scale(speed);
                                }
                                lastInteraction = blockPos;
                            }
                            case RETRACTED -> {
                            }
                        }
                    }
                }
            }
        }
        return remainingTicks > 0;
    }

    private int getNearbyGraphitePercent(Level level, BlockPos pos) {
        for (ControlPanelBlockEntity panel : ControlPanelBlockEntity.PANELS) {
            if (panel.getLevel() == level && panel.getBlockPos().closerThan(pos, 50)) {
                return panel.getGraphitePercent();
            }
        }
        return 100;
    }

    private void checkPlayerCollision(Level level) {
        if (!(level instanceof ServerLevel serverLevel) || damagedPlayer) {
            return;
        }

        for (Player player : serverLevel.players()) {
            if (player.getBoundingBox().inflate(0.3).contains(position)) {
                DamageSource damageSource = serverLevel.damageSources().generic();
                player.hurt(damageSource, 0.5f);
                damagedPlayer = true;
                return;
            }
        }
    }

    public void show(ServerLevel level) {
        ParticleOptions visual = switch (type) {
            case NEUTRON -> ParticleTypes.ELECTRIC_SPARK;
            case ALPHA -> ParticleTypes.FLAME;
            case GAMMA -> ParticleTypes.END_ROD;
        };

        level.sendParticles(visual, position.x, position.y, position.z, 1, 0.0, 0.0, 0.0, 0.0);
    }
}