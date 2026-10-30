package com.rbmkmod.rbmkmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

public class NuclearShockwaveManager {

    private static final List<Shockwave> WAVES = new ArrayList<>();

    // Zwiększony promień i mniejsza prędkość = fala trwa znacznie dłużej i obejmuje większy obszar
    private static final double MAX_RADIUS = 180.0;
    private static final double SPEED = 0.25;

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
        private final Set<UUID> hitPlayers = new HashSet<>();

        private double radius = 0;

        Shockwave(ServerLevel level, Vec3 center) {
            this.level = level;
            this.center = center;
        }

        private void particle(
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

        private void renderShockwave() {
            int particles = 180; // Zwiększona liczba cząsteczek frontu fali (dwukrotnie więcej)
            double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

            for (int i = 0; i < particles; i++) {
                double y = 1.0 - (2.0 * (i + 0.5) / particles);
                double horizontal = Math.sqrt(1.0 - y * y);
                double angle = goldenAngle * i;

                double x = Math.cos(angle) * horizontal;
                double z = Math.sin(angle) * horizontal;

                double px = center.x + x * radius;
                double py = center.y + y * radius;
                double pz = center.z + z * radius;

                particle(
                        ParticleTypes.END_ROD,
                        px, py, pz,
                        x * 0.15, y * 0.15, z * 0.15
                );

                if (i % 2 == 0) { // Częstszy ogień
                    particle(
                            ParticleTypes.FLAME,
                            px, py, pz,
                            x * 0.4, y * 0.4, z * 0.4
                    );
                }

                particle(
                        ParticleTypes.CLOUD,
                        px, py, pz,
                        x * 0.3, y * 0.3, z * 0.3
                );
            }
        }

        private void renderMushroomCloud() {
            RandomSource random = level.getRandom();

            double height = Math.min(radius * 0.8, 70.0); // Wyższy słup

            // 2. SŁUP DYMU (znacznie większa gęstość)
            for (int i = 0; i < 45; i++) {
                double y = center.y + random.nextDouble() * height;
                double angle = random.nextDouble() * Math.PI * 2;
                double spread = 1.5 + radius * 0.035;

                double x = center.x + Math.cos(angle) * spread;
                double z = center.z + Math.sin(angle) * spread;

                particle(
                        ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                        x, y, z,
                        (random.nextDouble() - 0.5) * 0.15,
                        0.35 + random.nextDouble() * 0.2,
                        (random.nextDouble() - 0.5) * 0.15
                );

                particle(
                        ParticleTypes.LARGE_SMOKE,
                        x, y, z,
                        (random.nextDouble() - 0.5) * 0.1,
                        0.25,
                        (random.nextDouble() - 0.5) * 0.1
                );
            }

            // 3. CZAPA GRZYBA ATOMOWEGO (gęstsza i większa)
            if (radius > 6) {
                double capRadius = Math.min((radius - 6) * 0.5, 45);
                double capY = center.y + height;

                for (int i = 0; i < 90; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = Math.sqrt(random.nextDouble()) * capRadius;

                    double x = center.x + Math.cos(angle) * distance;
                    double z = center.z + Math.sin(angle) * distance;
                    double y = capY + random.nextDouble() * 7;

                    particle(
                            ParticleTypes.LARGE_SMOKE,
                            x, y, z,
                            Math.cos(angle) * 0.3,
                            0.12,
                            Math.sin(angle) * 0.3
                    );

                    if (i % 2 == 0) {
                        particle(
                                ParticleTypes.CLOUD,
                                x, y, z,
                                Math.cos(angle) * 0.4,
                                0.15,
                                Math.sin(angle) * 0.4
                        );
                    }
                }
            }

            // 4. RADIOAKTYWNY PYŁ
            if (radius > 10) {
                for (int i = 0; i < 50; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = random.nextDouble() * radius * 0.7;

                    double x = center.x + Math.cos(angle) * distance;
                    double z = center.z + Math.sin(angle) * distance;
                    double y = center.y + random.nextDouble() * height;

                    particle(
                            ParticleTypes.ASH,
                            x, y, z,
                            (random.nextDouble() - 0.5) * 0.2,
                            -0.12,
                            (random.nextDouble() - 0.5) * 0.2
                    );
                }
            }

            // 5. GORĄCE ODCINKI W CENTRUM
            if (radius < 50) {
                for (int i = 0; i < 16; i++) {
                    double x = center.x + (random.nextDouble() - 0.5) * 10;
                    double z = center.z + (random.nextDouble() - 0.5) * 10;

                    particle(
                            ParticleTypes.FLAME,
                            x, center.y + random.nextDouble() * 10, z,
                            (random.nextDouble() - 0.5) * 0.5,
                            0.5,
                            (random.nextDouble() - 0.5) * 0.5
                    );
                }
            }
        }

        private void damagePlayers(double previousRadius) {
            for (ServerPlayer player : level.players()) {
                if (hitPlayers.contains(player.getUUID())) {
                    continue;
                }

                double distance = player.position().distanceTo(center);

                if (distance > previousRadius && distance <= radius) {
                    hitPlayers.add(player.getUUID());

                    if (distance <= 60) {
                        player.hurt(
                                level.damageSources().genericKill(),
                                Float.MAX_VALUE
                        );
                    } else {
                        float damage = (float) ((MAX_RADIUS - distance) * 1.5);
                        player.hurt(
                                level.damageSources().generic(),
                                damage
                        );
                    }

                    Vec3 direction = player.position()
                            .subtract(center)
                            .normalize();

                    double force = (MAX_RADIUS - distance) * 0.4;

                    player.setDeltaMovement(
                            direction.x * force,
                            Math.max(2.0, direction.y * force + 3.0),
                            direction.z * force
                    );

                    player.connection.send(
                            new ClientboundSetEntityMotionPacket(player)
                    );
                }
            }
        }

        public boolean tick() {
            double previousRadius = radius;

            radius += SPEED;

            renderShockwave();
            renderMushroomCloud();
            damagePlayers(previousRadius);

            return radius >= MAX_RADIUS;
        }
    }
}