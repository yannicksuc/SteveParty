package fr.lordfinn.steveparty.blocks.custom.PartyController;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A player leaving the parties he takes part in ({@code /steveparty leave}, the chat's « [Leave the party] », the
 * party HUD layout screen), or made to leave ({@code /steveparty kick}): see {@link PartyControllerEntity#removePlayer}.
 * It works whatever state the party is in: a controller that is not loaded is loaded for it (found through
 * {@link PartyMemberships}), one that is gone is forgotten, and the player's HUD is cleared in any case.
 */
public final class PartyLeave {
    public static final String COMMAND = "/steveparty leave";

    private PartyLeave() {
    }

    /**
     * {@code player} leaves every party he follows or plays a token in.
     *
     * @return where the parties he left are (empty: he was in none; his HUD is cleared all the same)
     */
    public static List<GlobalPos> leave(MinecraftServer server, UUID player) {
        List<GlobalPos> left = new ArrayList<>();
        for (PartyControllerEntity controller : PartyControllers.all()) {
            if (!controller.isRemoved() && controller.removePlayer(player)) left.add(controller.getGlobalPos());
        }
        PartyMemberships memberships = PartyMemberships.get(server);
        GlobalPos remembered = memberships.of(player);
        if (remembered != null && !left.contains(remembered)) {
            PartyControllerEntity controller = loadController(server, remembered);
            if (controller != null && controller.removePlayer(player)) left.add(remembered);
        }
        memberships.forget(player);
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
        if (online != null) PartyAudience.send(online, new PartyData());
        return left;
    }

    /** The controller at {@code at}, its chunk loaded if it was not; null if it is gone. */
    private static @Nullable PartyControllerEntity loadController(MinecraftServer server, GlobalPos at) {
        ServerWorld world = server.getWorld(at.dimension());
        if (world == null) return null;
        BlockPos pos = at.pos();
        ChunkPos chunk = new ChunkPos(pos);
        world.getChunk(chunk.x, chunk.z);
        return world.getBlockEntity(pos) instanceof PartyControllerEntity controller && !controller.isRemoved() ? controller : null;
    }

    /**
     * The player joined: a party he is remembered to follow that is no longer there (its controller loaded but gone,
     * its party over, or not followed by him any more) is forgotten, and his HUD is cleared unless a running party
     * sent it.
     *
     * @param following a loaded running party sent him its HUD
     */
    static void onPlayerJoin(ServerPlayerEntity player, boolean following) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        PartyMemberships memberships = PartyMemberships.get(server);
        GlobalPos remembered = memberships.of(player.getUuid());
        if (remembered != null) {
            ServerWorld world = server.getWorld(remembered.dimension());
            BlockPos pos = remembered.pos();
            boolean loaded = world != null && world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
            boolean stillThere = loaded && world.getBlockEntity(pos) instanceof PartyControllerEntity controller
                    && controller.getPartyData().isStarted() && controller.getInterestedPlayers().contains(player.getUuid());
            if (world == null || (loaded && !stillThere)) memberships.forget(player.getUuid());
        }
        if (!following) PartyAudience.send(player, new PartyData());
    }

    /** One chat line for {@code player}: which party he is in (where), and the button to leave it. */
    public static void tellHowToLeave(ServerPlayerEntity player, PartyControllerEntity controller) {
        player.sendMessage(Text.translatableWithFallback("message.steveparty.party_joined", "Party running: Party Controller at %s.",
                        where(player, controller.getGlobalPos())).formatted(Formatting.GRAY)
                .append("\n").append(leaveButton()), false);
    }

    /** « [Leave the party] », running {@link #COMMAND}. */
    public static MutableText leaveButton() {
        return Text.translatableWithFallback("message.steveparty.leave_button", "[Leave the party]")
                .styled(style -> style.withColor(Formatting.RED).withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, COMMAND))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatableWithFallback(
                                "message.steveparty.leave_hover", "Your pawn goes back to start, you keep your items"))));
    }

    /** « x, y, z » (and the dimension when it is not the player's). */
    public static Text where(@Nullable ServerPlayerEntity player, GlobalPos at) {
        BlockPos pos = at.pos();
        Text coords = Text.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).formatted(Formatting.WHITE);
        if (player == null || player.getWorld().getRegistryKey() == at.dimension()) return coords;
        String dimension = switch (at.dimension().getValue().getPath()) {
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            case "overworld" -> "Overworld";
            default -> at.dimension().getValue().getPath();
        };
        return Text.empty().append(coords).append(Text.literal(" (" + dimension + ")").formatted(Formatting.GRAY));
    }
}
