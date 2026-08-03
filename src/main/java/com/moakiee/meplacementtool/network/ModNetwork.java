package com.moakiee.meplacementtool.network;

import com.moakiee.meplacementtool.MEPlacementToolMod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            MEPlacementToolMod.id("me_placement"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int id;
    private static boolean registered;

    private ModNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.messageBuilder(UpdateWandConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateWandConfigPacket::encode).decoder(UpdateWandConfigPacket::decode)
                .consumerNetworkThread(UpdateWandConfigPacket::handle).add();
        CHANNEL.messageBuilder(UpdatePlacementCountPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdatePlacementCountPacket::encode).decoder(UpdatePlacementCountPacket::decode)
                .consumerNetworkThread(UpdatePlacementCountPacket::handle).add();
        CHANNEL.messageBuilder(UpdateDirectionModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateDirectionModePacket::encode).decoder(UpdateDirectionModePacket::decode)
                .consumerNetworkThread(UpdateDirectionModePacket::handle).add();
        CHANNEL.messageBuilder(UndoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UndoPacket::encode).decoder(UndoPacket::decode)
                .consumerNetworkThread(UndoPacket.Handler::handle).add();
        CHANNEL.messageBuilder(SyncPagePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SyncPagePacket::encode).decoder(SyncPagePacket::decode)
                .consumerNetworkThread(SyncPagePacket::handle).add();
        CHANNEL.messageBuilder(UpdateCableToolPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateCableToolPacket::encode).decoder(UpdateCableToolPacket::decode)
                .consumerNetworkThread(UpdateCableToolPacket::handle).add();
        CHANNEL.messageBuilder(OpenCableToolGuiPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenCableToolGuiPacket::encode).decoder(OpenCableToolGuiPacket::decode)
                .consumerNetworkThread(OpenCableToolGuiPacket::handle).add();
        CHANNEL.messageBuilder(ClearCableToolPointsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClearCableToolPointsPacket::encode).decoder(ClearCableToolPointsPacket::decode)
                .consumerNetworkThread(ClearCableToolPointsPacket::handle).add();
    }
}
