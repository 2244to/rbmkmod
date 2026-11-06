
package com.rbmkmod.rbmkmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.joml.Matrix4f;

import java.util.List;
import java.util.Locale;

public class ControlPanelBlockEntityRenderer
        implements BlockEntityRenderer<ControlPanelBlockEntity> {

    // Tor suwaków
    private static final float TRACK_MIN_X = 10.5f;
    private static final float TRACK_MAX_X = 15.5f;
    private static final float POINTER_W = 0.5f;

    public ControlPanelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            ControlPanelBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        BlockState state = blockEntity.getBlockState();

        Direction facing = Direction.NORTH;

        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }

        poseStack.pushPose();

        // 1. Obracanie wokół środka bloku
        poseStack.translate(0.5f, 0.5f, 0.5f);

        switch (facing) {
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(270));
            default -> {
            }
        }

        poseStack.translate(-0.5f, -0.5f, -0.5f);

        // 2. Skalowanie modelu do 2x2x1
        poseStack.scale(2.0f, 2.0f, 1.0f);

        // 3. Renderowanie modelu JSON
        BlockRenderDispatcher blockRenderer =
                Minecraft.getInstance().getBlockRenderer();

        BakedModel bakedModel = blockRenderer.getBlockModel(state);

        VertexConsumer modelConsumer =
                bufferSource.getBuffer(Sheets.cutoutBlockSheet());

        blockRenderer.getModelRenderer().renderModel(
                poseStack.last(),
                modelConsumer,
                state,
                bakedModel,
                1.0f,
                1.0f,
                1.0f,
                packedLight,
                packedOverlay
        );

        // 3b. Ruchome wskaźniki suwaków
        {
            VertexConsumer ptr = bufferSource.getBuffer(RenderType.debugQuads());
            Matrix4f m = poseStack.last().pose();

            // Suwak progu Auto AZ-5: 300..2400 °C
            float tempFrac =
                    (blockEntity.getAutoScramTemp() - 300) / 2100.0f;

            drawPointer(
                    ptr, m, tempFrac,
                    6.75f, 8.25f,
                    0xFFFF9900
            );

            // Suwak procentu grafitu: 0..100%
            float grFrac = blockEntity.getGraphitePercent() / 100.0f;

            drawPointer(
                    ptr, m, grFrac,
                    8.75f, 10.25f,
                    0xFF00E5FF
            );
        }

        // 4. Renderowanie diod na ekranie CRT
        List<CoreChannelData> channels =
                blockEntity.getClientSyncedChannels();

        if (channels != null && !channels.isEmpty()) {
            poseStack.pushPose();

            poseStack.translate(0.3218f, 0.6718f, 0.487f);

            VertexConsumer builder =
                    bufferSource.getBuffer(RenderType.debugQuads());

            Matrix4f matrix = poseStack.last().pose();

            BlockPos centerPos = blockEntity.getBlockPos();

            int minRelX = Integer.MAX_VALUE;
            int maxRelX = Integer.MIN_VALUE;
            int minRelZ = Integer.MAX_VALUE;
            int maxRelZ = Integer.MIN_VALUE;

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

            float mapSize = 0.52f;
            float zoom = blockEntity.getZoomFactor();

            float cellSize =
                    Math.min(mapSize / gridW, mapSize / gridH) * zoom;

            cellSize = Math.min(cellSize, 0.12f);

            float gap = cellSize >= 0.02f ? 0.002f : 0.0f;
            float drawSize = Math.max(0.004f, cellSize - gap);

            float centerGridX = (minRelX + maxRelX) / 2.0f;
            float centerGridZ = (minRelZ + maxRelZ) / 2.0f;

            float screenMinX = -0.290f;
            float screenMaxX = 0.290f;
            float screenMinY = -0.265f;
            float screenMaxY = 0.265f;

            for (CoreChannelData ch : channels) {
                int relX = ch.pos().getX() - centerPos.getX();
                int relZ = ch.pos().getZ() - centerPos.getZ();

                float px =
                        (relX - centerGridX) * cellSize - (drawSize / 2.0f);

                float py =
                        -(relZ - centerGridZ) * cellSize - (drawSize / 2.0f);

                if (px < screenMinX
                        || (px + drawSize) > screenMaxX
                        || py < screenMinY
                        || (py + drawSize) > screenMaxY) {
                    continue;
                }

                int color = getChannelColor(ch);

                int a = (color >> 24) & 0xFF;
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                drawQuad(
                        builder, matrix,
                        px, py, -0.001f,
                        drawSize, drawSize,
                        r, g, b, a
                );
            }

            poseStack.popPose();
        }

        // 5. Drugi ekran - produkcja turbiny Mekanism
        renderTurbineOutput(blockEntity, poseStack, bufferSource);

        poseStack.popPose();
    }

    /**
     * Wyświetla aktualną produkcję turbiny w kFE/t.
     */
    private void renderTurbineOutput(
            ControlPanelBlockEntity blockEntity,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        Font font = Minecraft.getInstance().font;

        String output = String.format(
                Locale.ROOT,
                "%.1f kFE/t",
                blockEntity.getTurbineOutputKfe()
        );

        poseStack.pushPose();

        // Środek drugiego ekranu:
        // X: 10.5..15.5
        // Y: 12.5..15
        // Z: przednia powierzchnia modelu
        poseStack.translate(
                13.0f / 16.0f,
                13.75f / 16.0f,
                7.79f / 16.0f
        );

        // Tekst skierowany na przód panelu
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        float textScale = 0.005f;

        poseStack.scale(
                -textScale,
                textScale,
                textScale
        );

        // Wyśrodkowanie napisu
        poseStack.translate(
                -font.width(output) / 2.0f,
                -3.0f,
                0.0f
        );

        font.drawInBatch(
                Component.literal(output),
                0,
                0,
                0xFF00FF55,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT
        );

        poseStack.popPose();
    }

    /**
     * Rysuje wskaźnik na torze suwaka.
     */
    private void drawPointer(
            VertexConsumer vc,
            Matrix4f m,
            float frac,
            float y0Px,
            float y1Px,
            int argb
    ) {
        frac = Math.max(0f, Math.min(1f, frac));

        float cx =
                TRACK_MIN_X + frac * (TRACK_MAX_X - TRACK_MIN_X);

        cx = Math.max(
                TRACK_MIN_X + POINTER_W / 2f,
                Math.min(TRACK_MAX_X - POINTER_W / 2f, cx)
        );

        float x0 = (cx - POINTER_W / 2f) / 16f;
        float x1 = (cx + POINTER_W / 2f) / 16f;

        float y0 = y0Px / 16f;
        float y1 = y1Px / 16f;

        float z0 = 7.5f / 16f;
        float z1 = 8.5f / 16f;

        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        int rs = (int) (r * 0.7f);
        int gs = (int) (g * 0.7f);
        int bs = (int) (b * 0.7f);

        // Przód
        quad(
                vc, m,
                x0, y0, z0,
                x1, y0, z0,
                x1, y1, z0,
                x0, y1, z0,
                r, g, b, a
        );

        // Góra
        quad(
                vc, m,
                x0, y1, z0,
                x1, y1, z0,
                x1, y1, z1,
                x0, y1, z1,
                rs, gs, bs, a
        );

        // Dół
        quad(
                vc, m,
                x0, y0, z0,
                x1, y0, z0,
                x1, y0, z1,
                x0, y0, z1,
                rs, gs, bs, a
        );

        // Lewy bok
        quad(
                vc, m,
                x0, y0, z0,
                x0, y1, z0,
                x0, y1, z1,
                x0, y0, z1,
                rs, gs, bs, a
        );

        // Prawy bok
        quad(
                vc, m,
                x1, y0, z0,
                x1, y1, z0,
                x1, y1, z1,
                x1, y0, z1,
                rs, gs, bs, a
        );
    }

    private void quad(
            VertexConsumer vc,
            Matrix4f m,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            int r, int g, int b, int a
    ) {
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x4, y4, z4).setColor(r, g, b, a);
    }

    private void drawQuad(
            VertexConsumer builder,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            float w,
            float h,
            int r,
            int g,
            int b,
            int a
    ) {
        builder.addVertex(matrix, x, y, z).setColor(r, g, b, a);
        builder.addVertex(matrix, x + w, y, z).setColor(r, g, b, a);
        builder.addVertex(matrix, x + w, y + h, z).setColor(r, g, b, a);
        builder.addVertex(matrix, x, y + h, z).setColor(r, g, b, a);
    }

    private int getChannelColor(CoreChannelData ch) {
        return switch (ch.type()) {
            case FUEL -> {
                double temp = ch.temperatureC();

                if (temp > 1200) yield 0xFFFF2222;
                if (temp > 500) yield 0xFFFF8800;
                if (temp > 150) yield 0xFFFFEE00;

                yield 0xFF00FF55;
            }

            case CONTROL_ROD -> switch (ch.rodMode()) {
                case BORON -> 0xFFCC00FF;
                case GRAPHITE -> 0xFF00E5FF;
                case RETRACTED -> 0xFF666666;
            };

            case GRAPHITE -> 0xFF4A4A4A;
            case BERYLLIUM -> 0xFF2255FF;
            case BORON -> 0xFF770099;
        };
    }
}
