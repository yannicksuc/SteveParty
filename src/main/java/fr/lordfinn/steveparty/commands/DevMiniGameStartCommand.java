package fr.lordfinn.steveparty.commands;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGameFreeze;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGameRoundCards;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads.RoundCard;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Dev only: {@code /steveparty dev_start [fast]} plays the start of a made-up mini-game round for the player (his card
 * at the top of the screen, the description told in the chat, the vote, the countdown), to look at it in game
 * without building an arena.
 */
public final class DevMiniGameStartCommand {
    private static final String DESCRIPTION = "&eAttrape la patate et passe-la avant qu'elle explose. Celui qui la tient à la fin perd. "
            + "Les bords de l'arène sont glissants, attention !";

    private DevMiniGameStartCommand() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("steveparty").then(literal("dev_start")
                        .executes(context -> start(context.getSource().getPlayerOrThrow(), false))
                        .then(literal("fast").executes(context -> start(context.getSource().getPlayerOrThrow(), true))))));
    }

    private static int start(ServerPlayerEntity player, boolean fast) {
        MiniGamePageData page = MiniGamePageData.empty(UUID.randomUUID()).withTexts("Patate chaude", DESCRIPTION);
        RoundCard card = new RoundCard(true, page.title(), MiniGameRoundCards.objective(page), 1, MiniGameFormat.Kind.TEAMS.ordinal(), List.of(2, 2));
        AtomicBoolean on = new AtomicBoolean(true);
        MiniGamePipes.enterParty(player.getUuid(), page.id(), on::get, card);
        MiniGameFreeze.Pace pace = fast ? MiniGameFreeze.Pace.FIRST.fast() : MiniGameFreeze.Pace.FIRST;
        MiniGameFreeze.start(player.getServer(), List.of(player), List.of(), page.title(), page, pace, on::get,
                () -> Steveparty.SCHEDULER.schedule(UUID.randomUUID(), 20 * 20, () -> {
                    on.set(false);
                    MiniGamePipes.leaveParty(player.getUuid());
                }));
        return 1;
    }
}
