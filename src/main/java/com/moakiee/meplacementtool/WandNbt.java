package com.moakiee.meplacementtool;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Utility methods for reading/writing wand NBT data.
 */
public final class WandNbt {

    private WandNbt() {}

    /**
     * Read the wand's configuration CompoundTag (the "placement_config" sub-tag).
     * Returns null if not present.
     */
    public static CompoundTag getConfig(ItemStack wand) {
        CompoundTag data = wand.getTag();
        if (data != null && data.contains(WandMenu.TAG_KEY)) {
            return data.getCompound(WandMenu.TAG_KEY);
        }
        return null;
    }

    /**
     * Deserialize the 18-slot ItemStackHandler from the wand's config tag.
     * Handles both old format (cfg == handler NBT) and new format (cfg.items == handler NBT).
     * Also handles old 9-slot data by expanding to 18 slots.
     */
    public static ItemStackHandler readInventory(CompoundTag cfg) {
        ItemStackHandler handler = new ItemStackHandler(18);
        if (cfg != null) {
            CompoundTag itemsTag = cfg.contains("items") ? cfg.getCompound("items") : cfg;
            if (itemsTag.contains("Items")) {
                net.minecraft.nbt.ListTag list = itemsTag.getList("Items", 10);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag itemTag = list.getCompound(i);
                    int slot = itemTag.getInt("Slot");
                    if (slot >= 0 && slot < 18) {
                        handler.setStackInSlot(slot, net.minecraft.world.item.ItemStack.of(itemTag));
                    }
                }
            } else {
                // Fallback: let deserializeNBT handle it, then verify slot count
                handler.deserializeNBT(itemsTag);
                if (handler.getSlots() < 18) {
                    ItemStackHandler expanded = new ItemStackHandler(18);
                    for (int i = 0; i < handler.getSlots(); i++) {
                        expanded.setStackInSlot(i, handler.getStackInSlot(i));
                    }
                    handler = expanded;
                }
            }
        }
        return handler;
    }

    /**
     * Get the selected slot index from the config tag, clamped to [0, 17].
     */
    public static int getSelectedSlot(CompoundTag cfg) {
        if (cfg != null && cfg.contains("SelectedSlot")) {
            int selected = cfg.getInt("SelectedSlot");
            if (selected >= 0 && selected < 18) return selected;
        }
        return 0;
    }
}
