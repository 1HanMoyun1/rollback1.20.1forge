package com.taobao.koi.rollbackmod.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record DayInfoPacket(
        boolean showHud,
        boolean countdownMode,
        int day,
        int remainingDays,
        boolean playAnimation,
        int fromNumber,
        int toNumber
) {
    public static void encode(DayInfoPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.showHud);
        buffer.writeBoolean(packet.countdownMode);
        buffer.writeVarInt(packet.day);
        buffer.writeVarInt(packet.remainingDays);
        buffer.writeBoolean(packet.playAnimation);
        if (packet.playAnimation) {
            buffer.writeVarInt(packet.fromNumber);
            buffer.writeVarInt(packet.toNumber);
        }
    }

    public static DayInfoPacket decode(FriendlyByteBuf buffer) {
        boolean showHud = buffer.readBoolean();
        boolean countdownMode = buffer.readBoolean();
        int day = buffer.readVarInt();
        int remainingDays = buffer.readVarInt();
        boolean playAnimation = buffer.readBoolean();
        int fromNumber = -1;
        int toNumber = -1;
        if (playAnimation) {
            fromNumber = buffer.readVarInt();
            toNumber = buffer.readVarInt();
        }
        return new DayInfoPacket(showHud, countdownMode, day, remainingDays, playAnimation, fromNumber, toNumber);
    }

    public static void handle(DayInfoPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            com.taobao.koi.rollbackmod.client.ClientDayHud.update(packet);
        }));
        context.setPacketHandled(true);
    }
}
