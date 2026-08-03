package com.moakiee.meplacementtool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;

import java.util.*;

public class UndoHistory
{
    private final LinkedHashMap<UUID, PlayerEntry> history;
    private static final int MAX_HISTORY_SIZE = 1;
    private static final int MAX_IDLE_PLAYER_ENTRIES = 100;

    public UndoHistory() {
        history = new LinkedHashMap<>(16, 0.75f, true);
    }

    /**
     * Called when a player logs out to clean up their undo history.
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        history.remove(event.getEntity().getUUID());
    }

    /**
     * Remove stale entries if the map grows too large.
     * Evicts the oldest (least recently accessed) entries to stay under the limit.
     * Called periodically from placement operations.
     */
    public void trimIfNeeded() {
        if (history.size() <= MAX_IDLE_PLAYER_ENTRIES) return;
        var iter = history.keySet().iterator();
        while (history.size() > MAX_IDLE_PLAYER_ENTRIES && iter.hasNext()) {
            iter.next();
            iter.remove();
        }
    }

    private PlayerEntry getEntryFromPlayer(Player player) {
        return history.computeIfAbsent(player.getUUID(), k -> new PlayerEntry());
    }

    public void add(Player player, Level world, List<PlacementSnapshot> placeSnapshots) {
        add(player, world, placeSnapshots, false);
    }

    public void add(Player player, Level world, List<PlacementSnapshot> placeSnapshots, boolean memoryCardApplied) {
        trimIfNeeded();
        LinkedList<HistoryEntry> list = getEntryFromPlayer(player).entries;
        list.clear();
        list.add(new HistoryEntry(placeSnapshots, world, memoryCardApplied, false));
    }

    /**
     * Add cable placement history with return key override.
     * When undoing cable placements, we return transparent cables instead of the colored ones.
     */
    public void addCablePlacement(Player player, Level world, List<CablePlacementSnapshot> cableSnapshots) {
        trimIfNeeded();
        LinkedList<HistoryEntry> list = getEntryFromPlayer(player).entries;
        list.clear();
        // Convert CablePlacementSnapshot to PlacementSnapshot for storage
        List<PlacementSnapshot> snapshots = new ArrayList<>();
        for (CablePlacementSnapshot cableSnap : cableSnapshots) {
            snapshots.add(cableSnap);
        }
        list.add(new HistoryEntry(snapshots, world, false, true));
    }

    public void removePlayer(Player player) {
        history.remove(player.getUUID());
    }

    /**
     * Result of undo operation
     */
    public enum UndoResult {
        SUCCESS,
        NO_HISTORY,
        OUT_OF_RANGE,
        MEMORY_CARD_APPLIED,
        FAILED
    }

    public UndoResult undoWithResult(Player player, Level world, BlockPos pos) {
        PlayerEntry playerEntry = getEntryFromPlayer(player);
        LinkedList<HistoryEntry> historyEntries = playerEntry.entries;
        if(historyEntries.isEmpty()) return UndoResult.NO_HISTORY;
        HistoryEntry entry = historyEntries.getLast();

        if(!entry.world.equals(world) || !entry.withinRange(pos)) return UndoResult.OUT_OF_RANGE;

        if(entry.memoryCardApplied) return UndoResult.MEMORY_CARD_APPLIED;

        if(entry.undo(player)) {
            historyEntries.remove(entry);
            return UndoResult.SUCCESS;
        }
        return UndoResult.FAILED;
    }

    public boolean undo(Player player, Level world, BlockPos pos) {
        return undoWithResult(player, world, pos) == UndoResult.SUCCESS;
    }

    private static class PlayerEntry
    {
        public final LinkedList<HistoryEntry> entries;

        public PlayerEntry() {
            entries = new LinkedList<>();
        }
    }

    private static class HistoryEntry
    {
        public final List<PlacementSnapshot> placeSnapshots;
        public final Level world;
        public final boolean memoryCardApplied;
        public final boolean isCablePlacement;

        public HistoryEntry(List<PlacementSnapshot> placeSnapshots, Level world, boolean memoryCardApplied, boolean isCablePlacement) {
            this.placeSnapshots = placeSnapshots;
            this.world = world;
            this.memoryCardApplied = memoryCardApplied;
            this.isCablePlacement = isCablePlacement;
        }

        public Set<BlockPos> getBlockPositions() {
            Set<BlockPos> positions = new HashSet<>();
            for(PlacementSnapshot snapshot : placeSnapshots) {
                positions.add(snapshot.pos);
            }
            return positions;
        }

        public boolean withinRange(BlockPos pos) {
            Set<BlockPos> positions = getBlockPositions();

            if(positions.contains(pos)) return true;

            for(BlockPos p : positions) {
                if(pos.closerThan(p, 3)) return true;
            }
            return false;
        }

        public boolean undo(Player player) {
            ItemStack wand = player.getMainHandItem();
            
            // Check if holding the correct tool
            boolean holdingMultiblockTool = wand.getItem() == MEPlacementToolMod.MULTIBLOCK_PLACEMENT_TOOL.get();
            boolean holdingCableTool = wand.getItem() == MEPlacementToolMod.ME_CABLE_PLACEMENT_TOOL.get();
            
            if (isCablePlacement && !holdingCableTool) {
                return false;
            }
            if (!isCablePlacement && !holdingMultiblockTool) {
                return false;
            }

            BasePlacementToolItem.LinkedNetwork linked = null;
            if(wand.getItem() instanceof BasePlacementToolItem placementTool) {
                linked = placementTool.getLinkedNetwork(wand, world, player);
            }

            if(linked == null) {
                return false;
            }
            var grid = linked.grid();
            var source = appeng.api.networking.security.IActionSource.ofPlayer(player, linked.accessPoint());

            // Check all snapshots can be restored
            for(PlacementSnapshot snapshot : placeSnapshots) {
                if(!snapshot.canRestore(world, player)) return false;
            }
            
            // Perform undo
            for(PlacementSnapshot snapshot : placeSnapshots) {
                if(snapshot.restore(world, player)) {
                    var storage = grid.getStorageService().getInventory();
                    // For cable placements, use the return key (transparent cable)
                    AEKey returnKey = snapshot.getReturnKey();
                    if(returnKey != null) {
                        long inserted = storage.insert(returnKey, snapshot.amount,
                                appeng.api.config.Actionable.MODULATE, source);
                        long remainder = snapshot.amount - inserted;
                        if (remainder > 0 && returnKey instanceof appeng.api.stacks.AEItemKey itemKey) {
                            ItemStack returned = itemKey.toStack((int) Math.min(remainder, Integer.MAX_VALUE));
                            player.getInventory().placeItemBackInInventory(returned);
                        }
                    }
                }
            }

            world.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

            return true;
        }
    }

    public static class PlacementSnapshot
    {
        public final BlockState blockState;
        public final BlockPos pos;
        public final ItemStack placedItem;
        public final AEKey aeKey;
        public final long amount;

        public PlacementSnapshot(BlockState blockState, BlockPos pos, ItemStack placedItem, AEKey aeKey, long amount) {
            this.blockState = blockState;
            this.pos = pos;
            this.placedItem = placedItem;
            this.aeKey = aeKey;
            this.amount = amount;
        }

        public boolean canRestore(Level world, Player player) {
            return world.getBlockState(pos).equals(blockState);
        }

        public boolean restore(Level world, Player player) {
            world.removeBlock(pos, false);
            return true;
        }
        
        /**
         * Get the key to return to AE network when undoing.
         * Override in subclasses to return different items (e.g., transparent cable instead of colored).
         */
        public AEKey getReturnKey() {
            return aeKey;
        }
    }

    public static class PartPlacementSnapshot extends PlacementSnapshot
    {
        public final Direction side;
        public final AEKey returnKey;

        public PartPlacementSnapshot(BlockPos pos, Direction side, AEKey returnKey) {
            super(null, pos, ItemStack.EMPTY, null, 1);
            this.side = side;
            this.returnKey = returnKey;
        }

        @Override
        public boolean canRestore(Level world, Player player) {
            IPartHost host = PartHelper.getPartHost(world, pos);
            return host != null && host.getPart(side) != null;
        }

        @Override
        public boolean restore(Level world, Player player) {
            IPartHost host = PartHelper.getPartHost(world, pos);
            if (host == null || host.getPart(side) == null) {
                return false;
            }

            host.removePartFromSide(side);
            host.markForUpdate();
            if (host.isEmpty()) {
                host.cleanup();
            }
            return true;
        }

        @Override
        public AEKey getReturnKey() {
            return returnKey;
        }
    }
    
    /**
     * Snapshot for cable placements that handles AE2 Part removal.
     */
    public static class CablePlacementSnapshot extends PlacementSnapshot
    {
        public final ItemMECablePlacementTool.CableType cableType;
        public final AEKey returnKey; // Transparent cable key for return
        
        public CablePlacementSnapshot(BlockPos pos, ItemMECablePlacementTool.CableType cableType, AEKey returnKey) {
            super(null, pos, ItemStack.EMPTY, null, 1);
            this.cableType = cableType;
            this.returnKey = returnKey;
        }
        
        @Override
        public boolean canRestore(Level world, Player player) {
            // Check if there's a cable part at this position
            IPartHost host = PartHelper.getPartHost(world, pos);
            if (host == null) return false;
            
            // Check if there's a cable (center part)
            IPart cablePart = host.getPart(null);
            return cablePart != null;
        }
        
        @Override
        public boolean restore(Level world, Player player) {
            IPartHost host = PartHelper.getPartHost(world, pos);
            if (host == null) return false;
            
            // Remove the cable part (center part, side = null)
            IPart cablePart = host.getPart(null);
            if (cablePart != null) {
                // Use removePartFromSide instead of removePart
                host.removePartFromSide(null);
                host.markForUpdate();
                
                // If host is now empty, cleanup the block entity
                if (host.isEmpty()) {
                    host.cleanup();
                }
                return true;
            }
            return false;
        }
        
        @Override
        public AEKey getReturnKey() {
            return returnKey;
        }
    }
}

