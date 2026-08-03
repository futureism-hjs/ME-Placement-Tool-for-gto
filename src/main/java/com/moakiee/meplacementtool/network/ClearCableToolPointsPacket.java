package com.moakiee.meplacementtool.network;

import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet to clear Cable Tool selected points from client side.
 * Used when player left-clicks on air (LeftClickEmpty event only fires on client).
 */
public class ClearCableToolPointsPacket {
    private final int slot;

    public ClearCableToolPointsPacket(int slot) {
        this.slot = slot;
    }

    public static void encode(ClearCableToolPointsPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.slot);
    }

    public static ClearCableToolPointsPacket decode(FriendlyByteBuf buf) {
        return new ClearCableToolPointsPacket(buf.readInt());
    }

    public static void handle(ClearCableToolPointsPacket msg, Supplier<NetworkEvent.Context> ctx) {
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

            // Check if any points are set
            var p1 = ItemMECablePlacementTool.getPoint1(stack);
            var p2 = ItemMECablePlacementTool.getPoint2(stack);
            var p3 = ItemMECablePlacementTool.getPoint3(stack);

            if (p1 != null || p2 != null || p3 != null) {
                // Clear all points
                ItemMECablePlacementTool.setPoint1(stack, null);
                ItemMECablePlacementTool.setPoint2(stack, null);
                ItemMECablePlacementTool.setPoint3(stack, null);

                player.displayClientMessage(Component.translatable("message.meplacementtool.points_cleared"), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
