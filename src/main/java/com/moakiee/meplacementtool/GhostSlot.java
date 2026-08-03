package com.moakiee.meplacementtool;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * A ghost slot for the WandMenu that displays items based on the current page.
 * The actual item handler contains items for all pages, but this slot shows
 * only the item for the current page position.
 */
public class GhostSlot extends SlotItemHandler {
    private final int visualIndex; // 0-8 position in the 3x3 grid
    private final IItemHandler itemHandler;
    private WandMenu menu; // Reference to menu for page info

    public GhostSlot(IItemHandler itemHandler, int visualIndex, int x, int y) {
        super(itemHandler, visualIndex, x, y);
        this.visualIndex = visualIndex;
        this.itemHandler = itemHandler;
    }

    /**
     * Set the menu reference for page-aware item retrieval.
     */
    public void setMenu(WandMenu menu) {
        this.menu = menu;
    }

    public int getVisualIndex() {
        return visualIndex;
    }

    /**
     * Returns the item for the current page position.
     * This allows standard Minecraft slot rendering to work correctly.
     */
    @Override
    public ItemStack getItem() {
        if (menu != null) {
            int actualIndex = menu.getActualSlotIndex(visualIndex);
            if (actualIndex >= 0 && actualIndex < itemHandler.getSlots()) {
                return itemHandler.getStackInSlot(actualIndex);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void set(ItemStack stack) {
        // Do nothing - item setting is handled by WandMenu.clicked() with page offset
    }

    @Override
    public void setChanged() {
        // No-op
    }

    @Override
    public ItemStack remove(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false; // Ghost slot, no placing
    }

    @Override
    public boolean mayPickup(Player player) {
        return false; // Ghost slot, no picking up
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
