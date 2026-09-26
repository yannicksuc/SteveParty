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
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("steveparty").then(literal("board")
                .then(literal("check")
                        .executes(context -> check(context.getSource(), null))
                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                .executes(context -> check(context.getSource(), BlockPosArgumentType.getLoadedBlockPos(context, "pos")))))));
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
        for (Text line : BoardValidator.lines(report)) source.sendFeedback(() -> line, false);
        return report.ok() ? 1 : 0;
    }
}
