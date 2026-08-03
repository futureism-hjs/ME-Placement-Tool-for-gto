package com.moakiee.meplacementtool.network;

import com.moakiee.meplacementtool.ItemMultiblockPlacementTool;
import com.moakiee.meplacementtool.WandMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateDirectionModePacket {
    public final int modeId;

    public UpdateDirectionModePacket(int modeId) {
        this.modeId = modeId;
    }

    public static void encode(UpdateDirectionModePacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.modeId);
    }

    public static UpdateDirectionModePacket decode(FriendlyByteBuf buf) {
        return new UpdateDirectionModePacket(buf.readInt());
    }

    public static void handle(UpdateDirectionModePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            ItemStack main = player.getMainHandItem();
            if (main.isEmpty() || !(main.getItem() instanceof ItemMultiblockPlacementTool)) {
                return;
            }
            if (pkt.modeId < 0 || pkt.modeId >= ItemMultiblockPlacementTool.DirectionMode.values().length) {
                return;
            }

            CompoundTag data = main.getOrCreateTag();
            CompoundTag cfg = data.contains(WandMenu.TAG_KEY) ? data.getCompound(WandMenu.TAG_KEY).copy() : new CompoundTag();
            cfg.putInt("DirectionMode", pkt.modeId);
            data.put(WandMenu.TAG_KEY, cfg);
        });
        ctx.get().setPacketHandled(true);
    }
}
