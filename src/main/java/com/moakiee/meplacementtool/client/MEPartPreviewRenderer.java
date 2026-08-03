package com.moakiee.meplacementtool.client;

import static net.minecraft.client.renderer.RenderStateShard.COLOR_WRITE;
import static net.minecraft.client.renderer.RenderStateShard.ITEM_ENTITY_TARGET;
import static net.minecraft.client.renderer.RenderStateShard.NO_CULL;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_LINES_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY;
import static net.minecraft.client.renderer.RenderStateShard.VIEW_OFFSET_Z_LAYERING;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;


import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.parts.BusCollisionHelper;
import appeng.parts.PartPlacement;

import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.WandNbt;

/**
 * Renders placement preview for ME Placement Tool when holding cables, panels, quartz fiber, etc.
 * Completely follows AE2's RenderBlockOutlineHook implementation.
 */
public class MEPartPreviewRenderer {
    private MEPartPreviewRenderer() {
    }

    /**
     * Similar to {@link RenderType#LINES}, but with inverted depth test.
     * Copied from AE2's RenderBlockOutlineHook.
     */
    public static final RenderType LINES_BEHIND_BLOCK = RenderType.create(
            "meplacementtool_lines_behind_block",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard(">", GL11.GL_GREATER))
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    // Rainbow gradient preview is now used instead of static color

    public static void install() {
        // Use HIGH priority to run before AE2's handler (which uses default priority)
        // AE2's handler checks if the item is IPartItem, but our tool is not IPartItem
        // So we need higher priority to render preview for our tool
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, MEPartPreviewRenderer::handleEvent);
    }

    private static void handleEvent(RenderHighlightEvent.Block evt) {
        var level = Minecraft.getInstance().level;
        var poseStack = evt.getPoseStack();
        var buffers = evt.getMultiBufferSource();
        var camera = evt.getCamera();
        if (level == null || buffers == null) {
            return;
        }

        var blockHitResult = evt.getTarget();
        if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        if (replaceBlockOutline(level, poseStack, buffers, camera, blockHitResult)) {
            evt.setCanceled(true);
        }
    }

    /**
     * Renders placement preview for ME Placement Tool.
     * Only renders for IPartItem (cables, panels, quartz fiber, etc.)
     */
    private static boolean replaceBlockOutline(ClientLevel level,
            PoseStack poseStack,
            MultiBufferSource buffers,
            Camera camera,
            BlockHitResult hitResult) {

        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        // Check if player is holding ME Placement Tool
        ItemStack wand = player.getMainHandItem();
        if (wand.isEmpty() || wand.getItem() != MEPlacementToolMod.ME_PLACEMENT_TOOL.get()) {
            return false;
        }

        // Get the configured item from the wand
        ItemStack targetItem = getConfiguredItem(wand);
        if (targetItem == null || targetItem.isEmpty()) {
            return false;
        }

        // Only render preview for IPartItem (cables, panels, quartz fiber, etc.) or IFacadeItem
        if (!(targetItem.getItem() instanceof IPartItem<?>) && !(targetItem.getItem() instanceof appeng.api.implementations.items.IFacadeItem)) {
            return false;
        }

        // Render without depth test to also have a preview for parts inside blocks.
        if (targetItem.getItem() instanceof IPartItem<?>) {
            showPartPlacementPreview(player, poseStack, buffers, camera, hitResult, targetItem, true);
            showPartPlacementPreview(player, poseStack, buffers, camera, hitResult, targetItem, false);
        } else if (targetItem.getItem() instanceof appeng.api.implementations.items.IFacadeItem) {
            showFacadePlacementPreview(player, poseStack, buffers, camera, hitResult, targetItem);
        }

        return true;
    }

    /**
     * Get the currently configured item from the wand's NBT
     */
    private static ItemStack getConfiguredItem(ItemStack wand) {
        CompoundTag cfg = WandNbt.getConfig(wand);
        int selected = WandNbt.getSelectedSlot(cfg);
        var handler = WandNbt.readInventory(cfg);

        ItemStack target = handler.getStackInSlot(selected);
        
        // Unwrap AE wrapped stacks
        if (target != null && !target.isEmpty()) {
            try {
                var unwrapped = appeng.api.stacks.GenericStack.unwrapItemStack(target);
                if (unwrapped != null && unwrapped.what() instanceof appeng.api.stacks.AEItemKey itemKey) {
                    return itemKey.toStack();
                }
            } catch (Exception ignored) {}
        }
        
        return target;
    }

    /**
     * Show placement preview for AE2 parts (cables, panels, etc.)
     * Follows AE2's showPartPlacementPreview implementation exactly.
     */
    private static void showPartPlacementPreview(
            Player player,
            PoseStack poseStack,
            MultiBufferSource buffers,
            Camera camera,
            BlockHitResult blockHitResult,
            ItemStack itemInHand,
            boolean insideBlock) {
        if (itemInHand.getItem() instanceof IPartItem<?> partItem) {
            var placement = PartPlacement.getPartPlacement(player,
                    player.level(),
                    itemInHand,
                    blockHitResult.getBlockPos(),
                    blockHitResult.getDirection(),
                    blockHitResult.getLocation());

            if (placement != null) {
                var part = partItem.createPart();
                renderPart(poseStack, buffers, camera, placement.pos(), part, placement.side(), true, insideBlock);
            }
        }
    }

    /**
     * Show placement preview for AE2 facades
     */
    private static void showFacadePlacementPreview(
            Player player,
            PoseStack poseStack,
            MultiBufferSource buffers,
            Camera camera,
            BlockHitResult blockHitResult,
            ItemStack itemInHand) {
        if (itemInHand.getItem() instanceof appeng.api.implementations.items.IFacadeItem facadeItem) {
            var facade = facadeItem.createPartFromItemStack(itemInHand, blockHitResult.getDirection());
            if (facade != null) {
                renderFacade(poseStack, buffers, camera, blockHitResult.getBlockPos(), facade);
            }
        }
    }

    /**
     * Render the facade preview boxes.
     */
    private static void renderFacade(PoseStack poseStack,
            MultiBufferSource buffers,
            Camera camera,
            BlockPos pos,
            appeng.api.parts.IFacadePart facade) {
        var boxes = new ArrayList<AABB>();
        var helper = new BusCollisionHelper(boxes, facade.getSide(), true);
        facade.getBoxes(helper, false);
        renderBoxes(poseStack, buffers, camera, pos, boxes, true, false);
    }

    /**
     * Render the part preview boxes.
     * Follows AE2's renderPart implementation exactly.
     */
    private static void renderPart(PoseStack poseStack,
            MultiBufferSource buffers,
            Camera camera,
            BlockPos pos,
            IPart part,
            Direction side,
            boolean preview,
            boolean insideBlock) {
        var boxes = new ArrayList<AABB>();
        var helper = new BusCollisionHelper(boxes, side, true);
        part.getBoxes(helper);
        renderBoxes(poseStack, buffers, camera, pos, boxes, preview, insideBlock);
    }

    /**
     * Render the preview boxes.
     * Follows AE2's renderBoxes implementation exactly, but with custom color (same as MultiblockPreviewRenderer).
     */
    private static void renderBoxes(PoseStack poseStack,
            MultiBufferSource buffers,
            Camera camera,
            BlockPos pos,
            List<AABB> boxes,
            boolean preview,
            boolean insideBlock) {
        RenderType renderType = insideBlock ? LINES_BEHIND_BLOCK : RenderType.lines();
        var buffer = buffers.getBuffer(renderType);
        float alpha = insideBlock ? 0.2f : preview ? 0.6f : 0.4f;

        // Use rainbow gradient based on spatial position
        RainbowRenderHelper.renderRainbowBoxes(
                poseStack,
                buffer,
                pos,
                boxes,
                camera.getPosition().x,
                camera.getPosition().y,
                camera.getPosition().z,
                alpha);
    }
}
