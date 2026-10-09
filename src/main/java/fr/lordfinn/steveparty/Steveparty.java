package fr.lordfinn.steveparty;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleNetwork;
import fr.lordfinn.steveparty.blocks.custom.LootingBoxBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyChunkHolds;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileMigration;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.GlandouilleTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeNetworks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEvents;
import fr.lordfinn.steveparty.board.ExplorerHelmet;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.board.WrenchActions;
import fr.lordfinn.steveparty.commands.BoardCommands;
import fr.lordfinn.steveparty.commands.MiniGamePageCommand;
import fr.lordfinn.steveparty.commands.MoveTokenCommand;
import fr.lordfinn.steveparty.commands.MulaCommand;
import fr.lordfinn.steveparty.commands.PartyCommands;
import fr.lordfinn.steveparty.commands.PlayerPawnCommand;
import fr.lordfinn.steveparty.commands.PodiumCommand;
import fr.lordfinn.steveparty.commands.VillagerBlockCommand;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.criteria.ModScoreboardCriteria;
import fr.lordfinn.steveparty.data.handler.ModHandler;
import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.entities.PetTeleports;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderBoxes;
import fr.lordfinn.steveparty.entities.custom.MulaEphemeride;
import fr.lordfinn.steveparty.entities.custom.MulaLift;
import fr.lordfinn.steveparty.entities.custom.MulaRebirths;
import fr.lordfinn.steveparty.entities.custom.boomcart.BoomcartSpawns;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxSpawns;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleEvents;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleSpawns;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleCarrySave;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleSpawns;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleTowers;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriBadLuck;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriSummoning;
import fr.lordfinn.steveparty.entities.custom.pawn.PawnPossessions;
import fr.lordfinn.steveparty.items.custom.BoxCostumeItem;
import fr.lordfinn.steveparty.items.custom.TokenizerFlare;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.loot.ModLootTableModifiers;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.events.ModEvents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.loot.RandomStencilPatternLootFunction;
import fr.lordfinn.steveparty.minigame.MiniGameAdventure;
import fr.lordfinn.steveparty.minigame.MiniGameArena;
import fr.lordfinn.steveparty.minigame.MiniGameControllers;
import fr.lordfinn.steveparty.minigame.MiniGameNameColors;
import fr.lordfinn.steveparty.minigame.MiniGamePageNetworking;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGameReturns;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
import fr.lordfinn.steveparty.minigame.PageZoneTool;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.blocks.switchable.SwitchableConfig;
import fr.lordfinn.steveparty.payloads.ModPayloads;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.recipes.ModRecipes;
import fr.lordfinn.steveparty.registry.LegacyIds;
import fr.lordfinn.steveparty.registry.ModGameRules;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.service.BoardActors;
import fr.lordfinn.steveparty.service.FrousseuxThefts;
import fr.lordfinn.steveparty.service.MistigriSentences;
import fr.lordfinn.steveparty.service.PartyStars;
import fr.lordfinn.steveparty.service.ShopStops;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.stencil.StencilLibrary;
import fr.lordfinn.steveparty.telescope.TelescopeService;
import fr.lordfinn.steveparty.utils.TaskScheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Steveparty implements ModInitializer {

    public static final String MOD_ID = "steveparty";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final TaskScheduler SCHEDULER = new TaskScheduler();
    public static MinecraftServer SERVER = null;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(this::onServerStopped);
        ModSounds.initialize();
        ModParticles.initialize();
        ModBlocks.initialize();
        // Former ids of renamed blocks, items and entities, so saved worlds keep them
        LegacyIds.initialize();
        ModGameRules.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();
        // Before the party / movement listeners of TileReachedEvent (a movement going on stops the chain)
        TileFeedback.initialize();
        TileTeleport.initialize();
        GlandouilleTileBehavior.initialize();
        BoardActors.initialize();
        FrousseuxThefts.initialize();
        MistigriSentences.initialize();
        MistigriBadLuck.initialize();
        MistigriSummoning.initialize();
        TileMigration.initialize();
        VillagerBlockEvents.initialize();
        ModComponents.initialize();
        ModScreensHandlers.initialize();
        ModEffects.initialize();
        RandomStencilPatternLootFunction.initialize();
        StencilLibrary.initialize();
        ModLootTableModifiers.initialize();
        ModPayloads.initialize();
        MiniGamePageNetworking.initialize();
        SwitchableConfig.initialize();
        ModEntities.initialize();
        ModEvents.initialize();
        ModHandler.initialize();
        ModRecipes.initialize();
        ModScoreboardCriteria.initialize();
        GoalPoleNetwork.initialize();
        MiniGamePipes.initialize();
        MiniGameNameColors.initialize();
        Podiums.initialize();
        MiniGameTest.initialize();
        PartyChunkHolds.initialize();
        InventoryCartridgeItem.initialize();
        ZoneBubbles.initialize();
        // After the bubbles: its join hook gives a player its inventory back (ZoneBubbles.settle), then its place
        MiniGameReturns.initialize();
        MiniGameArena.initialize();
        MiniGameControllers.initialize();
        MiniGameAdventure.initialize();
        PageZoneTool.initialize();
        PipeNetworks.initialize();
        PipeTravel.initialize();
        BoxCostumeItem.initialize();
        BoxedTraderBoxes.initialize();
        ExplorerHelmet.initialize();

        MoveTokenCommand.initialize();
        MulaCommand.initialize();
        PlayerPawnCommand.initialize();
        VillagerBlockCommand.initialize();
        MulaRebirths.initialize();
        MulaLift.initialize();
        TokenizerFlare.initialize();
        PawnPossessions.initialize();
        MulaEphemeride.initialize();
        TelescopeService.initialize();
        PartyCommands.initialize();
        BoardCommands.initialize();
        MiniGamePageCommand.initialize();
        PodiumCommand.initialize();
        WrenchActions.initialize();
        TileLinkerBrush.initialize();
        new TokenMovementService();
        ShopStops.initialize();
        PartyStars.initialize();
        GlandouilleSpawns.initialize();
        GlandouilleTowers.initialize();
        GlandouilleCarrySave.initialize();
        FrousseuxSpawns.initialize();
        FumaroleSpawns.initialize();
        FumaroleEvents.initialize();
        BoomcartSpawns.initialize();
        PetTeleports.initialize();
        // the Frousseux candle holder burns as planks do: fire catches it and burns it away, the Frousseux gone with it
        FlammableBlockRegistry.getDefaultInstance().add(
                ModBlocks.FROUSSEUX_CANDLE_HOLDER, 5, 20);

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
