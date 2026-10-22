package com.battlegrounds.client;

import com.battlegrounds.client.anim.BodyAnimator;
import com.battlegrounds.client.fx.Shells;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import com.battlegrounds.client.geo.GeoAssets;
import com.battlegrounds.client.render.GunRenderer;
import com.battlegrounds.client.screen.AttachmentScreen;
import com.battlegrounds.item.GunItem;
import com.battlegrounds.registry.ModItems;
import com.battlegrounds.registry.ModScreens;
import com.battlegrounds.weapon.AttachmentType;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class BattlegroundsClient implements ClientModInitializer {
    public static final String CATEGORY = "category.battlegrounds";

    public static KeyBinding RELOAD;
    public static KeyBinding FIRE_MODE;
    public static KeyBinding CROUCH;
    public static KeyBinding PRONE;
    public static KeyBinding ATTACHMENTS;

    @Override
    public void onInitializeClient() {
        RELOAD = register("reload", GLFW.GLFW_KEY_R);
        FIRE_MODE = register("fire_mode", GLFW.GLFW_KEY_B);
        CROUCH = register("crouch", GLFW.GLFW_KEY_C);
        PRONE = register("prone", GLFW.GLFW_KEY_Z);
        ATTACHMENTS = register("attachments", GLFW.GLFW_KEY_G);

        ClientTickEvents.END_CLIENT_TICK.register(ClientController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Shells.tickWorld());
        WorldRenderEvents.AFTER_ENTITIES.register(Shells::renderWorld);
        // эффекты выстрела: пакет от сервера → вспышка, трассер, попадание
        ClientTickEvents.END_CLIENT_TICK.register(mc -> com.battlegrounds.client.fx.ShotFx.tick());
        WorldRenderEvents.AFTER_ENTITIES.register(com.battlegrounds.client.fx.ShotFx::renderWorld);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.battlegrounds.network.Packets.SHOT_FX, (client, handler, buf, sender) -> {
                    int shooter = buf.readVarInt();
                    net.minecraft.util.math.Vec3d origin = new net.minecraft.util.math.Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
                    net.minecraft.util.math.Vec3d hit = new net.minecraft.util.math.Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
                    int type = buf.readByte();
                    int face = buf.readByte();
                    int state = buf.readVarInt();
                    boolean suppressed = buf.readBoolean();
                    boolean flashHidden = buf.readBoolean();
                    boolean heavy = buf.readByte() == 1;
                    net.minecraft.util.math.Vec3d water = buf.readBoolean()
                            ? new net.minecraft.util.math.Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
                    client.execute(() -> com.battlegrounds.client.fx.ShotFx.onShot(shooter, origin, hit, type, face, state,
                            suppressed, flashHidden, heavy, water));
                });
        HudRenderCallback.EVENT.register(Hud::render);
        HandledScreens.register(ModScreens.ATTACHMENTS, AttachmentScreen::new);

        // костные модели и анимации оружия (вид от первого лица по эталону)
        GeoAssets.init("m416", "akm", "m16a4", "mk47_mutant", "k2", "scar_l", "g36c", "beryl_m762", "ace32", "aug", "qbz", "famas", "groza");

        // модели оружия и обвесов, которые собираются на лету
        List<Identifier> extra = new ArrayList<>();
        for (GunItem gun : ModItems.GUNS.values()) {
            extra.add(GunRenderer.baseModelId(gun.stats.id()));
            BuiltinItemRendererRegistry.INSTANCE.register(gun, new GunRenderer(gun.stats.id()));
        }
        for (AttachmentType type : AttachmentType.values()) {
            extra.add(GunRenderer.attachmentModelId(type));
        }
        ModelLoadingPlugin.register(ctx -> ctx.addModels(extra));

        // свои анимации тела со сгибаемыми суставами — для каждого игрока
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) ->
                stack.addAnimLayer(900, new BodyAnimator(player)));
    }

    private static KeyBinding register(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.battlegrounds." + name,
                InputUtil.Type.KEYSYM, key, CATEGORY));
    }
}
