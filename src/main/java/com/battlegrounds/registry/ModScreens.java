package com.battlegrounds.registry;

import com.battlegrounds.Battlegrounds;
import com.battlegrounds.screen.AttachmentScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreens {
    public static final ScreenHandlerType<AttachmentScreenHandler> ATTACHMENTS = Registry.register(
            Registries.SCREEN_HANDLER, Battlegrounds.id("attachments"),
            new ScreenHandlerType<>(AttachmentScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static void init() {
    }

    private ModScreens() {
    }
}
