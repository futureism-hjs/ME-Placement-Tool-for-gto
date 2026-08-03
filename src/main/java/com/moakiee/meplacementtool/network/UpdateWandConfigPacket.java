package com.moakiee.meplacementtool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateWandConfigPacket {
    public final CompoundTag tag;

    public UpdateWandConfigPacket(CompoundTag tag) {
        this.tag = tag == null ? new CompoundTag() : tag;
    }

    public static void encode(UpdateWandConfigPacket pkt, FriendlyByteBuf buf) {
        buf.writeNbt(pkt.tag);
    }

    public static UpdateWandConfigPacket decode(FriendlyByteBuf buf) {
        return new UpdateWandConfigPacket(buf.readNbt());
    }

    public static void handle(UpdateWandConfigPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var tool = player.containerMenu instanceof com.moakiee.meplacementtool.WandMenu menu
                    ? player.getItemInHand(menu.getToolHand())
                    : player.getMainHandItem();
            if (!(tool.getItem() instanceof com.moakiee.meplacementtool.BasePlacementToolItem)) return;
            CompoundTag sanitized = pkt.tag.copy();
            int selected = sanitized.getInt("SelectedSlot");
            sanitized.putInt("SelectedSlot", Math.max(0, Math.min(17, selected)));
            if (sanitized.contains("DirectionMode")) {
                int mode = sanitized.getInt("DirectionMode");
                int max = com.moakiee.meplacementtool.ItemMultiblockPlacementTool.DirectionMode.values().length - 1;
                sanitized.putInt("DirectionMode", Math.max(0, Math.min(max, mode)));
            }
            tool.getOrCreateTag().put("placement_config", sanitized);
        });
        ctx.get().setPacketHandled(true);
    }
}
