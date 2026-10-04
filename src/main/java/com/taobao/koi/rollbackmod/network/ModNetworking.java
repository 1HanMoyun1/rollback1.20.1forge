package com.taobao.koi.rollbackmod.network;

import com.taobao.koi.rollbackmod.RollbackMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";
    private static int nextPacketId;

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(RollbackMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private ModNetworking() {
    }

    public static void register() {
        CHANNEL.messageBuilder(DayInfoPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DayInfoPacket::encode)
                .decoder(DayInfoPacket::decode)
                .consumerMainThread(DayInfoPacket::handle)
                .add();
    }
}
