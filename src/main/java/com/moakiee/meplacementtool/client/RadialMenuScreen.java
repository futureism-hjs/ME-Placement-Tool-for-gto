package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEFluidKey;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.WandMenu;
import com.moakiee.meplacementtool.WandNbt;
import com.moakiee.meplacementtool.network.ModNetwork;
import com.moakiee.meplacementtool.network.UpdateWandConfigPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Radial menu for ME Placement Tool item selection.
 * Inspired by Ars Nouveau's radial menu implementation.
 */
public class RadialMenuScreen extends Screen {
    private static final float PRECISION = 5.0f;
    private static final int MAX_SLOTS = 18;
    private static final float OPEN_ANIMATION_LENGTH = 0.25f;

    private final int openKey;
    private float totalTime;
    private float prevTick;
    private float extraTick;
    private int selectedItem = -1;
    private boolean closing = false;
    private int currentSelectedSlot = -1; // Currently selected slot from config

    // Slot data
    private final List<SlotData> slots = new ArrayList<>();
    private final ItemStack wandStack;

    public record SlotData(int index, ItemStack displayStack, String name, boolean isFluid) {}

    public RadialMenuScreen(int openKey) {
        super(Component.literal(""));
        this.openKey = openKey;
        this.minecraft = Minecraft.getInstance();
        this.wandStack = minecraft.player.getMainHandItem();
        loadSlots();
        loadCurrentSelection();
    }

    private void loadCurrentSelection() {
        if (wandStack.isEmpty()) return;
        CompoundTag cfg = WandNbt.getConfig(wandStack);
        currentSelectedSlot = WandNbt.getSelectedSlot(cfg);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private void loadSlots() {
        slots.clear();
        if (wandStack.isEmpty()) return;

        CompoundTag cfg = WandNbt.getConfig(wandStack);
        if (cfg == null) return;

        ItemStackHandler handler = WandNbt.readInventory(cfg);

        CompoundTag fluids = cfg.contains("fluids") ? cfg.getCompound("fluids") : new CompoundTag();

        // Use actual slot count from handler (may be 9 for old data or 18 for new data)
        int slotCount = handler.getSlots();
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            String fluidId = fluids.getString(Integer.toString(i));

            if (!stack.isEmpty()) {
                // Check if it's an AE wrapped fluid
                try {
                    var gs = GenericStack.unwrapItemStack(stack);
                    if (gs != null) {
                        String name = gs.what().getDisplayName().getString();
                        slots.add(new SlotData(i, stack, name, AEFluidKey.is(gs.what())));
                        continue;
                    }
                } catch (Exception ignored) {}

                slots.add(new SlotData(i, stack, stack.getHoverName().getString(), false));
            } else if (fluidId != null && !fluidId.isEmpty()) {
                try {
                    var rl = new net.minecraft.resources.ResourceLocation(fluidId);
                    var fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(rl);
                    if (fluid != null) {
                        var aeFluidKey = AEFluidKey.of(fluid);
                        ItemStack wrapped = GenericStack.wrapInItemStack(aeFluidKey, 1);
                        String name = aeFluidKey.getDisplayName().getString();
                        slots.add(new SlotData(i, wrapped, name, true));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void tick() {
        if (totalTime < OPEN_ANIMATION_LENGTH) {
            extraTick++;
        }

        // Check if the open key is still held
        boolean keyIsDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), openKey);
        if (!keyIsDown) {
            // Key released - close menu (selection already made via click)
            minecraft.player.closeContainer();
        }
    }

    private void selectSlot(int slotIndex) {
        if (wandStack.isEmpty()) return;

        currentSelectedSlot = slotIndex; // Update local state for highlight
        CompoundTag data = wandStack.getOrCreateTag();
        CompoundTag cfg = data.contains(WandMenu.TAG_KEY) ? data.getCompound(WandMenu.TAG_KEY).copy() : new CompoundTag();
        cfg.putInt("SelectedSlot", slotIndex);
        data.put(WandMenu.TAG_KEY, cfg);

        // Send to server
        ModNetwork.CHANNEL.sendToServer(new UpdateWandConfigPacket(cfg));

        // Show overlay
        String name = Component.translatable("gui.meplacementtool.empty_slot").getString();
        for (SlotData slot : slots) {
            if (slot.index == slotIndex) {
                name = slot.name;
                break;
            }
        }
        MEPlacementToolMod.ClientForgeEvents.showSelectedOverlay(name);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Don't call super.render() - it draws a background that covers our radial menu
        // Instead just render our content directly
        
        if (slots.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("message.meplacementtool.no_configured_item"), width / 2, height / 2, 0xFFFFFF);
            return;
        }

        PoseStack ms = graphics.pose();
        float openAnimation = closing ? 1.0f - totalTime / OPEN_ANIMATION_LENGTH : totalTime / OPEN_ANIMATION_LENGTH;
        float currTick = minecraft.getFrameTime();
        totalTime += (currTick + extraTick - prevTick) / 20f;
        extraTick = 0;
        prevTick = currTick;

        float animProgress = Mth.clamp(openAnimation, 0, 1);
        animProgress = (float) (1 - Math.pow(1 - animProgress, 3));
        
        int numberOfSlices = Math.min(MAX_SLOTS, slots.size());
        // Dynamic radius based on number of slices
        float baseRadius = Math.max(45, 30 + numberOfSlices * 3);
        float radiusIn = Math.max(0.1f, baseRadius * animProgress);
        float radiusOut = radiusIn + Math.max(40, 25 + numberOfSlices * 2) * animProgress;
        float itemRadius = (radiusIn + radiusOut) * 0.5f;

        int centerX = width / 2;
        int centerY = height / 2;

        double mouseAngle = Math.toDegrees(Math.atan2(mouseY - centerY, mouseX - centerX));
        double mouseDistance = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));
        float slot0 = (((0 - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
        if (mouseAngle < slot0) {
            mouseAngle += 360;
        }

        ms.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Determine selected item
        // Allow selection based on angle only - works inside the ring and extends outward infinitely
        if (!closing) {
            selectedItem = -1;
            for (int i = 0; i < numberOfSlices; i++) {
                float sliceBorderLeft = (((i - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
                float sliceBorderRight = (((i + 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
                // Only check angle - no distance limit, selection works everywhere
                if (mouseAngle >= sliceBorderLeft && mouseAngle < sliceBorderRight) {
                    selectedItem = i;
                    break;
                }
            }
        }

        // Draw gray background ring as full background
        drawSlice(buffer, centerX, centerY, 9, radiusIn, radiusOut, 0, 360, 80, 80, 80, 120);

        // Only draw highlights for hovered and currently selected slices (not all slices)
        int mousedOverSlot = -1;
        for (int i = 0; i < numberOfSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            
            // Calculate adjusted index for checking current selection
            int adjusted = ((i + (numberOfSlices / 2 + 1)) % numberOfSlices) - 1;
            adjusted = adjusted == -1 ? numberOfSlices - 1 : adjusted;
            boolean isCurrentlySelected = adjusted < slots.size() && slots.get(adjusted).index == currentSelectedSlot;
            
            if (selectedItem == i) {
                // Hovered slice - blue highlight
                drawSlice(buffer, centerX, centerY, 10, radiusIn, radiusOut, sliceBorderLeft, sliceBorderRight, 63, 161, 191, 150);
                mousedOverSlot = selectedItem;
            } else if (isCurrentlySelected) {
                // Currently selected slot - green highlight
                drawSlice(buffer, centerX, centerY, 10, radiusIn, radiusOut, sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
            }
            // Non-selected, non-hovered slices: no additional color, gray background shows through
        }

        BufferUploader.drawWithShader(buffer.end());
        
        // Draw divider lines between slices
        buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < numberOfSlices; i++) {
            float angle = (float) Math.toRadians((((i - 0.5f) / (float) numberOfSlices) + 0.25f) * 360);
            float x1 = centerX + radiusIn * (float) Math.cos(angle);
            float y1 = centerY + radiusIn * (float) Math.sin(angle);
            float x2 = centerX + radiusOut * (float) Math.cos(angle);
            float y2 = centerY + radiusOut * (float) Math.sin(angle);
            buffer.vertex(x1, y1, 11).color(200, 200, 200, 100).endVertex();
            buffer.vertex(x2, y2, 11).color(200, 200, 200, 100).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // Draw hovered item name
        if (mousedOverSlot != -1) {
            int adjusted = ((mousedOverSlot + (numberOfSlices / 2 + 1)) % numberOfSlices) - 1;
            adjusted = adjusted == -1 ? numberOfSlices - 1 : adjusted;
            if (adjusted >= 0 && adjusted < slots.size()) {
                graphics.drawCenteredString(font, slots.get(adjusted).name, centerX, (height - font.lineHeight) / 2, 0xFFFFFF);
            }
        }

        ms.popPose();

        // Draw item icons
        for (int i = 0; i < numberOfSlices; i++) {
            float angle = ((i / (float) numberOfSlices) - 0.25f) * 2 * (float) Math.PI;
            if (numberOfSlices % 2 != 0) {
                angle += Math.PI / numberOfSlices;
            }
            float posX = centerX - 8 + itemRadius * (float) Math.cos(angle);
            float posY = centerY - 8 + itemRadius * (float) Math.sin(angle);
            RenderSystem.disableDepthTest();

            SlotData slot = slots.get(i);
            if (!slot.displayStack.isEmpty()) {
                graphics.renderItem(slot.displayStack, (int) posX, (int) posY);
            }
        }

        // Adjust selected item for display mapping
        if (mousedOverSlot != -1) {
            int adjusted = ((mousedOverSlot + (numberOfSlices / 2 + 1)) % numberOfSlices) - 1;
            adjusted = adjusted == -1 ? numberOfSlices - 1 : adjusted;
            selectedItem = adjusted;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Left-click selects without closing, G release closes menu
        if (selectedItem >= 0 && selectedItem < slots.size()) {
            selectSlot(slots.get(selectedItem).index);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void drawSlice(BufferBuilder buffer, float x, float y, float z, float radiusIn, float radiusOut, 
                           float startAngle, float endAngle, int r, int g, int b, int a) {
        float angle = endAngle - startAngle;
        int sections = Math.max(1, Mth.ceil(angle / PRECISION));

        for (int i = 0; i < sections; i++) {
            float angle1 = (float) Math.toRadians(startAngle + (i / (float) sections) * angle);
            float angle2 = (float) Math.toRadians(startAngle + ((i + 1) / (float) sections) * angle);

            float x1In = x + radiusIn * (float) Math.cos(angle1);
            float y1In = y + radiusIn * (float) Math.sin(angle1);
            float x1Out = x + radiusOut * (float) Math.cos(angle1);
            float y1Out = y + radiusOut * (float) Math.sin(angle1);
            float x2In = x + radiusIn * (float) Math.cos(angle2);
            float y2In = y + radiusIn * (float) Math.sin(angle2);
            float x2Out = x + radiusOut * (float) Math.cos(angle2);
            float y2Out = y + radiusOut * (float) Math.sin(angle2);

            buffer.vertex(x1In, y1In, z).color(r, g, b, a).endVertex();
            buffer.vertex(x1Out, y1Out, z).color(r, g, b, a).endVertex();
            buffer.vertex(x2Out, y2Out, z).color(r, g, b, a).endVertex();
            buffer.vertex(x2In, y2In, z).color(r, g, b, a).endVertex();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
