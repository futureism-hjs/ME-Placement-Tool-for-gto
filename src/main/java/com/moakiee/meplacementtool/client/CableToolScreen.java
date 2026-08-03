package com.moakiee.meplacementtool.client;

import appeng.api.util.AEColor;
import appeng.client.Point;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import com.moakiee.meplacementtool.CableToolMenu;
import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.network.UpdateCableToolPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for the Cable Placement Tool GUI.
 * Layout based on exact pixel coordinates from GUI design.
 * 
 * Features:
 * - Color shortcut bar with marking capability (works without upgrade too)
 * - First slot always shows currently selected color (no green frame)
 * - Green frame only in expanded menu when that color is selected
 * - AE2-style upgrade panel on the right side of GUI
 */
public class CableToolScreen extends AbstractContainerScreen<CableToolMenu> {

    // Textures
    private static final ResourceLocation BACKGROUND = new ResourceLocation("meplacementtool", "textures/gui/cable_tool.png");
    private static final ResourceLocation COLOR_UNSELECTED = new ResourceLocation("meplacementtool", "textures/gui/color_unselected.png");
    private static final ResourceLocation COLOR_FRAME = new ResourceLocation("meplacementtool", "textures/gui/color_frame.png");
    private static final ResourceLocation EXPAND_BUTTON = new ResourceLocation("meplacementtool", "textures/gui/expand_button.png");
    private static final ResourceLocation COLOR_MENU = new ResourceLocation("meplacementtool", "textures/gui/color_menu.png");
    private static final ResourceLocation BUTTON_NORMAL = new ResourceLocation("meplacementtool", "textures/gui/button_normal.png");
    private static final ResourceLocation BUTTON_PRESSED = new ResourceLocation("meplacementtool", "textures/gui/button_pressed.png");
    // Custom upgrade slot textures
    private static final ResourceLocation UPGRADE_SLOT_BG = new ResourceLocation("meplacementtool", "textures/gui/upgrade_slot_bg.png");
    private static final ResourceLocation UPGRADE_SLOT_ICON = new ResourceLocation("meplacementtool", "textures/gui/upgrade_slot_icon.png");
    private static final int UPGRADE_SLOT_BG_WIDTH = 30;  // Full background texture width (0-29)
    private static final int UPGRADE_SLOT_BG_HEIGHT = 30; // Full background texture height (0-29)
    private static final int UPGRADE_SLOT_OFFSET_X = 8;   // Core slot starts at x=8 in the texture
    private static final int UPGRADE_SLOT_OFFSET_Y = 6;   // Core slot starts at y=6 in the texture

    // GUI dimensions
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 195;

    // === Color selection bar: (9,18) to (116,32) ===
    private static final int COLOR_BAR_LEFT = 9;
    private static final int COLOR_BAR_TOP = 18;
    private static final int COLOR_BAR_RIGHT = 116;
    private static final int COLOR_BAR_BOTTOM = 32;
    private static final int COLOR_CELL_SIZE = 11;
    private static final int COLOR_CELL_COUNT = 6;
    private static final int COLOR_CELL_SPACING = 4;
    private static final int EXPAND_BTN_SIZE = 12;

    // === Expanded color menu: at (7,33), 112x49px ===
    private static final int COLOR_MENU_X = 7;
    private static final int COLOR_MENU_Y = 33;
    private static final int COLOR_MENU_WIDTH = 112;
    private static final int COLOR_MENU_HEIGHT = 49;
    private static final int COLOR_MENU_COLS = 6;
    private static final int COLOR_MENU_CELL_SIZE = 11;
    private static final int COLOR_MENU_CELL_SPACING = 4;
    private static final int COLOR_MENU_PADDING = 2;

    // === Cable selection: (7,49) to (118,102) - TWO COLUMNS ===
    private static final int CABLE_AREA_LEFT = 7;
    private static final int CABLE_AREA_TOP = 49;
    private static final int CABLE_AREA_RIGHT = 118;
    private static final int CABLE_AREA_BOTTOM = 102;
    private static final int CABLE_BTN_WIDTH = 12;
    private static final int CABLE_BTN_HEIGHT = 11;
    private static final int CABLE_BTN_SPACING = 3;

    // === Mode selection: (124,49) to (168,102) ===
    private static final int MODE_AREA_LEFT = 124;
    private static final int MODE_AREA_TOP = 49;
    private static final int MODE_AREA_RIGHT = 168;
    private static final int MODE_AREA_BOTTOM = 102;
    private static final int MODE_BTN_WIDTH = 12;
    private static final int MODE_BTN_HEIGHT = 11;
    private static final int MODE_BTN_SPACING = 3;

    // AE2 Upgrade Panel - positioned to the right of GUI
    private static final int AE2_PADDING = 5;
    private final UpgradesPanel upgradesPanel;

    // UI state
    private boolean colorMenuExpanded = false;
    private int hoveredColorIndex = -1;
    private int hoveredExpandedColorIndex = -1;
    private int hoveredCableIndex = -1;
    private int hoveredModeIndex = -1;
    private boolean hoveredExpandButton = false;

    @Nullable
    private Component hintText = null;
    private int hintColor = 0xFFFFFF;

    // Color shortcut bar - stores color indices (-1 = empty slot)
    private int[] colorShortcuts = new int[]{0, -1, -1, -1, -1, -1};

    public CableToolScreen(CableToolMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelX = -9999;
        this.titleLabelY = -9999;
        this.inventoryLabelX = -9999;
        this.inventoryLabelY = -9999;

        // Create AE2 UpgradesPanel for the upgrade slot
        this.upgradesPanel = new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE));

        // Load current color for slot 0
        colorShortcuts[0] = menu.currentColor;
        
        // Load saved color shortcuts from menu (slots 1-5)
        int[] savedShortcuts = menu.getColorShortcuts();
        for (int i = 0; i < 5 && i < savedShortcuts.length; i++) {
            colorShortcuts[i + 1] = savedShortcuts[i];
        }
    }

    @Override
    protected void init() {
        super.init();
        // Position the AE2 upgrade panel to the right side of GUI
        this.upgradesPanel.setPosition(new Point(GUI_WIDTH - 2, AE2_PADDING));
        this.upgradesPanel.populateScreen(this::addRenderableWidget, getBounds(), null);
    }

    private Rect2i getBounds() {
        return new Rect2i(leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Draw main GUI background
        guiGraphics.blit(BACKGROUND, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // Reset hover states
        hoveredColorIndex = -1;
        hoveredExpandedColorIndex = -1;
        hoveredCableIndex = -1;
        hoveredModeIndex = -1;
        hoveredExpandButton = false;
        hintText = null;

        // Draw color shortcut bar
        drawColorBar(guiGraphics, x, y, mouseX, mouseY);

        // Draw cable and mode selection areas (unless color menu is covering them)
        if (!colorMenuExpanded) {
            drawCableSection(guiGraphics, x, y, mouseX, mouseY);
            drawModeSection(guiGraphics, x, y, mouseX, mouseY);
        }

        // Draw expanded color menu ON TOP
        if (colorMenuExpanded) {
            drawColorMenu(guiGraphics, x, y, mouseX, mouseY);
        }

        // Update AE2 upgrade panel (but don't draw its default background, use our custom one)
        upgradesPanel.updateBeforeRender();
        
        // Draw custom upgrade slot background and ghost icon
        drawUpgradeSlotIcon(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Draw hint text in the area to the right of color bar
        if (hintText != null) {
            int hintAreaLeft = COLOR_BAR_RIGHT + 5;
            int hintAreaRight = GUI_WIDTH - 5;
            int hintAreaWidth = hintAreaRight - hintAreaLeft;
            int hintY = 18;
            
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(hintText, hintAreaWidth);
            for (int i = 0; i < lines.size() && i < 3; i++) {
                int lineWidth = font.width(lines.get(i));
                int lineX = hintAreaLeft + (hintAreaWidth - lineWidth) / 2;
                guiGraphics.drawString(font, lines.get(i), lineX, hintY + i * 10, hintColor, false);
            }
        }
    }

    /**
     * Draw color shortcut bar at (9,18)-(116,32)
     */
    private void drawColorBar(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int areaWidth = COLOR_BAR_RIGHT - COLOR_BAR_LEFT + 1;
        int areaHeight = COLOR_BAR_BOTTOM - COLOR_BAR_TOP + 1;
        
        int totalCellsWidth = COLOR_CELL_COUNT * COLOR_CELL_SIZE + (COLOR_CELL_COUNT - 1) * COLOR_CELL_SPACING + EXPAND_BTN_SIZE + COLOR_CELL_SPACING;
        int startX = baseX + COLOR_BAR_LEFT + (areaWidth - totalCellsWidth) / 2;
        int startY = baseY + COLOR_BAR_TOP + (areaHeight - COLOR_CELL_SIZE) / 2;

        // Slot 0: shows current color only if upgrade is installed, otherwise stays Fluix
        if (menu.hasUpgrade) {
            colorShortcuts[0] = menu.currentColor;
        } else {
            colorShortcuts[0] = AEColor.TRANSPARENT.ordinal(); // Fluix = 16
        }

        for (int i = 0; i < COLOR_CELL_COUNT; i++) {
            int cellX = startX + i * (COLOR_CELL_SIZE + COLOR_CELL_SPACING);
            int cellY = startY;

            int colorIndex = colorShortcuts[i];
            boolean isHovered = isInBounds(mouseX, mouseY, cellX, cellY, COLOR_CELL_SIZE, COLOR_CELL_SIZE);

            if (isHovered) {
                hoveredColorIndex = i;
                if (colorIndex >= 0) {
                    AEColor color = AEColor.values()[colorIndex];
                    hintText = Component.translatable("meplacementtool.color." + color.name().toLowerCase());
                    hintColor = getDisplayColor(color);
                } else {
                    hintText = Component.translatable("gui.meplacementtool.empty_slot");
                    hintColor = 0x808080;
                }
            }

            if (colorIndex < 0) {
                guiGraphics.blit(COLOR_UNSELECTED, cellX, cellY, 0, 0, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE);
            } else {
                guiGraphics.blit(COLOR_FRAME, cellX, cellY, 0, 0, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE, COLOR_CELL_SIZE);
                AEColor color = AEColor.values()[colorIndex];
                int fillColor = getDisplayColor(color);
                guiGraphics.fill(cellX + 1, cellY + 1, cellX + COLOR_CELL_SIZE - 1, cellY + COLOR_CELL_SIZE - 1, 0xFF000000 | fillColor);
            }
        }

        int expandX = startX + COLOR_CELL_COUNT * (COLOR_CELL_SIZE + COLOR_CELL_SPACING);
        int expandY = startY + (COLOR_CELL_SIZE - EXPAND_BTN_SIZE) / 2;
        hoveredExpandButton = isInBounds(mouseX, mouseY, expandX, expandY, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE);

        if (hoveredExpandButton) {
            if (menu.hasUpgrade) {
                hintText = Component.translatable(colorMenuExpanded ? "gui.meplacementtool.collapse_colors" : "gui.meplacementtool.expand_colors");
                hintColor = 0xFFFFFF;
            } else {
                hintText = Component.translatable("gui.meplacementtool.need_spectrum_key");
                hintColor = 0xFF5555;
            }
        }

        guiGraphics.blit(EXPAND_BUTTON, expandX, expandY, 0, 0, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE, EXPAND_BTN_SIZE);
    }

    /**
     * Draw expanded color menu at (7,33).
     */
    private void drawColorMenu(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int menuX = baseX + COLOR_MENU_X;
        int menuY = baseY + COLOR_MENU_Y;

        guiGraphics.blit(COLOR_MENU, menuX, menuY, 0, 0, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT);

        AEColor[] colors = AEColor.values();
        
        int areaWidth = COLOR_BAR_RIGHT - COLOR_BAR_LEFT + 1;
        int totalCellsWidth = COLOR_CELL_COUNT * COLOR_CELL_SIZE + (COLOR_CELL_COUNT - 1) * COLOR_CELL_SPACING + EXPAND_BTN_SIZE + COLOR_CELL_SPACING;
        int cellStartX = baseX + COLOR_BAR_LEFT + (areaWidth - totalCellsWidth) / 2;
        int cellStartY = menuY + COLOR_MENU_PADDING;

        for (int i = 0; i < colors.length; i++) {
            int col = i % COLOR_MENU_COLS;
            int row = i / COLOR_MENU_COLS;
            int cellX = cellStartX + col * (COLOR_MENU_CELL_SIZE + COLOR_MENU_CELL_SPACING);
            int cellY = cellStartY + row * (COLOR_MENU_CELL_SIZE + COLOR_MENU_CELL_SPACING);

            boolean isHovered = isInBounds(mouseX, mouseY, cellX, cellY, COLOR_MENU_CELL_SIZE, COLOR_MENU_CELL_SIZE);
            boolean isSelected = (i == menu.currentColor);

            if (isHovered) {
                hoveredExpandedColorIndex = i;
                AEColor color = colors[i];
                hintText = Component.translatable("meplacementtool.color." + color.name().toLowerCase());
                hintColor = getDisplayColor(color);
            }

            guiGraphics.blit(COLOR_FRAME, cellX, cellY, 0, 0, COLOR_MENU_CELL_SIZE, COLOR_MENU_CELL_SIZE, COLOR_MENU_CELL_SIZE, COLOR_MENU_CELL_SIZE);
            AEColor color = colors[i];
            int fillColor = getDisplayColor(color);
            guiGraphics.fill(cellX + 1, cellY + 1, cellX + COLOR_MENU_CELL_SIZE - 1, cellY + COLOR_MENU_CELL_SIZE - 1, 0xFF000000 | fillColor);

            if (isSelected) {
                guiGraphics.renderOutline(cellX - 1, cellY - 1, COLOR_MENU_CELL_SIZE + 2, COLOR_MENU_CELL_SIZE + 2, 0xFF00FF00);
            }
        }

        Component markHint = Component.translatable("gui.meplacementtool.mark_hint", 
            ModKeyBindings.MARK_COLOR_SHORTCUT.getTranslatedKeyMessage());
        guiGraphics.drawString(font, markHint, menuX + 2, menuY + COLOR_MENU_HEIGHT + 2, 0xAAAAAA, false);
    }

    /**
     * Draw cable type selection at (7,49)-(118,102) - TWO COLUMNS
     */
    private void drawCableSection(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int areaWidth = CABLE_AREA_RIGHT - CABLE_AREA_LEFT + 1;
        
        int selectedCable = menu.currentCableType;
        ItemMECablePlacementTool.CableType[] types = ItemMECablePlacementTool.CableType.values();
        String[] cableKeys = {"glass", "covered", "smart", "dense_covered", "dense_smart"};

        int colWidth = areaWidth / 2;
        int rowHeight = CABLE_BTN_HEIGHT + CABLE_BTN_SPACING;
        
        int leftColX = baseX + CABLE_AREA_LEFT + 5;
        int rightColX = baseX + CABLE_AREA_LEFT + colWidth + 5;
        int startY = baseY + CABLE_AREA_TOP + 5;

        for (int i = 0; i < types.length; i++) {
            int col = i / 3;
            int row = i % 3;
            
            int btnX = (col == 0) ? leftColX : rightColX;
            int btnY = startY + row * rowHeight;

            boolean isHovered = isInBounds(mouseX, mouseY, btnX, btnY, CABLE_BTN_WIDTH, CABLE_BTN_HEIGHT);
            boolean isSelected = (i == selectedCable);

            if (isHovered) {
                hoveredCableIndex = i;
                hintText = Component.translatable("meplacementtool.cable." + cableKeys[i]);
                hintColor = 0x8B479B;
            }

            ResourceLocation btnTex = isSelected ? BUTTON_PRESSED : BUTTON_NORMAL;
            guiGraphics.blit(btnTex, btnX, btnY, 0, 0, CABLE_BTN_WIDTH, CABLE_BTN_HEIGHT, CABLE_BTN_WIDTH, CABLE_BTN_HEIGHT);

            ItemStack cableStack = types[i].getStack(AEColor.TRANSPARENT);
            guiGraphics.pose().pushPose();
            float scale = 0.55f;
            guiGraphics.pose().translate(btnX + (CABLE_BTN_WIDTH - 16 * scale) / 2, btnY + (CABLE_BTN_HEIGHT - 16 * scale) / 2, 0);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.renderItem(cableStack, 0, 0);
            guiGraphics.pose().popPose();

            String label = Component.translatable("meplacementtool.cable." + cableKeys[i] + ".short").getString();
            int textColor = isSelected ? 0xFFFFFF : 0x404040;
            guiGraphics.drawString(font, label, btnX + CABLE_BTN_WIDTH + 2, btnY + 1, textColor, false);
        }
    }

    /**
     * Draw placement mode selection at (124,49)-(168,102)
     */
    private void drawModeSection(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int areaHeight = MODE_AREA_BOTTOM - MODE_AREA_TOP + 1;

        int selectedMode = menu.currentMode;
        ItemMECablePlacementTool.PlacementMode[] modes = ItemMECablePlacementTool.PlacementMode.values();
        String[] modeIcons = {"L", "F", "B"};
        String[] modeKeys = {"line", "plane_fill", "plane_branching"};

        int rowHeight = MODE_BTN_HEIGHT + MODE_BTN_SPACING;
        int totalHeight = modes.length * MODE_BTN_HEIGHT + (modes.length - 1) * MODE_BTN_SPACING;
        int startX = baseX + MODE_AREA_LEFT + 5;
        int startY = baseY + MODE_AREA_TOP + (areaHeight - totalHeight) / 2;

        for (int i = 0; i < modes.length; i++) {
            int btnX = startX;
            int btnY = startY + i * rowHeight;

            boolean isHovered = isInBounds(mouseX, mouseY, btnX, btnY, MODE_BTN_WIDTH, MODE_BTN_HEIGHT);
            boolean isSelected = (i == selectedMode);

            if (isHovered) {
                hoveredModeIndex = i;
                hintText = Component.translatable("meplacementtool.mode." + modeKeys[i]);
                hintColor = 0x000000;
            }

            ResourceLocation btnTex = isSelected ? BUTTON_PRESSED : BUTTON_NORMAL;
            guiGraphics.blit(btnTex, btnX, btnY, 0, 0, MODE_BTN_WIDTH, MODE_BTN_HEIGHT, MODE_BTN_WIDTH, MODE_BTN_HEIGHT);

            guiGraphics.drawCenteredString(font, modeIcons[i], btnX + MODE_BTN_WIDTH / 2, btnY + 1, isSelected ? 0xFFFFFF : 0xE0E0E0);

            String label = Component.translatable("meplacementtool.mode." + modeKeys[i] + ".short").getString();
            int textColor = isSelected ? 0xFFFFFF : 0x404040;
            guiGraphics.drawString(font, label, btnX + MODE_BTN_WIDTH + 2, btnY + 1, textColor, false);
        }
    }

    /**
     * Draw custom upgrade slot background and icon when slot is empty.
     * Uses custom textures instead of AE2 default style.
     * Background texture: upgrade_slot_bg.png (26x26px, slot area at (8,6) to (23,21), 16x16)
     * Icon texture: upgrade_slot_icon.png (Key of Spectrum ghost icon, shown when empty)
     */
    private void drawUpgradeSlotIcon(GuiGraphics guiGraphics) {
        List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        if (!upgradeSlots.isEmpty()) {
            Slot upgradeSlot = upgradeSlots.get(0);
            // Calculate position: slot position minus the offset to align the background correctly
            int bgX = leftPos + upgradeSlot.x - UPGRADE_SLOT_OFFSET_X;
            int bgY = topPos + upgradeSlot.y - UPGRADE_SLOT_OFFSET_Y;
            
            // Draw the custom background texture
            guiGraphics.blit(UPGRADE_SLOT_BG, bgX, bgY, 0, 0, 
                UPGRADE_SLOT_BG_WIDTH, UPGRADE_SLOT_BG_HEIGHT, 
                UPGRADE_SLOT_BG_WIDTH, UPGRADE_SLOT_BG_HEIGHT);
            
            // Draw the ghost icon if slot is empty
            if (upgradeSlot.getItem().isEmpty()) {
                // Render ghost icon with transparency at the slot position
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, 0.7f);
                guiGraphics.blit(UPGRADE_SLOT_ICON, 
                    leftPos + upgradeSlot.x, topPos + upgradeSlot.y, 
                    0, 0, 16, 16, 16, 16);
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f); // Reset color
            }
        }
    }

    private int getDisplayColor(AEColor color) {
        return color == AEColor.TRANSPARENT ? 0x8B479B : color.mediumVariant;
    }

    private boolean isInBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        if (!upgradeSlots.isEmpty()) {
            Slot upgradeSlot = upgradeSlots.get(0);
            int slotX = leftPos + upgradeSlot.x;
            int slotY = topPos + upgradeSlot.y;

            if (isInBounds(mouseX, mouseY, slotX, slotY, 16, 16)) {
                if (upgradeSlot.getItem().isEmpty()) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.meplacementtool.compatible_upgrades").withStyle(ChatFormatting.GOLD));
                    tooltip.add(Component.translatable("gui.meplacementtool.upgrade_hint",
                            new ItemStack(MEPlacementToolMod.KEY_OF_SPECTRUM.get()).getHoverName()).withStyle(ChatFormatting.GRAY));
                    guiGraphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                } else {
                    guiGraphics.renderTooltip(font, upgradeSlot.getItem(), mouseX, mouseY);
                    return;
                }
            }
        }
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hoveredExpandButton) {
                if (menu.hasUpgrade) {
                    colorMenuExpanded = !colorMenuExpanded;
                    playButtonClickSound();
                }
                return true;
            }

            if (colorMenuExpanded && hoveredExpandedColorIndex >= 0) {
                menu.setColor(hoveredExpandedColorIndex);
                syncToServer();
                playButtonClickSound();
                colorMenuExpanded = false;
                return true;
            }

            if (!colorMenuExpanded && hoveredColorIndex >= 0 && colorShortcuts[hoveredColorIndex] >= 0) {
                menu.setColor(colorShortcuts[hoveredColorIndex]);
                syncToServer();
                playButtonClickSound();
                return true;
            }

            if (hoveredCableIndex >= 0) {
                menu.setCableType(hoveredCableIndex);
                syncToServer();
                playButtonClickSound();
                return true;
            }

            if (hoveredModeIndex >= 0) {
                menu.setMode(hoveredModeIndex);
                syncToServer();
                playButtonClickSound();
                return true;
            }
        }

        if (colorMenuExpanded && button == 0) {
            int menuX = leftPos + COLOR_MENU_X;
            int menuY = topPos + COLOR_MENU_Y;
            if (!isInBounds((int)mouseX, (int)mouseY, menuX, menuY, COLOR_MENU_WIDTH, COLOR_MENU_HEIGHT + 15) && !hoveredExpandButton) {
                colorMenuExpanded = false;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeyBindings.OPEN_CABLE_TOOL_GUI.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        if (ModKeyBindings.MARK_COLOR_SHORTCUT.matches(keyCode, scanCode)) {
            if (colorMenuExpanded && hoveredExpandedColorIndex >= 0) {
                markColorToShortcut(hoveredExpandedColorIndex);
                playButtonClickSound();
                return true;
            }
            if (hoveredColorIndex > 0 && colorShortcuts[hoveredColorIndex] >= 0) {
                markColorToShortcut(colorShortcuts[hoveredColorIndex]);
                playButtonClickSound();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Mark or unmark a color in the shortcut bar.
     */
    private void markColorToShortcut(int colorIndex) {
        for (int i = 1; i < colorShortcuts.length; i++) {
            if (colorShortcuts[i] == colorIndex) {
                colorShortcuts[i] = -1;
                menu.setColorShortcut(i - 1, -1); // Save to menu (0-indexed for menu)
                return;
            }
        }
        
        for (int i = 1; i < colorShortcuts.length; i++) {
            if (colorShortcuts[i] < 0) {
                colorShortcuts[i] = colorIndex;
                menu.setColorShortcut(i - 1, colorIndex); // Save to menu (0-indexed for menu)
                return;
            }
        }
        colorShortcuts[colorShortcuts.length - 1] = colorIndex;
        menu.setColorShortcut(4, colorIndex); // Save to menu
    }

    private void playButtonClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void syncToServer() {
        com.moakiee.meplacementtool.network.ModNetwork.CHANNEL.sendToServer(new UpdateCableToolPacket(
            menu.currentMode,
            menu.currentCableType,
            menu.currentColor
        ));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
