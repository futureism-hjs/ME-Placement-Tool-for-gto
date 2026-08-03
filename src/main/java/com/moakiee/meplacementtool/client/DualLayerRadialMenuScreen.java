package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.meplacementtool.ItemMultiblockPlacementTool.DirectionMode;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.WandMenu;
import com.moakiee.meplacementtool.WandNbt;
import com.moakiee.meplacementtool.network.ModNetwork;
import com.moakiee.meplacementtool.network.UpdateDirectionModePacket;
import com.moakiee.meplacementtool.network.UpdatePlacementCountPacket;
import com.moakiee.meplacementtool.network.UpdateWandConfigPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Triple-layer radial menu for Multiblock Placement Tool.
 * Innermost ring: placement direction mode.
 * Middle ring: placement count.
 * Outer ring: item selection.
 */
public class DualLayerRadialMenuScreen extends Screen {
    private static final float PRECISION = 5.0f;
    private static final float OPEN_ANIMATION_LENGTH = 0.25f;

    private static final int[] COUNT_OPTIONS = {1, 8, 64, 256, 1024};
    private static final DirectionMode[] DIRECTION_OPTIONS = DirectionMode.values();

    private final int openKey;
    private float totalTime;
    private float prevTick;
    private float extraTick;
    private int selectedItem = -1;
    private int selectedCount = -1;
    private int selectedDirection = -1;
    private boolean closing = false;

    private DirectionMode currentDirection = DirectionMode.AUTO;
    private int currentPlacementCount = 1;
    private int currentSelectedSlot = -1;

    private final List<SlotData> slots = new ArrayList<>();
    private final ItemStack wandStack;

    // 0 = direction, 1 = count, 2 = item
    private int selectionLayer = -1;

    public record SlotData(int index, ItemStack displayStack, String name, boolean isFluid) {}

    public DualLayerRadialMenuScreen(int openKey) {
        super(Component.literal(""));
        this.openKey = openKey;
        this.minecraft = Minecraft.getInstance();
        this.wandStack = minecraft.player.getMainHandItem();
        loadSlots();
        loadCurrentConfig();
    }

    private void loadCurrentConfig() {
        if (wandStack.isEmpty()) {
            return;
        }

        CompoundTag data = wandStack.getOrCreateTag();
        if (data.contains("placement_count")) {
            currentPlacementCount = data.getInt("placement_count");
        }

        CompoundTag cfg = WandNbt.getConfig(wandStack);
        if (cfg != null) {
            currentSelectedSlot = WandNbt.getSelectedSlot(cfg);
            if (cfg.contains("DirectionMode")) {
                currentDirection = DirectionMode.fromId(cfg.getInt("DirectionMode"));
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private void loadSlots() {
        slots.clear();
        if (wandStack.isEmpty()) {
            return;
        }

        CompoundTag cfg = WandNbt.getConfig(wandStack);
        if (cfg == null) {
            return;
        }

        ItemStackHandler handler = WandNbt.readInventory(cfg);

        CompoundTag fluids = cfg.contains("fluids") ? cfg.getCompound("fluids") : new CompoundTag();

        int slotCount = handler.getSlots();
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            String fluidId = fluids.getString(Integer.toString(i));

            if (!stack.isEmpty()) {
                try {
                    var gs = GenericStack.unwrapItemStack(stack);
                    if (gs != null) {
                        String name = gs.what().getDisplayName().getString();
                        slots.add(new SlotData(i, stack, name, AEFluidKey.is(gs.what())));
                        continue;
                    }
                } catch (Exception ignored) {
                }

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
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void tick() {
        if (totalTime < OPEN_ANIMATION_LENGTH) {
            extraTick++;
        }

        boolean keyIsDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), openKey);
        if (!keyIsDown) {
            minecraft.player.closeContainer();
        }
    }

    private void selectSlot(int slotIndex) {
        if (wandStack.isEmpty()) {
            return;
        }

        currentSelectedSlot = slotIndex;
        CompoundTag data = wandStack.getOrCreateTag();
        CompoundTag cfg = data.contains(WandMenu.TAG_KEY) ? data.getCompound(WandMenu.TAG_KEY).copy() : new CompoundTag();
        cfg.putInt("SelectedSlot", slotIndex);
        data.put(WandMenu.TAG_KEY, cfg);

        ModNetwork.CHANNEL.sendToServer(new UpdateWandConfigPacket(cfg));

        String name = Component.translatable("gui.meplacementtool.empty_slot").getString();
        for (SlotData slot : slots) {
            if (slot.index == slotIndex) {
                name = slot.name;
                break;
            }
        }
        MEPlacementToolMod.ClientForgeEvents.showSelectedOverlay(name);
    }

    private void selectCount(int count) {
        if (wandStack.isEmpty()) {
            return;
        }

        currentPlacementCount = count;
        wandStack.getOrCreateTag().putInt("placement_count", count);
        ModNetwork.CHANNEL.sendToServer(new UpdatePlacementCountPacket(count));
        MEPlacementToolMod.ClientForgeEvents.showCountOverlay(
                Component.translatable("meplacementtool.hud.placement_count", count).getString());
    }

    private void selectDirection(DirectionMode mode) {
        if (wandStack.isEmpty()) {
            return;
        }

        currentDirection = mode;

        CompoundTag data = wandStack.getOrCreateTag();
        CompoundTag cfg = data.contains(WandMenu.TAG_KEY) ? data.getCompound(WandMenu.TAG_KEY).copy() : new CompoundTag();
        cfg.putInt("DirectionMode", mode.ordinal());
        data.put(WandMenu.TAG_KEY, cfg);

        ModNetwork.CHANNEL.sendToServer(new UpdateDirectionModePacket(mode.ordinal()));

        String name = Component.translatable(mode.translationKey()).getString();
        MEPlacementToolMod.ClientForgeEvents.showCountOverlay(
                Component.translatable("meplacementtool.hud.direction", name).getString());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
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

        int numberOfDirectionSlices = DIRECTION_OPTIONS.length;
        int numberOfCountSlices = COUNT_OPTIONS.length;
        int numberOfItemSlices = Math.max(1, slots.size());

        float dirRadiusMin = Math.max(0.1f, 12 * animProgress);
        float dirRadiusMax = Math.max(0.1f, 32 * animProgress);
        float countRadiusMin = dirRadiusMax + 5 * animProgress;
        float countRadiusMax = countRadiusMin + 18 * animProgress;
        float outerRadiusMin = countRadiusMax + 7 * animProgress;
        float outerRadiusMax = outerRadiusMin + Math.max(33, 23 + numberOfItemSlices * 1.3f) * animProgress;

        float dirItemRadius = (dirRadiusMin + dirRadiusMax) * 0.5f;
        float countItemRadius = (countRadiusMin + countRadiusMax) * 0.5f;
        float outerItemRadius = (outerRadiusMin + outerRadiusMax) * 0.5f;

        int centerX = width / 2;
        int centerY = height / 2;

        double mouseAngle = Math.toDegrees(Math.atan2(mouseY - centerY, mouseX - centerX));
        double mouseDistance = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));

        float dirSlot0 = (((0 - 0.5f) / (float) numberOfDirectionSlices) + 0.25f) * 360;
        float countSlot0 = (((0 - 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
        float itemSlot0 = (((0 - 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
        double dirMouseAngle = mouseAngle;
        double countMouseAngle = mouseAngle;
        double itemMouseAngle = mouseAngle;
        if (dirMouseAngle < dirSlot0) {
            dirMouseAngle += 360;
        }
        if (countMouseAngle < countSlot0) {
            countMouseAngle += 360;
        }
        if (itemMouseAngle < itemSlot0) {
            itemMouseAngle += 360;
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

        if (!closing) {
            selectionLayer = -1;
            selectedDirection = -1;
            selectedCount = -1;
            selectedItem = -1;

            if (mouseDistance >= dirRadiusMin && mouseDistance < dirRadiusMax) {
                selectionLayer = 0;
                for (int i = 0; i < numberOfDirectionSlices; i++) {
                    float sliceBorderLeft = (((i - 0.5f) / (float) numberOfDirectionSlices) + 0.25f) * 360;
                    float sliceBorderRight = (((i + 0.5f) / (float) numberOfDirectionSlices) + 0.25f) * 360;
                    if (dirMouseAngle >= sliceBorderLeft && dirMouseAngle < sliceBorderRight) {
                        selectedDirection = i;
                        break;
                    }
                }
            } else if (mouseDistance >= countRadiusMin && mouseDistance < countRadiusMax) {
                selectionLayer = 1;
                for (int i = 0; i < numberOfCountSlices; i++) {
                    float sliceBorderLeft = (((i - 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
                    float sliceBorderRight = (((i + 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
                    if (countMouseAngle >= sliceBorderLeft && countMouseAngle < sliceBorderRight) {
                        selectedCount = i;
                        break;
                    }
                }
            } else if (mouseDistance >= outerRadiusMin && mouseDistance < outerRadiusMax && !slots.isEmpty()) {
                selectionLayer = 2;
                for (int i = 0; i < numberOfItemSlices; i++) {
                    float sliceBorderLeft = (((i - 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
                    float sliceBorderRight = (((i + 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
                    if (itemMouseAngle >= sliceBorderLeft && itemMouseAngle < sliceBorderRight) {
                        selectedItem = i;
                        break;
                    }
                }
            }
        }

        drawSlice(buffer, centerX, centerY, 9, dirRadiusMin, dirRadiusMax, 0, 360, 80, 80, 80, 120);
        drawSlice(buffer, centerX, centerY, 9, countRadiusMin, countRadiusMax, 0, 360, 80, 80, 80, 120);
        if (!slots.isEmpty()) {
            drawSlice(buffer, centerX, centerY, 9, outerRadiusMin, outerRadiusMax, 0, 360, 80, 80, 80, 120);
        }

        for (int i = 0; i < numberOfDirectionSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfDirectionSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfDirectionSlices) + 0.25f) * 360;

            int adjusted = adjustIndex(i, numberOfDirectionSlices);
            boolean isCurrentlySelected = adjusted >= 0 && adjusted < DIRECTION_OPTIONS.length
                    && DIRECTION_OPTIONS[adjusted] == currentDirection;

            if (selectionLayer == 0 && selectedDirection == i) {
                drawSlice(buffer, centerX, centerY, 10, dirRadiusMin, dirRadiusMax, sliceBorderLeft, sliceBorderRight, 191, 113, 63, 150);
            } else if (isCurrentlySelected) {
                drawSlice(buffer, centerX, centerY, 10, dirRadiusMin, dirRadiusMax, sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
            }
        }

        for (int i = 0; i < numberOfCountSlices; i++) {
            float sliceBorderLeft = (((i - 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;
            float sliceBorderRight = (((i + 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360;

            int adjusted = adjustIndex(i, numberOfCountSlices);
            boolean isCurrentlySelected = adjusted >= 0 && adjusted < COUNT_OPTIONS.length
                    && COUNT_OPTIONS[adjusted] == currentPlacementCount;

            if (selectionLayer == 1 && selectedCount == i) {
                drawSlice(buffer, centerX, centerY, 10, countRadiusMin, countRadiusMax, sliceBorderLeft, sliceBorderRight, 191, 161, 63, 150);
            } else if (isCurrentlySelected) {
                drawSlice(buffer, centerX, centerY, 10, countRadiusMin, countRadiusMax, sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
            }
        }

        if (!slots.isEmpty()) {
            for (int i = 0; i < numberOfItemSlices; i++) {
                float sliceBorderLeft = (((i - 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;
                float sliceBorderRight = (((i + 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360;

                int adjusted = adjustIndex(i, numberOfItemSlices);
                boolean isCurrentlySelected = adjusted < slots.size() && slots.get(adjusted).index == currentSelectedSlot;

                if (selectionLayer == 2 && selectedItem == i) {
                    drawSlice(buffer, centerX, centerY, 10, outerRadiusMin, outerRadiusMax, sliceBorderLeft, sliceBorderRight, 63, 161, 191, 150);
                } else if (isCurrentlySelected) {
                    drawSlice(buffer, centerX, centerY, 10, outerRadiusMin, outerRadiusMax, sliceBorderLeft, sliceBorderRight, 80, 180, 80, 130);
                }
            }
        }

        BufferUploader.drawWithShader(buffer.end());

        buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < numberOfDirectionSlices; i++) {
            float angle = (float) Math.toRadians((((i - 0.5f) / (float) numberOfDirectionSlices) + 0.25f) * 360);
            float x1 = centerX + dirRadiusMin * (float) Math.cos(angle);
            float y1 = centerY + dirRadiusMin * (float) Math.sin(angle);
            float x2 = centerX + dirRadiusMax * (float) Math.cos(angle);
            float y2 = centerY + dirRadiusMax * (float) Math.sin(angle);
            buffer.vertex(x1, y1, 11).color(200, 200, 200, 100).endVertex();
            buffer.vertex(x2, y2, 11).color(200, 200, 200, 100).endVertex();
        }

        for (int i = 0; i < numberOfCountSlices; i++) {
            float angle = (float) Math.toRadians((((i - 0.5f) / (float) numberOfCountSlices) + 0.25f) * 360);
            float x1 = centerX + countRadiusMin * (float) Math.cos(angle);
            float y1 = centerY + countRadiusMin * (float) Math.sin(angle);
            float x2 = centerX + countRadiusMax * (float) Math.cos(angle);
            float y2 = centerY + countRadiusMax * (float) Math.sin(angle);
            buffer.vertex(x1, y1, 11).color(200, 200, 200, 100).endVertex();
            buffer.vertex(x2, y2, 11).color(200, 200, 200, 100).endVertex();
        }

        if (!slots.isEmpty()) {
            for (int i = 0; i < numberOfItemSlices; i++) {
                float angle = (float) Math.toRadians((((i - 0.5f) / (float) numberOfItemSlices) + 0.25f) * 360);
                float x1 = centerX + outerRadiusMin * (float) Math.cos(angle);
                float y1 = centerY + outerRadiusMin * (float) Math.sin(angle);
                float x2 = centerX + outerRadiusMax * (float) Math.cos(angle);
                float y2 = centerY + outerRadiusMax * (float) Math.sin(angle);
                buffer.vertex(x1, y1, 11).color(200, 200, 200, 100).endVertex();
                buffer.vertex(x2, y2, 11).color(200, 200, 200, 100).endVertex();
            }
        }

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        int hoverY = (int) (centerY - outerRadiusMax - font.lineHeight - 4);
        if (selectionLayer == 0 && selectedDirection >= 0) {
            int adjusted = adjustIndex(selectedDirection, numberOfDirectionSlices);
            if (adjusted >= 0 && adjusted < DIRECTION_OPTIONS.length) {
                String text = Component.translatable(DIRECTION_OPTIONS[adjusted].translationKey()).getString();
                graphics.drawCenteredString(font, text, centerX, hoverY, 0xFFCC88);
            }
        } else if (selectionLayer == 1 && selectedCount >= 0) {
            int adjusted = adjustIndex(selectedCount, numberOfCountSlices);
            if (adjusted >= 0 && adjusted < COUNT_OPTIONS.length) {
                String countText = String.valueOf(COUNT_OPTIONS[adjusted]);
                graphics.drawCenteredString(font, countText, centerX, hoverY, 0xFFFF00);
            }
        } else if (selectionLayer == 2 && selectedItem >= 0) {
            int adjusted = adjustIndex(selectedItem, numberOfItemSlices);
            if (adjusted >= 0 && adjusted < slots.size()) {
                graphics.drawCenteredString(font, slots.get(adjusted).name, centerX, hoverY, 0xFFFFFF);
            }
        }

        ms.popPose();

        for (int i = 0; i < numberOfDirectionSlices; i++) {
            float angle = ((i / (float) numberOfDirectionSlices) - 0.25f) * 2 * (float) Math.PI;
            if (numberOfDirectionSlices % 2 != 0) {
                angle += Math.PI / numberOfDirectionSlices;
            }
            float posX = centerX + dirItemRadius * (float) Math.cos(angle);
            float posY = centerY + dirItemRadius * (float) Math.sin(angle);
            String label = Component.translatable(DIRECTION_OPTIONS[i].translationKey() + ".short").getString();
            graphics.drawCenteredString(font, label, (int) posX, (int) posY - font.lineHeight / 2, 0xFFFFFF);
        }

        for (int i = 0; i < numberOfCountSlices; i++) {
            float angle = ((i / (float) numberOfCountSlices) - 0.25f) * 2 * (float) Math.PI;
            if (numberOfCountSlices % 2 != 0) {
                angle += Math.PI / numberOfCountSlices;
            }
            float posX = centerX + countItemRadius * (float) Math.cos(angle);
            float posY = centerY + countItemRadius * (float) Math.sin(angle);
            String countText = String.valueOf(COUNT_OPTIONS[i]);
            graphics.drawCenteredString(font, countText, (int) posX, (int) posY - font.lineHeight / 2, 0xFFFFFF);
        }

        if (!slots.isEmpty()) {
            for (int i = 0; i < numberOfItemSlices; i++) {
                float angle = ((i / (float) numberOfItemSlices) - 0.25f) * 2 * (float) Math.PI;
                if (numberOfItemSlices % 2 != 0) {
                    angle += Math.PI / numberOfItemSlices;
                }
                float posX = centerX - 8 + outerItemRadius * (float) Math.cos(angle);
                float posY = centerY - 8 + outerItemRadius * (float) Math.sin(angle);
                RenderSystem.disableDepthTest();

                SlotData slot = slots.get(i);
                if (!slot.displayStack.isEmpty()) {
                    graphics.renderItem(slot.displayStack, (int) posX, (int) posY);
                }
            }
        }

        if (selectionLayer == 0 && selectedDirection >= 0) {
            selectedDirection = adjustIndex(selectedDirection, numberOfDirectionSlices);
        } else if (selectionLayer == 1 && selectedCount >= 0) {
            selectedCount = adjustIndex(selectedCount, numberOfCountSlices);
        } else if (selectionLayer == 2 && selectedItem >= 0) {
            selectedItem = adjustIndex(selectedItem, numberOfItemSlices);
        }
    }

    private static int adjustIndex(int sliceIndex, int totalSlices) {
        int adjusted = ((sliceIndex + (totalSlices / 2 + 1)) % totalSlices);
        adjusted = adjusted == 0 ? totalSlices - 1 : adjusted - 1;
        return adjusted;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (selectionLayer == 0 && selectedDirection >= 0 && selectedDirection < DIRECTION_OPTIONS.length) {
                selectDirection(DIRECTION_OPTIONS[selectedDirection]);
            } else if (selectionLayer == 1 && selectedCount >= 0 && selectedCount < COUNT_OPTIONS.length) {
                selectCount(COUNT_OPTIONS[selectedCount]);
            } else if (selectionLayer == 2 && selectedItem >= 0 && selectedItem < slots.size()) {
                selectSlot(slots.get(selectedItem).index);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
