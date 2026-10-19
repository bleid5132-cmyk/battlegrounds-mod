package com.battlegrounds.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class BattlegroundsClient implements ClientModInitializer {
    public static final String CATEGORY = "category.battlegrounds";

    public static KeyBinding RELOAD;
    public static KeyBinding FIRE_MODE;
    public static KeyBinding CROUCH;
    public static KeyBinding PRONE;

    @Override
    public void onInitializeClient() {
        RELOAD = register("reload", GLFW.GLFW_KEY_R);
        FIRE_MODE = register("fire_mode", GLFW.GLFW_KEY_B);
        CROUCH = register("crouch", GLFW.GLFW_KEY_C);
        PRONE = register("prone", GLFW.GLFW_KEY_Z);

        ClientTickEvents.END_CLIENT_TICK.register(ClientController::tick);
        HudRenderCallback.EVENT.register(Hud::render);
    }

    private static KeyBinding register(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.battlegrounds." + name,
                InputUtil.Type.KEYSYM, key, CATEGORY));
    }
}
