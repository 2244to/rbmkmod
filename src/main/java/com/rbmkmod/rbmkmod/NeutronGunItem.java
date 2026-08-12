package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Handheld neutron source used to kick-start chain reactions in enriched uranium.
 */
public class NeutronGunItem extends Item {
    private static final int NEUTRONS_PER_SHOT = 5;
    private static final double NEUTRON_SPEED = 0.55;
    private static final int NEUTRON_LIFETIME = 60;
    private static final int COOLDOWN_TICKS = 8;
    private static final double SPREAD = 0.08;

    public NeutronGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            Vec3 look = player.getLookAngle().normalize();
            // Spawn slightly in front of the eyes so the shooter is not hit immediately.
            Vec3 origin = player.getEyePosition().add(look.scale(1.0));
            BlockPos originBlock = BlockPos.containing(origin);

            for (int i = 0; i < NEUTRONS_PER_SHOT; i++) {
                Vec3 direction = applySpread(look, serverLevel);
                NuclearParticleManager.add(serverLevel, new NuclearParticle(
                        NuclearParticle.Type.NEUTRON,
                        origin,
                        direction.scale(NEUTRON_SPEED),
                        NEUTRON_LIFETIME,
                        originBlock));
            }

            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static Vec3 applySpread(Vec3 look, ServerLevel level) {
        double ox = (level.random.nextDouble() - 0.5) * SPREAD;
        double oy = (level.random.nextDouble() - 0.5) * SPREAD;
        double oz = (level.random.nextDouble() - 0.5) * SPREAD;
        return look.add(ox, oy, oz).normalize();
    }
}
