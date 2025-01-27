package fr.lordfinn.steveparty;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.commands.MoveTokenCommand;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.events.ModEvents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.payloads.ModPayloads;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.service.VillagerBlockSpawnListener;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.ServerNetworking;
import fr.lordfinn.steveparty.utils.TaskScheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Steveparty implements ModInitializer {

    public static final String MOD_ID = "steveparty";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Steveparty Instance = null;
    public static final TaskScheduler SCHEDULER = new TaskScheduler();
    public static MinecraftServer SERVER = null;

    @Override
    public void onInitialize() {
        Instance = this;
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(this::onServerStopped);
        ModSounds.initialize();
        ModParticles.initialize();
        ModBlocks.registerBlocks();
        ModItems.initialize();
        ModBlockEntities.initialize();
        ModComponents.initialize();
        ModScreensHandlers.initialize();
        ModEffects.initialize();
        ModPayloads.initialize();
        ModEntities.initialize();
        ModEvents.initialize();

        ServerNetworking.init();

        MoveTokenCommand.init();

        new TokenMovementService();

        VillagerBlockSpawnListener.registerChatListener();
    }

    private void onServerStopped(MinecraftServer minecraftServer) {
        SERVER = minecraftServer;
    }

    private void onServerStarted(MinecraftServer minecraftServer) {
        SERVER = null;
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
