package com.xinyihl.functionalstoragelegacy.common.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

public class NetworkHandler {

    public static SimpleNetworkWrapper CHANNEL;

    public static void init() {
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("xfsl");

        // Register packets here as needed
        // Example: CHANNEL.registerMessage(SomePacket.Handler.class, SomePacket.class, packetId++, Side.SERVER);
    }
}
