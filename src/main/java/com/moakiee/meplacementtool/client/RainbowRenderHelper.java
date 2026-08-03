package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Set;

/**
 * Utility class for rendering rainbow gradient preview boxes.
 * Provides consistent time-based rainbow gradient rendering across placement tools.
 * 
 * Uses 8-bit color depth (256 steps) for smooth transitions.
 */
public class RainbowRenderHelper {
    
    private RainbowRenderHelper() {}
    
    // Time-based animation speed (full cycle every ~3 seconds for smoother feel)
    private static final float CYCLE_DURATION_MS = 3000.0f;  // 3 seconds per full rainbow cycle

    // Reused output buffer for the rainbow color. Single render thread; do not mutate the returned array.
    private static final float[] CACHED_COLOR = new float[3];
    private static long lastColorTimeMs = Long.MIN_VALUE;

    /**
     * Get rainbow color based on current time using HSV to RGB conversion.
     * Uses full 8-bit color depth (256 hue steps) for ultra-smooth transitions.
     *
     * @return float array {red, green, blue} in range 0-1. The array is reused across calls; callers must not mutate it.
     */
    public static float[] getTimeBasedRainbowColor() {
        long now = System.currentTimeMillis();
        if (now != lastColorTimeMs) {
            float time = (now % (long)CYCLE_DURATION_MS) / CYCLE_DURATION_MS;
            hsvToRgb(time, 1.0f, 1.0f, CACHED_COLOR);
            lastColorTimeMs = now;
        }
        return CACHED_COLOR;
    }

    /**
     * Convert HSV (Hue, Saturation, Value) to RGB, writing into {@code out}.
     * This provides smooth 8-bit color transitions through the full rainbow spectrum.
     *
     * @param h Hue (0-1, where 0=red, 0.33=green, 0.66=blue, 1=red)
     * @param s Saturation (0-1)
     * @param v Value/Brightness (0-1)
     * @param out output array of length >= 3, receives r, g, b in range 0-1
     */
    private static void hsvToRgb(float h, float s, float v, float[] out) {
        int i = (int)(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        switch (i % 6) {
            case 0: out[0] = v; out[1] = t; out[2] = p; break;
            case 1: out[0] = q; out[1] = v; out[2] = p; break;
            case 2: out[0] = p; out[1] = v; out[2] = t; break;
            case 3: out[0] = p; out[1] = q; out[2] = v; break;
            case 4: out[0] = t; out[1] = p; out[2] = v; break;
            case 5: out[0] = v; out[1] = p; out[2] = q; break;
            default: out[0] = v; out[1] = t; out[2] = p; break;
        }
    }
    
    /**
     * Legacy method for compatibility - now uses time-based color.
     */
    public static float[] getRainbowColor(double x, double y, double z) {
        return getTimeBasedRainbowColor();
    }
    
    /**
     * Legacy method for compatibility - now uses time-based color.
     */
    public static float[] getRainbowColor(BlockPos pos) {
        return getTimeBasedRainbowColor();
    }
    
    /**
     * Draw a line with time-based rainbow color.
     */
    public static void drawGradientLine(
            VertexConsumer buffer,
            Matrix4f matrix,
            Matrix3f normal,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            double worldX1, double worldY1, double worldZ1,
            double worldX2, double worldY2, double worldZ2,
            float alpha) {
        
        // Get time-based rainbow color (same color for entire line)
        float[] color = getTimeBasedRainbowColor();
        
        // Calculate normal direction for the line
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0) {
            dx /= length;
            dy /= length;
            dz /= length;
        }
        
        // Draw line with time-based color
        buffer.vertex(matrix, x1, y1, z1).color(color[0], color[1], color[2], alpha).normal(normal, dx, dy, dz).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(color[0], color[1], color[2], alpha).normal(normal, dx, dy, dz).endVertex();
    }
    
    /**
     * Render a rainbow line box for a single block position.
     * Uses time-based rainbow color that animates smoothly.
     */
    public static void renderRainbowLineBox(
            PoseStack poseStack,
            VertexConsumer buffer,
            BlockPos pos,
            double camX, double camY, double camZ,
            float alpha) {
        
        AABB aabb = new AABB(pos).move(-camX, -camY, -camZ);
        float[] color = getTimeBasedRainbowColor();
        
        // Use standard LevelRenderer with time-based rainbow color
        LevelRenderer.renderLineBox(poseStack, buffer, aabb, color[0], color[1], color[2], alpha);
    }
    
    /**
     * Render rainbow gradient line boxes for a set of block positions.
     * All blocks share the same time-based rainbow color.
     */
    public static void renderRainbowLineBoxes(
            PoseStack poseStack,
            MultiBufferSource buffers,
            Set<BlockPos> blocks,
            Camera camera,
            float alpha) {
        
        VertexConsumer lineBuilder = buffers.getBuffer(RenderType.lines());
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;
        
        for (BlockPos block : blocks) {
            renderRainbowLineBox(poseStack, lineBuilder, block, camX, camY, camZ, alpha);
        }
    }
    
    /**
     * Render AABB boxes with time-based rainbow color.
     */
    public static void renderRainbowBoxes(
            PoseStack poseStack,
            VertexConsumer buffer,
            BlockPos pos,
            List<AABB> boxes,
            double camX, double camY, double camZ,
            float alpha) {
        
        float[] color = getTimeBasedRainbowColor();
        
        for (AABB box : boxes) {
            VoxelShape shape = Shapes.create(box);
            LevelRenderer.renderShape(
                    poseStack,
                    buffer,
                    shape,
                    pos.getX() - camX,
                    pos.getY() - camY,
                    pos.getZ() - camZ,
                    color[0],
                    color[1],
                    color[2],
                    alpha);
        }
    }
}
