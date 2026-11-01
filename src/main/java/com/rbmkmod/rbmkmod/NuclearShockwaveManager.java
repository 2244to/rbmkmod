package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

import java.util.*;

public class NuclearShockwaveManager {

    private static final List<Shockwave> WAVES = new ArrayList<>();

    private static final double MAX_RADIUS = 180.0;
    private static final double SPEED = 0.35;

    // Gigantyczne cząsteczki (skala 4.0f = maksymalny rozmiar w silniku Minecrafta!)
    private static final DustParticleOptions GIANT_FIREBALL_PARTICLE =
            new DustParticleOptions(new Vector3f(1.0f, 0.42f, 0.05f), 4.0f);
    private static final DustParticleOptions GIANT_DARK_SMOKE_PARTICLE =
            new DustParticleOptions(new Vector3f(0.12f, 0.11f, 0.10f), 4.0f);
    private static final DustParticleOptions GIANT_SHOCKWAVE_DUST =
            new DustParticleOptions(new Vector3f(0.55f, 0.50f, 0.44f), 4.0f);
    private static final DustParticleOptions GIANT_CHERENKOV_BLUE =
            new DustParticleOptions(new Vector3f(0.10f, 0.78f, 1.0f), 4.0f);

    public static void create(ServerLevel level, Vec3 center) {
        WAVES.add(new Shockwave(level, center));
    }

    public static void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(NuclearShockwaveManager.class);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Shockwave> iterator = WAVES.iterator();

        while (iterator.hasNext()) {
            Shockwave wave = iterator.next();

            if (wave.tick()) {
                iterator.remove();
            }
        }
    }

    private static class Shockwave {

        private final ServerLevel level;
        private final Vec3 center;
        private final Set<UUID> hitEntities = new HashSet<>();

        private double radius = 0;
        private int tickCount = 0;

        Shockwave(ServerLevel level, Vec3 center) {
            this.level = level;
            this.center = center;
        }

        /** Pojedyncza cząsteczka z kierunkowym wektorem prędkości (count = 0) */
        private void directionalParticle(
                ParticleOptions type,
                double x, double y, double z,
                double vx, double vy, double vz
        ) {
            for (ServerPlayer player : level.players()) {
                level.sendParticles(
                        player,
                        type,
                        true,
                        x, y, z,
                        0,
                        vx, vy, vz,
                        1.0
                );
            }
        }

        /** Gęsta chmura wielu dużych cząsteczek w jednym pakiecie sieciowym */
        private void clusterParticles(
                ParticleOptions type,
                double x, double y, double z,
                int count,
                double spreadX, double spreadY, double spreadZ,
                double speed
        ) {
            for (ServerPlayer player : level.players()) {
                level.sendParticles(
                        player,
                        type,
                        true,
                        x, y, z,
                        count,
                        spreadX, spreadY, spreadZ,
                        speed
                );
            }
        }

        private void renderShockwave() {
            RandomSource random = level.getRandom();

            // 1. SFERYCZNA KOPUŁA FALI UDERZENIOWEJ
            int spherePoints = 120;
            double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

            for (int i = 0; i < spherePoints; i++) {
                double y = 1.0 - (2.0 * (i + 0.5) / spherePoints);
                if (y < -0.15) continue; // Nie rysujemy fali głęboko pod ziemią

                double horizontal = Math.sqrt(1.0 - y * y);
                double angle = goldenAngle * i;

                double x = Math.cos(angle) * horizontal;
                double z = Math.sin(angle) * horizontal;

                double px = center.x + x * radius;
                double py = center.y + y * radius;
                double pz = center.z + z * radius;

                // Gęste obłoki pary na czole fali
                clusterParticles(ParticleTypes.CLOUD, px, py, pz, 3, 1.2, 1.2, 1.2, 0.08);

                if (i % 3 == 0) {
                    clusterParticles(GIANT_SHOCKWAVE_DUST, px, py, pz, 2, 1.5, 1.5, 1.5, 0.02);
                }

                if (radius < 75 && i % 2 == 0) {
                    directionalParticle(ParticleTypes.FLAME, px, py, pz, x * 0.45, y * 0.45, z * 0.45);
                }
            }

            // 2. NAZIEMNY WAŁ UDERZENIOWY + ODRYWANIE BLOKÓW Z ZIEMI
            int groundPoints = 48;
            for (int i = 0; i < groundPoints; i++) {
                double angle = (Math.PI * 2.0 * i) / groundPoints + (random.nextDouble() * 0.08);
                double dirX = Math.cos(angle);
                double dirZ = Math.sin(angle);

                double gx = center.x + dirX * radius;
                double gz = center.z + dirZ * radius;

                BlockPos surfacePos = level.getHeightmapPos(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(gx, center.y, gz)
                );
                double gy = surfacePos.getY() + 0.5;

                // Gęsty pierścień pyłu i dymu sunący po powierzchni ziemi
                clusterParticles(GIANT_SHOCKWAVE_DUST, gx, gy + 1.5, gz, 5, 2.2, 2.0, 2.2, 0.05);
                clusterParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, gx, gy + 1.0, gz, 2, 1.5, 1.5, 1.5, 0.03);

                if (i % 6 == 0) {
                    clusterParticles(ParticleTypes.EXPLOSION, gx, gy + 1.5, gz, 2, 1.5, 1.5, 1.5, 0.0);
                }

                // Wyrywanie bloków z powierzchni ziemi przez przelatującą falę uderzeniową!
                if (radius < 110 && tickCount % 2 == 0 && i % 12 == 0 && random.nextFloat() < 0.65f) {
                    BlockPos groundBlockPos = surfacePos.below();
                    BlockState groundState = level.getBlockState(groundBlockPos);

                    if (!groundState.isAir()
                            && groundState.getFluidState().isEmpty()
                            && groundState.getDestroySpeed(level, groundBlockPos) >= 0
                            && groundState.getDestroySpeed(level, groundBlockPos) < 50.0f
                            && level.getBlockEntity(groundBlockPos) == null) {

                        // Podrzucamy blok w górę i odpychamy zgodnie z kierunkiem fali
                        FallingBlockEntity flyingGround = FallingBlockEntity.fall(level, groundBlockPos.above(2), groundState);
                        flyingGround.dropItem = false;
                        flyingGround.setDeltaMovement(
                                dirX * (0.4 + random.nextDouble() * 0.6),
                                0.55 + random.nextDouble() * 0.65,
                                dirZ * (0.4 + random.nextDouble() * 0.6)
                        );
                        flyingGround.hurtMarked = true;

                        // Rozbryzg cząsteczek ziemi/kamienia
                        clusterParticles(
                                new BlockParticleOption(ParticleTypes.BLOCK, groundState),
                                gx, gy + 1.0, gz,
                                10, 1.0, 1.0, 1.0, 0.25
                        );

                        // Czasami fala zostawia ogień
                        if (radius < 55 && random.nextFloat() < 0.3f && level.getBlockState(surfacePos).isAir()) {
                            level.setBlockAndUpdate(surfacePos, Blocks.FIRE.defaultBlockState());
                        }
                    }
                }
            }
        }

        private void renderMushroomCloud() {
            RandomSource random = level.getRandom();

            double height = Math.min(radius * 0.95, 95.0);

            // 1. BŁĘKITNY SŁUP PROMIENIOWANIA CZERENKOWA (Zjonizowane powietrze bijące z rdzenia w niebo)
            if (radius < 120) {
                for (int i = 0; i < 12; i++) {
                    double beamY = center.y + random.nextDouble() * (height + 35.0);
                    double beamX = center.x + (random.nextDouble() - 0.5) * 3.5;
                    double beamZ = center.z + (random.nextDouble() - 0.5) * 3.5;

                    clusterParticles(GIANT_CHERENKOV_BLUE, beamX, beamY, beamZ, 3, 1.2, 2.5, 1.2, 0.02);
                    clusterParticles(ParticleTypes.SOUL_FIRE_FLAME, beamX, beamY, beamZ, 2, 1.0, 2.0, 1.0, 0.05);
                }
            }

            // 2. POTĘŻNY, GĘSTY SŁUP GRZYBA ATOMOWEGO (Trzon)
            for (int i = 0; i < 35; i++) {
                double progress = random.nextDouble();
                double y = center.y + progress * height;
                double angle = random.nextDouble() * Math.PI * 2;

                // Kształt klepsydry / lejka (szerszy u podstawy i przy czapie)
                double funnelFactor = (progress < 0.2) ? (1.8 - progress * 4.0) : 1.0;
                double spread = (3.5 + radius * 0.05) * funnelFactor;

                double x = center.x + Math.cos(angle) * spread;
                double z = center.z + Math.sin(angle) * spread;

                // Bardzo duże i gęste kłęby dymu sygnałowego oraz gigantycznego czarnego pyłu
                clusterParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 4, 2.5, 2.5, 2.5, 0.02);
                clusterParticles(GIANT_DARK_SMOKE_PARTICLE, x, y, z, 4, 2.8, 2.8, 2.8, 0.02);

                if (i % 3 == 0) {
                    clusterParticles(GIANT_FIREBALL_PARTICLE, x, y, z, 3, 2.2, 2.2, 2.2, 0.04);
                }
            }

            // 3. GIGANTYCZNA CZAPA GRZYBA ATOMOWEGO + PIERŚCIEŃ WIROWY
            if (radius > 5) {
                double capRadius = Math.min((radius - 5) * 0.65, 62.0);
                double capY = center.y + height;

                for (int i = 0; i < 65; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = Math.sqrt(random.nextDouble()) * capRadius;

                    // Kształt kopuły (wyżej w środku, zawijający się na brzegach)
                    double domeOffset = Math.cos((distance / Math.max(1.0, capRadius)) * (Math.PI * 0.5)) * 16.0;

                    double x = center.x + Math.cos(angle) * distance;
                    double z = center.z + Math.sin(angle) * distance;
                    double y = capY + domeOffset + (random.nextDouble() - 0.5) * 8.0;

                    // Gęste wielkie chmury dymu i ognia wewnątrz czapy grzyba
                    clusterParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 5, 3.5, 3.0, 3.5, 0.02);
                    clusterParticles(GIANT_DARK_SMOKE_PARTICLE, x, y, z, 5, 4.0, 3.0, 4.0, 0.02);
                    clusterParticles(GIANT_FIREBALL_PARTICLE, x, y - 2.0, z, 4, 3.5, 2.5, 3.5, 0.05);

                    if (i % 4 == 0) {
                        clusterParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 2.0, 2.0, 2.0, 0.0);
                    }
                    if (i % 2 == 0) {
                        clusterParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 4, 3.0, 2.5, 3.0, 0.04);
                    }
                }
            }

            // 4. KULA OGNIA (FIREBALL) W EPICENTRUM
            if (radius < 70) {
                double fireballRadius = Math.min(radius * 0.45, 22.0);
                for (int i = 0; i < 20; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = random.nextDouble() * fireballRadius;
                    double x = center.x + Math.cos(angle) * dist;
                    double z = center.z + Math.sin(angle) * dist;
                    double y = center.y + random.nextDouble() * 18.0;

                    clusterParticles(GIANT_FIREBALL_PARTICLE, x, y, z, 5, 2.5, 2.5, 2.5, 0.08);
                    clusterParticles(ParticleTypes.LAVA, x, y, z, 3, 2.0, 2.0, 2.0, 0.2);
                    clusterParticles(ParticleTypes.FLAME, x, y, z, 5, 2.5, 2.5, 2.5, 0.15);
                }

                if (tickCount % 5 == 0) {
                    clusterParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 4.0, center.z, 3, 8.0, 5.0, 8.0, 0.0);
                }
            }

            // 5. OPADAJĄCY RADIOAKTYWNY POPIÓŁ I ŻAR
            if (radius > 10) {
                for (int i = 0; i < 60; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = random.nextDouble() * radius * 0.85;

                    double x = center.x + Math.cos(angle) * distance;
                    double z = center.z + Math.sin(angle) * distance;
                    double y = center.y + random.nextDouble() * (height + 10.0);

                    clusterParticles(ParticleTypes.WHITE_ASH, x, y, z, 4, 3.0, 3.0, 3.0, 0.05);
                    if (i % 4 == 0) {
                        clusterParticles(ParticleTypes.LAVA, x, y, z, 1, 1.0, 1.0, 1.0, 0.05);
                    }
                }
            }
        }

        private void damageAndPushEntities(double previousRadius) {
            AABB searchBox = new AABB(
                    center.x - radius - 4, center.y - 60, center.z - radius - 4,
                    center.x + radius + 4, center.y + 120, center.z + radius + 4
            );

            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, searchBox);

            for (LivingEntity entity : entities) {
                if (hitEntities.contains(entity.getUUID())) {
                    continue;
                }

                double distance = entity.position().distanceTo(center);

                if (distance > previousRadius && distance <= radius + 1.5) {
                    hitEntities.add(entity.getUUID());

                    if (distance <= 60) {
                        entity.hurt(
                                level.damageSources().genericKill(),
                                Float.MAX_VALUE
                        );
                    } else {
                        float damage = (float) ((MAX_RADIUS - distance) * 1.5);
                        entity.hurt(
                                level.damageSources(). explosion(null, null),
                                damage
                        );
                        entity.igniteForSeconds(8);
                    }

                    Vec3 direction = entity.position()
                            .subtract(center)
                            .normalize();

                    double force = Math.min(8.0, (MAX_RADIUS - distance) * 0.08);

                    entity.setDeltaMovement(
                            direction.x * force,
                            Math.max(1.2, direction.y * force + 1.5),
                            direction.z * force
                    );
                    entity.hurtMarked = true;

                    if (entity instanceof ServerPlayer player) {
                        player.connection.send(
                                new ClientboundSetEntityMotionPacket(player)
                        );
                    }
                }
            }
        }

        public boolean tick() {
            double previousRadius = radius;
            radius += SPEED;
            tickCount++;

            // Dudniące wtórne grzmoty w trakcie rozrastania się grzyba atomowego
            if (tickCount % 30 == 0 && radius < 120) {
                level.playSound(
                        null,
                        BlockPos.containing(center.x, center.y, center.z),
                        SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.WEATHER,
                        14.0f,
                        0.5f + level.random.nextFloat() * 0.3f
                );
            }

            renderShockwave();
            renderMushroomCloud();
            damageAndPushEntities(previousRadius);

            return radius >= MAX_RADIUS;
        }
    }
}