package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import appeng.api.config.Actionable;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.items.tools.MemoryCardItem;
import appeng.items.tools.NetworkToolItem;
import appeng.api.networking.security.IActionSource;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for applying memory card settings to placed blocks/parts
 */
public class MemoryCardHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Count how many of a specific item the player has in their inventory and network tool
     *
     * @param player the player
     * @param item   the item to count
     * @return the total count
     */
    private static int countItemInPlayerAndNetworkTool(Player player, Item item) {
        int count = player.getInventory().countItem(item);
        
        // Also check network tool inventory
        var networkTool = NetworkToolItem.findNetworkToolInv(player);
        if (networkTool != null) {
            for (var stack : networkTool.getInventory()) {
                if (stack.getItem() == item) {
                    count += stack.getCount();
                }
            }
        }
        
        return count;
    }

    /**
     * Check only the amount still needed from ME storage to avoid overflowing int counters
     * with effectively unlimited storage providers.
     */
    private static long countAvailableFromNetwork(MEStorage storage, Item item, long needed,
            IActionSource source) {
        if (needed <= 0) {
            return 0;
        }

        var key = AEItemKey.of(item);
        if (key == null) {
            return 0;
        }

        return Math.min(needed, storage.extract(key, needed, Actionable.SIMULATE, source));
    }

    /**
     * Get the expected settings name for a block entity
     *
     * @param be the block entity
     * @return the settings name (description id of the block item)
     */
    private static String getBlockEntitySettingsName(BlockEntity be) {
        var blockState = be.getBlockState();
        var block = blockState.getBlock();
        return block.asItem().getDescriptionId();
    }

    /**
     * Get the expected settings name for a part
     *
     * @param part the part
     * @return the settings name (description id of the part item, with block equivalents)
     */
    private static String getPartSettingsName(IPart part) {
        Item partItem = part.getPartItem().asItem();
        
        // Blocks and parts share the same soul! (same as AE2's logic)
        if (AEParts.INTERFACE.stack().getItem() == partItem) {
            partItem = AEBlocks.INTERFACE.stack().getItem();
        } else if (AEParts.PATTERN_PROVIDER.stack().getItem() == partItem) {
            partItem = AEBlocks.PATTERN_PROVIDER.stack().getItem();
        }
        
        return partItem.getDescriptionId();
    }

    /**
     * Check if the player has a configured memory card in their off-hand
     *
     * @param player the player to check
     * @return true if off-hand contains a configured memory card
     */
    public static boolean hasConfiguredMemoryCard(Player player) {
        ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offHandStack.isEmpty()) {
            return false;
        }
        if (!(offHandStack.getItem() instanceof IMemoryCard memoryCard)) {
            return false;
        }
        // Check if the memory card has any stored settings
        CompoundTag data = memoryCard.getData(offHandStack);
        return data != null && !data.isEmpty();
    }

    /**
     * Get the memory card from player's off-hand
     *
     * @param player the player
     * @return the memory card item stack, or empty if not present
     */
    public static ItemStack getOffHandMemoryCard(Player player) {
        ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!offHandStack.isEmpty() && offHandStack.getItem() instanceof IMemoryCard) {
            return offHandStack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Count how many patterns are stored in the memory card data
     *
     * @param data the memory card data
     * @return the number of patterns that need blank patterns
     */
    private static int countPatternsInMemoryCard(CompoundTag data) {
        if (!data.contains(PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS)) {
            return 0;
        }
        
        var desiredPatterns = new AppEngInternalInventory(9); // Default pattern provider size
        desiredPatterns.readFromNBT(data, PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS);
        
        int count = 0;
        for (int i = 0; i < desiredPatterns.size(); i++) {
            ItemStack pattern = desiredPatterns.getStackInSlot(i);
            if (!pattern.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get the upgrades stored in the memory card data
     *
     * @param data the memory card data
     * @return a map of Item to count for desired upgrades
     */
    private static Map<Item, Integer> getUpgradesInMemoryCard(CompoundTag data) {
        Map<Item, Integer> upgrades = new HashMap<>();
        
        if (!data.contains("upgrades", Tag.TAG_COMPOUND)) {
            return upgrades;
        }
        
        var upgradesTag = data.getCompound("upgrades");
        for (String itemIdStr : upgradesTag.getAllKeys()) {
            try {
                ResourceLocation itemId = new ResourceLocation(itemIdStr);
                var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                if (item != null) {
                    int count = upgradesTag.getInt(itemIdStr);
                    if (count > 0) {
                        upgrades.put(item, count);
                    }
                }
            } catch (Exception e) {
                // debug removed: failed to parse upgrade item id
            }
        }
        
        return upgrades;
    }

    /**
     * Pre-fetch blank patterns from AE network to player inventory before applying memory card settings.
     * This allows the pattern provider's importSettings to find enough blank patterns.
     * Only fetches from network if player inventory + network tool don't have enough.
     *
     * @param player the player
     * @param grid   the AE grid to extract from
     * @param data   the memory card data
     * @return the number of blank patterns added to player inventory
     */
    private static int preFetchBlankPatternsFromNetwork(Player player, IGrid grid, CompoundTag data,
            IActionSource source) {
        if (player.getAbilities().instabuild) {
            return 0; // Creative mode doesn't need blank patterns
        }
        
        int patternsNeeded = countPatternsInMemoryCard(data);
        if (patternsNeeded <= 0) {
            return 0;
        }
        
        // Check how many blank patterns the player already has (inventory + network tool)
        int existingBlankPatterns = countItemInPlayerAndNetworkTool(player,
                AEItems.BLANK_PATTERN.stack().getItem());
        int needToFetch = patternsNeeded - existingBlankPatterns;
        
        if (needToFetch <= 0) {
            return 0; // Player already has enough
        }
        
        // debug removed: need to fetch blank patterns
        
        // Try to extract blank patterns from the AE network
        var storage = grid.getStorageService().getInventory();
        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.stack().getItem());
        
        // First simulate to see how many we can get
        long available = storage.extract(blankPatternKey, needToFetch, Actionable.SIMULATE, source);
        if (available <= 0) {
            return 0;
        }
        
        // Actually extract
        long extracted = storage.extract(blankPatternKey, Math.min(available, needToFetch), Actionable.MODULATE,
                source);
            if (extracted > 0) {
            // Add to player inventory
            ItemStack blankPatterns = AEItems.BLANK_PATTERN.stack((int) extracted);
            player.getInventory().placeItemBackInInventory(blankPatterns);
            // debug removed: pre-fetched blank patterns
        }
        
        return (int) extracted;
    }

    /**
     * Pre-fetch upgrades (acceleration cards, etc.) from AE network to player inventory before applying memory card settings.
     * This allows the importSettings to find enough upgrade cards.
     * Only fetches from network if player inventory + network tool don't have enough.
     *
     * @param player the player
     * @param grid   the AE grid to extract from
     * @param data   the memory card data
     * @return the total number of upgrades added to player inventory
     */
    private static int preFetchUpgradesFromNetwork(Player player, IGrid grid, CompoundTag data,
            IActionSource source) {
        if (player.getAbilities().instabuild) {
            return 0; // Creative mode doesn't need upgrades
        }
        
        Map<Item, Integer> desiredUpgrades = getUpgradesInMemoryCard(data);
        if (desiredUpgrades.isEmpty()) {
            return 0;
        }
        
        var storage = grid.getStorageService().getInventory();
        int totalFetched = 0;
        
        for (var entry : desiredUpgrades.entrySet()) {
            Item upgradeItem = entry.getKey();
            int needed = entry.getValue();
            
            // Check how many the player already has (inventory + network tool)
            int existing = countItemInPlayerAndNetworkTool(player, upgradeItem);
            int needToFetch = needed - existing;
            
            if (needToFetch <= 0) {
                continue; // Player already has enough
            }
            
            // debug removed: need to fetch upgrades
            
            // Try to extract from AE network
            var upgradeKey = AEItemKey.of(upgradeItem);
            if (upgradeKey == null) {
                continue;
            }
            
            // First simulate
            long available = storage.extract(upgradeKey, needToFetch, Actionable.SIMULATE, source);
            if (available <= 0) {
                continue;
            }
            
            // Actually extract
            long extracted = storage.extract(upgradeKey, Math.min(available, needToFetch), Actionable.MODULATE,
                    source);
                if (extracted > 0) {
                // Add to player inventory
                ItemStack upgradeStack = new ItemStack(upgradeItem, (int) extracted);
                player.getInventory().placeItemBackInInventory(upgradeStack);
                totalFetched += (int) extracted;
                // debug removed: pre-fetched upgrades
            }
        }
        
        return totalFetched;
    }

    /**
     * Pre-fetch all required items (blank patterns and upgrades) from AE network
     *
     * @param player the player
     * @param grid   the AE grid to extract from
     * @param data   the memory card data
     */
    private static void preFetchAllFromNetwork(Player player, IGrid grid, CompoundTag data,
            IActionSource source) {
        // Pre-fetch blank patterns if memory card contains patterns
        if (data.contains(PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS)) {
            preFetchBlankPatternsFromNetwork(player, grid, data, source);
        }
        
        // Pre-fetch upgrades if memory card contains upgrades
        if (data.contains("upgrades", Tag.TAG_COMPOUND)) {
            preFetchUpgradesFromNetwork(player, grid, data, source);
        }
    }

    /**
     * Result of resource check for memory card application
     */
    public static class ResourceCheckResult {
        public final boolean sufficient;
        public final Map<Item, Integer> missingItems; // Item -> missing count

        public ResourceCheckResult(boolean sufficient, Map<Item, Integer> missingItems) {
            this.sufficient = sufficient;
            this.missingItems = missingItems;
        }

        /**
         * Get a formatted message describing missing items
         */
        public String getMissingItemsMessage() {
            if (missingItems.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (var entry : missingItems.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(entry.getValue()).append("x ").append(entry.getKey().getDescription().getString());
            }
            return sb.toString();
        }
    }

    /**
     * Check if there are enough resources (blank patterns, upgrades) available to apply memory card settings
     * to multiple blocks. Checks player inventory, network tool, and AE network.
     *
     * @param player     the player
     * @param grid       the AE grid to check (can be null)
     * @param blockCount the number of blocks to apply settings to
     * @return ResourceCheckResult indicating if resources are sufficient and what's missing
     */
    public static ResourceCheckResult checkResourcesForMultipleBlocks(Player player, IGrid grid, int blockCount,
            IActionSource source) {
        ItemStack memoryCardStack = getOffHandMemoryCard(player);
        if (memoryCardStack.isEmpty()) {
            return new ResourceCheckResult(true, new HashMap<>()); // No memory card, no requirements
        }

        IMemoryCard memoryCard = (IMemoryCard) memoryCardStack.getItem();
        CompoundTag data = memoryCard.getData(memoryCardStack);
        if (data == null || data.isEmpty()) {
            return new ResourceCheckResult(true, new HashMap<>()); // No data, no requirements
        }

        // Creative mode doesn't need resources
        if (player.getAbilities().instabuild) {
            return new ResourceCheckResult(true, new HashMap<>());
        }

        Map<Item, Integer> missingItems = new HashMap<>();

        // Check blank patterns
        int patternsPerBlock = countPatternsInMemoryCard(data);
        if (patternsPerBlock > 0) {
            long totalPatternsNeeded = (long) patternsPerBlock * blockCount;
            long available = countItemInPlayerAndNetworkTool(player,
                    AEItems.BLANK_PATTERN.stack().getItem());
            
            // Also count from AE network
            if (grid != null) {
                var storage = grid.getStorageService().getInventory();
                available += countAvailableFromNetwork(storage, AEItems.BLANK_PATTERN.stack().getItem(),
                        totalPatternsNeeded - available, source);
            }

            if (available < totalPatternsNeeded) {
                missingItems.put(AEItems.BLANK_PATTERN.stack().getItem(),
                        saturatingInt(totalPatternsNeeded - available));
            }
        }

        // Check upgrades
        Map<Item, Integer> upgradesPerBlock = getUpgradesInMemoryCard(data);
        for (var entry : upgradesPerBlock.entrySet()) {
            Item upgradeItem = entry.getKey();
            long totalNeeded = (long) entry.getValue() * blockCount;
            long available = countItemInPlayerAndNetworkTool(player, upgradeItem);

            // Also count from AE network
            if (grid != null) {
                var storage = grid.getStorageService().getInventory();
                available += countAvailableFromNetwork(storage, upgradeItem, totalNeeded - available, source);
            }

            if (available < totalNeeded) {
                missingItems.put(upgradeItem, saturatingInt(totalNeeded - available));
            }
        }

        return new ResourceCheckResult(missingItems.isEmpty(), missingItems);
    }

    private static int saturatingInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(value, 0);
    }

    /**
     * Apply memory card settings to a placed block entity
     *
     * @param player    the player who placed the block
     * @param level     the world
     * @param pos       the position of the placed block
     * @param showMessage whether to show a message to the player
     * @return true if settings were applied successfully
     */
    public static boolean applyMemoryCardToBlock(Player player, Level level, BlockPos pos, boolean showMessage) {
        return applyMemoryCardToBlock(player, level, pos, showMessage, null, null);
    }

    /**
     * Apply memory card settings to a placed block entity, with optional AE grid for pattern fetching
     *
     * @param player      the player who placed the block
     * @param level       the world
     * @param pos         the position of the placed block
     * @param showMessage whether to show a message to the player
     * @param grid        the AE grid to fetch blank patterns and upgrades from (can be null)
     * @return true if settings were applied successfully
     */
    public static boolean applyMemoryCardToBlock(Player player, Level level, BlockPos pos, boolean showMessage,
            IGrid grid, IActionSource source) {
        ItemStack memoryCardStack = getOffHandMemoryCard(player);
        if (memoryCardStack.isEmpty()) {
            return false;
        }

        IMemoryCard memoryCard = (IMemoryCard) memoryCardStack.getItem();
        CompoundTag data = memoryCard.getData(memoryCardStack);
        if (data == null || data.isEmpty()) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }

        // Get the settings name from memory card
        String storedName = memoryCard.getSettingsName(memoryCardStack);

        // Pre-fetch all required items (blank patterns, upgrades) from AE network
        if (grid != null && source != null) {
            preFetchAllFromNetwork(player, grid, data, source);
        }

        boolean applied = false;
        boolean exactMatch = false;

        // Try to apply to AE2 BlockEntity
        if (be instanceof AEBaseBlockEntity aeBlockEntity) {
            String expectedName = getBlockEntitySettingsName(be);
            exactMatch = expectedName.equals(storedName);
            
            try {
                if (exactMatch) {
                    // Exact match - use importSettings
                    aeBlockEntity.importSettings(SettingsFrom.MEMORY_CARD, data, player);
                    applied = true;
                } else {
                    // Not exact match - use importGenericSettingsAndNotify for partial restore
                    if (showMessage) {
                        MemoryCardItem.importGenericSettingsAndNotify(aeBlockEntity, data, player);
                    } else {
                        MemoryCardItem.importGenericSettings(aeBlockEntity, data, player);
                    }
                    applied = true;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to apply memory card settings to BlockEntity at {}", pos, e);
            }
        }

        // Try to apply to IPartHost (for cable bus blocks) - only if not already applied as AEBaseBlockEntity
        if (!applied && be instanceof IPartHost partHost) {
            // Try to apply to all parts in the host
            for (Direction side : Direction.values()) {
                IPart part = partHost.getPart(side);
                if (part != null) {
                    String expectedName = getPartSettingsName(part);
                    boolean partExactMatch = expectedName.equals(storedName);
                    
                    try {
                        if (partExactMatch) {
                            part.importSettings(SettingsFrom.MEMORY_CARD, data, player);
                            exactMatch = true;
                        } else {
                            if (showMessage) {
                                MemoryCardItem.importGenericSettingsAndNotify(part, data, player);
                            } else {
                                MemoryCardItem.importGenericSettings(part, data, player);
                            }
                        }
                        applied = true;
                    } catch (Exception e) {
                        LOGGER.warn("Failed to apply memory card settings to part on side {} at {}", side, pos, e);
                    }
                }
            }
            // Also try center part (cable)
            IPart centerPart = partHost.getPart(null);
            if (centerPart != null) {
                String expectedName = getPartSettingsName(centerPart);
                boolean partExactMatch = expectedName.equals(storedName);
                
                try {
                    if (partExactMatch) {
                        centerPart.importSettings(SettingsFrom.MEMORY_CARD, data, player);
                        exactMatch = true;
                    } else {
                        if (showMessage) {
                            MemoryCardItem.importGenericSettingsAndNotify(centerPart, data, player);
                        } else {
                            MemoryCardItem.importGenericSettings(centerPart, data, player);
                        }
                    }
                    applied = true;
                } catch (Exception e) {
                    LOGGER.warn("Failed to apply memory card settings to center part at {}", pos, e);
                }
            }
        }

        // Only show SETTINGS_LOADED for exact match, otherwise importGenericSettingsAndNotify already showed message
        if (applied && showMessage && exactMatch) {
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        }

        return applied;
    }

    /**
     * Apply memory card settings to a newly placed part
     *
     * @param player    the player who placed the part
     * @param level     the world
     * @param pos       the position of the part host
     * @param side      the side where the part was placed (null for center/cable)
     * @param showMessage whether to show a message to the player
     * @return true if settings were applied successfully
     */
    public static boolean applyMemoryCardToPart(Player player, Level level, BlockPos pos, Direction side, boolean showMessage) {
        ItemStack memoryCardStack = getOffHandMemoryCard(player);
        if (memoryCardStack.isEmpty()) {
            return false;
        }

        IMemoryCard memoryCard = (IMemoryCard) memoryCardStack.getItem();
        CompoundTag data = memoryCard.getData(memoryCardStack);
        if (data == null || data.isEmpty()) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof IPartHost partHost)) {
            return false;
        }

        IPart part = partHost.getPart(side);
        if (part == null) {
            return false;
        }

        // Get the settings name from memory card
        String storedName = memoryCard.getSettingsName(memoryCardStack);
        String expectedName = getPartSettingsName(part);
        boolean exactMatch = expectedName.equals(storedName);

        boolean applied = false;
        try {
                if (exactMatch) {
                // Exact match - use importSettings
                part.importSettings(SettingsFrom.MEMORY_CARD, data, player);
                applied = true;
            } else {
                // Not exact match - use importGenericSettingsAndNotify for partial restore
                if (showMessage) {
                    MemoryCardItem.importGenericSettingsAndNotify(part, data, player);
                } else {
                    MemoryCardItem.importGenericSettings(part, data, player);
                }
                applied = true;
                // debug removed: applied partial memory card settings to part
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to apply memory card settings to part at {} on side {}", pos, side, e);
        }

        // Only show SETTINGS_LOADED for exact match
        if (applied && showMessage && exactMatch) {
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        }

        return applied;
    }

    /**
     * Apply memory card settings to a specific part instance
     *
     * @param player    the player who placed the part
     * @param part      the part to apply settings to
     * @param showMessage whether to show a message to the player
     * @return true if settings were applied successfully
     */
    public static boolean applyMemoryCardToPart(Player player, IPart part, boolean showMessage) {
        return applyMemoryCardToPart(player, part, showMessage, null, null);
    }

    /**
     * Apply memory card settings to a specific part instance, with optional AE grid for pattern fetching
     *
     * @param player      the player who placed the part
     * @param part        the part to apply settings to
     * @param showMessage whether to show a message to the player
     * @param grid        the AE grid to fetch blank patterns and upgrades from (can be null)
     * @return true if settings were applied successfully
     */
    public static boolean applyMemoryCardToPart(Player player, IPart part, boolean showMessage, IGrid grid,
            IActionSource source) {
        if (part == null) {
            return false;
        }

        ItemStack memoryCardStack = getOffHandMemoryCard(player);
        if (memoryCardStack.isEmpty()) {
            return false;
        }

        IMemoryCard memoryCard = (IMemoryCard) memoryCardStack.getItem();
        CompoundTag data = memoryCard.getData(memoryCardStack);
        if (data == null || data.isEmpty()) {
            return false;
        }

        // Pre-fetch all required items (blank patterns, upgrades) from AE network
        if (grid != null && source != null) {
            preFetchAllFromNetwork(player, grid, data, source);
        }

        // Get the settings name from memory card
        String storedName = memoryCard.getSettingsName(memoryCardStack);
        String expectedName = getPartSettingsName(part);
        boolean exactMatch = expectedName.equals(storedName);

        boolean applied = false;
        try {
            if (exactMatch) {
                // Exact match - use importSettings
                part.importSettings(SettingsFrom.MEMORY_CARD, data, player);
                applied = true;
            } else {
                // Not exact match - use importGenericSettingsAndNotify for partial restore
                if (showMessage) {
                    MemoryCardItem.importGenericSettingsAndNotify(part, data, player);
                } else {
                    MemoryCardItem.importGenericSettings(part, data, player);
                }
                applied = true;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to apply memory card settings to part", e);
        }

        // Only show SETTINGS_LOADED for exact match
        if (applied && showMessage && exactMatch) {
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        }

        return applied;
    }
}
