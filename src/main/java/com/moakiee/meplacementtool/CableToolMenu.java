package com.moakiee.meplacementtool;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.inventories.InternalInventory;
import appeng.client.gui.Icon;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import appeng.api.util.AEColor;

/**
 * Menu for the Cable Placement Tool GUI.
 * Provides cable type, color, and placement mode selection with an upgrade slot for Key of Spectrum.
 */
public class CableToolMenu extends AEBaseMenu {

    private static final String ACTION_SET_MODE = "setMode";
    private static final String ACTION_SET_CABLE_TYPE = "setCableType";
    private static final String ACTION_SET_COLOR = "setColor";
    private static final String ACTION_SET_COLOR_SHORTCUT = "setColorShortcut";

    // Sync fields
    @GuiSync(0)
    public int currentMode;
    @GuiSync(1)
    public int currentCableType;
    @GuiSync(2)
    public int currentColor;
    @GuiSync(3)
    public boolean hasUpgrade;
    
    // Color shortcuts (slots 1-5, slot 0 is always current color)
    @GuiSync(4)
    public int colorShortcut1 = -1;
    @GuiSync(5)
    public int colorShortcut2 = -1;
    @GuiSync(6)
    public int colorShortcut3 = -1;
    @GuiSync(7)
    public int colorShortcut4 = -1;
    @GuiSync(8)
    public int colorShortcut5 = -1;

    private final InternalInventory upgradeInv;
    private final ItemStack toolStack;
    private final Player player;
    private final int toolSlot;

    private record MenuData(ItemStack stack, int slot) {
    }

    public CableToolMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, readMenuData(playerInventory, buf));
    }

    private CableToolMenu(int id, Inventory playerInventory, MenuData data) {
        this(id, playerInventory, data.stack(), data.slot());
    }

    private static MenuData readMenuData(Inventory inventory, FriendlyByteBuf buffer) {
        int slot = buffer.readInt();
        return new MenuData(getToolStackFromSlot(inventory, slot), slot);
    }

    private static ItemStack getToolStackFromSlot(Inventory inv, int slot) {
        if (slot == Inventory.SLOT_OFFHAND) {
            return inv.player.getOffhandItem();
        }
        return slot >= 0 && slot < inv.getContainerSize() ? inv.getItem(slot) : ItemStack.EMPTY;
    }

    public CableToolMenu(int id, Inventory playerInventory, ItemStack toolStack) {
        this(id, playerInventory, toolStack, playerInventory.selected);
    }

    public CableToolMenu(int id, Inventory playerInventory, ItemStack toolStack, int slot) {
        super(ModMenus.CABLE_TOOL_MENU.get(), id, playerInventory,
                new ItemMenuHost(playerInventory.player, slot, toolStack));

        this.player = playerInventory.player;
        this.toolStack = toolStack;
        this.toolSlot = slot;

        // Initialize upgrade inventory with callback when contents change
        this.upgradeInv = new AppEngInternalInventory(new appeng.util.inv.InternalInventoryHost() {
            @Override
            public void saveChanges() {
                onUpgradeChanged();
            }
            
            @Override
            public void onChangeInventory(InternalInventory inv, int slot) {
                onUpgradeChanged();
            }
            
            @Override
            public boolean isClientSide() {
                return player.level().isClientSide;
            }
        }, 1, 1);

        // Load existing upgrade from tool
        loadUpgradeFromTool();

        // Add upgrade slot - only accepts Key of Spectrum
        // Position: GUI_WIDTH(175) + AE2_PADDING(5) + 1 = 181, Y = 6
        var upgradeSlot = new KeyOfSpectrumSlot(this.upgradeInv, 0, 181, 6);
        upgradeSlot.setIcon(Icon.BACKGROUND_UPGRADE);
        this.addSlot(upgradeSlot, SlotSemantics.UPGRADE);

        // Create player inventory slots with custom positions
        // Main inventory: starts at (8,172), 9 columns x 3 rows, 16x16 slots, 2px spacing
        // Hotbar: starts at (8,230), 9 slots
        createCustomPlayerInventorySlots(playerInventory);

        // Load current settings from tool
        loadSettings();

        // Register client actions
        registerClientAction(ACTION_SET_MODE, Integer.class, this::setMode);
        registerClientAction(ACTION_SET_CABLE_TYPE, Integer.class, this::setCableType);
        registerClientAction(ACTION_SET_COLOR, Integer.class, this::setColor);
        registerClientAction(ACTION_SET_COLOR_SHORTCUT, int[].class, this::setColorShortcutFromClient);
    }

    private void loadUpgradeFromTool() {
        if (ItemMECablePlacementTool.hasUpgrade(toolStack)) {
            this.upgradeInv.setItemDirect(0, new ItemStack(MEPlacementToolMod.KEY_OF_SPECTRUM.get()));
        }
        this.hasUpgrade = !this.upgradeInv.getStackInSlot(0).isEmpty();
    }

    private void loadSettings() {
        this.currentMode = ItemMECablePlacementTool.getMode(toolStack).ordinal();
        this.currentCableType = ItemMECablePlacementTool.getCableType(toolStack).ordinal();
        this.currentColor = ItemMECablePlacementTool.getColor(toolStack).ordinal();
        this.hasUpgrade = ItemMECablePlacementTool.hasUpgrade(toolStack);
        
        // Load color shortcuts
        int[] shortcuts = ItemMECablePlacementTool.getColorShortcuts(toolStack);
        if (shortcuts.length >= 5) {
            this.colorShortcut1 = shortcuts[0];
            this.colorShortcut2 = shortcuts[1];
            this.colorShortcut3 = shortcuts[2];
            this.colorShortcut4 = shortcuts[3];
            this.colorShortcut5 = shortcuts[4];
        }
    }

    /**
     * Create player inventory slots at custom positions matching the GUI texture.
     * Inventory area: (8,111)-(167,162), 16x16 slots with 2px spacing (18px total)
     * Hotbar area: (8,169)-(167,184), same slot size
     */
    private void createCustomPlayerInventorySlots(Inventory playerInventory) {
        final int SLOT_SIZE = 18; // 16px slot + 2px spacing
        final int INV_X = 8;
        final int INV_Y = 111;
        final int HOTBAR_Y = 169;
        
        // Main inventory (slots 9-35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9 + 9;
                int x = INV_X + col * SLOT_SIZE;
                int y = INV_Y + row * SLOT_SIZE;
                this.addSlot(new net.minecraft.world.inventory.Slot(playerInventory, index, x, y), SlotSemantics.PLAYER_INVENTORY);
            }
        }
        
        // Hotbar (slots 0-8)
        for (int col = 0; col < 9; col++) {
            int x = INV_X + col * SLOT_SIZE;
            this.addSlot(new net.minecraft.world.inventory.Slot(playerInventory, col, x, HOTBAR_Y), SlotSemantics.PLAYER_HOTBAR);
        }
    }

    private void onUpgradeChanged() {
        boolean newHasUpgrade = !this.upgradeInv.getStackInSlot(0).isEmpty();
        if (this.hasUpgrade != newHasUpgrade) {
            this.hasUpgrade = newHasUpgrade;
            // Save to tool NBT
            ItemMECablePlacementTool.setUpgrade(toolStack, newHasUpgrade);
        }
    }

    public void setMode(int mode) {
        if (mode >= 0 && mode < ItemMECablePlacementTool.PlacementMode.values().length) {
            this.currentMode = mode;
            ItemMECablePlacementTool.setMode(toolStack, ItemMECablePlacementTool.PlacementMode.values()[mode]);
            if (isClientSide()) {
                sendClientAction(ACTION_SET_MODE, mode);
            }
        }
    }

    public void setCableType(int type) {
        if (type >= 0 && type < ItemMECablePlacementTool.CableType.values().length) {
            this.currentCableType = type;
            ItemMECablePlacementTool.setCableType(toolStack, ItemMECablePlacementTool.CableType.values()[type]);
            if (isClientSide()) {
                sendClientAction(ACTION_SET_CABLE_TYPE, type);
            }
        }
    }

    public void setColor(int color) {
        if (color >= 0 && color < AEColor.values().length) {
            this.currentColor = color;
            ItemMECablePlacementTool.setColor(toolStack, AEColor.values()[color]);
            if (isClientSide()) {
                sendClientAction(ACTION_SET_COLOR, color);
            }
        }
    }

    public ItemMECablePlacementTool.PlacementMode getPlacementMode() {
        return ItemMECablePlacementTool.PlacementMode.values()[currentMode];
    }

    public ItemMECablePlacementTool.CableType getCableType() {
        return ItemMECablePlacementTool.CableType.values()[currentCableType];
    }

    public AEColor getSelectedColor() {
        return AEColor.values()[currentColor];
    }

    public boolean hasUpgradeInstalled() {
        return this.hasUpgrade;
    }

    /**
     * Get the color shortcuts array.
     * @return Array of 5 color indices (-1 = empty slot)
     */
    public int[] getColorShortcuts() {
        return new int[]{colorShortcut1, colorShortcut2, colorShortcut3, colorShortcut4, colorShortcut5};
    }

    /**
     * Set a specific color shortcut slot.
     * @param slot Slot index (0-4)
     * @param colorIndex Color index or -1 for empty
     */
    public void setColorShortcut(int slot, int colorIndex) {
        switch (slot) {
            case 0 -> this.colorShortcut1 = colorIndex;
            case 1 -> this.colorShortcut2 = colorIndex;
            case 2 -> this.colorShortcut3 = colorIndex;
            case 3 -> this.colorShortcut4 = colorIndex;
            case 4 -> this.colorShortcut5 = colorIndex;
        }
        
        // Save to tool stack
        int[] shortcuts = getColorShortcuts();
        ItemMECablePlacementTool.setColorShortcuts(toolStack, shortcuts);
        
        if (isClientSide()) {
            sendClientAction(ACTION_SET_COLOR_SHORTCUT, new int[]{slot, colorIndex});
        }
    }

    /**
     * Handle setColorShortcut action from client.
     */
    private void setColorShortcutFromClient(int[] data) {
        if (data != null && data.length >= 2) {
            setColorShortcut(data[0], data[1]);
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        if (!player.level().isClientSide) {
            // Save upgrade state
            ItemStack upgradeStack = this.upgradeInv.getStackInSlot(0);
            ItemMECablePlacementTool.setUpgrade(toolStack, !upgradeStack.isEmpty());

            // If upgrade was removed, give it back to player
            // This is handled by slot extraction, no need to do anything special
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return getToolStackFromSlot(player.getInventory(), toolSlot) == this.toolStack
                && toolStack.getItem() == MEPlacementToolMod.ME_CABLE_PLACEMENT_TOOL.get();
    }

    public ItemStack getToolStack() {
        return toolStack;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        var slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index == 0
                ? moveItemStackTo(stack, 1, slots.size(), true)
                : moveItemStackTo(stack, 0, 1, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * Custom slot that only accepts Key of Spectrum items.
     */
    public static class KeyOfSpectrumSlot extends AppEngSlot {

        public KeyOfSpectrumSlot(InternalInventory inv, int invSlot, int x, int y) {
            super(inv, invSlot);
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() == MEPlacementToolMod.KEY_OF_SPECTRUM.get();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
