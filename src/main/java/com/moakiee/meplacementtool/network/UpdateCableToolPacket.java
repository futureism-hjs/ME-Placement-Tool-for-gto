package com.moakiee.meplacementtool.network;

import com.moakiee.meplacementtool.ItemMECablePlacementTool;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateCableToolPacket {
    private final int mode;
    private final int cableType;
    private final int color;

    public UpdateCableToolPacket(int mode, int cableType, int color) {
        this.mode = mode;
        this.cableType = cableType;
        this.color = color;
    }

    public static void encode(UpdateCableToolPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.mode);
        buf.writeInt(pkt.cableType);
        buf.writeInt(pkt.color);
    }

    public static UpdateCableToolPacket decode(FriendlyByteBuf buf) {
        return new UpdateCableToolPacket(buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(UpdateCableToolPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof com.moakiee.meplacementtool.CableToolMenu menu)) return;
            if (pkt.mode < 0 || pkt.mode >= ItemMECablePlacementTool.PlacementMode.values().length
                    || pkt.cableType < 0 || pkt.cableType >= ItemMECablePlacementTool.CableType.values().length
                    || pkt.color < 0 || pkt.color >= appeng.api.util.AEColor.values().length) return;
            var tool = menu.getToolStack();
            if (!(tool.getItem() instanceof ItemMECablePlacementTool)) return;
            ItemMECablePlacementTool.setMode(tool, ItemMECablePlacementTool.PlacementMode.values()[pkt.mode]);
            ItemMECablePlacementTool.setCableType(tool, ItemMECablePlacementTool.CableType.values()[pkt.cableType]);
            ItemMECablePlacementTool.setColor(tool, appeng.api.util.AEColor.values()[pkt.color]);
        });
        ctx.get().setPacketHandled(true);
    }
}
