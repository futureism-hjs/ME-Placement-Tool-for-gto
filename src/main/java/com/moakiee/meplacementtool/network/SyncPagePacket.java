package com.moakiee.meplacementtool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import com.moakiee.meplacementtool.WandMenu;

import java.util.function.Supplier;

/**
 * Packet to sync the current page from client to server for WandMenu.
 */
public class SyncPagePacket {
    private final int page;

    public SyncPagePacket(int page) {
        this.page = page;
    }

    public static void encode(SyncPagePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.page);
    }

    public static SyncPagePacket decode(FriendlyByteBuf buf) {
        return new SyncPagePacket(buf.readInt());
    }

    public static void handle(SyncPagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof WandMenu menu) {
                menu.setCurrentPage(msg.page);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
