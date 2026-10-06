package com.taobao.koi.rollbackmod.network;

import com.taobao.koi.rollbackmod.RollbackMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public final class ModNetworking {
    private static final int PROTOCOL_VERSION = 1;
    private static int nextPacketId;

    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(new ResourceLocation(RollbackMod.MOD_ID, "main"))
            .networkProtocolVersion(PROTOCOL_VERSION)
            .clientAcceptedVersions(Channel.VersionTest.exact(PROTOCOL_VERSION))
            .serverAcceptedVersions(Channel.VersionTest.exact(PROTOCOL_VERSION))
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

    public static void sendToPlayer(ServerPlayer player, DayInfoPacket packet) {
        CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }

    public static void sendToAll(MinecraftServer server, DayInfoPacket packet) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
        }
    }
}