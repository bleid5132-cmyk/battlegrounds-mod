package com.battlegrounds.client;

import com.battlegrounds.client.anim.BodyAnimator;
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
        HudRenderCallback.EVENT.register(Hud::render);
        HandledScreens.register(ModScreens.ATTACHMENTS, AttachmentScreen::new);

        // костные модели и анимации оружия (вид от первого лица по эталону)
        GeoAssets.init("m416");

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
