package com.moakiee.meplacementtool;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.AEBaseMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

public class WandMenu extends AEBaseMenu {
    public static final String TAG_KEY = "placement_config";
    public static final int SLOTS_PER_PAGE = 9;
    public static final int TOTAL_SLOTS = 18;
    public static final int MAX_PAGES = TOTAL_SLOTS / SLOTS_PER_PAGE;

    private final ItemStackHandler handler;
    private final List<GhostSlot> ghostSlots = new ArrayList<>();
    private final java.util.Map<Integer, String> fluidMap = new java.util.HashMap<>();
    private final InteractionHand hand;
    private final ItemStack toolStack;
    private int currentPage = 0;

    private record BufResult(ItemStackHandler handler, CompoundTag config, InteractionHand hand) {}

    public WandMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, parseBuf(buf));
    }

    private WandMenu(int id, Inventory playerInventory, BufResult result) {
        this(id, playerInventory, result.handler(), result.hand());
        if (result.config() != null && result.config().contains("fluids")) {
            var ftag = result.config().getCompound("fluids");
            for (String key : ftag.getAllKeys()) {
                try {
                    int idx = Integer.parseInt(key);
                    this.fluidMap.put(idx, ftag.getString(key));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private static BufResult parseBuf(FriendlyByteBuf buf) {
        CompoundTag cfg = buf.readNbt();
        ItemStackHandler h = new ItemStackHandler(TOTAL_SLOTS);
        if (cfg != null) {
            CompoundTag itemsTag = cfg.contains("items") ? cfg.getCompound("items") : cfg;
            if (itemsTag.contains("Items")) {
                net.minecraft.nbt.ListTag list = itemsTag.getList("Items", 10);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag itemTag = list.getCompound(i);
                    int slot = itemTag.getInt("Slot");
                    if (slot >= 0 && slot < TOTAL_SLOTS) {
                        h.setStackInSlot(slot, ItemStack.of(itemTag));
                    }
                }
            }
        }
        return new BufResult(h, cfg, buf.readEnum(InteractionHand.class));
    }

    public WandMenu(int id, Inventory playerInventory, ItemStackHandler handler, InteractionHand hand) {
        super(ModMenus.WAND_MENU.get(), id, playerInventory,
                new ItemMenuHost(playerInventory.player,
                        hand == InteractionHand.MAIN_HAND ? playerInventory.selected : Inventory.SLOT_OFFHAND,
                        playerInventory.player.getItemInHand(hand)));
        this.hand = hand;
        this.toolStack = playerInventory.player.getItemInHand(hand);

        // Ensure handler is always 18 slots (expand old 9-slot data if needed)
        if (handler == null) {
            this.handler = new ItemStackHandler(TOTAL_SLOTS);
        } else if (handler.getSlots() < TOTAL_SLOTS) {
            // Expand old handler to 18 slots - backward compatibility
            ItemStackHandler newHandler = new ItemStackHandler(TOTAL_SLOTS);
            for (int i = 0; i < handler.getSlots(); i++) {
                newHandler.setStackInSlot(i, handler.getStackInSlot(i));
            }
            this.handler = newHandler;
        } else {
            this.handler = handler;
        }

        // Register custom semantic for ghost slots
        var ghostSemantic = appeng.menu.SlotSemantics.get("ME_WAND_GHOST");
        if (ghostSemantic == null) {
            ghostSemantic = appeng.menu.SlotSemantics.register("ME_WAND_GHOST", false);
        }

        // Add 9 ghost slots - these render items based on current page via getItem()
        // 3x3 grid: starts at (62,19), 16px slots with 2px spacing = 18px per cell
        int startX = 62;
        int startY = 19;  // Updated from 17 to 19 per new toolbox.png
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int row = i / 3;
            int col = i % 3;
            int x = startX + col * 18;
            int y = startY + row * 18;
            GhostSlot s = new GhostSlot(this.handler, i, x, y);
            s.setMenu(this); // Set menu reference for page-aware item retrieval
            this.addSlot(s, ghostSemantic);
            this.ghostSlots.add(s);
        }

        // Add player inventory slots
        // Main inventory: starts at (8,84), 9 columns x 3 rows, 18px spacing
        int playerInvX = 8;
        int playerInvY = 84;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int x = playerInvX + col * 18;
                int y = playerInvY + row * 18;
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, x, y));
            }
        }
        // Hotbar: starts at (8,142)
        int hotbarY = 142;
        for (int hb = 0; hb < 9; ++hb) {
            int x = playerInvX + hb * 18;
            this.addSlot(new Slot(playerInventory, hb, x, hotbarY));
        }
    }

    public ItemStackHandler getHandler() {
        return this.handler;
    }

    public List<GhostSlot> getGhostSlots() {
        return this.ghostSlots;
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public InteractionHand getToolHand() {
        return hand;
    }

    public void setCurrentPage(int page) {
        if (page >= 0 && page < MAX_PAGES) {
            this.currentPage = page;
        }
    }

    /**
     * Get the actual handler slot index for a visual slot index on the current page.
     */
    public int getActualSlotIndex(int visualIndex) {
        return currentPage * SLOTS_PER_PAGE + visualIndex;
    }

    /**
     * Get the item at a visual slot position on the current page.
     */
    public ItemStack getItemAtVisualSlot(int visualIndex) {
        int actualIndex = getActualSlotIndex(visualIndex);
        if (actualIndex >= 0 && actualIndex < handler.getSlots()) {
            return handler.getStackInSlot(actualIndex);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Set the item at a visual slot position on the current page.
     */
    public void setItemAtVisualSlot(int visualIndex, ItemStack stack) {
        int actualIndex = getActualSlotIndex(visualIndex);
        if (actualIndex >= 0 && actualIndex < handler.getSlots()) {
            handler.setStackInSlot(actualIndex, stack);
        }
    }

    public void setFluidSlot(int index, String fluidId) {
        if (fluidId == null) this.fluidMap.remove(index);
        else this.fluidMap.put(index, fluidId);
    }

    public String getFluidSlot(int index) {
        return this.fluidMap.get(index);
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        // Intercept clicks on our ghost slots
        if (slotId >= 0 && slotId < this.slots.size()) {
            Slot slot = this.slots.get(slotId);
            if (slot instanceof GhostSlot ghostSlot) {
                int visualIndex = ghostSlot.getVisualIndex();
                int actualIndex = getActualSlotIndex(visualIndex);
                
                ItemStack carried = this.getCarried();
                if (!carried.isEmpty()) {
                    ItemStack copy = carried.copy();
                    copy.setCount(1);
                    this.handler.setStackInSlot(actualIndex, copy);
                } else {
                    this.handler.setStackInSlot(actualIndex, ItemStack.EMPTY);
                }
                // Do not propagate default behavior
                return;
            }
        }

        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            // Server side: save to item
            ItemStack currentTool = player.getItemInHand(hand);
            if (currentTool == toolStack && currentTool.getItem() instanceof BasePlacementToolItem) {
                CompoundTag combined = new CompoundTag();
                combined.put("items", this.handler.serializeNBT());
                CompoundTag ftag = new CompoundTag();
                for (var e : this.fluidMap.entrySet()) {
                    ftag.putString(Integer.toString(e.getKey()), e.getValue());
                }
                combined.put("fluids", ftag);

                // Preserve selection metadata while rewriting item/fluid config.
                CompoundTag existing = currentTool.getOrCreateTag();
                if (existing.contains(TAG_KEY)) {
                    CompoundTag prev = existing.getCompound(TAG_KEY);
                    if (prev.contains("SelectedSlot")) {
                        combined.putInt("SelectedSlot", prev.getInt("SelectedSlot"));
                    }
                    if (prev.contains("DirectionMode")) {
                        combined.putInt("DirectionMode", prev.getInt("DirectionMode"));
                    }
                }

                currentTool.getOrCreateTag().put(TAG_KEY, combined);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        ItemStack current = pPlayer.getItemInHand(hand);
        return current.getItem() instanceof BasePlacementToolItem
                && (current == toolStack || (pPlayer.level().isClientSide
                        && ItemStack.isSameItemSameTags(current, toolStack)));
    }
}
