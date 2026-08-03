package com.moakiee.meplacementtool.client;

import com.moakiee.meplacementtool.ClientConfig;
import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.ItemMEPlacementTool;
import com.moakiee.meplacementtool.ItemMultiblockPlacementTool;
import com.moakiee.meplacementtool.WandNbt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;


import java.util.ArrayList;
import java.util.List;

/**
 * Renders tool information HUD on the right side of the crosshair.
 * Displays different information based on the tool type held by the player.
 * Shows HUD for 2 seconds when switching to the tool, then auto-hides.
 */
@OnlyIn(Dist.CLIENT)
public class ToolInfoHudRenderer {

    private static final int CROSSHAIR_OFFSET_X = 15;
    private static final float FONT_SCALE = 0.75f;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int TEXT_COLOR_DIM = 0xAAAAAA;
    
    // Track the last held tool item to detect switching
    private Item lastHeldToolItem = null;
    // Track when the tool was switched to
    private long toolSwitchTime = 0L;

    @SubscribeEvent
    public void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        // Only render after crosshair overlay
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }

        // Check if any radial menu is open - don't render HUD if so
        if (mc.screen instanceof RadialMenuScreen || 
            mc.screen instanceof DualLayerRadialMenuScreen) {
            return;
        }

        // Check main hand and off hand for placement tools
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // Determine current tool item
        Item currentToolItem = null;
        ItemStack currentToolStack = ItemStack.EMPTY;

        if (isPlacementTool(mainHand.getItem())) {
            currentToolItem = mainHand.getItem();
            currentToolStack = mainHand;
        } else if (isPlacementTool(offHand.getItem())) {
            currentToolItem = offHand.getItem();
            currentToolStack = offHand;
        }
        
        // Handle tool switching detection
        long currentTime = System.currentTimeMillis();
        
        if (currentToolItem != null) {
            // Check if we just switched to a tool
            if (lastHeldToolItem != currentToolItem) {
                // Switched to a new tool, reset timer
                toolSwitchTime = currentTime;
                lastHeldToolItem = currentToolItem;
            }

            if (ClientConfig.hudDisplayDuration == 0) {
                return; // HUD disabled
            }
            if (ClientConfig.hudDisplayDuration > 0 && currentTime - toolSwitchTime > ClientConfig.hudDisplayDuration) {
                return; // Timed display expired
            }
            // hudDisplayDuration < 0: permanent display, never hide
        } else {
            // Not holding any tool, reset state
            lastHeldToolItem = null;
            toolSwitchTime = 0L;
            return;
        }

        List<String> lines = new ArrayList<>();

        if (currentToolStack.getItem() instanceof ItemMEPlacementTool) {
            collectMEPlacementToolInfo(currentToolStack, lines);
        } else if (currentToolStack.getItem() instanceof ItemMultiblockPlacementTool) {
            collectMultiblockToolInfo(currentToolStack, lines);
        } else if (currentToolStack.getItem() instanceof ItemMECablePlacementTool) {
            collectCableToolInfo(currentToolStack, lines);
        }

        if (!lines.isEmpty()) {
            renderHudLines(event.getGuiGraphics(), mc, lines);
        }
    }

    /**
     * Collect information for ME Placement Tool.
     */
    private void collectMEPlacementToolInfo(ItemStack tool, List<String> lines) {
        CompoundTag cfg = WandNbt.getConfig(tool);
        if (cfg == null) {
            return;
        }

        int selected = WandNbt.getSelectedSlot(cfg);
        var handler = WandNbt.readInventory(cfg);

        ItemStack target = handler.getStackInSlot(selected);
        if (target != null && !target.isEmpty()) {
            String itemName = target.getHoverName().getString();
            lines.add(Component.translatable("meplacementtool.hud.item", itemName).getString());
        }
    }

    /**
     * Collect information for Multiblock Placement Tool.
     */
    private void collectMultiblockToolInfo(ItemStack tool, List<String> lines) {
        // First collect item info same as ME Placement Tool
        collectMEPlacementToolInfo(tool, lines);

        // Add placement count
        int placementCount = ItemMultiblockPlacementTool.getPlacementCount(tool);
        lines.add(Component.translatable("meplacementtool.hud.placement_count", placementCount).getString());

        // Add direction mode
        ItemMultiblockPlacementTool.DirectionMode direction = ItemMultiblockPlacementTool.getDirectionMode(tool);
        String directionName = Component.translatable(direction.translationKey()).getString();
        lines.add(Component.translatable("meplacementtool.hud.direction", directionName).getString());
    }

    /**
     * Collect information for Cable Placement Tool.
     */
    private void collectCableToolInfo(ItemStack tool, List<String> lines) {
        // Cable Type
        ItemMECablePlacementTool.CableType cableType = ItemMECablePlacementTool.getCableType(tool);
        String cableTypeName = getCableTypeName(cableType);
        lines.add(Component.translatable("meplacementtool.hud.cable_type", cableTypeName).getString());

        // Color - only show when upgrade (Key of Spectrum) is installed
        // Without upgrade, the tool always uses transparent color, so no need to display
        boolean hasUpgrade = ItemMECablePlacementTool.hasUpgrade(tool);
        if (hasUpgrade) {
            appeng.api.util.AEColor color = ItemMECablePlacementTool.getColor(tool);
            String colorName = getColorName(color);
            lines.add(Component.translatable("meplacementtool.hud.color", colorName).getString());
        }

        // Mode
        ItemMECablePlacementTool.PlacementMode mode = ItemMECablePlacementTool.getMode(tool);
        String modeName = getModeName(mode);
        lines.add(Component.translatable("meplacementtool.hud.mode", modeName).getString());
    }

    /**
     * Get localized cable type name.
     */
    private String getCableTypeName(ItemMECablePlacementTool.CableType type) {
        return switch (type) {
            case GLASS -> Component.translatable("meplacementtool.cable.glass").getString();
            case COVERED -> Component.translatable("meplacementtool.cable.covered").getString();
            case SMART -> Component.translatable("meplacementtool.cable.smart").getString();
            case DENSE_COVERED -> Component.translatable("meplacementtool.cable.dense_covered").getString();
            case DENSE_SMART -> Component.translatable("meplacementtool.cable.dense_smart").getString();
        };
    }

    /**
     * Get localized color name.
     */
    private String getColorName(appeng.api.util.AEColor color) {
        return switch (color) {
            case WHITE -> Component.translatable("meplacementtool.color.white").getString();
            case ORANGE -> Component.translatable("meplacementtool.color.orange").getString();
            case MAGENTA -> Component.translatable("meplacementtool.color.magenta").getString();
            case LIGHT_BLUE -> Component.translatable("meplacementtool.color.light_blue").getString();
            case YELLOW -> Component.translatable("meplacementtool.color.yellow").getString();
            case LIME -> Component.translatable("meplacementtool.color.lime").getString();
            case PINK -> Component.translatable("meplacementtool.color.pink").getString();
            case GRAY -> Component.translatable("meplacementtool.color.gray").getString();
            case LIGHT_GRAY -> Component.translatable("meplacementtool.color.light_gray").getString();
            case CYAN -> Component.translatable("meplacementtool.color.cyan").getString();
            case PURPLE -> Component.translatable("meplacementtool.color.purple").getString();
            case BLUE -> Component.translatable("meplacementtool.color.blue").getString();
            case BROWN -> Component.translatable("meplacementtool.color.brown").getString();
            case GREEN -> Component.translatable("meplacementtool.color.green").getString();
            case RED -> Component.translatable("meplacementtool.color.red").getString();
            case BLACK -> Component.translatable("meplacementtool.color.black").getString();
            case TRANSPARENT -> Component.translatable("meplacementtool.color.transparent").getString();
        };
    }

    /**
     * Get localized mode name.
     */
    private String getModeName(ItemMECablePlacementTool.PlacementMode mode) {
        return switch (mode) {
            case LINE -> Component.translatable("meplacementtool.mode.line").getString();
            case PLANE_FILL -> Component.translatable("meplacementtool.mode.plane_fill").getString();
            case PLANE_BRANCHING -> Component.translatable("meplacementtool.mode.plane_branching").getString();
        };
    }

    private static boolean isPlacementTool(Item item) {
        return item instanceof ItemMEPlacementTool
            || item instanceof ItemMultiblockPlacementTool
            || item instanceof ItemMECablePlacementTool;
    }

    private void renderHudLines(GuiGraphics guiGraphics, Minecraft mc, List<String> lines) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Center of screen (where crosshair is)
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Start position (right of crosshair)
        int startX = centerX + CROSSHAIR_OFFSET_X;
        int startY = centerY - (lines.size() * (int)(LINE_HEIGHT * FONT_SCALE)) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(FONT_SCALE, FONT_SCALE, 1.0f);

        // Scale coordinates to match the scaled rendering
        float scaledStartX = startX / FONT_SCALE;
        float scaledStartY = startY / FONT_SCALE;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int y = (int)(scaledStartY + i * LINE_HEIGHT);
            
            // Draw with shadow for better visibility
            guiGraphics.drawString(mc.font, line, (int)scaledStartX, y, TEXT_COLOR, true);
        }

        guiGraphics.pose().popPose();
    }
}
