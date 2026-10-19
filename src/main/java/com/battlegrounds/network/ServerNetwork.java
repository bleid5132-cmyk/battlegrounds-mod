package com.battlegrounds.network;

import com.battlegrounds.player.BgPlayer;
import com.battlegrounds.weapon.GunLogic;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.math.MathHelper;

public final class ServerNetwork {

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(Packets.FIRE, (server, player, handler, buf, sender) -> {
            boolean aiming = buf.readBoolean();
            server.execute(() -> GunLogic.fire(player, aiming));
        });

        ServerPlayNetworking.registerGlobalReceiver(Packets.RELOAD, (server, player, handler, buf, sender) ->
                server.execute(() -> GunLogic.startReload(player)));

        ServerPlayNetworking.registerGlobalReceiver(Packets.FIRE_MODE, (server, player, handler, buf, sender) ->
                server.execute(() -> GunLogic.toggleMode(player)));

        ServerPlayNetworking.registerGlobalReceiver(Packets.STANCE, (server, player, handler, buf, sender) -> {
            int stance = MathHelper.clamp(buf.readByte(), 0, 2);
            server.execute(() -> BgPlayer.of(player).bg$setStance(stance));
        });

        ServerPlayNetworking.registerGlobalReceiver(Packets.LEAN, (server, player, handler, buf, sender) -> {
            int lean = buf.readByte();
            server.execute(() -> BgPlayer.of(player).bg$setLean(lean));
        });

        ServerPlayNetworking.registerGlobalReceiver(Packets.MANTLE, (server, player, handler, buf, sender) -> {
            boolean mantling = buf.readBoolean();
            server.execute(() -> {
                BgPlayer.of(player).bg$setMantling(mantling);
                player.fallDistance = 0;
            });
        });
    }

    private ServerNetwork() {
    }
}
