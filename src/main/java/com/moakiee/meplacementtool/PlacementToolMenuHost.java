package com.moakiee.meplacementtool;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.ISubMenuHost;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.menu.ISubMenu;
import appeng.util.ConfigManager;

/**
 * Menu host for placement tools that supports autocrafting.
 * This is a custom implementation that does not depend on WirelessTerminalItem,
 * so other mods won't recognize this as a wireless terminal.
 */
public class PlacementToolMenuHost extends ItemMenuHost implements IPortableTerminal, IActionHost, ISubMenuHost {

    private final BasePlacementToolItem tool;
    private final BiConsumer<Player, ISubMenu> returnToMainMenu;
    private final IGrid targetGrid;
    private IStorageService sg;
    @Nullable
    private IWirelessAccessPoint myWap;

    public PlacementToolMenuHost(Player player, @Nullable Integer slot, ItemStack itemStack,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, slot, itemStack);
        if (!(itemStack.getItem() instanceof BasePlacementToolItem placementTool)) {
            throw new IllegalArgumentException("Can only use this class with subclasses of BasePlacementToolItem");
        }
        this.tool = placementTool;
        this.returnToMainMenu = returnToMainMenu;

        this.targetGrid = placementTool.getLinkedGrid(itemStack, player.level(), player);
        if (this.targetGrid != null) {
            this.sg = this.targetGrid.getStorageService();
        }
    }

    @Override
    public MEStorage getInventory() {
        return this.sg != null ? this.sg.getInventory() : null;
    }

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier usePowerMultiplier) {
        if (this.tool != null) {
            final double extracted = Math.min(amt, this.tool.getAECurrentPower(getItemStack()));

            if (mode == Actionable.SIMULATE) {
                return extracted;
            }

            return this.tool.usePower(getPlayer(), extracted, getItemStack()) ? extracted : 0;
        }
        return 0.0;
    }

    @Override
    public IConfigManager getConfigManager() {
        // Create a simple config manager for the placement tool
        var configManager = new ConfigManager((manager, settingName) -> {
            manager.writeToNBT(getItemStack().getOrCreateTag());
        });
        configManager.readFromNBT(getItemStack().getOrCreateTag().copy());
        return configManager;
    }

    @Override
    public IGridNode getActionableNode() {
        this.rangeCheck();
        if (this.myWap != null) {
            return this.myWap.getActionableNode();
        }
        return null;
    }

    public boolean rangeCheck() {
        if (this.targetGrid != null) {
            // Find any active WAP, no range or dimension limit
            for (var wap : this.targetGrid.getMachines(WirelessAccessPointBlockEntity.class)) {
                if (wap.isActive()) {
                    this.myWap = wap;
                    return true;
                }
            }
            this.myWap = null;
        }

        return false;
    }

    @Override
    public boolean onBroadcastChanges(AbstractContainerMenu menu) {
        // For placement tools, we do not enforce wireless range checks or continuous
        // power drain. The tool only consumes power when actually placing blocks.
        // This allows crafting menus to stay open regardless of WAP range.
        return ensureItemStillInSlot();
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        returnToMainMenu.accept(player, subMenu);
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return getItemStack();
    }

    public String getCloseHotkey() {
        return null; // No hotkey for placement tools
    }
}
