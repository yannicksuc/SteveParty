package fr.lordfinn.steveparty;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.screens.ModScreens;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Steveparty implements ModInitializer {

    public static final String MOD_ID = "steveparty";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModSounds.initialize();
        ModParticles.initialize();
        ModBlocks.registerBlocks();
        ModItems.initialize();
        ModBlockEntities.initialize();
        ModComponents.initialize();
        ModScreens.initialize();

        System.out.println("StevePartyMod initialized and blocks registered!");

    }
}
