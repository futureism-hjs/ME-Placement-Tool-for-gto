package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.function.DoubleSupplier;
import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.List;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEKey;
import appeng.menu.ISubMenu;
import appeng.parts.PartPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;


/**
 * ME Multiblock Placement Tool - extends BasePlacementToolItem to avoid being recognized as WirelessTerminalItem
 */
public class ItemMultiblockPlacementTool extends BasePlacementToolItem implements IMenuItem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int[] PLACEMENT_COUNTS = {1, 8, 64, 256, 1024};
    private static final String TAG_PLACEMENT_COUNT = "placement_count";

    /**
     * Placement direction modes for bulk placement.
     * AUTO uses BFS across the face plane.
     */
    public enum DirectionMode {
        AUTO,
        NORTH_SOUTH,
        EAST_WEST,
        VERTICAL;

        public static DirectionMode fromId(int id) {
            DirectionMode[] values = values();
            if (id < 0 || id >= values.length) {
                return AUTO;
            }
            return values[id];
        }

        public String translationKey() {
            return "meplacementtool.direction." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public ItemMultiblockPlacementTool(Item.Properties props) {
        super(() -> Config.multiblockPlacementToolEnergyCapacity, props);
    }

    public static int getPlacementCount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_PLACEMENT_COUNT)) {
            int count = tag.getInt(TAG_PLACEMENT_COUNT);
            for (int i = 0; i < PLACEMENT_COUNTS.length; i++) {
                if (PLACEMENT_COUNTS[i] == count) {
                    return count;
                }
            }
        }
        return PLACEMENT_COUNTS[0];
    }

    public static int getNextPlacementCount(ItemStack stack, boolean forward) {
        int current = getPlacementCount(stack);
        for (int i = 0; i < PLACEMENT_COUNTS.length; i++) {
            if (PLACEMENT_COUNTS[i] == current) {
                int nextIndex = forward ? (i + 1) % PLACEMENT_COUNTS.length : (i - 1 + PLACEMENT_COUNTS.length) % PLACEMENT_COUNTS.length;
                return PLACEMENT_COUNTS[nextIndex];
            }
        }
        return PLACEMENT_COUNTS[0];
    }

    public static DirectionMode getDirectionMode(ItemStack stack) {
        CompoundTag data = stack.getTag();
        if (data != null && data.contains(WandMenu.TAG_KEY)) {
            CompoundTag cfg = data.getCompound(WandMenu.TAG_KEY);
            if (cfg.contains("DirectionMode")) {
                return DirectionMode.fromId(cfg.getInt("DirectionMode"));
            }
        }
        return DirectionMode.AUTO;
    }

    @Override
    public ItemMenuHost getMenuHost(Player player, int inventorySlot, ItemStack itemStack, BlockPos pos) {
        return new PlacementToolMenuHost(player, inventorySlot, itemStack, (p, subMenu) -> {
            // Close the menu directly instead of returning to a main menu
            p.closeContainer();
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        ItemStack wand = player.getItemInHand(context.getHand());

        int placementCount = getPlacementCount(wand);
        final double ENERGY_COST = Config.multiblockPlacementToolBaseEnergyCost * placementCount;

        if (!this.hasPower(player, ENERGY_COST, wand)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.device_not_powered"), true);
            return InteractionResult.FAIL;
        }

        var linked = this.getLinkedNetwork(wand, level, player);
        if (linked == null) {
            return InteractionResult.FAIL;
        }
        var grid = linked.grid();

        CompoundTag cfg = WandNbt.getConfig(wand);
        int selected = WandNbt.getSelectedSlot(cfg);
        var handler = WandNbt.readInventory(cfg);

        ItemStack target = handler.getStackInSlot(selected);
        if (target == null || target.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.no_configured_item"), true);
            return InteractionResult.FAIL;
        }

        DirectionMode directionMode = DirectionMode.AUTO;
        if (cfg != null && cfg.contains("DirectionMode")) {
            directionMode = DirectionMode.fromId(cfg.getInt("DirectionMode"));
        }

        var storageService = grid.getStorageService();
        var storage = storageService.getInventory();
        var src = appeng.api.networking.security.IActionSource.ofPlayer(player, linked.accessPoint());

        try {
            var unwrapped = appeng.api.stacks.GenericStack.unwrapItemStack(target);
            if (unwrapped != null && appeng.api.stacks.AEFluidKey.is(unwrapped.what())) {
                var aeFluidKey = (appeng.api.stacks.AEFluidKey) unwrapped.what();
                var fluid = aeFluidKey.getFluid();

                // Check if fluid is a flowing fluid
                if (!(fluid instanceof net.minecraft.world.level.material.FlowingFluid)) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
                    return InteractionResult.FAIL;
                }

                var legacyBlock = fluid.defaultFluidState().createLegacyBlock();
                BlockPos clickedPos = context.getClickedPos();
                var clickedFace = context.getClickedFace();
                var clickedState = level.getBlockState(clickedPos);

                var placePositions = PlacementBfs.findPositions(
                    clickedPos.relative(clickedFace), placementCount, clickedFace, directionMode,
                    candidate -> {
                        BlockPos supportingPoint = candidate.relative(clickedFace.getOpposite());
                        return level.getBlockState(supportingPoint).getBlock() == clickedState.getBlock();
                    },
                    candidate -> {
                        var stateAtPos = level.getBlockState(candidate);
                        boolean stateIsLegacy = stateAtPos == legacyBlock;
                        boolean stateIsAir = stateAtPos.isAir();
                        boolean canBeReplaced = false;
                        try { canBeReplaced = stateAtPos.canBeReplaced(fluid); } catch (Exception ignored) {}
                        boolean isLiquidContainer = stateAtPos.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer;
                        boolean containerCanPlace = false;
                        if (isLiquidContainer) {
                            try {
                                containerCanPlace = ((net.minecraft.world.level.block.LiquidBlockContainer) stateAtPos.getBlock())
                                        .canPlaceLiquid(level, candidate, stateAtPos, fluid);
                            } catch (Exception ignored) {}
                        }
                        return !stateIsLegacy && !aeFluidKey.hasTag() && (stateIsAir || canBeReplaced || (isLiquidContainer && containerCanPlace));
                    }
                );
                if (placePositions.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                    return InteractionResult.sidedSuccess(false);
                }

                // Check if network has enough fluid
                long totalFluidNeeded = (long) placePositions.size() * appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK;
                long simAvail = storage.extract(aeFluidKey, totalFluidNeeded, appeng.api.config.Actionable.SIMULATE, src);
                if (simAvail < totalFluidNeeded) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", aeFluidKey.getDisplayName()), true);
                    return InteractionResult.FAIL;
                }

                // Place fluids at all positions
                int placedCount = 0;
                for (BlockPos placePos : placePositions) {
                    long blockAmount = appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK;
                    if (storage.extract(aeFluidKey, blockAmount,
                            appeng.api.config.Actionable.MODULATE, src) != blockAmount) {
                        break;
                    }
                    boolean success = false;
                    try {
                        var stateAtPos = level.getBlockState(placePos);
                        boolean stateIsAir = stateAtPos.isAir();
                        boolean canBeReplaced = false;
                        try { canBeReplaced = stateAtPos.canBeReplaced(fluid); } catch (Exception ignored) {}
                        boolean isLiquidContainer = stateAtPos.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer;

                        if (level.dimensionType().ultraWarm() && fluid.is(net.minecraft.tags.FluidTags.WATER)) {
                            success = true; // Water evaporates but still counts
                        } else if (isLiquidContainer && fluid == net.minecraft.world.level.material.Fluids.WATER) {
                            success = ((net.minecraft.world.level.block.LiquidBlockContainer) stateAtPos.getBlock())
                                    .placeLiquid(level, placePos, stateAtPos,
                                            ((net.minecraft.world.level.material.FlowingFluid) fluid).getSource(false));
                        } else {
                            if (canBeReplaced && !stateAtPos.liquid()) {
                                level.destroyBlock(placePos, true);
                            }
                            success = level.setBlock(placePos, legacyBlock, net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
                        }
                        if (success) {
                            placedCount++;
                        }
                    } catch (Exception t) {
                        LOGGER.warn("Exception during fluid placement at {}", placePos, t);
                    } finally {
                        if (!success) {
                            long returned = storage.insert(aeFluidKey, blockAmount,
                                    appeng.api.config.Actionable.MODULATE, src);
                            if (returned != blockAmount) {
                                LOGGER.error("Failed to return reserved fluid {} at {}: {}/{}",
                                        aeFluidKey, placePos, returned, blockAmount);
                            }
                        }
                    }
                }

                if (placedCount > 0) {
                    LOGGER.debug("Consuming {} AE from wand for player {} (placedCount={})", ENERGY_COST * placedCount / placementCount, player.getName().getString(), placedCount);
                    this.usePower(player, ENERGY_COST * placedCount / placementCount, wand);
                    level.playSound(null, clickedPos.relative(clickedFace), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(false);
                } else {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                    return InteractionResult.sidedSuccess(false);
                }
            }
        } catch (Exception ignored) {}

        String fluidId = null;
        if (cfg != null && cfg.contains("fluids")) {
            var ftag = cfg.getCompound("fluids");
            if (ftag.contains(Integer.toString(selected))) {
                fluidId = ftag.getString(Integer.toString(selected));
                if (fluidId != null && fluidId.isEmpty()) fluidId = null;
            }
        }

        if (fluidId != null) {
            try {
                var fid = new net.minecraft.resources.ResourceLocation(fluidId);
                var fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(fid);
                if (fluid == null) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
                    return InteractionResult.FAIL;
                }

                // Check if fluid is a flowing fluid
                if (!(fluid instanceof net.minecraft.world.level.material.FlowingFluid)) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
                    return InteractionResult.FAIL;
                }

                var aeFluidKey = appeng.api.stacks.AEFluidKey.of(fluid);
                var legacyBlock = fluid.defaultFluidState().createLegacyBlock();
                BlockPos clickedPos = context.getClickedPos();
                var clickedFace = context.getClickedFace();
                var clickedState = level.getBlockState(clickedPos);

                var placePositions = PlacementBfs.findPositions(
                    clickedPos.relative(clickedFace), placementCount, clickedFace, directionMode,
                    candidate -> {
                        BlockPos supportingPoint = candidate.relative(clickedFace.getOpposite());
                        return level.getBlockState(supportingPoint).getBlock() == clickedState.getBlock();
                    },
                    candidate -> {
                        var stateAtPos = level.getBlockState(candidate);
                        boolean stateIsLegacy = stateAtPos == legacyBlock;
                        boolean stateIsAir = stateAtPos.isAir();
                        boolean canBeReplaced = false;
                        try { canBeReplaced = stateAtPos.canBeReplaced(fluid); } catch (Exception ignored) {}
                        boolean isLiquidContainer = stateAtPos.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer;
                        boolean containerCanPlace = false;
                        if (isLiquidContainer) {
                            try {
                                containerCanPlace = ((net.minecraft.world.level.block.LiquidBlockContainer) stateAtPos.getBlock())
                                        .canPlaceLiquid(level, candidate, stateAtPos, fluid);
                            } catch (Exception ignored) {}
                        }
                        return !stateIsLegacy && !aeFluidKey.hasTag() && (stateIsAir || canBeReplaced || (isLiquidContainer && containerCanPlace));
                    }
                );
                if (placePositions.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                    return InteractionResult.sidedSuccess(false);
                }

                // Check if network has enough fluid
                long totalFluidNeeded = (long) placePositions.size() * appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK;
                long simAvail = storage.extract(aeFluidKey, totalFluidNeeded, appeng.api.config.Actionable.SIMULATE, src);
                if (simAvail < totalFluidNeeded) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", aeFluidKey.getDisplayName()), true);
                    return InteractionResult.FAIL;
                }

                // Place fluids at all positions
                int placedCount = 0;
                for (BlockPos placePos : placePositions) {
                    long blockAmount = appeng.api.stacks.AEFluidKey.AMOUNT_BLOCK;
                    if (storage.extract(aeFluidKey, blockAmount,
                            appeng.api.config.Actionable.MODULATE, src) != blockAmount) {
                        break;
                    }
                    boolean success = false;
                    try {
                        var stateAtPos = level.getBlockState(placePos);
                        boolean stateIsAir = stateAtPos.isAir();
                        boolean canBeReplaced = false;
                        try { canBeReplaced = stateAtPos.canBeReplaced(fluid); } catch (Exception ignored) {}
                        boolean isLiquidContainer = stateAtPos.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer;

                        if (level.dimensionType().ultraWarm() && fluid.is(net.minecraft.tags.FluidTags.WATER)) {
                            success = true; // Water evaporates but still counts
                        } else if (isLiquidContainer && fluid == net.minecraft.world.level.material.Fluids.WATER) {
                            success = ((net.minecraft.world.level.block.LiquidBlockContainer) stateAtPos.getBlock())
                                    .placeLiquid(level, placePos, stateAtPos,
                                            ((net.minecraft.world.level.material.FlowingFluid) fluid).getSource(false));
                        } else {
                            if (canBeReplaced && !stateAtPos.liquid()) {
                                level.destroyBlock(placePos, true);
                            }
                            success = level.setBlock(placePos, legacyBlock, net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE);
                        }
                        if (success) {
                            placedCount++;
                        }
                    } catch (Exception t) {
                        LOGGER.warn("Exception during fluid placement at {}", placePos, t);
                    } finally {
                        if (!success) {
                            long returned = storage.insert(aeFluidKey, blockAmount,
                                    appeng.api.config.Actionable.MODULATE, src);
                            if (returned != blockAmount) {
                                LOGGER.error("Failed to return reserved fluid {} at {}: {}/{}",
                                        aeFluidKey, placePos, returned, blockAmount);
                            }
                        }
                    }
                }

                if (placedCount > 0) {
                    this.usePower(player, ENERGY_COST * placedCount / placementCount, wand);
                    level.playSound(null, clickedPos.relative(clickedFace), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(false);
                } else {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                    return InteractionResult.sidedSuccess(false);
                }
            } catch (Exception t) {
                LOGGER.warn("Exception during fluid placement for player {} at {}", player.getName().getString(), context.getClickedPos(), t);
            }
        }

        // Find all matching items in the AE network (respects NBT whitelist config)
        var matchingKeys = Config.findAllMatchingKeys(storageService, target, src);
        if (matchingKeys.isEmpty()) {
            // Check if the item can be crafted
            var craftKey = appeng.api.stacks.AEItemKey.of(target);
            var craftingService = grid.getCraftingService();
            if (craftingService != null && craftKey != null && craftingService.isCraftable(craftKey)) {
                // Request crafting for the full amount needed
                long totalNeeded = placementCount;
                openCraftingMenu(serverPlayer, wand, craftKey, (int) totalNeeded);
                return InteractionResult.sidedSuccess(false);
            }
            player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", target.getHoverName()), true);
            return InteractionResult.FAIL;
        }
        
        // Calculate total available across all matching keys
        long totalAvailable = matchingKeys.stream().mapToLong(java.util.Map.Entry::getValue).sum();
        long totalNeeded = placementCount;

        if (totalAvailable < totalNeeded) {
            // Check if the item can be crafted
            var craftKey = appeng.api.stacks.AEItemKey.of(target);
            var craftingService = grid.getCraftingService();
            if (craftingService != null && craftKey != null && craftingService.isCraftable(craftKey)) {
                // Request crafting for the missing amount
                long missingAmount = totalNeeded - totalAvailable;
                openCraftingMenu(serverPlayer, wand, craftKey, (int) missingAmount);
                return InteractionResult.sidedSuccess(false);
            }
            player.displayClientMessage(Component.translatable("message.meplacementtool.network_missing", target.getHoverName()), true);
            return InteractionResult.FAIL;
        }

        var blockItem = target.getItem();
        if (blockItem instanceof IPartItem<?>) {
            var firstPlacement = getPartPlacementWithCableFallback(player, level, target, context.getClickedPos(),
                    context.getClickedFace(), context.getClickLocation());
            if (firstPlacement == null) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                return InteractionResult.sidedSuccess(false);
            }

            BlockPos clickedPos = context.getClickedPos();
            var clickedState = level.getBlockState(clickedPos);
            Direction partSide = firstPlacement.side();
            boolean placingOnClickedHost = firstPlacement.pos().equals(clickedPos);

            var placePositions = PlacementBfs.findPositions(
                firstPlacement.pos(), placementCount, context.getClickedFace(), directionMode,
                candidate -> {
                    if (placingOnClickedHost) {
                        return level.getBlockState(candidate).getBlock() == clickedState.getBlock();
                    } else {
                        return level.getBlockState(candidate.relative(partSide)).getBlock() == clickedState.getBlock();
                    }
                },
                candidate -> canPlaceConfiguredPartOnCable(player, level, target, candidate, partSide)
            );

            if (placePositions.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                return InteractionResult.sidedSuccess(false);
            }

            int placedCount = 0;
            List<UndoHistory.PlacementSnapshot> placedSnapshots = new ArrayList<>();
            java.util.List<java.util.Map.Entry<appeng.api.stacks.AEItemKey, Long>> availableKeys = new java.util.ArrayList<>();
            for (var entry : matchingKeys) {
                availableKeys.add(new java.util.AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            }

            for (BlockPos placePos : placePositions) {
                appeng.api.stacks.AEItemKey currentKey = null;
                for (var entry : availableKeys) {
                    if (entry.getValue() > 0) {
                        currentKey = entry.getKey();
                        entry.setValue(entry.getValue() - 1);
                        break;
                    }
                }

                if (currentKey == null) {
                    break;
                }

                if (storage.extract(currentKey, 1L, appeng.api.config.Actionable.MODULATE, src) != 1L) {
                    break;
                }

                var placeStack = currentKey.toStack(1);
                if (!(placeStack.getItem() instanceof IPartItem<?> partItem)) {
                    refundReservedItem(storage, src, currentKey, serverPlayer, placePos);
                    continue;
                }

                if (placePart(player, level, placePos, partSide, partItem, placeStack)) {
                    placedCount++;
                    placedSnapshots.add(new UndoHistory.PartPlacementSnapshot(placePos, partSide, currentKey));
                } else {
                    refundReservedItem(storage, src, currentKey, serverPlayer, placePos);
                }
            }

            if (placedCount == 0) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
                return InteractionResult.sidedSuccess(false);
            }

            boolean configApplied = false;
            if (MemoryCardHelper.hasConfiguredMemoryCard(player)) {
                boolean firstPart = true;
                for (UndoHistory.PlacementSnapshot snapshot : placedSnapshots) {
                    var host = appeng.api.parts.PartHelper.getPartHost(level, snapshot.pos);
                    if (host != null && MemoryCardHelper.applyMemoryCardToPart(player, host.getPart(partSide),
                            firstPart, grid, src)) {
                        configApplied = true;
                    }
                    firstPart = false;
                }
            }

            MEPlacementToolMod.instance.undoHistory.add(player, level, placedSnapshots, configApplied);

            LOGGER.debug("Consuming {} AE from wand for player {} (placedCount={})", ENERGY_COST * placedCount / placementCount, player.getName().getString(), placedCount);
            this.usePower(player, ENERGY_COST * placedCount / placementCount, wand);
            if (!placedSnapshots.isEmpty()) {
                BlockPos soundPos = placedSnapshots.get(0).pos;
                var placedState = level.getBlockState(soundPos);
                var soundType = placedState.getSoundType(level, soundPos, player);
                level.playSound(null, soundPos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                    (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
            }

            return InteractionResult.sidedSuccess(false);
        }

        if (!(blockItem instanceof BlockItem)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.unsupported_target"), true);
            return InteractionResult.FAIL;
        }

        var block = ((BlockItem) blockItem).getBlock();
        BlockPos clickedPos = context.getClickedPos();
        var clickedFace = context.getClickedFace();
        var clickedState = level.getBlockState(clickedPos);

        var placePositions = PlacementBfs.findPositions(
            clickedPos.relative(clickedFace), placementCount, clickedFace, directionMode,
            candidate -> level.getBlockState(candidate.relative(clickedFace.getOpposite())).getBlock() == clickedState.getBlock(),
            candidate -> {
                boolean canPlace = level.isEmptyBlock(candidate);
                if (!canPlace) {
                    try {
                        BlockPlaceContext checkContext = new BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(
                            player, context.getHand(), new net.minecraft.world.phys.BlockHitResult(
                                context.getClickLocation(), context.getClickedFace(), candidate, context.isInside()
                            )
                        ));
                        canPlace = level.getBlockState(candidate).canBeReplaced(checkContext);
                    } catch (Exception ignored) {}
                }
                return canPlace;
            }
        );

        if (placePositions.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
            return InteractionResult.sidedSuccess(false);
        }

        // Check if we have enough resources (blank patterns, upgrades) for memory card application
        if (MemoryCardHelper.hasConfiguredMemoryCard(player)) {
            var resourceCheck = MemoryCardHelper.checkResourcesForMultipleBlocks(player, grid,
                    placePositions.size(), src);
            if (!resourceCheck.sufficient) {
                String missing = resourceCheck.getMissingItemsMessage();
                player.displayClientMessage(Component.translatable("message.meplacementtool.missing_resources", missing), false);
                return InteractionResult.sidedSuccess(false);
            }
        }

        int placedCount = 0;
        List<UndoHistory.PlacementSnapshot> placedSnapshots = new ArrayList<>();
        
        // Create a mutable copy of matching keys with remaining counts
        java.util.List<java.util.Map.Entry<appeng.api.stacks.AEItemKey, Long>> availableKeys = new java.util.ArrayList<>();
        for (var entry : matchingKeys) {
            availableKeys.add(new java.util.AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
        }

        for (BlockPos placePos : placePositions) {
            // Find a key with available count
            appeng.api.stacks.AEItemKey currentKey = null;
            for (var entry : availableKeys) {
                if (entry.getValue() > 0) {
                    currentKey = entry.getKey();
                    entry.setValue(entry.getValue() - 1);
                    break;
                }
            }
            
            if (currentKey == null) {
                break;
            }

            if (storage.extract(currentKey, 1L, appeng.api.config.Actionable.MODULATE, src) != 1L) {
                break;
            }
            
            var placeStack = currentKey.toStack(1);
            ItemStack originalHand = player.getItemInHand(context.getHand());
            boolean placed = false;
            try {
                player.setItemInHand(context.getHand(), placeStack);
                // Create BlockPlaceContext with the correct placeStack (including NBT like energy)
                BlockPlaceContext placeContext = new BlockPlaceContext(
                    level, player, context.getHand(), placeStack,
                    new net.minecraft.world.phys.BlockHitResult(
                        context.getClickLocation(), context.getClickedFace(),
                        placePos, context.isInside()
                    )
                );
                var result = ((BlockItem) blockItem).place(placeContext);
                boolean consumes = result.consumesAction();
                if (consumes) {
                    placed = true;
                    placedCount++;
                    placedSnapshots.add(new UndoHistory.PlacementSnapshot(level.getBlockState(placePos), placePos,
                            currentKey.toStack(1), currentKey, 1));
                }
            } catch (Exception t) {
                LOGGER.warn("Exception during placement attempt for player {} at {}", player.getName().getString(), placePos, t);
            } finally {
                player.setItemInHand(context.getHand(), originalHand);
                if (!placed) {
                    refundReservedItem(storage, src, currentKey, serverPlayer, placePos);
                }
            }
        }

        if (placedCount == 0) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_place"), true);
            return InteractionResult.sidedSuccess(false);
        }

        // Apply memory card settings from off-hand if present to all placed blocks
        // Since all blocks are the same type, only show message once (for the first block)
        boolean configApplied = false;
        
        // AE2 Memory Card
        if (MemoryCardHelper.hasConfiguredMemoryCard(player)) {
            boolean firstBlock = true;
            for (UndoHistory.PlacementSnapshot snapshot : placedSnapshots) {
                if (MemoryCardHelper.applyMemoryCardToBlock(player, level, snapshot.pos, firstBlock, grid, src)) {
                    configApplied = true;
                }
                firstBlock = false;
            }
        }
        // Add to undo history, marking as non-undoable if config was applied
        MEPlacementToolMod.instance.undoHistory.add(player, level, placedSnapshots, configApplied);

        LOGGER.debug("Consuming {} AE from wand for player {} (placedCount={})", ENERGY_COST * placedCount / placementCount, player.getName().getString(), placedCount);
        this.usePower(player, ENERGY_COST * placedCount / placementCount, wand);
        // Play the block's own placement sound (use first placed position)
        if (!placedSnapshots.isEmpty()) {
            BlockPos soundPos = placedSnapshots.get(0).pos;
            var placedState = level.getBlockState(soundPos);
            var soundType = placedState.getSoundType(level, soundPos, player);
            level.playSound(null, soundPos, soundType.getPlaceSound(), SoundSource.BLOCKS, 
                (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        }

        return InteractionResult.sidedSuccess(false);
    }

    private boolean canPlaceConfiguredPartOnCable(Player player, Level level, ItemStack partStack, BlockPos pos,
            Direction side) {
        if (side != null && !hasCenterCable(level, pos)) {
            return false;
        }
        return PartPlacement.canPlacePartOnBlock(player, level, partStack, pos, side);
    }

    private boolean hasCenterCable(Level level, BlockPos pos) {
        var host = PartHelper.getPartHost(level, pos);
        return host != null && host.getPart(null) != null;
    }

    private PartPlacement.Placement getPartPlacementWithCableFallback(Player player, Level level, ItemStack partStack,
            BlockPos clickedPos, Direction clickedFace, net.minecraft.world.phys.Vec3 clickLocation) {
        var placement = PartPlacement.getPartPlacement(player, level, partStack, clickedPos, clickedFace, clickLocation);
        if (placement != null) {
            return placement;
        }

        var host = PartHelper.getPartHost(level, clickedPos);
        if (host != null && host.getPart(null) != null && host.canAddPart(partStack, clickedFace)) {
            return new PartPlacement.Placement(clickedPos, clickedFace);
        }

        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private boolean placePart(Player player, Level level, BlockPos pos, Direction side, IPartItem<?> partItem,
            ItemStack partStack) {
        try {
            return PartPlacement.placePart(player, level, (IPartItem) partItem, partStack.getTag(), pos, side) != null;
        } catch (Exception t) {
            LOGGER.warn("Exception during part placement attempt for player {} at {}", player.getName().getString(), pos, t);
            return false;
        }
    }

    private void refundReservedItem(appeng.api.storage.MEStorage storage,
            appeng.api.networking.security.IActionSource source,
            appeng.api.stacks.AEItemKey key, Player player, BlockPos pos) {
        if (storage.insert(key, 1L, appeng.api.config.Actionable.MODULATE, source) == 1L) {
            return;
        }
        ItemStack remainder = key.toStack(1);
        if (!player.getInventory().add(remainder)) {
            player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, remainder));
        }
        LOGGER.error("Failed to return reserved item {} to ME storage at {}", key, pos);
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        // Right-click: open configuration GUI only when not targeting a block (avoid conflict with placement)
        net.minecraft.world.phys.HitResult hr = player.pick(5.0D, 0.0F, false);
        if (hr != null && hr.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return new InteractionResultHolder<>(InteractionResult.PASS, wand);
        }

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            CompoundTag cfg = WandNbt.getConfig(wand);

            var handler = WandNbt.readInventory(cfg);

            NetworkHooks.openScreen(serverPlayer,
                new SimpleMenuProvider((wnd, inv, pl) -> new WandMenu(wnd, inv, handler, hand), Component.empty()),
                    buf -> {
                        buf.writeNbt(cfg);
                        buf.writeEnum(hand);
                    });
        }

        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), wand);
    }
}
