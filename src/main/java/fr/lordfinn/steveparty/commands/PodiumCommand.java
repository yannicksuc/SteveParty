package fr.lordfinn.steveparty.commands;

import com.mojang.brigadier.CommandDispatcher;
import fr.lordfinn.steveparty.podium.PodiumGroup;
import fr.lordfinn.steveparty.podium.Podiums;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /steveparty podium reset} (operators, level 2): resets the group of the podium the nearest to where the
 * command is run (within {@link Podiums#COMMAND_RADIUS} blocks): its columns are emptied, the points of its goal pole
 * bases go back to 0. The party currencies are not touched.
 */
public final class PodiumCommand {
    private static final int OP_LEVEL = 2;

    private PodiumCommand() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("steveparty").then(literal("podium")
                .requires(source -> source.hasPermissionLevel(OP_LEVEL))
                .then(literal("reset").executes(context -> reset(context.getSource())))));
    }

    /** @return the number of columns of the group reset, 0 if no podium is near */
    public static int reset(ServerCommandSource source) {
        BlockPos podium = Podiums.nearestPodium(source.getWorld(), source.getPosition(), Podiums.COMMAND_RADIUS);
        if (podium == null) {
            source.sendError(Text.translatable("command.steveparty.podium.none", Podiums.COMMAND_RADIUS));
            return 0;
        }
        PodiumGroup group = PodiumGroup.of(source.getWorld(), podium);
        Podiums.reset(group);
        int columns = group.columns().size();
        source.sendFeedback(() -> Text.translatable("command.steveparty.podium.reset", columns, podium.getX(), podium.getY(), podium.getZ()), true);
        return columns;
    }
}
