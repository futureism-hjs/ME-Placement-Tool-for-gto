package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.moakiee.meplacementtool.ItemMultiblockPlacementTool;
import com.moakiee.meplacementtool.ItemMultiblockPlacementTool.DirectionMode;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.PlacementBfs;
import com.moakiee.meplacementtool.WandNbt;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.GenericStack;
import appeng.parts.BusCollisionHelper;
import appeng.parts.PartPlacement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.items.ItemStackHandler;

public class MultiblockPreviewRenderer
{
    private BlockHitResult lastRayTraceResult;
    private CompoundTag lastWandTag;
    private Set<BlockPos> cachedPositions;
    private int lastPlacementCount;
    private DirectionMode lastDirectionMode;

    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public void renderBlockHighlight(RenderHighlightEvent.Block event) {
        if(event.getTarget().getType() != HitResult.Type.BLOCK) return;

        BlockHitResult rtr = event.getTarget();
        Entity entity = event.getCamera().getEntity();
        if(!(entity instanceof Player player)) return;

        ItemStack wand = player.getMainHandItem();
        if(wand.isEmpty() || wand.getItem() != MEPlacementToolMod.MULTIBLOCK_PLACEMENT_TOOL.get()) return;

        int placementCount = ItemMultiblockPlacementTool.getPlacementCount(wand);
        DirectionMode directionMode = ItemMultiblockPlacementTool.getDirectionMode(wand);

        Set<BlockPos> blocks;
        if(cachedPositions == null || !compareRTR(lastRayTraceResult, rtr)
                || !java.util.Objects.equals(lastWandTag, wand.getTag())
                || lastPlacementCount != placementCount
                || lastDirectionMode != directionMode) {
            blocks = calculatePlacementPositions(player, rtr, wand, placementCount, directionMode);
            cachedPositions = blocks;
            lastRayTraceResult = rtr;
            lastWandTag = wand.getTag() != null ? wand.getTag().copy() : null;
            lastPlacementCount = placementCount;
            lastDirectionMode = directionMode;
        } else {
            blocks = cachedPositions;
        }

        if(blocks == null || blocks.isEmpty()) return;

        PoseStack ms = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();

        Camera camera = event.getCamera();

        ItemStack target = getSelectedTargetStack(wand);
        if(target.getItem() instanceof IPartItem<?> partItem) {
            var placement = getPartPlacementWithCableFallback(player, player.level(), target, rtr.getBlockPos(),
                    rtr.getDirection(), rtr.getLocation());
            if(placement != null) {
                IPart part = partItem.createPart();
                for(BlockPos block : blocks) {
                    renderPart(ms, buffer, camera, block, part, placement.side(), false);
                    renderPart(ms, buffer, camera, block, part, placement.side(), true);
                }
            }
        } else {
            VertexConsumer lineBuilder = buffer.getBuffer(RenderType.LINES);
            double camX = camera.getPosition().x;
            double camY = camera.getPosition().y;
            double camZ = camera.getPosition().z;
            // Render all blocks with cyan/blue color (original style)
            for(BlockPos block : blocks) {
                AABB aabb = new AABB(block).move(-camX, -camY, -camZ);
                LevelRenderer.renderLineBox(ms, lineBuilder, aabb, 0.0F, 0.75F, 1.0F, 0.4F);
            }
        }

        event.setCanceled(true);
    }

    private Set<BlockPos> calculatePlacementPositions(Player player, BlockHitResult rtr, ItemStack wand,
            int placementCount, DirectionMode directionMode) {
        Set<BlockPos> placePositions = new HashSet<>();
        if(placementCount <= 0) return placePositions;

        var level = player.level();
        BlockPos clickedPos = rtr.getBlockPos();
        var clickedFace = rtr.getDirection();
        var clickedState = level.getBlockState(clickedPos);
        ItemStack target = getSelectedTargetStack(wand);

        if(target.getItem() instanceof IPartItem<?>) {
            var placement = getPartPlacementWithCableFallback(player, level, target, clickedPos, clickedFace, rtr.getLocation());
            if(placement == null) return placePositions;

            Direction partSide = placement.side();
            boolean placingOnClickedHost = placement.pos().equals(clickedPos);

            var foundPositions = PlacementBfs.findPositions(
                placement.pos(), placementCount, clickedFace, directionMode,
                candidate -> {
                    if (placingOnClickedHost) {
                        return level.getBlockState(candidate).getBlock() == clickedState.getBlock();
                    } else {
                        return level.getBlockState(candidate.relative(partSide)).getBlock() == clickedState.getBlock();
                    }
                },
                candidate -> canPlaceConfiguredPartOnCable(player, level, target, candidate, partSide)
            );
            placePositions.addAll(foundPositions);
            return placePositions;
        }

        var foundPositions = PlacementBfs.findPositions(
            clickedPos.relative(clickedFace), placementCount, clickedFace, directionMode,
            candidate -> level.getBlockState(candidate.relative(clickedFace.getOpposite())).getBlock() == clickedState.getBlock(),
            candidate -> {
                boolean canPlace = level.isEmptyBlock(candidate);
                if (!canPlace) {
                    try {
                        var checkContext = new net.minecraft.world.item.context.BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(
                            player, player.getUsedItemHand(), new net.minecraft.world.phys.BlockHitResult(
                                rtr.getLocation(), rtr.getDirection(), candidate, rtr.isInside()
                            )
                        ));
                        canPlace = level.getBlockState(candidate).canBeReplaced(checkContext);
                    } catch (Exception ignored) {}
                }
                return canPlace;
            }
        );
        return new HashSet<>(foundPositions);
    }

    private boolean canPlaceConfiguredPartOnCable(Player player, net.minecraft.world.level.Level level,
            ItemStack partStack, BlockPos pos, Direction side) {
        if(side != null && !hasCenterCable(level, pos)) {
            return false;
        }
        return PartPlacement.canPlacePartOnBlock(player, level, partStack, pos, side);
    }

    private boolean hasCenterCable(net.minecraft.world.level.Level level, BlockPos pos) {
        var host = PartHelper.getPartHost(level, pos);
        return host != null && host.getPart(null) != null;
    }

    private PartPlacement.Placement getPartPlacementWithCableFallback(Player player, net.minecraft.world.level.Level level,
            ItemStack partStack, BlockPos clickedPos, Direction clickedFace, net.minecraft.world.phys.Vec3 clickLocation) {
        var placement = PartPlacement.getPartPlacement(player, level, partStack, clickedPos, clickedFace, clickLocation);
        if(placement != null) {
            return placement;
        }

        var host = PartHelper.getPartHost(level, clickedPos);
        if(host != null && host.getPart(null) != null && host.canAddPart(partStack, clickedFace)) {
            return new PartPlacement.Placement(clickedPos, clickedFace);
        }

        return null;
    }

    private ItemStack getSelectedTargetStack(ItemStack wand) {
        CompoundTag cfg = WandNbt.getConfig(wand);
        if (cfg == null) {
            return ItemStack.EMPTY;
        }

        int selected = WandNbt.getSelectedSlot(cfg);
        ItemStackHandler handler = WandNbt.readInventory(cfg);

        ItemStack target = handler.getStackInSlot(selected);
        if(!target.isEmpty()) {
            try {
                var genericStack = GenericStack.unwrapItemStack(target);
                if(genericStack != null && genericStack.what() instanceof appeng.api.stacks.AEItemKey itemKey) {
                    return itemKey.toStack();
                }
            } catch(Exception ignored) {}
        }
        return target;
    }

    private void renderPart(PoseStack poseStack, MultiBufferSource buffers, Camera camera, BlockPos pos,
            IPart part, Direction side, boolean insideBlock) {
        var boxes = new ArrayList<AABB>();
        var helper = new BusCollisionHelper(boxes, side, true);
        part.getBoxes(helper);
        renderBoxes(poseStack, buffers, camera, pos, boxes, insideBlock);
    }

    private void renderBoxes(PoseStack poseStack, MultiBufferSource buffers, Camera camera, BlockPos pos,
            List<AABB> boxes, boolean insideBlock) {
        var buffer = buffers.getBuffer(insideBlock ? MEPartPreviewRenderer.LINES_BEHIND_BLOCK : RenderType.lines());
        RainbowRenderHelper.renderRainbowBoxes(poseStack, buffer, pos, boxes,
                camera.getPosition().x, camera.getPosition().y, camera.getPosition().z, insideBlock ? 0.2f : 0.6f);
    }

    private static boolean compareRTR(BlockHitResult rtr1, BlockHitResult rtr2) {
        if(rtr1 == null || rtr2 == null) return false;
        return rtr1.getBlockPos().equals(rtr2.getBlockPos()) && rtr1.getDirection().equals(rtr2.getDirection());
    }

    public void reset() {
        cachedPositions = null;
        lastRayTraceResult = null;
        lastWandTag = null;
        lastPlacementCount = 0;
        lastDirectionMode = null;
    }
}
