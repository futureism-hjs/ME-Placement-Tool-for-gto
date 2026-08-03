package com.moakiee.meplacementtool;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import com.moakiee.meplacementtool.network.ModNetwork;
import com.moakiee.meplacementtool.network.SyncPagePacket;

public class WandScreen extends AbstractContainerScreen<WandMenu> {
    // New toolbox background texture
    private static final ResourceLocation BG = new ResourceLocation("meplacementtool", "textures/gui/toolbox.png");
    // Page button textures
    private static final ResourceLocation PREV_PAGE = new ResourceLocation("meplacementtool", "textures/gui/prev_page.png");
    private static final ResourceLocation NEXT_PAGE = new ResourceLocation("meplacementtool", "textures/gui/next_page.png");
    
    private Button prevButton;
    private Button nextButton;
    
    // Button dimensions (should match the actual texture size)
    private static final int BTN_WIDTH = 16;
    private static final int BTN_HEIGHT = 16;

    public WandScreen(WandMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // New GUI dimensions: 175x167
        this.imageWidth = 175;
        this.imageHeight = 167;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        // Inventory label position - just above player inventory at y=84
        this.inventoryLabelY = 73;
    }

    @Override
    protected void init() {
        super.init();
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        // Previous page button - positioned to the left of the 3x3 grid
        // 3x3 grid starts at x=62, place button to the left with some margin
        this.prevButton = Button.builder(Component.empty(), button -> {
            int currentPage = this.menu.getCurrentPage();
            if (currentPage > 0) {
                this.menu.setCurrentPage(currentPage - 1);
                ModNetwork.CHANNEL.sendToServer(new SyncPagePacket(currentPage - 1));
                updateButtonVisibility();
            }
        }).bounds(relX + 44, relY + 35, BTN_WIDTH, BTN_HEIGHT).build();

        // Next page button - positioned to the right of the 3x3 grid
        // 3x3 grid ends at x=113, place button to the right with some margin
        this.nextButton = Button.builder(Component.empty(), button -> {
            int currentPage = this.menu.getCurrentPage();
            if (currentPage < WandMenu.MAX_PAGES - 1) {
                this.menu.setCurrentPage(currentPage + 1);
                ModNetwork.CHANNEL.sendToServer(new SyncPagePacket(currentPage + 1));
                updateButtonVisibility();
            }
        }).bounds(relX + 117, relY + 35, BTN_WIDTH, BTN_HEIGHT).build();

        this.addRenderableWidget(prevButton);
        this.addRenderableWidget(nextButton);
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        int currentPage = this.menu.getCurrentPage();
        this.prevButton.visible = currentPage > 0;
        this.nextButton.visible = currentPage < WandMenu.MAX_PAGES - 1;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        
        // Render custom button textures (override default button rendering)
        renderPageButtons(guiGraphics);
        
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // Draw page indicator above the 3x3 grid area
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;
        int currentPage = this.menu.getCurrentPage();
        String pageText = (currentPage + 1) + "/" + WandMenu.MAX_PAGES;
        int textWidth = this.font.width(pageText);
        // Position centered above the 3x3 grid (grid center is at x=87.5)
        guiGraphics.drawString(this.font, pageText, relX + 88 - textWidth / 2, relY + 8, 0x404040, false);
    }
    
    /**
     * Render custom page button textures over the invisible buttons.
     */
    private void renderPageButtons(GuiGraphics guiGraphics) {
        if (prevButton.visible) {
            guiGraphics.blit(PREV_PAGE, prevButton.getX(), prevButton.getY(), 0, 0, BTN_WIDTH, BTN_HEIGHT, BTN_WIDTH, BTN_HEIGHT);
        }
        if (nextButton.visible) {
            guiGraphics.blit(NEXT_PAGE, nextButton.getX(), nextButton.getY(), 0, 0, BTN_WIDTH, BTN_HEIGHT, BTN_WIDTH, BTN_HEIGHT);
        }
    }

    // Ghost slot items are now rendered through standard slot rendering via GhostSlot.getItem()

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int x, int y) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BG, relX, relY, 0, 0, this.imageWidth, this.imageHeight);
    }
}
