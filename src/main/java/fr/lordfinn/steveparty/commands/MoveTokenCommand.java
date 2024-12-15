package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import fr.lordfinn.steveparty.service.TokenMovementService;

import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class MoveTokenCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(
                literal("move_token")
                        .then(argument("tokenName", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    ServerCommandSource source = context.getSource();
                                    List<String> tokenNames = getNearbyTokenNames(source);
                                    tokenNames.forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .then(argument("rollNumber", IntegerArgumentType.integer(1, 10))
                                        .executes(context -> {
                                            String tokenName = StringArgumentType.getString(context, "tokenName");
                                            int rollNumber = IntegerArgumentType.getInteger(context, "rollNumber");

                                            ServerCommandSource source = context.getSource();
                                            MobEntity mob = getNearestMobWithCustomName(source, tokenName);

                                            if (mob == null) {
                                                source.sendError(Text.of("Aucun mob trouvé avec le nom spécifié : " + tokenName));
                                                return 0; // Échec
                                            }

                                            // Appel de la méthode du service
                                            TokenMovementService.moveEntityOnBoard(mob, rollNumber);
                                            source.sendFeedback(() -> Text.literal("Déplacement du token '" + tokenName + "' de " + rollNumber + " cases."), false);
                                            return 1; // Succès
                                        })
                                )
                        )
        );
    }

    /**
     * Récupère les noms personnalisés des mobs proches.
     */
    private static List<String> getNearbyTokenNames(ServerCommandSource source) {
        return source.getWorld()
                .getEntitiesByClass(MobEntity.class, getNearbyBox(source), mob -> mob.hasCustomName())
                .stream()
                .map(mob -> mob.getCustomName().getString())
                .collect(Collectors.toList());
    }

    /**
     * Récupère le `MobEntity` le plus proche avec le nom personnalisé donné.
     */
    private static MobEntity getNearestMobWithCustomName(ServerCommandSource source, String customName) {
        return source.getWorld()
                .getEntitiesByClass(MobEntity.class, getNearbyBox(source), mob -> mob.hasCustomName() && mob.getCustomName().getString().equals(customName))
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
    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher, registryAccess));
    }
}
