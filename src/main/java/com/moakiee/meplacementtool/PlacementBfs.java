package com.moakiee.meplacementtool;

import com.moakiee.meplacementtool.ItemMultiblockPlacementTool.DirectionMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parameterized BFS algorithm for finding block placement positions.
 * Eliminates duplication of the BFS skeleton across tool items and preview renderers.
 */
public final class PlacementBfs {

    private PlacementBfs() {}

    @FunctionalInterface
    public interface SupportChecker {
        boolean matches(BlockPos candidate);
    }

    @FunctionalInterface
    public interface CanPlaceChecker {
        boolean canPlace(BlockPos candidate);
    }

    /**
     * Find placement positions using BFS starting from the given point.
     *
     * @param startPoint     The first position to consider
     * @param maxCount       Maximum number of positions to find
     * @param face           The clicked face (used for adjacency expansion in AUTO mode)
     * @param directionMode  Direction mode for expansion and support fallback
     * @param supportChecker Checks if the supporting block matches (does NOT need to handle locked-mode fallback)
     * @param canPlaceChecker Checks if a block/part can be placed at the position
     * @return List of positions where placement is possible
     */
    public static List<BlockPos> findPositions(
            BlockPos startPoint,
            int maxCount,
            Direction face,
            DirectionMode directionMode,
            SupportChecker supportChecker,
            CanPlaceChecker canPlaceChecker) {

        if (maxCount <= 0) return new ArrayList<>();

        Deque<BlockPos> candidates = new ArrayDeque<>();
        Set<BlockPos> allCandidates = new HashSet<>();
        List<BlockPos> placePositions = new ArrayList<>();
        Set<BlockPos> acceptedPositions = new HashSet<>();

        candidates.add(startPoint);
        final int MAX_CANDIDATES = maxCount * 10;

        while (!candidates.isEmpty() && placePositions.size() < maxCount && allCandidates.size() < MAX_CANDIDATES) {
            BlockPos current = candidates.removeFirst();
            if (!allCandidates.add(current)) {
                continue;
            }

            boolean supportMatches = supportChecker.matches(current);
            if (!supportMatches && directionMode != DirectionMode.AUTO
                    && !hasLockedModeSupport(current, acceptedPositions, directionMode)) {
                continue;
            }

            if (canPlaceChecker.canPlace(current)) {
                placePositions.add(current);
                acceptedPositions.add(current);
                addAdjacentPositions(candidates, current, face, directionMode);
            }
        }

        return placePositions;
    }

    public static boolean hasLockedModeSupport(BlockPos candidate, Set<BlockPos> acceptedPositions,
            DirectionMode directionMode) {
        switch (directionMode) {
            case NORTH_SOUTH:
                return acceptedPositions.contains(candidate.north()) || acceptedPositions.contains(candidate.south());
            case EAST_WEST:
                return acceptedPositions.contains(candidate.east()) || acceptedPositions.contains(candidate.west());
            case VERTICAL:
                return acceptedPositions.contains(candidate.above()) || acceptedPositions.contains(candidate.below());
            case AUTO:
            default:
                return false;
        }
    }

    public static void addAdjacentPositions(Deque<BlockPos> candidates, BlockPos pos,
            Direction face, DirectionMode directionMode) {
        switch (directionMode) {
            case NORTH_SOUTH:
                candidates.add(pos.north());
                candidates.add(pos.south());
                break;
            case EAST_WEST:
                candidates.add(pos.east());
                candidates.add(pos.west());
                break;
            case VERTICAL:
                candidates.add(pos.above());
                candidates.add(pos.below());
                break;
            case AUTO:
            default:
                addAutoAdjacentPositions(candidates, pos, face);
                break;
        }
    }

    public static void addAutoAdjacentPositions(Deque<BlockPos> candidates, BlockPos pos, Direction face) {
        switch (face) {
            case DOWN:
            case UP:
                candidates.add(pos.north());
                candidates.add(pos.south());
                candidates.add(pos.east());
                candidates.add(pos.west());
                candidates.add(pos.north().east());
                candidates.add(pos.north().west());
                candidates.add(pos.south().east());
                candidates.add(pos.south().west());
                break;
            case NORTH:
            case SOUTH:
                candidates.add(pos.above());
                candidates.add(pos.below());
                candidates.add(pos.east());
                candidates.add(pos.west());
                candidates.add(pos.above().east());
                candidates.add(pos.above().west());
                candidates.add(pos.below().east());
                candidates.add(pos.below().west());
                break;
            case EAST:
            case WEST:
                candidates.add(pos.above());
                candidates.add(pos.below());
                candidates.add(pos.north());
                candidates.add(pos.south());
                candidates.add(pos.above().north());
                candidates.add(pos.above().south());
                candidates.add(pos.below().north());
                candidates.add(pos.below().south());
                break;
        }
    }
}
