package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.board.BoardValidator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Board tools as commands:
 * <ul>
 *     <li>{@code /steveparty board check [pos]}: checks the board around a position (default: the closest Party
 *     Controller within 100 blocks, else where the command runs), like a Wrench click on the Party Controller.</li>
 * </ul>
 */
public final class BoardCommands {
    private BoardCommands() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        // The undo history lives in memory: a new server (or world) starts empty
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> fr.lordfinn.steveparty.board.LinkHistory.clear());
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("steveparty").then(literal("board")
                .then(literal("undo").executes(context -> undo(context.getSource(), true)))
                .then(literal("redo").executes(context -> undo(context.getSource(), false)))
                .then(literal("check")
                        .executes(context -> check(context.getSource(), null))
                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                .executes(context -> check(context.getSource(), BlockPosArgumentType.getLoadedBlockPos(context, "pos")))))));
    }

    private static int undo(ServerCommandSource source, boolean undo) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.network.ServerPlayerEntity player = source.getPlayerOrThrow();
        net.minecraft.item.ItemStack held = player.getMainHandStack();
        return fr.lordfinn.steveparty.board.LinkHistory.undo(player, undo,
                fr.lordfinn.steveparty.board.WrenchActions.isWrench(held) ? held : null) ? 1 : 0;
    }

    private static int check(ServerCommandSource source, @Nullable BlockPos pos) {
        ServerWorld world = source.getWorld();
        BlockPos here = BlockPos.ofFloored(source.getPosition());
        BlockPos center = pos != null ? pos : PartyControllerEntity.getActivePartyControllers().stream()
                .filter(controller -> controller.getWorld() == world && !controller.isRemoved())
                .map(PartyControllerEntity::getPos)
                .filter(controllerPos -> controllerPos.isWithinDistance(here, BoardValidator.RADIUS))
                .min(java.util.Comparator.comparingDouble(controllerPos -> controllerPos.getSquaredDistance(here)))
                .orElse(here);
        BoardValidator.Report report = BoardValidator.check(world, center);
        // Straight to the player: the report is what they asked for, even with sendCommandFeedback off
        if (source.getPlayer() != null) BoardValidator.send(source.getPlayer(), report);
        else for (Text line : BoardValidator.lines(report)) source.sendFeedback(() -> line, false);
        return report.ok() ? 1 : 0;
    }
}
