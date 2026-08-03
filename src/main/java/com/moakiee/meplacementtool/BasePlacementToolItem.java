package com.moakiee.meplacementtool;

import java.util.List;
import java.util.function.DoubleSupplier;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.api.config.Actionable;
import appeng.api.features.IGridLinkableHandler;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.localization.Tooltips;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.util.Platform;

/**
 * Base class for placement tools that need AE network connection and power,
 * but should NOT be recognized as a WirelessTerminalItem by other mods.
 * 
 * This extends AEBasePoweredItem directly instead of WirelessTerminalItem,
 * so other mods checking instanceof WirelessTerminalItem will not match.
 */
public abstract class BasePlacementToolItem extends AEBasePoweredItem {

    protected static final Logger LOGGER = LogUtils.getLogger();

    private static final String TAG_ACCESS_POINT_POS = "accessPoint";

    public static final IGridLinkableHandler LINKABLE_HANDLER = new LinkableHandler();

    public BasePlacementToolItem(DoubleSupplier powerCapacity, Item.Properties props) {
        super(powerCapacity, props);
    }

    /**
     * Find the inventory slot containing the given item stack.
     */
    protected static int findInventorySlot(Player player, ItemStack itemStack) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == itemStack) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Open the crafting menu for an item that can be crafted.
     */
    protected static void openCraftingMenu(ServerPlayer player, ItemStack wand, AEKey whatToCraft, int amount) {
        int wandSlot = findInventorySlot(player, wand);
        if (wandSlot >= 0) {
            CraftAmountMenu.open(player, MenuLocators.forInventorySlot(wandSlot), whatToCraft, amount);
        } else if (player.getMainHandItem() == wand) {
            CraftAmountMenu.open(player, MenuLocators.forHand(player, net.minecraft.world.InteractionHand.MAIN_HAND), whatToCraft, amount);
        } else if (player.getOffhandItem() == wand) {
            CraftAmountMenu.open(player, MenuLocators.forHand(player, net.minecraft.world.InteractionHand.OFF_HAND), whatToCraft, amount);
        }
    }
    
    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag advancedTooltips) {
        super.appendHoverText(stack, level, lines, advancedTooltips);
        
        // Show linked/unlinked status like WirelessTerminalItem
        if (getLinkedPosition(stack) == null) {
            lines.add(Tooltips.of(GuiText.Unlinked, Tooltips.RED));
        } else {
            lines.add(Tooltips.of(GuiText.Linked, Tooltips.GREEN));
        }
    }
    
    /**
     * Gets the position of the wireless access point that this tool is linked to.
     */
    @Nullable
    public GlobalPos getLinkedPosition(ItemStack item) {
        var tag = item.getTag();
        if (tag != null && tag.contains(TAG_ACCESS_POINT_POS)) {
            return GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_ACCESS_POINT_POS))
                    .result()
                    .orElse(null);
        }
        return null;
    }
    
    /**
     * Gets the AE grid that this tool is linked to.
     * 
     * @param item The tool item stack
     * @param level The current level
     * @param sendMessagesTo Optional player to send error messages to
     * @return The linked grid, or null if not linked or unavailable
     */
    @Nullable
    public IGrid getLinkedGrid(ItemStack item, Level level, @Nullable Player sendMessagesTo) {
        var linked = getLinkedNetwork(item, level, sendMessagesTo);
        return linked == null ? null : linked.grid();
    }

    /** Resolve both the grid and the access point used as the action host. */
    @Nullable
    public LinkedNetwork getLinkedNetwork(ItemStack item, Level level, @Nullable Player sendMessagesTo) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        var pos = getLinkedPosition(item);
        if (pos == null) {
            if (sendMessagesTo != null) {
                sendMessagesTo.displayClientMessage(PlayerMessages.DeviceNotLinked.text(), true);
            }
            return null;
        }

        var linkedLevel = serverLevel.getServer().getLevel(pos.dimension());
        if (linkedLevel == null) {
            if (sendMessagesTo != null) {
                sendMessagesTo.displayClientMessage(PlayerMessages.LinkedNetworkNotFound.text(), true);
            }
            return null;
        }

        var be = Platform.getTickingBlockEntity(linkedLevel, pos.pos());
        if (!(be instanceof IWirelessAccessPoint accessPoint)) {
            if (sendMessagesTo != null) {
                sendMessagesTo.displayClientMessage(PlayerMessages.LinkedNetworkNotFound.text(), true);
            }
            return null;
        }

        IGrid grid = accessPoint.getGrid();
        if (grid == null) {
            if (sendMessagesTo != null) {
                sendMessagesTo.displayClientMessage(PlayerMessages.LinkedNetworkNotFound.text(), true);
            }
            return null;
        }
        return new LinkedNetwork(grid, accessPoint);
    }

    public record LinkedNetwork(IGrid grid, IWirelessAccessPoint accessPoint) {
    }
    
    /**
     * Use an amount of power, in AE units.
     *
     * @param player The player using the tool
     * @param amount Power amount in AE units (5 per MJ)
     * @param is The tool item stack
     * @return true if power was successfully consumed
     */
    public boolean usePower(Player player, double amount, ItemStack is) {
        return extractAEPower(is, amount, Actionable.MODULATE) >= amount - 0.5;
    }
    
    /**
     * Gets the power status of the item.
     *
     * @param player The player holding the tool
     * @param amt The minimum power required
     * @param is The tool item stack
     * @return true if there is enough power left
     */
    public boolean hasPower(Player player, double amt, ItemStack is) {
        return getAECurrentPower(is) >= amt;
    }
    
    /**
     * Gets the charge rate for this item when being charged in an AE charger.
     *
     * @param stack The item stack
     * @return The charge rate in AE units per tick
     */
    @Override
    public double getChargeRate(ItemStack stack) {
        return 800d; // Same as WirelessTerminalItem base rate
    }
    
    /**
     * Handler for linking/unlinking the tool to wireless access points.
     */
    private static class LinkableHandler implements IGridLinkableHandler {
        @Override
        public boolean canLink(ItemStack stack) {
            return stack.getItem() instanceof BasePlacementToolItem;
        }

        @Override
        public void link(ItemStack itemStack, GlobalPos pos) {
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos)
                    .result()
                    .ifPresent(tag -> itemStack.getOrCreateTag().put(TAG_ACCESS_POINT_POS, tag));
        }

        @Override
        public void unlink(ItemStack itemStack) {
            itemStack.removeTagKey(TAG_ACCESS_POINT_POS);
        }
    }
}
