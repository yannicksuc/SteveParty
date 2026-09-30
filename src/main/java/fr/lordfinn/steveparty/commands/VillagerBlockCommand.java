package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerMode;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerReaction;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Operator tool to look at the villager block's reactions (tests, previews, videos), level 2:
 * <ul>
 *   <li>{@code /villagerblock <pos> react <reaction>}: plays a reaction now (cooldown and priority ignored), facing
 *   the player who runs it;</li>
 *   <li>{@code /villagerblock <pos> mode <mode>}: asleep, dancing, or back to normal.</li>
 * </ul>
 */
public final class VillagerBlockCommand {
    private VillagerBlockCommand() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("villagerblock")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("pos", BlockPosArgumentType.blockPos())
                        .then(literal("react").then(argument("reaction", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (VillagerReaction reaction : VillagerReaction.values()) {
                                        builder.suggest(reaction.name().toLowerCase(Locale.ROOT));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    VillagerBlockEntity villager = villager(context);
                                    if (villager == null) return 0;
                                    String name = StringArgumentType.getString(context, "reaction");
                                    VillagerReaction reaction = parse(VillagerReaction.class, name);
                                    if (reaction == null) {
                                        context.getSource().sendError(Text.translatable("command.steveparty.villager_block.unknown", name));
                                        return 0;
                                    }
                                    villager.forceReact(reaction, context.getSource().getEntity());
                                    return 1;
                                })))
                        .then(literal("mode").then(argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (VillagerMode mode : VillagerMode.values()) builder.suggest(mode.name().toLowerCase(Locale.ROOT));
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    VillagerBlockEntity villager = villager(context);
                                    if (villager == null) return 0;
                                    String name = StringArgumentType.getString(context, "mode");
                                    VillagerMode mode = parse(VillagerMode.class, name);
                                    if (mode == null) {
                                        context.getSource().sendError(Text.translatable("command.steveparty.villager_block.unknown", name));
                                        return 0;
                                    }
                                    villager.setMode(mode);
                                    return 1;
                                })))));
    }

    private static VillagerBlockEntity villager(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(context, "pos");
        if (context.getSource().getWorld().getBlockEntity(pos) instanceof VillagerBlockEntity villager) return villager;
        context.getSource().sendError(Text.translatable("command.steveparty.villager_block.not_found"));
        return null;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
