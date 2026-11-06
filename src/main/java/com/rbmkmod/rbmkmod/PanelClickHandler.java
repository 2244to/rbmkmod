package com.rbmkmod.rbmkmod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Wykrywa prawy klik w przyciski panelu w świecie (bez GUI). Działa po stronie klienta,
 * bo panel jest wizualnie większy niż jego komórka bloku.
 */
@EventBusSubscriber(modid = "rbmkmod", value = Dist.CLIENT)
public class PanelClickHandler {

    private static final int SCAN_RADIUS = 7;

    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.screen != null) return;

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()));

        // Dystans do zwykłego bloku, który mógłby zasłaniać panel (np. ściana)
        double blockDist = Double.MAX_VALUE;
        HitResult hr = mc.hitResult;
        if (hr instanceof BlockHitResult bhr && hr.getType() == HitResult.Type.BLOCK
                && !(level.getBlockState(bhr.getBlockPos()).getBlock() instanceof ControlPanelBlock)) {
            blockDist = hr.getLocation().distanceTo(eye);
        }

        BlockPos bestPos = null;
        PanelButton.Hit bestHit = null;

        BlockPos base = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(
                base.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                base.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof ControlPanelBlock)) continue;

            Direction facing = state.getValue(ControlPanelBlock.FACING);
            PanelButton.Hit hit = PanelButton.raycast(p, facing, eye, end);
            if (hit != null && (bestHit == null || hit.t() < bestHit.t())) {
                bestHit = hit;
                bestPos = p.immutable();
            }
        }

        if (bestHit == null) return; // nie trafiono w przycisk -> normalne zachowanie (GUI)

        double hitDist = bestHit.t() * eye.distanceTo(end);
        if (hitDist > blockDist) return; // coś stoi na drodze

        event.setCanceled(true);
        event.setSwingHand(true);

        int value = bestHit.button().slider ? bestHit.button().valueFor(bestHit.frac()) : 0;
        PacketDistributor.sendToServer(
                new PanelNetwork.ClickPayload(bestPos, bestHit.button().ordinal(), value));
    }
}
