package com.rbmkmod.rbmkmod;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

@EventBusSubscriber(modid = RbmkMod.MODID)
public final class NuclearParticleManager {
    private static final int MAX_PARTICLES_PER_LEVEL = 256;
    private static final Map<ServerLevel, List<NuclearParticle>> PARTICLES = new WeakHashMap<>();
    private static final Map<ServerLevel, List<NuclearParticle>> PENDING = new WeakHashMap<>();

    private NuclearParticleManager() {}

    public static synchronized void add(ServerLevel level, NuclearParticle particle) {
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>()).add(particle);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Przenosimy oczekujące cząstki do głównej listy przed iteracją
        List<NuclearParticle> pending = PENDING.remove(serverLevel);
        List<NuclearParticle> list = PARTICLES.computeIfAbsent(serverLevel, k -> new ArrayList<>());

        if (pending != null) {
            list.addAll(pending);
            while (list.size() > MAX_PARTICLES_PER_LEVEL) {
                list.remove(0);
            }
        }

        if (list.isEmpty()) {
            return;
        }

        Iterator<NuclearParticle> iterator = list.iterator();
        while (iterator.hasNext()) {
            NuclearParticle particle = iterator.next();
            particle.show(serverLevel);
            if (!particle.tick(serverLevel)) {
                iterator.remove();
            }
        }
    }
}