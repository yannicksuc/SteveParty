package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.lordfinn.steveparty.entities.custom.pawn.PawnPossessions;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Operator tool, level 2: {@code /playerpawn <players> [size]} casts the token spell on players, as the Tokenizer
 * Wand does (they become pawns they are inside; sneak to get out). The pawns belong to whoever runs the command
 * (to the player themselves from the console or a command block).
 */
public final class PlayerPawnCommand {
    private PlayerPawnCommand() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("playerpawn")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("players", EntityArgumentType.players())
                        .executes(context -> run(context, TokenizerWandItem.DEFAULT_TOKEN_SIZE))
                        .then(argument("size", FloatArgumentType.floatArg(TokenizerWandItem.MIN_TOKEN_SIZE, TokenizerWandItem.MAX_TOKEN_SIZE))
                                .executes(context -> run(context, FloatArgumentType.getFloat(context, "size"))))));
    }

    private static int run(CommandContext<ServerCommandSource> context, float size) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "players");
        ServerPlayerEntity source = context.getSource().getPlayer();
        int count = 0;
        for (ServerPlayerEntity player : players) {
            if (!PawnPossessions.canTokenize(player)) continue;
            UUID owner = source != null ? source.getUuid() : player.getUuid();
            PawnPossessions.startSpell(player, owner, TokenizerWandItem.clampTokenSize(size), TokenizerWandItem.NO_COLOR);
            count++;
        }
        int done = count;
        if (done == 0) {
            context.getSource().sendError(Text.translatable("command.steveparty.playerpawn.none"));
        } else {
            context.getSource().sendFeedback(() -> Text.translatable("command.steveparty.playerpawn.done", done), true);
        }
        return done;
    }
}
