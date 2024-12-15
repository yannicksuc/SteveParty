package fr.lordfinn.steveparty;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.tiles.behaviors.TileBehaviorFactory;
import fr.lordfinn.steveparty.commands.MoveTokenCommand;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.events.ModEvents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.payloads.ModPayloads;
import fr.lordfinn.steveparty.screens.ModScreens;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.TaskScheduler;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Steveparty implements ModInitializer {

    public static final String MOD_ID = "steveparty";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Steveparty Instance = null;
    public static final TaskScheduler SCHEDULER = new TaskScheduler();

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
        ModEntities.initialize();
        ModEvents.initialize();

        MoveTokenCommand.init();

        new TokenMovementService();
        System.out.println("StevePartyMod initialized and blocks registered!");
    }

}
