package com.battlegrounds;

import com.battlegrounds.network.ServerNetwork;
import com.battlegrounds.registry.ModItems;
import com.battlegrounds.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Battlegrounds implements ModInitializer {
    public static final String MOD_ID = "battlegrounds";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModSounds.init();
        ModItems.init();
        ServerNetwork.init();
        LOGGER.info("Battlegrounds loaded");
    }
}
