package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.service.TurnMoves;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import fr.lordfinn.steveparty.service.TokenMovementService;

import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class MoveTokenCommand {
    private static final int OP_LEVEL = 2;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(
                literal("move_token")
                        .requires(source -> source.hasPermissionLevel(OP_LEVEL))
                        .then(argument("tokenName", StringArgumentType.string())
                                // Quoted when needed: the argument is a string, a name with a space must be typed in quotes
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        getNearbyTokenNames(context.getSource()).stream().map(StringArgumentType::escapeIfRequired), builder))
                                .then(argument("rollNumber", IntegerArgumentType.integer(1, 10))
                                        .executes(context -> {
                                            String tokenName = StringArgumentType.getString(context, "tokenName");
                                            int rollNumber = IntegerArgumentType.getInteger(context, "rollNumber");

                                            ServerCommandSource source = context.getSource();
                                            MobEntity mob = getNearestMobWithCustomName(source, tokenName);

                                            if (mob == null) {
                                                source.sendError(Text.translatable("command.steveparty.move_token.not_found", tokenName));
                                                return 0; // Échec
                                            }

                                            // A roll of one die showing rollNumber (what the board spaces of the move read)
                                            BoardSpaceBlockEntity from =
                                                    BoardSpaces.boardSpaceOf(mob);
                                            TurnMoves.record(mob, rollNumber, List.of(rollNumber),
                                                    from == null ? null : from.getPos());
                                            TokenMovementService.moveEntityOnBoard(mob, rollNumber);
                                            source.sendFeedback(() -> Text.translatable("command.steveparty.move_token.moved", tokenName, rollNumber), false);
                                            return 1; // Succès
                                        })
                                )
                        )
        );
    }

    /** The custom names of the mobs nearby: the ones the command can move. */
    private static List<String> getNearbyTokenNames(ServerCommandSource source) {
        return source.getWorld()
                .getEntitiesByClass(MobEntity.class, getNearbyBox(source), MobEntity::hasCustomName)
                .stream()
                .map(mob -> mob.getCustomName().getString())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Récupère le `MobEntity` le plus proche avec le nom personnalisé donné.
     */
    private static MobEntity getNearestMobWithCustomName(ServerCommandSource source, String customName) {
        return source.getWorld()
                .getEntitiesByClass(MobEntity.class, getNearbyBox(source),
                        mob -> mob.hasCustomName() &&
                        (mob.getCustomName() != null ? mob.getCustomName() : mob.getName()).getString().equals(customName))
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Définit une zone autour du joueur.
     */
    private static Box getNearbyBox(ServerCommandSource source) {
        return Box.of(source.getPosition(), 10, 10, 10); // Cube de 10 blocs autour
    }

    /**
     * Inscription de la commande.
     */
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher, registryAccess));
    }
}
