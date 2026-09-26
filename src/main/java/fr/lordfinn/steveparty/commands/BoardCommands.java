package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.board.BoardBlueprint;
import fr.lordfinn.steveparty.board.BoardValidator;
import fr.lordfinn.steveparty.board.LinkHistory;
import fr.lordfinn.steveparty.board.WrenchActions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Board tools as commands ({@code /steveparty board ...}):
 * <ul>
 *     <li>{@code check [pos]}: checks the board around a position (default: the closest Party Controller within 100
 *     blocks, else where the command runs), like a Wrench click on the Party Controller;</li>
 *     <li>{@code undo}, {@code redo}: like a left click in the air with the Wrench (sneaking: redo);</li>
 *     <li>operators: {@code copy <from> <to>} and {@code paste [rotation] [keep_outside_links]}: a piece of board with
 *     its links, relative to where the player stands, turned by 0 / 90 / 180 / 270 degrees clockwise;</li>
 *     <li>operators: {@code translate <from> <to> <dx> <dy> <dz>}: after moving board spaces with another tool
 *     (WorldEdit...) by that offset, their links into the former area follow;</li>
 *     <li>operators: {@code template loop|line <count> [spacing]}: linked tiles in front of the player.</li>
 * </ul>
 */
public final class BoardCommands {
    private static final int OP_LEVEL = 2;
    private static final SuggestionProvider<ServerCommandSource> ROTATIONS = (context, builder) -> {
        for (String rotation : List.of("0", "90", "180", "270")) builder.suggest(rotation);
        return builder.buildFuture();
    };

    private BoardCommands() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        // The undo history and the clipboards live in memory: a new server (or world) starts empty
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LinkHistory.clear();
            BoardBlueprint.clear();
        });
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("steveparty").then(literal("board")
                .then(literal("undo").executes(context -> undo(context.getSource(), true)))
                .then(literal("redo").executes(context -> undo(context.getSource(), false)))
                .then(literal("check")
                        .executes(context -> check(context.getSource(), null))
                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                .executes(context -> check(context.getSource(), BlockPosArgumentType.getLoadedBlockPos(context, "pos")))))
                .then(literal("copy").requires(source -> source.hasPermissionLevel(OP_LEVEL))
                        .then(argument("from", BlockPosArgumentType.blockPos()).then(argument("to", BlockPosArgumentType.blockPos())
                                .executes(BoardCommands::copy))))
                .then(literal("paste").requires(source -> source.hasPermissionLevel(OP_LEVEL))
                        .executes(context -> paste(context, 0, false))
                        .then(argument("rotation", IntegerArgumentType.integer(0, 270)).suggests(ROTATIONS)
                                .executes(context -> paste(context, IntegerArgumentType.getInteger(context, "rotation"), false))
                                .then(argument("keep_outside_links", BoolArgumentType.bool())
                                        .executes(context -> paste(context, IntegerArgumentType.getInteger(context, "rotation"),
                                                BoolArgumentType.getBool(context, "keep_outside_links"))))))
                .then(literal("translate").requires(source -> source.hasPermissionLevel(OP_LEVEL))
                        .then(argument("from", BlockPosArgumentType.blockPos()).then(argument("to", BlockPosArgumentType.blockPos())
                                .then(argument("dx", IntegerArgumentType.integer()).then(argument("dy", IntegerArgumentType.integer())
                                        .then(argument("dz", IntegerArgumentType.integer()).executes(BoardCommands::translate)))))))
                .then(literal("template").requires(source -> source.hasPermissionLevel(OP_LEVEL))
                        .then(templateNode("loop", BoardBlueprint.Template.LOOP, 4))
                        .then(templateNode("line", BoardBlueprint.Template.LINE, 1)))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> templateNode(String name, BoardBlueprint.Template template, int min) {
        return CommandManager.literal(name).then(argument("count", IntegerArgumentType.integer(min, 128))
                .executes(context -> template(context, template, 3))
                .then(argument("spacing", IntegerArgumentType.integer(1, 16))
                        .executes(context -> template(context, template, IntegerArgumentType.getInteger(context, "spacing")))));
    }

    private static @Nullable ItemStack heldWrench(ServerPlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        return WrenchActions.isWrench(held) ? held : null;
    }

    private static int undo(ServerCommandSource source, boolean undo) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        return LinkHistory.undo(player, undo, heldWrench(player)) ? 1 : 0;
    }

    private static int check(ServerCommandSource source, @Nullable BlockPos pos) {
        ServerWorld world = source.getWorld();
        BlockPos here = BlockPos.ofFloored(source.getPosition());
        BlockPos center = pos != null ? pos : PartyControllerEntity.getActivePartyControllers().stream()
                .filter(controller -> controller.getWorld() == world && !controller.isRemoved())
                .map(PartyControllerEntity::getPos)
                .filter(controllerPos -> controllerPos.isWithinDistance(here, BoardValidator.RADIUS))
                .min(Comparator.comparingDouble(controllerPos -> controllerPos.getSquaredDistance(here)))
                .orElse(here);
        BoardValidator.Report report = BoardValidator.check(world, center);
        // Straight to the player: the report is what they asked for, even with sendCommandFeedback off
        if (source.getPlayer() != null) BoardValidator.send(source.getPlayer(), report);
        else for (Text line : BoardValidator.lines(report)) source.sendFeedback(() -> line, false);
        return report.ok() ? 1 : 0;
    }

    private static BlockBox box(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return BlockBox.create(BlockPosArgumentType.getLoadedBlockPos(context, "from"), BlockPosArgumentType.getLoadedBlockPos(context, "to"));
    }

    private static int copy(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        BlockBox box = box(context);
        Vec3i size = box.getDimensions();
        if ((long) (size.getX() + 1) * (size.getY() + 1) * (size.getZ() + 1) > BoardBlueprint.MAX_VOLUME) {
            context.getSource().sendError(Text.translatable("command.steveparty.board.too_big", BoardBlueprint.MAX_VOLUME));
            return 0;
        }
        BoardBlueprint.Clip clip = BoardBlueprint.copyFor(player, context.getSource().getWorld(), box);
        player.sendMessage(Text.translatable("command.steveparty.board.copied", clip.entries().size(), clip.boardSpaces()), false);
        return clip.entries().size();
    }

    private static int paste(CommandContext<ServerCommandSource> context, int degrees, boolean keepOutsideLinks) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        BoardBlueprint.Clip clip = BoardBlueprint.clipboard(player.getUuid());
        if (clip == null) {
            context.getSource().sendError(Text.translatable("command.steveparty.board.nothing_copied"));
            return 0;
        }
        BlockRotation rotation = switch (Math.floorMod(degrees, 360)) {
            case 90 -> BlockRotation.CLOCKWISE_90;
            case 180 -> BlockRotation.CLOCKWISE_180;
            case 270 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
        ServerWorld world = context.getSource().getWorld();
        int[] placed = {0};
        Text label = Text.translatable("command.steveparty.board.pasted", clip.entries().size(), clip.boardSpaces());
        WrenchActions.recorded(player, world, null, () -> {
            placed[0] = BoardBlueprint.paste(world, clip, player.getBlockPos(), rotation, keepOutsideLinks, player);
            WrenchActions.say(player, label);
        });
        player.sendMessage(label, false);
        return placed[0];
    }

    private static int translate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        BlockBox box = box(context);
        Vec3i offset = new Vec3i(IntegerArgumentType.getInteger(context, "dx"), IntegerArgumentType.getInteger(context, "dy"),
                IntegerArgumentType.getInteger(context, "dz"));
        int[] moved = {0};
        Runnable action = () -> moved[0] = BoardBlueprint.translate(source.getWorld(), box, offset, player);
        Text[] label = {null};
        if (player != null) {
            WrenchActions.recorded(player, source.getWorld(), null, () -> {
                action.run();
                label[0] = Text.translatable("command.steveparty.board.translated", moved[0]);
                WrenchActions.say(player, label[0]);
            });
        } else {
            action.run();
        }
        Text result = Text.translatable("command.steveparty.board.translated", moved[0]);
        source.sendFeedback(() -> result, true);
        return moved[0];
    }

    private static int template(CommandContext<ServerCommandSource> context, BoardBlueprint.Template template, int spacing) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int count = IntegerArgumentType.getInteger(context, "count");
        if (template == BoardBlueprint.Template.LOOP) count -= count % 2; // a rectangle has an even number of spaces
        ServerWorld world = context.getSource().getWorld();
        int finalCount = count;
        int[] placed = {0};
        Text label = Text.translatable("command.steveparty.board.template", finalCount);
        WrenchActions.recorded(player, world, null, () -> {
            placed[0] = BoardBlueprint.template(world, template, player.getBlockPos(), player.getHorizontalFacing(), finalCount, spacing, player).size();
            WrenchActions.say(player, label);
        });
        player.sendMessage(label, false);
        return placed[0];
    }
}
