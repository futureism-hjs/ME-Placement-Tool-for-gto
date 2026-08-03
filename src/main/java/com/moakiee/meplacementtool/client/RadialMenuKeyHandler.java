package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.CableToolMenu;
import net.minecraft.network.chat.Component;

/**
 * Event handler for radial menu and GUI key bindings.
 * Opens the radial menu when G is pressed while holding ME Placement Tool or Multiblock Placement Tool.
 * Opens the GUI screen when G is pressed while holding ME Cable Placement Tool.
 */
public class RadialMenuKeyHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // Only respond to key press (not release or repeat)
        if (event.getAction() != InputConstants.PRESS) return;

        // Don't open if another screen is already open
        if (Minecraft.getInstance().screen != null) return;

        // Check which tool player is holding
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()) return;

        // Handle Cable Placement Tool - opens GUI directly
        if (mainHand.getItem() == MEPlacementToolMod.ME_CABLE_PLACEMENT_TOOL.get()) {
            if (ModKeyBindings.OPEN_CABLE_TOOL_GUI.matches(event.getKey(), event.getScanCode())) {
                openCableToolGui(player);
                return;
            }
        }

        // Handle other tools - open radial menu
        if (!ModKeyBindings.OPEN_RADIAL_MENU.matches(event.getKey(), event.getScanCode())) return;

        int openKey = ModKeyBindings.OPEN_RADIAL_MENU.getKey().getValue();

        if (mainHand.getItem() == MEPlacementToolMod.ME_PLACEMENT_TOOL.get()) {
            // Open single-layer radial menu for ME Placement Tool
            Minecraft.getInstance().setScreen(new RadialMenuScreen(openKey));
        } else if (mainHand.getItem() == MEPlacementToolMod.MULTIBLOCK_PLACEMENT_TOOL.get()) {
            // Open dual-layer radial menu for Multiblock Placement Tool
            Minecraft.getInstance().setScreen(new DualLayerRadialMenuScreen(openKey));
        }
    }

    /**
     * Open the Cable Tool GUI menu (same as right-click behavior).
     */
    private void openCableToolGui(Player player) {
        // Send packet to server to open the menu
        int slot = player.getInventory().selected;
        ItemStack stack = player.getMainHandItem();
        
        // Use NetworkHooks to open the screen (requires server-side action)
        // We send a custom packet to request opening the GUI
        com.moakiee.meplacementtool.network.ModNetwork.CHANNEL.sendToServer(
            new com.moakiee.meplacementtool.network.OpenCableToolGuiPacket(slot)
        );
    }
}
