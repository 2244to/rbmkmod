package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lightweight simulation particle used by the reactor, not a persistent world entity. */
public class NuclearParticle {
    public enum Type {
        NEUTRON,
        ALPHA,
        GAMMA
    }

    private final Type type;

    // Typy proste zamiast obiektów Vec3 = zero alokacji pamięci w każdym ticku
    private double posX, posY, posZ;
    private double velX, velY, velZ;

    private int remainingTicks;
    private double neutronEnergyEv;

    private final int originX, originY, originZ;
    private int lastInteractedX = Integer.MIN_VALUE;
    private int lastInteractedY = Integer.MIN_VALUE;
    private int lastInteractedZ = Integer.MIN_VALUE;

    // Jeden reużywalny BlockPos zamiast tworzenia nowego w każdym ticku
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    private boolean damagedPlayer;

    public NuclearParticle(Type type, Vec3 position, Vec3 velocity, int remainingTicks) {
        this(type, position, velocity, remainingTicks, BlockPos.containing(position.x, position.y, position.z));
    }

    public NuclearParticle(Type type, Vec3 position, Vec3 velocity, int remainingTicks, BlockPos sourcePos) {
        this.type = type;
        this.posX = position.x;
        this.posY = position.y;
        this.posZ = position.z;
        this.velX = velocity.x;
        this.velY = velocity.y;
        this.velZ = velocity.z;
        this.remainingTicks = remainingTicks;
        this.neutronEnergyEv = type == Type.NEUTRON ? 2_000_000.0 : 0.0;
        this.originX = sourcePos.getX();
        this.originY = sourcePos.getY();
        this.originZ = sourcePos.getZ();
    }

    public boolean tick(Level level) {
        double prevPosX = posX;
        double prevPosY = posY;
        double prevPosZ = posZ;

        posX += velX;
        posY += velY;
        posZ += velZ;
        remainingTicks--;

        if (!damagedPlayer && (type == Type.NEUTRON || type == Type.GAMMA)) {
            checkPlayerCollision(level);
        }

        if (type == Type.NEUTRON) {
            int bx = Mth.floor(posX);
            int by = Mth.floor(posY);
            int bz = Mth.floor(posZ);

            // Pomijamy blok źródłowy
            if (bx != originX || by != originY || bz != originZ) {
                mutablePos.set(bx, by, bz);
                BlockState hitBlock = level.getBlockState(mutablePos);

                // Szybkie wyjście, jeśli neutron leci w powietrzu
                if (hitBlock.isAir()) {
                    return remainingTicks > 0;
                }

                Block block = hitBlock.getBlock();

                // 0. Stały blok Boru = natychmiastowa absorpcja neutronu
                if (block == ModBlocks.BORON_BLOCK.get()) {
                    return false;
                }

                // 3. Wzbogacony Uran = blok paliwowy: rozszczepienie jąder
                if (block == ModBlocks.ENRICHED_URANIUM_BLOCK.get()) {
                    if (level instanceof ServerLevel serverLevel
                            && level.getBlockEntity(mutablePos) instanceof EnrichedUraniumBlockEntity reactor) {
                        boolean isThermal = neutronEnergyEv < 1000.0;
                        if (isThermal || level.random.nextFloat() < 0.05f) {
                            reactor.receiveNeutron(neutronEnergyEv, serverLevel);
                            return false;
                        } else if (!isSameLastInteraction(bx, by, bz)) {
                            scatterVelocity(level);
                            setLastInteraction(bx, by, bz);
                        }
                    }
                    return remainingTicks > 0;
                }

                // Dla pozostałych bloków sprawdzamy, czy neutron już wszedł w interakcję z tym blokiem
                if (!isSameLastInteraction(bx, by, bz)) {
                    boolean isPurifiedGraphite = (block == ModBlocks.PURIFIED_GRAPHITE_BLOCK.get());

                    // 1. Grafit stacjonarny = moderator: spowalnia i rozprasza neutron
                    if (isPurifiedGraphite || block == ModBlocks.GRAPHITE_BLOCK.get()) {
                        if (!isPurifiedGraphite && level.random.nextFloat() < 0.10f) {
                            return false; // Zanieczyszczenia w zwykłym graficie pochłaniają neutron
                        }

                        double graphiteEffect = getNearbyGraphitePercent(level, bx, by, bz) * 0.01;
                        double purityMultiplier = isPurifiedGraphite ? 1.0 : 0.7;

                        if (level.random.nextDouble() < graphiteEffect * purityMultiplier) {
                            neutronEnergyEv = Math.max(1.0, neutronEnergyEv * 1e-8);
                        }
                        scatterVelocity(level);
                        setLastInteraction(bx, by, bz);

                        // 2. Beryl = reflektor: odbicie zwierciadlane
                    } else if (block instanceof NeutronReflectorBlock reflector) {
                        if (level.random.nextFloat() <= reflector.getReflectionRate()) {
                            int dx = bx - Mth.floor(prevPosX);
                            int dy = by - Mth.floor(prevPosY);
                            int dz = bz - Mth.floor(prevPosZ);

                            if (dx == 0 && dy == 0 && dz == 0) {
                                velX = -velX;
                                velY = -velY;
                                velZ = -velZ;
                            } else {
                                if (dx != 0) velX = -velX;
                                if (dy != 0) velY = -velY;
                                if (dz != 0) velZ = -velZ;
                            }
                        }
                        setLastInteraction(bx, by, bz);

                        // 4. Niewzbogacony blok uranu = rozprasza lub rzadko pochłania
                    } else if (block == ModBlocks.URANIUM_BLOCK.get()) {
                        if (level.random.nextFloat() < 0.35f) {
                            return false; // Pochłonięcie przez U-238
                        }
                        scatterVelocity(level);
                        setLastInteraction(bx, by, bz);

                        // 5. Pręt sterujący = pręt z wyborem trybu
                    } else if (block == ModBlocks.CONTROL_ROD_BLOCK.get()
                            && level.getBlockEntity(mutablePos) instanceof ControlRodBlockEntity controlRod) {

                        switch (controlRod.getMode()) {
                            case BORON -> {
                                return false;
                            }
                            case GRAPHITE -> {
                                double graphiteEffect = getNearbyGraphitePercent(level, bx, by, bz) * 0.01;
                                if (level.random.nextDouble() < graphiteEffect) {
                                    neutronEnergyEv = Math.max(1.0, neutronEnergyEv * 1e-8);
                                }
                                scatterVelocity(level);
                                setLastInteraction(bx, by, bz);
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

    private boolean isSameLastInteraction(int x, int y, int z) {
        return x == lastInteractedX && y == lastInteractedY && z == lastInteractedZ;
    }

    private void setLastInteraction(int x, int y, int z) {
        this.lastInteractedX = x;
        this.lastInteractedY = y;
        this.lastInteractedZ = z;
    }

    /** Rozpraszanie wektora prędkości bez tworzenia żadnych obiektów Vec3 */
    private void scatterVelocity(Level level) {
        double speedSq = velX * velX + velY * velY + velZ * velZ;
        if (speedSq > 1.0e-12) {
            double speed = Math.sqrt(speedSq);
            double invSpeed = 1.0 / speed;

            double nx = (velX * invSpeed) + (level.random.nextDouble() - 0.5) * 0.4;
            double ny = (velY * invSpeed) + (level.random.nextDouble() - 0.5) * 0.4;
            double nz = (velZ * invSpeed) + (level.random.nextDouble() - 0.5) * 0.4;

            double norm = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (norm > 1.0e-12) {
                double scale = speed / norm;
                velX = nx * scale;
                velY = ny * scale;
                velZ = nz * scale;
            }
        }
    }

    private int getNearbyGraphitePercent(Level level, int bx, int by, int bz) {
        if (ControlPanelBlockEntity.PANELS.isEmpty()) {
            return 100;
        }
        int radius = Config.PANEL_SCAN_RADIUS.get();
        int radiusSq = radius * radius;

        for (ControlPanelBlockEntity panel : ControlPanelBlockEntity.PANELS) {
            if (panel.getLevel() == level) {
                BlockPos pPos = panel.getBlockPos();
                int dx = pPos.getX() - bx;
                int dy = pPos.getY() - by;
                int dz = pPos.getZ() - bz;
                if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                    return panel.getGraphitePercent();
                }
            }
        }
        return 100;
    }

    private void checkPlayerCollision(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var players = serverLevel.players();
        if (players.isEmpty()) {
            return;
        }

        float dmg = Config.RADIATION_DAMAGE.get().floatValue();
        if (dmg <= 0.0f) return;

        for (Player player : players) {
            // Bezpośrednie sprawdzenie współrzędnych zamiast alokacji nowego obiektu AABB przez .inflate(0.3)
            AABB box = player.getBoundingBox();
            if (posX >= box.minX - 0.3 && posX <= box.maxX + 0.3
                    && posY >= box.minY - 0.3 && posY <= box.maxY + 0.3
                    && posZ >= box.minZ - 0.3 && posZ <= box.maxZ + 0.3) {
                DamageSource damageSource = serverLevel.damageSources().generic();
                player.hurt(damageSource, dmg);
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

        level.sendParticles(visual, posX, posY, posZ, 1, 0.0, 0.0, 0.0, 0.0);
    }
}