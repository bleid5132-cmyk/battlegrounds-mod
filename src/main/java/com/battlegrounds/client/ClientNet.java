package com.battlegrounds.client;

import com.battlegrounds.network.Packets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class ClientNet {

    public static void fire(boolean aiming) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(aiming);
        send(Packets.FIRE, buf);
    }

    public static void reload() {
        send(Packets.RELOAD, PacketByteBufs.empty());
    }

    public static void fireMode() {
        send(Packets.FIRE_MODE, PacketByteBufs.empty());
    }

    public static void stance(int stance) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(stance);
        send(Packets.STANCE, buf);
    }

    public static void lean(int lean) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(lean);
        send(Packets.LEAN, buf);
    }

    public static void mantle(boolean mantling) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(mantling);
        send(Packets.MANTLE, buf);
    }

    private static void send(Identifier id, PacketByteBuf buf) {
        if (ClientPlayNetworking.canSend(id)) {
            ClientPlayNetworking.send(id, buf);
        }
    }

    private ClientNet() {
    }
}
