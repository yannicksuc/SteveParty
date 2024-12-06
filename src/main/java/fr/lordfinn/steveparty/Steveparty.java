package fr.lordfinn.steveparty;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.payloads.ModPayloads;
import fr.lordfinn.steveparty.screens.ModScreens;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Steveparty implements ModInitializer {

    public static final String MOD_ID = "steveparty";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Steveparty Instance = null;

    @Override
    public void onInitialize() {
        Instance = this;
        ModSounds.initialize();
        ModParticles.initialize();
        ModBlocks.registerBlocks();
        ModItems.initialize();
        ModBlockEntities.initialize();
        ModComponents.initialize();
        ModScreens.initialize();
        ModEffects.initialize();
        ModPayloads.initialize();

        System.out.println("StevePartyMod initialized and blocks registered!");

    }
}
