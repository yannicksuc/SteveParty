package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLeave;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.service.ShopStops;
import fr.lordfinn.steveparty.service.TrichaudronPrizes;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Party commands, usable by any player taking part in the party (the chat buttons of an absent turn run them):
 * <ul>
 *     <li>{@code /steveparty skip_turn [token] [controller pos]}: skips the current turn if it waits for its absent token</li>
 *     <li>{@code /steveparty exclude <token> [controller pos]}: excludes a token from the party (own token, absent
 *     current turn, or any token with a Game Master wand in hand)</li>
 *     <li>{@code /steveparty leave}: the player leaves the party he is in, wherever it is (see {@link PartyLeave})</li>
 *     <li>{@code /steveparty kick <players>} (operators, or a Game Master wand holder near the party): makes them leave</li>
 *     <li>{@code /steveparty party stop [controller pos]} (operators): stops the running party, as the dashboard's
 *     « Stop the party » button does; without a position, the closest one within {@link #STOP_RANGE} blocks</li>
 * </ul>
 * Without a position, the closest running party within {@link #RANGE} blocks (containing the token, if given) is used.
 */
public class PartyCommands {
    /** A non-op player must be this close to the party controller to use the commands. */
    public static final int RANGE = PartyControllerEntity.PARTY_AUDIENCE_RADIUS;
    private static final int OP_LEVEL = 2;
    /** {@code /steveparty party stop} without a position: the closest running party this close. */
    public static final int STOP_RANGE = 64;

    private static final SuggestionProvider<ServerCommandSource> PARTY_TOKENS = (context, builder) -> {
        findParty(context.getSource(), null, null).ifPresent(party ->
                party.getPartyData().getTokens().forEach(token -> builder.suggest(token.toString())));
        return builder.buildFuture();
    };

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("steveparty")
                .then(literal("skip_turn")
                        .executes(context -> skipTurn(context.getSource(), null, null))
                        .then(argument("token", UuidArgumentType.uuid()).suggests(PARTY_TOKENS)
                                .executes(context -> skipTurn(context.getSource(), UuidArgumentType.getUuid(context, "token"), null))
                                .then(argument("controller", BlockPosArgumentType.blockPos())
                                        .executes(context -> skipTurn(context.getSource(),
                                                UuidArgumentType.getUuid(context, "token"), getPos(context))))))
                .then(literal("exclude")
                        .then(argument("token", UuidArgumentType.uuid()).suggests(PARTY_TOKENS)
                                .executes(context -> exclude(context.getSource(), UuidArgumentType.getUuid(context, "token"), null))
                                .then(argument("controller", BlockPosArgumentType.blockPos())
                                        .executes(context -> exclude(context.getSource(),
                                                UuidArgumentType.getUuid(context, "token"), getPos(context))))))
                // The button in the chat once a Trichaudron space's player has been choosing too long
                .then(literal("trichaudron_random")
                        .then(argument("token", UuidArgumentType.uuid())
                                .executes(context -> TrichaudronPrizes.pickAtRandom(UuidArgumentType.getUuid(context, "token"),
                                        context.getSource().getPlayerOrThrow()) ? 1 : 0)))
                // The chat's « [Open the shop] » of a shop stop, for its shopper
                .then(literal("shop_open")
                        .then(argument("token", UuidArgumentType.uuid())
                                .executes(context -> ShopStops.open(context.getSource().getPlayerOrThrow(),
                                        UuidArgumentType.getUuid(context, "token")) ? 1 : 0)))
                // The player leaves the party he is in, wherever it is (also the chat's « [Leave the party] »)
                .then(literal("leave")
                        .executes(context -> leave(context.getSource())))
                // Makes players leave: operators, or a Game Master near the party
                .then(literal("kick")
                        .then(argument("players", GameProfileArgumentType.gameProfile())
                                .executes(context -> kick(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "players")))))
                .then(literal("party").requires(source -> source.hasPermissionLevel(OP_LEVEL))
                        .then(literal("stop")
                                .executes(context -> stop(context.getSource(), null))
                                .then(argument("controller", BlockPosArgumentType.blockPos())
                                        .executes(context -> stop(context.getSource(), getPos(context)))))));
    }

    private static int stop(ServerCommandSource source, @Nullable BlockPos controllerPos) {
        ServerWorld world = source.getWorld();
        PartyControllerEntity party = controllerPos != null
                ? PartyControllerEntity.getPartyControllerEntity(world, controllerPos)
                : PartyControllerEntity.getActivePartyControllers().stream()
                        .filter(entity -> !entity.isRemoved() && entity.getWorld() == world && entity.getPartyData().isStarted())
                        .filter(entity -> entity.getPos().toCenterPos().isInRange(source.getPosition(), STOP_RANGE))
                        .min(Comparator.comparingDouble(entity -> entity.getPos().toCenterPos().squaredDistanceTo(source.getPosition())))
                        .orElse(null);
        if (party == null || !party.stopParty(source.getDisplayName())) {
            source.sendError(Text.translatableWithFallback("command.steveparty.no_party", "No running party found."));
            return 0;
        }
        BlockPos at = party.getPos();
        source.sendFeedback(() -> Text.translatableWithFallback("command.steveparty.party_stopped",
                "Party of the controller at %s, %s, %s stopped.", at.getX(), at.getY(), at.getZ()), true);
        return 1;
    }

    private static int leave(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        // A mini-game played out of a party first: what he owns given back, back where he stood
        boolean leftTest = MiniGameTest.leave(player.getUuid());
        List<GlobalPos> left = PartyLeave.leave(source.getServer(), player.getUuid());
        if (left.isEmpty()) {
            if (leftTest) {
                source.sendFeedback(() -> Text.translatable("command.steveparty.leave.mini_game"), false);
                return 1;
            }
            source.sendFeedback(() -> Text.translatableWithFallback("command.steveparty.leave.none",
                    "You are in no party."), false);
            return 0;
        }
        source.sendFeedback(() -> Text.translatableWithFallback("command.steveparty.leave.done",
                "You left the party. Your pawn goes back to start, you keep your items."), false);
        return left.size();
    }

    private static int kick(ServerCommandSource source, Collection<GameProfile> profiles) {
        boolean op = source.hasPermissionLevel(OP_LEVEL);
        if (!op && (source.getPlayer() == null || !holdsGameMasterWand(source.getPlayer()))) {
            source.sendError(Text.translatableWithFallback("command.steveparty.kick.not_allowed",
                    "Only an operator, or a Game Master (Tokenizer Wand enchanted with Game Master in hand), can do this."));
            return 0;
        }
        int kicked = 0;
        for (GameProfile profile : profiles) {
            // A Game Master only kicks from the parties around him
            if (!op && !nearPartyOf(source, profile.getId())) {
                source.sendError(Text.translatableWithFallback("command.steveparty.kick.not_near",
                        "%s is in no party near you.", profile.getName()));
                continue;
            }
            List<GlobalPos> left = PartyLeave.leave(source.getServer(), profile.getId());
            if (left.isEmpty()) {
                source.sendError(Text.translatableWithFallback("command.steveparty.kick.none", "%s is in no party.", profile.getName()));
                continue;
            }
            kicked++;
            ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(profile.getId());
            if (target != null && target != source.getPlayer()) {
                target.sendMessage(Text.translatableWithFallback("message.steveparty.kicked", "%s took you out of the party.",
                        source.getDisplayName()).formatted(Formatting.RED), false);
            }
            source.sendFeedback(() -> Text.translatableWithFallback("command.steveparty.kick.done",
                    "%s is out of the party.", profile.getName()), true);
        }
        return kicked;
    }

    /** {@code player} follows or plays a running party whose controller is within {@link #RANGE} blocks of the source. */
    private static boolean nearPartyOf(ServerCommandSource source, UUID player) {
        return PartyControllerEntity.getActivePartyControllers().stream()
                .filter(entity -> !entity.isRemoved() && entity.getWorld() == source.getWorld() && entity.getPartyData().isStarted())
                .filter(entity -> entity.getPos().toCenterPos().isInRange(source.getPosition(), RANGE))
                .anyMatch(entity -> entity.getInterestedPlayers().contains(player)
                        || entity.getPartyData().getTokens().stream().anyMatch(token -> entity.isTokenOwnedBy(token, player)));
    }

    private static BlockPos getPos(CommandContext<ServerCommandSource> context) {
        return BlockPosArgumentType.getBlockPos(context, "controller");
    }

    private static int skipTurn(ServerCommandSource source, @Nullable UUID token, @Nullable BlockPos controllerPos) {
        PartyControllerEntity party = resolveParty(source, token, controllerPos, false);
        if (party == null) return 0;
        PartyStep currentStep = party.getPartyData().getCurrentStep();
        if (!(currentStep instanceof TokenTurnPartyStep turn) || (token != null && !token.equals(turn.getTokenUUID()))) {
            source.sendError(Text.translatableWithFallback("command.steveparty.turn_not_current",
                    "This turn is not the current one any more."));
            return 0;
        }
        if (!turn.isWaitingForAbsentToken()) {
            source.sendError(Text.translatableWithFallback("command.steveparty.turn_not_absent",
                    "The token of the current turn is not absent, its turn cannot be skipped."));
            return 0;
        }
        if (!turn.skipAbsentTurn(party)) return 0;
        return 1;
    }

    private static int exclude(ServerCommandSource source, UUID token, @Nullable BlockPos controllerPos) {
        PartyControllerEntity party = resolveParty(source, token, controllerPos, true);
        if (party == null) return 0;
        if (!party.getPartyData().getTokens().contains(token)) {
            source.sendError(Text.translatableWithFallback("command.steveparty.token_not_in_party",
                    "This token is not part of the party."));
            return 0;
        }
        if (!source.hasPermissionLevel(OP_LEVEL) && !canExcludeToken(source.getPlayer(), party, token)) {
            source.sendError(Text.translatableWithFallback("command.steveparty.exclude_not_allowed",
                    "You can only exclude your own token, or the token whose turn is waiting for it."));
            return 0;
        }
        return party.excludeToken(token) ? 1 : 0;
    }

    /**
     * Exclusion rights of a non-operator player: their own token, the token whose (current) turn is waiting for
     * it (absent), or any token of the party while holding a Game Master wand.
     */
    public static boolean canExcludeToken(@Nullable PlayerEntity player, PartyControllerEntity party, UUID token) {
        if (player != null && (holdsGameMasterWand(player) || party.isTokenOwnedBy(token, player.getUuid()))) return true;
        return party.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn
                && token.equals(turn.getTokenUUID()) && turn.isWaitingForAbsentToken();
    }

    /** @return true if the player holds (either hand) a Tokenizer Wand enchanted with {@code steveparty:game_master}. */
    public static boolean holdsGameMasterWand(PlayerEntity player) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof TokenizerWandItem && TokenizerWandItem.hasGameMaster(stack, player.getWorld())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the party targeted by the command and checks the source may act on it.
     *
     * @param allowGameMaster a player holding a Game Master wand near the controller may act without taking part
     * @return null (the error has been sent) if there is no such party or the source may not use it
     */
    private static @Nullable PartyControllerEntity resolveParty(ServerCommandSource source, @Nullable UUID token,
                                                                @Nullable BlockPos controllerPos, boolean allowGameMaster) {
        PartyControllerEntity party = findParty(source, token, controllerPos).orElse(null);
        if (party == null || !party.getPartyData().isStarted()) {
            source.sendError(Text.translatableWithFallback("command.steveparty.no_party", "No running party found."));
            return null;
        }
        if (source.hasPermissionLevel(OP_LEVEL)) return party;

        ServerPlayerEntity player = source.getPlayer();
        if (player == null || player.getWorld() != party.getWorld()
                || !player.getPos().isInRange(party.getPos().toCenterPos(), RANGE)
                || !(party.isParticipant(player) || (allowGameMaster && holdsGameMasterWand(player)))) {
            source.sendError(Text.translatableWithFallback("command.steveparty.not_participant",
                    "You must take part in this party and be near its controller."));
            return null;
        }
        return party;
    }

    private static Optional<PartyControllerEntity> findParty(ServerCommandSource source, @Nullable UUID token, @Nullable BlockPos controllerPos) {
        ServerWorld world = source.getWorld();
        if (controllerPos != null)
            return Optional.ofNullable(PartyControllerEntity.getPartyControllerEntity(world, controllerPos));
        return PartyControllerEntity.getActivePartyControllers().stream()
                .filter(entity -> !entity.isRemoved() && entity.getWorld() == world)
                .filter(entity -> entity.getPartyData().isStarted())
                .filter(entity -> token == null || entity.getPartyData().getTokens().contains(token))
                .filter(entity -> entity.getPos().toCenterPos().isInRange(source.getPosition(), RANGE))
                .min(Comparator.comparingDouble(entity -> entity.getPos().toCenterPos().squaredDistanceTo(source.getPosition())));
    }
}
