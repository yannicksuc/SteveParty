package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Party commands, usable by any player taking part in the party (the chat buttons of an absent turn run them):
 * <ul>
 *     <li>{@code /steveparty skip_turn [token] [controller pos]}: skips the current turn if it waits for its absent token</li>
 *     <li>{@code /steveparty exclude <token> [controller pos]}: excludes a token from the party</li>
 * </ul>
 * Without a position, the closest running party within {@link #RANGE} blocks (containing the token, if given) is used.
 */
public class PartyCommands {
    /** A non-op player must be this close to the party controller to use the commands. */
    public static final int RANGE = PartyControllerEntity.PARTY_AUDIENCE_RADIUS;
    private static final int OP_LEVEL = 2;

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
                                                UuidArgumentType.getUuid(context, "token"), getPos(context)))))));
    }

    private static BlockPos getPos(CommandContext<ServerCommandSource> context) {
        return BlockPosArgumentType.getBlockPos(context, "controller");
    }

    private static int skipTurn(ServerCommandSource source, @Nullable UUID token, @Nullable BlockPos controllerPos) {
        PartyControllerEntity party = resolveParty(source, token, controllerPos);
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
        PartyControllerEntity party = resolveParty(source, token, controllerPos);
        if (party == null) return 0;
        if (!party.getPartyData().getTokens().contains(token)) {
            source.sendError(Text.translatableWithFallback("command.steveparty.token_not_in_party",
                    "This token is not part of the party."));
            return 0;
        }
        // A player may exclude their own token, or the token whose (current) turn is waiting for it
        if (!source.hasPermissionLevel(OP_LEVEL)) {
            ServerPlayerEntity player = source.getPlayer();
            boolean ownToken = player != null && party.isTokenOwnedBy(token, player.getUuid());
            boolean absentCurrentTurn = party.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn
                    && token.equals(turn.getTokenUUID()) && turn.isWaitingForAbsentToken();
            if (!ownToken && !absentCurrentTurn) {
                source.sendError(Text.translatableWithFallback("command.steveparty.exclude_not_allowed",
                        "You can only exclude your own token, or the token whose turn is waiting for it."));
                return 0;
            }
        }
        return party.excludeToken(token) ? 1 : 0;
    }

    /**
     * Finds the party targeted by the command and checks the source may act on it.
     *
     * @return null (the error has been sent) if there is no such party or the source may not use it
     */
    private static @Nullable PartyControllerEntity resolveParty(ServerCommandSource source, @Nullable UUID token, @Nullable BlockPos controllerPos) {
        PartyControllerEntity party = findParty(source, token, controllerPos).orElse(null);
        if (party == null || !party.getPartyData().isStarted()) {
            source.sendError(Text.translatableWithFallback("command.steveparty.no_party", "No running party found."));
            return null;
        }
        if (source.hasPermissionLevel(OP_LEVEL)) return party;

        ServerPlayerEntity player = source.getPlayer();
        if (player == null || player.getWorld() != party.getWorld()
                || !player.getPos().isInRange(party.getPos().toCenterPos(), RANGE)
                || !party.isParticipant(player)) {
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
