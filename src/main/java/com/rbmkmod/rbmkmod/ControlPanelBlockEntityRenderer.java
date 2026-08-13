package com.rbmkmod.rbmkmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

import java.util.List;

public class ControlPanelBlockEntityRenderer implements BlockEntityRenderer<ControlPanelBlockEntity> {

    public ControlPanelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ControlPanelBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        List<CoreChannelData> channels = blockEntity.getClientSyncedChannels();
        if (channels == null || channels.isEmpty()) return;

        poseStack.pushPose();

        // Podniesienie całego kineskopu 1 cm nad górną ściankę bloku (Y = 1.01)
        poseStack.translate(0.5f, 1.01f, 0.5f);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));

        VertexConsumer builder = bufferSource.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();

        // WARSTWA 0: Tło ekranu monitora (z = 0.000f)
        drawQuad(builder, matrix, -0.42f, -0.42f, 0.000f, 0.84f, 0.84f, 0x0A, 0x0D, 0x0A, 255);

        BlockPos centerPos = blockEntity.getBlockPos();
        int minRelX = Integer.MAX_VALUE, maxRelX = Integer.MIN_VALUE;
        int minRelZ = Integer.MAX_VALUE, maxRelZ = Integer.MIN_VALUE;

        for (CoreChannelData ch : channels) {
            int relX = ch.pos().getX() - centerPos.getX();
            int relZ = ch.pos().getZ() - centerPos.getZ();
            if (relX < minRelX) minRelX = relX;
            if (relX > maxRelX) maxRelX = relX;
            if (relZ < minRelZ) minRelZ = relZ;
            if (relZ > maxRelZ) maxRelZ = relZ;
        }

        int gridW = Math.max(1, maxRelX - minRelX + 1);
        int gridH = Math.max(1, maxRelZ - minRelZ + 1);

        float mapSize = 0.76f;
        float cellSize = Math.min(mapSize / gridW, mapSize / gridH);
        cellSize = Math.min(cellSize, 0.08f);

        float gap = cellSize >= 0.02f ? 0.004f : 0.0f;
        float drawSize = Math.max(0.005f, cellSize - gap);

        float centerGridX = (minRelX + maxRelX) / 2.0f;
        float centerGridZ = (minRelZ + maxRelZ) / 2.0f;

        // WARSTWA 1: Kafelki kanałów (z = 0.002f - wysunięte nad czarne tło)
        for (CoreChannelData ch : channels) {
            int relX = ch.pos().getX() - centerPos.getX();
            int relZ = ch.pos().getZ() - centerPos.getZ();

            float px = (relX - centerGridX) * cellSize - (drawSize / 2.0f);
            float py = (relZ - centerGridZ) * cellSize - (drawSize / 2.0f);

            int color = getChannelColor(ch);
            int a = (color >> 24) & 0xFF;
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            drawQuad(builder, matrix, px, py, 0.002f, drawSize, drawSize, r, g, b, a);
        }

        poseStack.popPose();
    }

    private void drawQuad(VertexConsumer builder, Matrix4f matrix, float x, float y, float z, float w, float h, int r, int g, int b, int a) {
        builder.addVertex(matrix, x, y, z).setColor(r, g, b, a);
        builder.addVertex(matrix, x + w, y, z).setColor(r, g, b, a);
        builder.addVertex(matrix, x + w, y + h, z).setColor(r, g, b, a);
        builder.addVertex(matrix, x, y + h, z).setColor(r, g, b, a);
    }

    private int getChannelColor(CoreChannelData ch) {
        return switch (ch.type()) {
            case FUEL -> {
                double temp = ch.temperatureC();
                if (temp > 1000) yield 0xFFFF2222;
                if (temp > 400) yield 0xFFFF8800;
                if (temp > 100) yield 0xFFFFEE00;
                yield 0xFF00FF55;
            }
            case CONTROL_ROD -> switch (ch.rodMode()) {
                case BORON -> 0xFFCC00FF;
                case GRAPHITE -> 0xFF00E5FF;
                case RETRACTED -> 0xFF666666;
            };
            case GRAPHITE -> 0xFF4A4A4A;
            case BERYLLIUM -> 0xFF2255FF;
        };
    }
}