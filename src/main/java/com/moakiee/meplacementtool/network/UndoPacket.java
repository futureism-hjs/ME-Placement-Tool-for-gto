package com.moakiee.meplacementtool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import com.moakiee.meplacementtool.UndoHistory;

import java.util.function.Supplier;

public class UndoPacket
{
    public BlockPos pos;

    public UndoPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(UndoPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(msg.pos);
    }

    public static UndoPacket decode(FriendlyByteBuf buffer) {
        return new UndoPacket(buffer.readBlockPos());
    }

    public static class Handler
    {
        public static void handle(final UndoPacket msg, final Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if(player == null) return;
                var heldItem = player.getMainHandItem().getItem();
                if (!(heldItem instanceof com.moakiee.meplacementtool.ItemMultiblockPlacementTool)
                        && !(heldItem instanceof com.moakiee.meplacementtool.ItemMECablePlacementTool)) return;

                UndoHistory.UndoResult result = MEPlacementToolMod.instance.undoHistory.undoWithResult(player, player.level(), msg.pos);
                if (result == UndoHistory.UndoResult.MEMORY_CARD_APPLIED) {
                    player.displayClientMessage(Component.translatable("message.meplacementtool.cannot_undo_memory_card"), true);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
