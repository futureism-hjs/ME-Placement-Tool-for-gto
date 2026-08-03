package com.moakiee.meplacementtool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdatePlacementCountPacket {
    public final int count;

    public UpdatePlacementCountPacket(int count) {
        this.count = count;
    }

    public static void encode(UpdatePlacementCountPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.count);
    }

    public static UpdatePlacementCountPacket decode(FriendlyByteBuf buf) {
        return new UpdatePlacementCountPacket(buf.readInt());
    }

    public static void handle(UpdatePlacementCountPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ItemStack main = player.getMainHandItem();
            if (!(main.getItem() instanceof com.moakiee.meplacementtool.ItemMultiblockPlacementTool)) return;
            if (pkt.count != 1 && pkt.count != 8 && pkt.count != 64
                    && pkt.count != 256 && pkt.count != 1024) return;
            main.getOrCreateTag().putInt("placement_count", pkt.count);
        });
        ctx.get().setPacketHandled(true);
    }
}
