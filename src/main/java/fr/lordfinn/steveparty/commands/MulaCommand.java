package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.Collection;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Operator tool to look at the Mula's animations and feedback (tests, previews, videos), level 2:
 * <ul>
 *   <li>{@code /mula <mulas> play <animation>}: plays one of its animations (as the server does);</li>
 *   <li>{@code /mula <mulas> interact <player>}: as if that player right-clicked it with what they hold (feed,
 *   refuse, sit / stand...);</li>
 *   <li>{@code /mula <mulas> tame <player> <success>}: a taming attempt with a forced outcome.</li>
 * </ul>
 * Nothing it does is out of reach in survival: it only shortcuts the clicks and the luck.
 */
public final class MulaCommand {
    private MulaCommand() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("mula")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("mulas", EntityArgumentType.entities())
                        .then(literal("play").then(argument("animation", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    MulaEntity.animationNames().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String animation = StringArgumentType.getString(context, "animation");
                                    if (!MulaEntity.animationNames().contains(animation)) {
                                        context.getSource().sendError(Text.literal("Unknown Mula animation: " + animation));
                                        return 0;
                                    }
                                    int count = 0;
                                    for (MulaEntity mula : mulas(EntityArgumentType.getEntities(context, "mulas"))) {
                                        mula.playAnimation(animation);
                                        count++;
                                    }
                                    return count;
                                })))
                        .then(literal("interact").then(argument("player", EntityArgumentType.player())
                                .executes(context -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                    int count = 0;
                                    for (MulaEntity mula : mulas(EntityArgumentType.getEntities(context, "mulas"))) {
                                        mula.interactMob(player, Hand.MAIN_HAND);
                                        count++;
                                    }
                                    return count;
                                })))
                        .then(literal("tame").then(argument("player", EntityArgumentType.player())
                                .then(argument("success", BoolArgumentType.bool())
                                        .executes(context -> {
                                            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                            boolean success = BoolArgumentType.getBool(context, "success");
                                            int count = 0;
                                            for (MulaEntity mula : mulas(EntityArgumentType.getEntities(context, "mulas"))) {
                                                mula.tameAttempt(player, success);
                                                count++;
                                            }
                                            return count;
                                        }))))));
    }

    private static Iterable<MulaEntity> mulas(Collection<? extends Entity> entities) {
        return entities.stream().filter(MulaEntity.class::isInstance).map(MulaEntity.class::cast).toList();
    }
}
