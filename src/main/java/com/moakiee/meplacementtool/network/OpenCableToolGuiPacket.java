package com.moakiee.meplacementtool.network;

import com.moakiee.meplacementtool.CableToolMenu;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/**
 * Packet to request opening the Cable Tool GUI from client side.
 */
public class OpenCableToolGuiPacket {
    private final int slot;

    public OpenCableToolGuiPacket(int slot) {
        this.slot = slot;
    }

    public static void encode(OpenCableToolGuiPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.slot);
    }

    public static OpenCableToolGuiPacket decode(FriendlyByteBuf buf) {
        return new OpenCableToolGuiPacket(buf.readInt());
    }

    public static void handle(OpenCableToolGuiPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (msg.slot < 0 || msg.slot >= player.getInventory().getContainerSize()
                    || (msg.slot != player.getInventory().selected
                            && msg.slot != net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND)) return;

            ItemStack stack = player.getInventory().getItem(msg.slot);
            if (stack.getItem() != MEPlacementToolMod.ME_CABLE_PLACEMENT_TOOL.get()) {
                return;
            }

            // Open the Cable Tool GUI
            NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.meplacementtool.cable_tool");
                }

                @Override
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory playerInventory, net.minecraft.world.entity.player.Player p) {
                    return new CableToolMenu(containerId, playerInventory, stack, msg.slot);
                }
            }, buf -> {
                buf.writeInt(msg.slot);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
