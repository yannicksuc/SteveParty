package fr.lordfinn.steveparty;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.LootingBoxBlock;
import fr.lordfinn.steveparty.commands.MoveTokenCommand;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.criteria.ModScoreboardCriteria;
import fr.lordfinn.steveparty.data.handler.ModHandler;
import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.loot.ModLootTableModifiers;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.events.ModEvents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.blocks.switchable.SwitchableConfig;
import fr.lordfinn.steveparty.payloads.ModPayloads;
import fr.lordfinn.steveparty.recipes.ModRecipes;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.TaskScheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
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
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();
        // Before the party / movement listeners of TileReachedEvent (a movement going on stops the chain)
        fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.initialize();
        fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport.initialize();
        fr.lordfinn.steveparty.blocks.custom.boardspaces.TileMigration.initialize();
        fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEvents.initialize();
        ModComponents.initialize();
        ModScreensHandlers.initialize();
        ModEffects.initialize();
        fr.lordfinn.steveparty.loot.RandomStencilPatternLootFunction.initialize();
        fr.lordfinn.steveparty.stencil.StencilLibrary.initialize();
        ModLootTableModifiers.initialize();
        ModPayloads.initialize();
        fr.lordfinn.steveparty.minigame.MiniGamePageNetworking.initialize();
        SwitchableConfig.initialize();
        ModEntities.initialize();
        ModEvents.initialize();
        ModHandler.initialize();
        ModRecipes.initialize();
        ModScoreboardCriteria.initialize();
        fr.lordfinn.steveparty.blocks.custom.GoalPoleNetwork.initialize();
        fr.lordfinn.steveparty.minigame.MiniGamePipes.initialize();
        fr.lordfinn.steveparty.minigame.MiniGameNameColors.initialize();
        fr.lordfinn.steveparty.podium.Podiums.initialize();
        fr.lordfinn.steveparty.minigame.MiniGameTest.initialize();
        fr.lordfinn.steveparty.blocks.custom.PartyController.PartyChunkHolds.initialize();
        fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem.initialize();
        fr.lordfinn.steveparty.minigame.zone.ZoneBubbles.initialize();
        // After the bubbles: its join hook gives a player its inventory back (ZoneBubbles.settle), then its place
        fr.lordfinn.steveparty.minigame.MiniGameReturns.initialize();
        fr.lordfinn.steveparty.minigame.MiniGameArena.initialize();
        fr.lordfinn.steveparty.minigame.MiniGameControllers.initialize();
        fr.lordfinn.steveparty.minigame.MiniGameAdventure.initialize();
        fr.lordfinn.steveparty.minigame.PageZoneTool.initialize();
        // The names the iron and golden mini-game pipes (and their block entity) had
        fr.lordfinn.steveparty.registry.RegistryAliases.add(id("super_golden_minigame_pipe"), id("iron_minigame_pipe"));
        fr.lordfinn.steveparty.registry.RegistryAliases.add(id("mega_golden_minigame_pipe"), id("golden_minigame_pipe"));
        fr.lordfinn.steveparty.registry.RegistryAliases.add(id("golden_pipe"), id("minigame_pipe"));
        fr.lordfinn.steveparty.blocks.custom.pipe.PipeNetworks.initialize();
        fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel.initialize();
        fr.lordfinn.steveparty.items.custom.BoxCostumeItem.initialize();
        fr.lordfinn.steveparty.entities.custom.BoxedTraderBoxes.initialize();

        MoveTokenCommand.initialize();
        fr.lordfinn.steveparty.commands.MulaCommand.initialize();
        fr.lordfinn.steveparty.commands.VillagerBlockCommand.initialize();
        fr.lordfinn.steveparty.entities.custom.MulaRebirths.initialize();
        fr.lordfinn.steveparty.entities.custom.MulaLift.initialize();
        fr.lordfinn.steveparty.items.custom.TokenizerFlare.initialize();
        fr.lordfinn.steveparty.entities.custom.MulaEphemeride.initialize();
        fr.lordfinn.steveparty.telescope.TelescopeService.initialize();
        fr.lordfinn.steveparty.commands.PartyCommands.initialize();
        fr.lordfinn.steveparty.commands.BoardCommands.initialize();
        fr.lordfinn.steveparty.commands.MiniGamePageCommand.initialize();
        fr.lordfinn.steveparty.commands.PodiumCommand.initialize();
        fr.lordfinn.steveparty.board.WrenchActions.initialize();
        new TokenMovementService();
        fr.lordfinn.steveparty.service.ShopStops.initialize();

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            for (ServerPlayerEntity player : world.getPlayers()) {
                LootingBoxBlock.testForCollision(player);
            }
        });
    }

    private void onServerStopped(MinecraftServer minecraftServer) {
        SERVER = null;
    }

    private void onServerStarted(MinecraftServer minecraftServer) {
        SERVER = minecraftServer;
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
