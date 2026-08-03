package com.moakiee.meplacementtool;

import appeng.api.config.Actionable;
import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEKey;
import appeng.helpers.IMouseWheelItem;
import appeng.parts.PartPlacement;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import appeng.core.definitions.ColoredItemDefinition;
import appeng.api.networking.security.IActionSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ItemMECablePlacementTool extends BasePlacementToolItem implements IMenuItem, IMouseWheelItem {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /**
     * Check if a cable can be placed at the given position.
     * A cable can be placed if:
     * - The position is air, OR
     * - The position is an AE2 IPartHost without a center cable (only has side parts like panels, anchors, quartz fiber)
     */
    public static boolean canPlaceCableAt(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        
        // Air blocks are always valid
        if (state.isAir()) {
            return true;
        }
        
        // Check for AE2 cable bus with no center cable
        IPartHost host = PartHelper.getPartHost(level, pos);
        if (host != null) {
            // If there's no center cable, we can place one
            // This allows placing cables where only panels/anchors/quartz fiber exist
            return host.getPart(null) == null;
        }
        
        // Any other block - cannot place
        return false;
    }

    /**
     * Get the smart target position for cable placement.
     * When clicking on an IPartHost (cable bus with parts but no cable):
     * - If clicking on a face that HAS a part (panel front) -> use adjacent position
     * - If clicking on a face whose OPPOSITE has a part (panel back) -> use clicked position
     * - If clicking on a side face (neither has part) -> use adjacent position
     * For other blocks: use standard logic (try adjacent first, then clicked)
     */
    public static BlockPos getSmartTargetPos(Level level, BlockPos clickedPos, Direction clickedFace) {
        BlockPos adjacentPos = clickedPos.relative(clickedFace);
        
        // Check if clicked position is an AE2 part host without center cable
        IPartHost host = PartHelper.getPartHost(level, clickedPos);
        if (host != null && host.getPart(null) == null) {
            // This is a valid cable placement position (has parts but no center cable)
            
            // Check if the clicked face itself has a part (panel front)
            if (host.getPart(clickedFace) != null) {
                // Clicking on panel front face -> use adjacent position
                if (canPlaceCableAt(level, adjacentPos)) {
                    return adjacentPos;
                }
                return clickedPos;
            }
            
            // Check if the opposite face has a part (we're clicking on panel backside)
            if (host.getPart(clickedFace.getOpposite()) != null) {
                // Clicking on panel backside -> place cable in this block
                return clickedPos;
            }
            
            // Side face (no part on clicked face or opposite) -> use adjacent position
            if (canPlaceCableAt(level, adjacentPos)) {
                return adjacentPos;
            }
            return clickedPos;
        }
        
        // Standard logic for non-part-host blocks
        if (canPlaceCableAt(level, adjacentPos)) {
            return adjacentPos;
        }
        if (canPlaceCableAt(level, clickedPos)) {
            return clickedPos;
        }
        // Neither is valid, return adjacent (will be filtered later)
        return adjacentPos;
    }

    public enum PlacementMode {
        LINE,
        PLANE_FILL,
        PLANE_BRANCHING
    }

    public enum CableType {
        GLASS(AEParts.GLASS_CABLE),
        COVERED(AEParts.COVERED_CABLE),
        SMART(AEParts.SMART_CABLE),
        DENSE_COVERED(AEParts.COVERED_DENSE_CABLE),
        DENSE_SMART(AEParts.SMART_DENSE_CABLE);

        private final ColoredItemDefinition<?> definition;
        private final java.util.Map<AEItemKey, AEColor> colorLookup;

        CableType(ColoredItemDefinition<?> definition) {
            this.definition = definition;
            var map = new java.util.LinkedHashMap<AEItemKey, AEColor>();
            for (AEColor c : AEColor.values()) {
                AEItemKey key = AEItemKey.of(definition.stack(c));
                if (key != null) {
                    map.put(key, c);
                }
            }
            this.colorLookup = java.util.Collections.unmodifiableMap(map);
        }

        public ItemStack getStack(AEColor color) {
            return definition.stack(color);
        }

        public AEColor getColorFromKey(AEItemKey key) {
            return colorLookup.getOrDefault(key, AEColor.TRANSPARENT);
        }

        public java.util.Map<AEItemKey, AEColor> getColorLookup() {
            return colorLookup;
        }
    }

    public ItemMECablePlacementTool(Item.Properties props) {
        super(() -> Config.cablePlacementToolEnergyCapacity, props);
    }

    @Override
    public ItemMenuHost getMenuHost(Player player, int inventorySlot, ItemStack itemStack, BlockPos pos) {
        return new PlacementToolMenuHost(player, inventorySlot, itemStack, (p, subMenu) -> p.closeContainer());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        // In LINE mode, if point1 is set, allow confirming placement by right-clicking air
        PlacementMode mode = getMode(stack);
        BlockPos p1 = getPoint1(stack);
        
        if (mode == PlacementMode.LINE && p1 != null) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                // Use player look direction to determine endpoint
                BlockPos endpoint = findLine(player, p1);
                if (endpoint != null) {
                    setPoint2(stack, endpoint);
                    player.displayClientMessage(Component.translatable("message.meplacementtool.point2_set", endpoint.toShortString()), true);
                    boolean craftingTriggered = executePlacement(serverPlayer, stack, level, p1, endpoint);
                    // Only clear points if crafting was NOT triggered
                    if (!craftingTriggered) {
                        setPoint1(stack, null);
                        setPoint2(stack, null);
                    }
                    return InteractionResultHolder.success(stack);
                }
            }
            return InteractionResultHolder.success(stack);
        }
        
        return InteractionResultHolder.pass(stack);
    }


    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Left-click clears points (handled in event handler)
        // Right-click sets points
        // Get the position next to the clicked face (like vanilla block placement)
        BlockPos clickedPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos targetPos = getSmartTargetPos(level, clickedPos, face);
        
        PlacementMode mode = getMode(stack);
        BlockPos p1 = getPoint1(stack);
        BlockPos p2 = getPoint2(stack);

        if (mode == PlacementMode.PLANE_BRANCHING) {
            // Branching mode uses 3 points
            if (p1 == null) {
                setPoint1(stack, targetPos);
                player.displayClientMessage(Component.translatable("message.meplacementtool.branch_point1_set", targetPos.toShortString()), true);
            } else if (p2 == null) {
                setPoint2(stack, targetPos);
                player.displayClientMessage(Component.translatable("message.meplacementtool.branch_point2_set", targetPos.toShortString()), true);
            } else {
                setPoint3(stack, targetPos);
                player.displayClientMessage(Component.translatable("message.meplacementtool.branch_point3_set", targetPos.toShortString()), true);
                boolean craftingTriggered = executeBranchPlacement((ServerPlayer) player, stack, level, p1, p2, targetPos);
                // Only clear points if crafting was NOT triggered
                if (!craftingTriggered) {
                    setPoint1(stack, null);
                    setPoint2(stack, null);
                    setPoint3(stack, null);
                }
            }
        } else if (mode == PlacementMode.LINE) {
            // LINE mode: first click sets start, second click uses player look direction
            if (p1 == null) {
                setPoint1(stack, targetPos);
                player.displayClientMessage(Component.translatable("message.meplacementtool.point1_set", targetPos.toShortString()), true);
            } else {
                // Use player look direction to determine endpoint (effortless-building style)
                BlockPos endpoint = findLine(player, p1);
                boolean craftingTriggered;
                if (endpoint != null) {
                    setPoint2(stack, endpoint);
                    player.displayClientMessage(Component.translatable("message.meplacementtool.point2_set", endpoint.toShortString()), true);
                    craftingTriggered = executePlacement((ServerPlayer) player, stack, level, p1, endpoint);
                } else {
                    // Fallback: use clicked position
                    setPoint2(stack, targetPos);
                    player.displayClientMessage(Component.translatable("message.meplacementtool.point2_set", targetPos.toShortString()), true);
                    craftingTriggered = executePlacement((ServerPlayer) player, stack, level, p1, targetPos);
                }
                // Only clear points if crafting was NOT triggered
                if (!craftingTriggered) {
                    setPoint1(stack, null);
                    setPoint2(stack, null);
                }
            }
        } else {
            // PLANE_FILL uses 2 points (original behavior)
            if (p1 == null) {
                setPoint1(stack, targetPos);
                player.displayClientMessage(Component.translatable("message.meplacementtool.point1_set", targetPos.toShortString()), true);
            } else {
                setPoint2(stack, targetPos);
                player.displayClientMessage(Component.translatable("message.meplacementtool.point2_set", targetPos.toShortString()), true);
                boolean craftingTriggered = executePlacement((ServerPlayer) player, stack, level, p1, targetPos);
                // Only clear points if crafting was NOT triggered
                if (!craftingTriggered) {
                    setPoint1(stack, null);
                    setPoint2(stack, null);
                }
            }
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Find an available cable key from ME storage.
     * Priority: same color > any other color
     */
    private AEItemKey findAvailableCableKey(MEStorage storage, IActionSource src, CableType cableType, AEColor preferredColor) {
        // First try: preferred (same) color
        ItemStack preferredStack = cableType.getStack(preferredColor);
        AEItemKey preferredKey = AEItemKey.of(preferredStack);
        if (storage.extract(preferredKey, 1, Actionable.SIMULATE, src) >= 1) {
            return preferredKey;
        }
        
        // Second try: any other color
        for (var entry : cableType.getColorLookup().entrySet()) {
            if (entry.getValue() == preferredColor) continue;
            if (storage.extract(entry.getKey(), 1, Actionable.SIMULATE, src) >= 1) {
                return entry.getKey();
            }
        }
        
        return null; // No cable available
    }

    /**
     * Get the AEColor from a cable AEItemKey.
     */
    private AEColor getColorFromCableKey(AEItemKey key, CableType cableType) {
        return cableType.getColorFromKey(key);
    }

    /**
     * Execute cable placement.
     * @return true if crafting was triggered (points should be preserved), false otherwise
     */
    private boolean executePlacement(ServerPlayer player, ItemStack tool, Level level, BlockPos p1, BlockPos p2) {
        PlacementMode mode = getMode(tool);
        CableType cableType = getCableType(tool);
        boolean hasUpgrade = hasUpgrade(tool);

        // Determine effective color and whether to consume dye
        ColorLogicResult colorLogic = determineColorLogic(player, tool);
        AEColor color = colorLogic.color;

        List<BlockPos> positions = calculatePositions(p1, p2, mode);
        if (positions.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.no_positions"), true);
            return false;
        }

        // Check Power
        double energyCost = Config.cablePlacementToolEnergyCost * positions.size();
        if (!this.hasPower(player, energyCost, tool)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.device_not_powered"), true);
            return false;
        }

        // Check Network
        var linked = this.getLinkedNetwork(tool, level, player);
        if (linked == null) return false;
        IGrid grid = linked.grid();
        MEStorage storage = grid.getStorageService().getInventory();
        var src = appeng.api.networking.security.IActionSource.ofPlayer(player, linked.accessPoint());

        // The actual cable to place
        ItemStack placeCableStack = cableType.getStack(color);

        // Pre-check: Count how many positions actually need cables (filter out non-air)
        List<BlockPos> validPositions = new java.util.ArrayList<>();
        for (BlockPos pos : positions) {
            if (canPlaceCableAt(level, pos)) {
                validPositions.add(pos);
            }
        }
        
        if (validPositions.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.no_positions"), true);
            return false;
        }
        
        int totalNeeded = validPositions.size();
        
        // Pre-check: Count total available cables in network (any color of this type)
        long totalAvailable = 0;
        for (var entry : cableType.getColorLookup().entrySet()) {
            totalAvailable += storage.extract(entry.getKey(), totalNeeded, Actionable.SIMULATE, src);
            if (totalAvailable >= totalNeeded) break; // Enough found
        }
        
        // If not enough cables, trigger crafting BEFORE placing anything
        if (totalAvailable < totalNeeded) {
            // Try to craft Fluix (TRANSPARENT) cable as it's the base type
            var fluixCableStack = cableType.getStack(AEColor.TRANSPARENT);
            var craftKey = AEItemKey.of(fluixCableStack);
            var craftingService = grid.getCraftingService();
            if (craftingService != null && craftKey != null && craftingService.isCraftable(craftKey)) {
                // Request crafting for the missing amount
                int missingAmount = (int) (totalNeeded - totalAvailable);
                openCraftingMenu(player, tool, craftKey, missingAmount);
                return true; // Crafting triggered, preserve all points, no placement done
            }
            player.displayClientMessage(Component.translatable("message.meplacementtool.missing_cable", placeCableStack.getHoverName()), true);
            return false;
        }

        int requiredDye = calculateRequiredDye(storage, src, cableType, colorLogic, totalNeeded);
        if (!hasDye(player, storage, src, color, requiredDye)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.missing_dye", requiredDye,
                    DyeItem.byColor(color.dye).getDescription()), true);
            return false;
        }

        // Now we know we have enough cables, proceed with placement
        int placedCount = 0;
        int recoloredCount = 0;
        List<UndoHistory.CablePlacementSnapshot> placedSnapshots = new java.util.ArrayList<>();

        for (BlockPos pos : validPositions) {
            // Find available cable (priority: same color > any color)
            AEItemKey keyToExtract = findAvailableCableKey(storage, src, cableType, color);
            if (keyToExtract == null) {
                // This shouldn't happen since we pre-checked, but handle gracefully
                player.displayClientMessage(Component.translatable("message.meplacementtool.missing_cable", placeCableStack.getHoverName()), true);
                break;
            }

            // Check if we need dye for this cable (only if extracted color != target color)
            AEColor extractedColor = getColorFromCableKey(keyToExtract, cableType);
            boolean needsDyeForThis = colorLogic.needsDye && (extractedColor != color);

            if (storage.extract(keyToExtract, 1L, Actionable.MODULATE, src) != 1L) {
                break;
            }
            boolean placed = false;
            boolean dyeConsumedForThisCable = false;

            // One dye starts each batch of eight successfully recolored cables.
            if (needsDyeForThis && color != AEColor.TRANSPARENT) {
                if (recoloredCount % 8 == 0) {
                    if (!consumeDye(player, storage, src, color, 1)) {
                        returnReservedCable(storage, src, keyToExtract, player, pos);
                        player.displayClientMessage(Component.translatable("message.meplacementtool.missing_dye", 1, DyeItem.byColor(color.dye).getDescription()), true);
                        break;
                    }
                    dyeConsumedForThisCable = true;
                }
            }

            // Place
            if (placeCable(player, (ServerLevel) level, pos, placeCableStack)) {
                placed = true;
                placedCount++;
                if (needsDyeForThis) {
                    recoloredCount++;
                }
                // Record for undo - return the same type of cable that was extracted
                placedSnapshots.add(new UndoHistory.CablePlacementSnapshot(pos, cableType, keyToExtract));
            }
            if (!placed) {
                returnReservedCable(storage, src, keyToExtract, player, pos);
                if (dyeConsumedForThisCable) {
                    refundDye(player, storage, src, color, 1);
                }
            }
        }

        if (placedCount > 0) {
            this.usePower(player, Config.cablePlacementToolEnergyCost * placedCount, tool);
            player.displayClientMessage(Component.translatable("message.meplacementtool.placed_count", placedCount), true);
            
            // Play cable placement sound (use first placed position)
            if (!placedSnapshots.isEmpty()) {
                BlockPos soundPos = placedSnapshots.get(0).pos;
                var placedState = level.getBlockState(soundPos);
                var soundType = placedState.getSoundType(level, soundPos, player);
                level.playSound(null, soundPos, soundType.getPlaceSound(), SoundSource.BLOCKS, 
                    (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
            }
            
            // Add to undo history
            MEPlacementToolMod.instance.undoHistory.addCablePlacement(player, level, placedSnapshots);
        }
        return false; // Normal completion, can clear points
    }

    private boolean placeCable(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack cableStack) {
        try {
            IPartItem<?> partItem = (IPartItem<?>) cableStack.getItem();
            if (PartPlacement.placePart(player, level, partItem, null, pos, Direction.UP) != null) {
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Cable placement failed for {} at {}", player.getGameProfile().getName(), pos, e);
        }
        return false;
    }

    private void returnReservedCable(MEStorage storage, IActionSource source, AEItemKey key,
            ServerPlayer player, BlockPos pos) {
        if (storage.insert(key, 1L, Actionable.MODULATE, source) == 1L) {
            return;
        }
        ItemStack remainder = key.toStack(1);
        if (!player.getInventory().add(remainder)) {
            player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, remainder));
        }
        LOGGER.error("Failed to return reserved cable {} to ME storage at {}", key, pos);
    }

    /**
     * Execute branch placement using 3 points.
     * @return true if crafting was triggered (points should be preserved), false otherwise
     */
    private boolean executeBranchPlacement(ServerPlayer player, ItemStack tool, Level level, BlockPos p1, BlockPos p2, BlockPos p3) {
        CableType cableType = getCableType(tool);
        boolean hasUpgrade = hasUpgrade(tool);
        
        // Determine effective color and whether to consume dye
        ColorLogicResult colorLogic = determineColorLogic(player, tool);
        AEColor color = colorLogic.color;

        List<BlockPos> positions = calculateBranchPositions(p1, p2, p3);
        if (positions.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.no_positions"), true);
            return false;
        }

        // Check Power
        double energyCost = Config.cablePlacementToolEnergyCost * positions.size();
        if (!this.hasPower(player, energyCost, tool)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.device_not_powered"), true);
            return false;
        }

        // Check Network
        var linked = this.getLinkedNetwork(tool, level, player);
        if (linked == null) return false;
        IGrid grid = linked.grid();
        MEStorage storage = grid.getStorageService().getInventory();
        var src = appeng.api.networking.security.IActionSource.ofPlayer(player, linked.accessPoint());

        // The actual cable to place
        ItemStack placeCableStack = cableType.getStack(color);

        // Pre-check: Count how many positions actually need cables (filter out non-air)
        List<BlockPos> validPositions = new java.util.ArrayList<>();
        for (BlockPos pos : positions) {
            if (canPlaceCableAt(level, pos)) {
                validPositions.add(pos);
            }
        }
        
        if (validPositions.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.no_positions"), true);
            return false;
        }
        
        int totalNeeded = validPositions.size();
        
        // Pre-check: Count total available cables in network (any color of this type)
        long totalAvailable = 0;
        for (var entry : cableType.getColorLookup().entrySet()) {
            totalAvailable += storage.extract(entry.getKey(), totalNeeded, Actionable.SIMULATE, src);
            if (totalAvailable >= totalNeeded) break; // Enough found
        }
        
        // If not enough cables, trigger crafting BEFORE placing anything
        if (totalAvailable < totalNeeded) {
            // Try to craft Fluix (TRANSPARENT) cable as it's the base type
            var fluixCableStack = cableType.getStack(AEColor.TRANSPARENT);
            var craftKey = AEItemKey.of(fluixCableStack);
            var craftingService = grid.getCraftingService();
            if (craftingService != null && craftKey != null && craftingService.isCraftable(craftKey)) {
                // Request crafting for the missing amount
                int missingAmount = (int) (totalNeeded - totalAvailable);
                openCraftingMenu(player, tool, craftKey, missingAmount);
                return true; // Crafting triggered, preserve all points, no placement done
            }
            player.displayClientMessage(Component.translatable("message.meplacementtool.missing_cable", placeCableStack.getHoverName()), true);
            return false;
        }

        int requiredDye = calculateRequiredDye(storage, src, cableType, colorLogic, totalNeeded);
        if (!hasDye(player, storage, src, color, requiredDye)) {
            player.displayClientMessage(Component.translatable("message.meplacementtool.missing_dye", requiredDye,
                    DyeItem.byColor(color.dye).getDescription()), true);
            return false;
        }

        // Now we know we have enough cables, proceed with placement
        int placedCount = 0;
        int recoloredCount = 0;
        List<UndoHistory.CablePlacementSnapshot> placedSnapshots = new java.util.ArrayList<>();
        
        for (BlockPos pos : validPositions) {
            // Find available cable (priority: same color > any color)
            AEItemKey keyToExtract = findAvailableCableKey(storage, src, cableType, color);
            if (keyToExtract == null) {
                // This shouldn't happen since we pre-checked, but handle gracefully
                player.displayClientMessage(Component.translatable("message.meplacementtool.missing_cable", placeCableStack.getHoverName()), true);
                break;
            }

            // Check if we need dye for this cable (only if extracted color != target color)
            AEColor extractedColor = getColorFromCableKey(keyToExtract, cableType);
            boolean needsDyeForThis = colorLogic.needsDye && (extractedColor != color);

            if (storage.extract(keyToExtract, 1L, Actionable.MODULATE, src) != 1L) {
                break;
            }
            boolean placed = false;
            boolean dyeConsumedForThisCable = false;

            // One dye starts each batch of eight successfully recolored cables.
            if (needsDyeForThis && color != AEColor.TRANSPARENT) {
                if (recoloredCount % 8 == 0) {
                    if (!consumeDye(player, storage, src, color, 1)) {
                        returnReservedCable(storage, src, keyToExtract, player, pos);
                        player.displayClientMessage(Component.translatable("message.meplacementtool.missing_dye", 1, DyeItem.byColor(color.dye).getDescription()), true);
                        break;
                    }
                    dyeConsumedForThisCable = true;
                }
            }

            if (placeCable(player, (ServerLevel) level, pos, placeCableStack)) {
                placed = true;
                placedCount++;
                if (needsDyeForThis) {
                    recoloredCount++;
                }
                // Record for undo - return the same type of cable that was extracted
                placedSnapshots.add(new UndoHistory.CablePlacementSnapshot(pos, cableType, keyToExtract));
            }
            if (!placed) {
                returnReservedCable(storage, src, keyToExtract, player, pos);
                if (dyeConsumedForThisCable) {
                    refundDye(player, storage, src, color, 1);
                }
            }
        }

        if (placedCount > 0) {
            this.usePower(player, Config.cablePlacementToolEnergyCost * placedCount, tool);
            player.displayClientMessage(Component.translatable("message.meplacementtool.placed_count", placedCount), true);
            
            // Play cable placement sound (use first placed position)
            if (!placedSnapshots.isEmpty()) {
                BlockPos soundPos = placedSnapshots.get(0).pos;
                var placedState = level.getBlockState(soundPos);
                var soundType = placedState.getSoundType(level, soundPos, player);
                level.playSound(null, soundPos, soundType.getPlaceSound(), SoundSource.BLOCKS, 
                    (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
            }
            
            // Add to undo history
            MEPlacementToolMod.instance.undoHistory.addCablePlacement(player, level, placedSnapshots);
        }
        return false; // Normal completion, can clear points
    }

    /**
     * Calculate positions for cable placement based on the selected mode.
     * LINE mode: only axis-aligned or smart-snap lines (no diagonal stepping).
     * PLANE_FILL: fill a rectangular area.
     * PLANE_BRANCHING: calculated separately with 3 points.
     */
    public static List<BlockPos> calculatePositions(BlockPos p1, BlockPos p2, PlacementMode mode) {
        List<BlockPos> list = new ArrayList<>();
        int x1 = p1.getX(), y1 = p1.getY(), z1 = p1.getZ();
        int x2 = p2.getX(), y2 = p2.getY(), z2 = p2.getZ();

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        if (mode == PlacementMode.LINE) {
            // LINE mode uses getLineBlocks for axis-aligned lines
            // findLine already returns axis-aligned endpoint, so we just generate the line blocks
            return getLineBlocks(x1, y1, z1, x2, y2, z2);
        } else if (mode == PlacementMode.PLANE_FILL) {
            // Fill area - works for any 3D box
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        list.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        // PLANE_BRANCHING is handled by calculateBranchPositions with 3 points
        return list;
    }

    /**
     * Calculate branching positions using 3 points.
     * Supports all 3 planes: XZ (horizontal), XY (vertical east-west), YZ (vertical north-south).
     * Point 1 (A): Start point
     * Point 2 (B): Determines main trunk direction and branch interval
     * Point 3 (C): Forms a plane with Point 1, determines branch length
     */
    public static List<BlockPos> calculateBranchPositions(BlockPos p1, BlockPos p2, BlockPos p3) {
        List<BlockPos> list = new ArrayList<>();
        
        int x1 = p1.getX(), y1 = p1.getY(), z1 = p1.getZ();
        int x2 = p2.getX(), y2 = p2.getY(), z2 = p2.getZ();
        int x3 = p3.getX(), y3 = p3.getY(), z3 = p3.getZ();
        
        // P1 to P2 determines trunk direction and branch interval
        int dx12 = x2 - x1;
        int dy12 = y2 - y1;
        int dz12 = z2 - z1;
        
        // Find the dominant axis for trunk direction (largest absolute delta)
        int absDx = Math.abs(dx12);
        int absDy = Math.abs(dy12);
        int absDz = Math.abs(dz12);
        
        // Trunk axis: 0=X, 1=Y, 2=Z
        int trunkAxis;
        int trunkDir;
        int interval;
        
        if (absDx >= absDy && absDx >= absDz) {
            trunkAxis = 0; // X
            trunkDir = dx12 == 0 ? 1 : Integer.signum(dx12);
            interval = Math.max(1, absDx);
        } else if (absDy >= absDx && absDy >= absDz) {
            trunkAxis = 1; // Y
            trunkDir = dy12 == 0 ? 1 : Integer.signum(dy12);
            interval = Math.max(1, absDy);
        } else {
            trunkAxis = 2; // Z
            trunkDir = dz12 == 0 ? 1 : Integer.signum(dz12);
            interval = Math.max(1, absDz);
        }
        
        // P1 to P3 determines the extent of the plane
        int dx13 = x3 - x1;
        int dy13 = y3 - y1;
        int dz13 = z3 - z1;
        
        // Determine branch axis (different from trunk axis, choose the one with largest delta)
        int branchAxis;
        int branchDir;
        int trunkLength;
        int branchLength;
        
        if (trunkAxis == 0) {
            // Trunk along X, branch along Y or Z
            trunkLength = Math.abs(dx13);
            if (Math.abs(dy13) >= Math.abs(dz13)) {
                branchAxis = 1; // Y
                branchLength = Math.abs(dy13);
                branchDir = dy13 == 0 ? 1 : Integer.signum(dy13);
            } else {
                branchAxis = 2; // Z
                branchLength = Math.abs(dz13);
                branchDir = dz13 == 0 ? 1 : Integer.signum(dz13);
            }
        } else if (trunkAxis == 1) {
            // Trunk along Y, branch along X or Z
            trunkLength = Math.abs(dy13);
            if (Math.abs(dx13) >= Math.abs(dz13)) {
                branchAxis = 0; // X
                branchLength = Math.abs(dx13);
                branchDir = dx13 == 0 ? 1 : Integer.signum(dx13);
            } else {
                branchAxis = 2; // Z
                branchLength = Math.abs(dz13);
                branchDir = dz13 == 0 ? 1 : Integer.signum(dz13);
            }
        } else {
            // Trunk along Z, branch along X or Y
            trunkLength = Math.abs(dz13);
            if (Math.abs(dx13) >= Math.abs(dy13)) {
                branchAxis = 0; // X
                branchLength = Math.abs(dx13);
                branchDir = dx13 == 0 ? 1 : Integer.signum(dx13);
            } else {
                branchAxis = 1; // Y
                branchLength = Math.abs(dy13);
                branchDir = dy13 == 0 ? 1 : Integer.signum(dy13);
            }
        }
        
        // Generate trunk and branches using the determined axes
        for (int t = 0; t <= trunkLength; t++) {
            int tx = x1, ty = y1, tz = z1;
            
            // Move along trunk axis
            if (trunkAxis == 0) tx = x1 + t * trunkDir;
            else if (trunkAxis == 1) ty = y1 + t * trunkDir;
            else tz = z1 + t * trunkDir;
            
            // Add trunk position
            list.add(new BlockPos(tx, ty, tz));
            
            // Add branches at intervals
            if (t % interval == 0) {
                for (int b = 1; b <= branchLength; b++) {
                    int bx = tx, by = ty, bz = tz;
                    
                    // Move along branch axis
                    if (branchAxis == 0) bx = tx + b * branchDir;
                    else if (branchAxis == 1) by = ty + b * branchDir;
                    else bz = tz + b * branchDir;
                    
                    list.add(new BlockPos(bx, by, bz));
                }
            }
        }
        
        return list;
    }

    public static void setPoint1(ItemStack stack, @Nullable BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        if (pos == null) {
            tag.remove("Point1");
        } else {
            tag.put("Point1", NbtUtils.writeBlockPos(pos));
        }
    }

    @Nullable
    public static BlockPos getPoint1(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Point1")) {
            return NbtUtils.readBlockPos(tag.getCompound("Point1"));
        }
        return null;
    }

    public static void setPoint2(ItemStack stack, @Nullable BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        if (pos == null) {
            tag.remove("Point2");
        } else {
            tag.put("Point2", NbtUtils.writeBlockPos(pos));
        }
    }

    @Nullable
    public static BlockPos getPoint2(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Point2")) {
            return NbtUtils.readBlockPos(tag.getCompound("Point2"));
        }
        return null;
    }

    public static void setPoint3(ItemStack stack, @Nullable BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        if (pos == null) {
            tag.remove("Point3");
        } else {
            tag.put("Point3", NbtUtils.writeBlockPos(pos));
        }
    }

    @Nullable
    public static BlockPos getPoint3(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Point3")) {
            return NbtUtils.readBlockPos(tag.getCompound("Point3"));
        }
        return null;
    }


    public static PlacementMode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Mode")) {
            return PlacementMode.values()[tag.getInt("Mode")];
        }
        return PlacementMode.LINE;
    }

    public static void setMode(ItemStack stack, PlacementMode mode) {
        stack.getOrCreateTag().putInt("Mode", mode.ordinal());
        // Reset all points when mode changes
        setPoint1(stack, null);
        setPoint2(stack, null);
        setPoint3(stack, null);
    }

    public static CableType getCableType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("CableType")) {
            return CableType.values()[tag.getInt("CableType")];
        }
        return CableType.GLASS;
    }

    public static void setCableType(ItemStack stack, CableType type) {
        stack.getOrCreateTag().putInt("CableType", type.ordinal());
    }

    private static final AEColor[] COLOR_VALUES = AEColor.values();

    public static AEColor getColor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Color")) {
            return COLOR_VALUES[tag.getInt("Color")];
        }
        return AEColor.TRANSPARENT;
    }

    /// Cycle to the next color in the enum and save it. Returns the new color
    public static AEColor cycleColor(ItemStack stack, int offset) {
        AEColor current = getColor(stack);
        // If has upgrade, cycle through all colors
        if(hasUpgrade(stack)) {
            int nextOrdinal = ((current.ordinal() + offset) + COLOR_VALUES.length) % COLOR_VALUES.length;
            AEColor next = COLOR_VALUES[nextOrdinal];
            setColor(stack, next);
            return next;
        } else {
            return current;
        }
    }

    @Override
    public void onWheel(ItemStack is, boolean up) {
        cycleColor(is, up ? -1 : 1);
    }

    public static void setColor(ItemStack stack, AEColor color) {
        stack.getOrCreateTag().putInt("Color", color.ordinal());
    }

    public static boolean hasUpgrade(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("HasUpgrade");
    }

    public static void setUpgrade(ItemStack stack, boolean has) {
        stack.getOrCreateTag().putBoolean("HasUpgrade", has);
    }

    /**
     * Get the color shortcuts array from the tool stack.
     * @return Array of 5 color indices (-1 = empty slot)
     */
    public static int[] getColorShortcuts(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("ColorShortcuts")) {
            return tag.getIntArray("ColorShortcuts");
        }
        return new int[]{-1, -1, -1, -1, -1}; // Default: all empty
    }

    /**
     * Set the color shortcuts array to the tool stack.
     * @param shortcuts Array of 5 color indices (-1 = empty slot)
     */
    public static void setColorShortcuts(ItemStack stack, int[] shortcuts) {
        stack.getOrCreateTag().putIntArray("ColorShortcuts", shortcuts);
    }

    private static class ColorLogicResult {
        AEColor color;
        boolean needsDye;

        ColorLogicResult(AEColor color, boolean needsDye) {
            this.color = color;
            this.needsDye = needsDye;
        }
    }

    @Nullable
    private AEColor getDyeColorFromStack(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof DyeItem dyeItem) {
            return AEColor.fromDye(dyeItem.getDyeColor());
        }
        return null;
    }

    private ColorLogicResult determineColorLogic(Player player, ItemStack tool) {
        ItemStack offhandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        AEColor offhandDyeColor = getDyeColorFromStack(offhandStack);
        boolean hasUpgrade = hasUpgrade(tool);
        AEColor selectedColor = getColor(tool);

        if (offhandDyeColor != null) {
            // Case 1 & 2: Offhand has dye.
            if (hasUpgrade) {
                // Case 1: Has Upgrade -> Use Offhand Color, No Cost.
                return new ColorLogicResult(offhandDyeColor, false);
            } else {
                // Case 2: No Upgrade -> Use Offhand Color, Consume Dye.
                return new ColorLogicResult(offhandDyeColor, true);
            }
        } else {
            // Case 3 & 4: Offhand has NO dye.
            if (hasUpgrade) {
                // Case 3: Has Upgrade -> Use Selected Color, No Cost.
                return new ColorLogicResult(selectedColor, false);
            } else {
                // Case 4: No Upgrade -> Use Transparent (Fluix), No Dye Cost.
                return new ColorLogicResult(AEColor.TRANSPARENT, false);
            }
        }
    }

    private int calculateRequiredDye(MEStorage storage, IActionSource src, CableType cableType,
            ColorLogicResult colorLogic, int cableCount) {
        if (!colorLogic.needsDye || colorLogic.color == AEColor.TRANSPARENT || cableCount <= 0) {
            return 0;
        }

        AEItemKey preferredKey = AEItemKey.of(cableType.getStack(colorLogic.color));
        long preferredAvailable = preferredKey == null ? 0
                : storage.extract(preferredKey, cableCount, Actionable.SIMULATE, src);
        long recolorCount = Math.max(0L, cableCount - preferredAvailable);
        return (int) ((recolorCount + 7L) / 8L);
    }

    private boolean hasDye(Player player, MEStorage storage, IActionSource src, AEColor color, int amount) {
        if (amount <= 0 || color == AEColor.TRANSPARENT) {
            return true;
        }

        DyeItem dyeItem = (DyeItem) DyeItem.byColor(color.dye);
        AEItemKey dyeKey = AEItemKey.of(dyeItem);
        long available = storage.extract(dyeKey, amount, Actionable.SIMULATE, src);
        if (available >= amount) {
            return true;
        }

        for (ItemStack slotStack : player.getInventory().items) {
            if (getDyeColorFromStack(slotStack) == color) {
                available += slotStack.getCount();
                if (available >= amount) {
                    return true;
                }
            }
        }

        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (getDyeColorFromStack(offhand) == color) {
            available += offhand.getCount();
        }
        return available >= amount;
    }

    private boolean consumeDye(Player player, MEStorage storage, IActionSource src, AEColor color, int amount) {
        if (amount <= 0 || color == AEColor.TRANSPARENT) {
            return true;
        }
        if (!hasDye(player, storage, src, color, amount)) {
            return false;
        }

        DyeItem dyeItem = (DyeItem) DyeItem.byColor(color.dye);
        AEItemKey dyeKey = AEItemKey.of(dyeItem);
        int remaining = amount;

        long extracted = storage.extract(dyeKey, remaining, Actionable.MODULATE, src);
        remaining -= (int) Math.min(remaining, extracted);

        for (ItemStack slotStack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (getDyeColorFromStack(slotStack) == color) {
                int taken = Math.min(remaining, slotStack.getCount());
                slotStack.shrink(taken);
                remaining -= taken;
            }
        }

        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (remaining > 0 && getDyeColorFromStack(offhand) == color) {
            int taken = Math.min(remaining, offhand.getCount());
            offhand.shrink(taken);
            remaining -= taken;
        }

        if (remaining > 0) {
            refundDye(player, storage, src, color, amount - remaining);
            return false;
        }
        return true;
    }

    private void refundDye(Player player, MEStorage storage, IActionSource src, AEColor color, int amount) {
        if (amount <= 0 || color == AEColor.TRANSPARENT) {
            return;
        }

        DyeItem dyeItem = (DyeItem) DyeItem.byColor(color.dye);
        AEItemKey dyeKey = AEItemKey.of(dyeItem);
        long inserted = storage.insert(dyeKey, amount, Actionable.MODULATE, src);
        int remaining = amount - (int) Math.min(amount, inserted);
        if (remaining <= 0) {
            return;
        }

        ItemStack refund = new ItemStack(dyeItem, remaining);
        player.getInventory().add(refund);
        if (!refund.isEmpty()) {
            player.drop(refund, false);
        }
    }

    // ==================== Line Mode (based on effortless-building) ====================

    /**
     * Find line endpoint based on player look direction.
     * Ported from effortless-building Line.findLine()
     */
    public static BlockPos findLine(Player player, BlockPos firstPos) {
        Vec3 look = getPlayerLookVec(player);
        Vec3 start = new Vec3(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());

        List<LineCriteria> criteriaList = new ArrayList<>(3);

        // X plane
        Vec3 xBound = findXBound(firstPos.getX(), start, look);
        criteriaList.add(new LineCriteria(xBound, firstPos, start));

        // Y plane
        Vec3 yBound = findYBound(firstPos.getY(), start, look);
        criteriaList.add(new LineCriteria(yBound, firstPos, start));

        // Z plane
        Vec3 zBound = findZBound(firstPos.getZ(), start, look);
        criteriaList.add(new LineCriteria(zBound, firstPos, start));

        // Remove invalid criteria
        int reach = 64;
        criteriaList.removeIf(c -> !c.isValid(start, look, reach));

        if (criteriaList.isEmpty()) return null;

        // Select the best criteria
        LineCriteria selected = criteriaList.get(0);
        if (criteriaList.size() > 1) {
            for (int i = 1; i < criteriaList.size(); i++) {
                LineCriteria criteria = criteriaList.get(i);
                if (criteria.distToLineSq < 2.0 && selected.distToLineSq < 2.0) {
                    // Both very close to line, choose closest to player
                    if (criteria.distToPlayerSq < selected.distToPlayerSq)
                        selected = criteria;
                } else {
                    // Pick closest to line
                    if (criteria.distToLineSq < selected.distToLineSq)
                        selected = criteria;
                }
            }
        }

        return BlockPos.containing(selected.lineBound);
    }

    /**
     * Get line blocks between two positions (axis-aligned only).
     * Ported from effortless-building Line.getLineBlocks()
     */
    public static List<BlockPos> getLineBlocks(int x1, int y1, int z1, int x2, int y2, int z2) {
        List<BlockPos> list = new ArrayList<>();

        if (x1 != x2) {
            for (int x = x1; x1 < x2 ? x <= x2 : x >= x2; x += x1 < x2 ? 1 : -1) {
                list.add(new BlockPos(x, y1, z1));
            }
        } else if (y1 != y2) {
            for (int y = y1; y1 < y2 ? y <= y2 : y >= y2; y += y1 < y2 ? 1 : -1) {
                list.add(new BlockPos(x1, y, z1));
            }
        } else {
            for (int z = z1; z1 < z2 ? z <= z2 : z >= z2; z += z1 < z2 ? 1 : -1) {
                list.add(new BlockPos(x1, y1, z));
            }
        }

        return list;
    }

    private static Vec3 getPlayerLookVec(Player player) {
        Vec3 lookVec = player.getLookAngle();
        double x = lookVec.x;
        double y = lookVec.y;
        double z = lookVec.z;

        // Avoid exactly 0 or 1 to prevent division issues
        if (Math.abs(x) < 0.0001) x = 0.0001;
        if (Math.abs(x - 1.0) < 0.0001) x = 0.9999;
        if (Math.abs(x + 1.0) < 0.0001) x = -0.9999;

        if (Math.abs(y) < 0.0001) y = 0.0001;
        if (Math.abs(y - 1.0) < 0.0001) y = 0.9999;
        if (Math.abs(y + 1.0) < 0.0001) y = -0.9999;

        if (Math.abs(z) < 0.0001) z = 0.0001;
        if (Math.abs(z - 1.0) < 0.0001) z = 0.9999;
        if (Math.abs(z + 1.0) < 0.0001) z = -0.9999;

        return new Vec3(x, y, z);
    }

    private static Vec3 findXBound(double x, Vec3 start, Vec3 look) {
        double y = (x - start.x) / look.x * look.y + start.y;
        double z = (x - start.x) / look.x * look.z + start.z;
        return new Vec3(x, y, z);
    }

    private static Vec3 findYBound(double y, Vec3 start, Vec3 look) {
        double x = (y - start.y) / look.y * look.x + start.x;
        double z = (y - start.y) / look.y * look.z + start.z;
        return new Vec3(x, y, z);
    }

    private static Vec3 findZBound(double z, Vec3 start, Vec3 look) {
        double x = (z - start.z) / look.z * look.x + start.x;
        double y = (z - start.z) / look.z * look.y + start.y;
        return new Vec3(x, y, z);
    }

    private static class LineCriteria {
        Vec3 planeBound;
        Vec3 lineBound;
        double distToLineSq;
        double distToPlayerSq;

        LineCriteria(Vec3 planeBound, BlockPos firstPos, Vec3 start) {
            this.planeBound = planeBound;
            this.lineBound = toLongestLine(planeBound, firstPos);
            this.distToLineSq = this.lineBound.subtract(planeBound).lengthSqr();
            this.distToPlayerSq = planeBound.subtract(start).lengthSqr();
        }

        // Convert plane bound to axis-aligned line (select longest axis)
        private Vec3 toLongestLine(Vec3 boundVec, BlockPos firstPos) {
            int bx = (int) Math.floor(boundVec.x);
            int by = (int) Math.floor(boundVec.y);
            int bz = (int) Math.floor(boundVec.z);

            int dx = Math.abs(bx - firstPos.getX());
            int dy = Math.abs(by - firstPos.getY());
            int dz = Math.abs(bz - firstPos.getZ());

            int longest = Math.max(dx, Math.max(dy, dz));
            if (longest == dx && dx > 0) {
                return new Vec3(bx, firstPos.getY(), firstPos.getZ());
            }
            if (longest == dy && dy > 0) {
                return new Vec3(firstPos.getX(), by, firstPos.getZ());
            }
            if (longest == dz && dz > 0) {
                return new Vec3(firstPos.getX(), firstPos.getY(), bz);
            }
            return new Vec3(firstPos.getX(), firstPos.getY(), firstPos.getZ());
        }

        boolean isValid(Vec3 start, Vec3 look, int reach) {
            // Must be in front of player
            if (planeBound.subtract(start).dot(look) <= 0) return false;
            // Must be at least 1 block away and within reach
            if (distToPlayerSq < 1 || distToPlayerSq > reach * reach) return false;
            return true;
        }
    }
}

