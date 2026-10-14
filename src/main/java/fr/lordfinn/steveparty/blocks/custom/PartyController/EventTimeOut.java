package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGameReturns;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The time out of an event (an Event card of the program, an Event space): the party pauses, the map maker's redstone
 * and commands take over (the "event" party bells ring), the players come out of the event's pipes, if any, one pipe
 * after the other. It ends at the first of: its hourglass running out ({@link #DURATIONS}), a waiting "event" bell
 * receiving redstone, a Step Controller's pulse. The players then come back where they stood and the party goes on.
 * See fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EventPartyStep.
 */
public final class EventTimeOut {
    /** The hourglass choices, in seconds: 0 is none (only a bell or a Step Controller ends the event). */
    public static final int[] DURATIONS = {0, 30, 60, 120, 300};
    /** The pipes an event may send the players to. */
    public static final int MAX_PIPES = 8;

    private EventTimeOut() {
    }

    /** « 1:30 », « 0:45 ». */
    public static String clock(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** The hourglass choice {@code seconds} as said in a menu or a tooltip: « 1:00 », or « none ». */
    public static Text durationText(int seconds) {
        return seconds <= 0 ? Text.translatable("gui.steveparty.event.duration.none") : Text.literal(clock(seconds));
    }

    /** The choice after {@code seconds} (back to none after the last). */
    public static int nextDuration(int seconds) {
        for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == seconds) return DURATIONS[(i + 1) % DURATIONS.length];
        return DURATIONS[0];
    }

    // ---------------------------------------------------------------- the players

    /** The players of the party, each once, in the play order. */
    public static List<ServerPlayerEntity> players(PartyControllerEntity controller, ServerWorld world) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (PlayerEntity player : controller.getPartyData().getOwners(world))
            if (player instanceof ServerPlayerEntity serverPlayer && !players.contains(serverPlayer)) players.add(serverPlayer);
        return players;
    }

    /**
     * The players come out of the {@code pipes} in turn (the first player out of the first pipe...).
     *
     * @return where each player sent stood before (to come back there at the end)
     */
    public static Map<UUID, MiniGameReturns.Return> sendOut(PartyControllerEntity controller, ServerWorld world, List<EndPipe> pipes) {
        Map<UUID, MiniGameReturns.Return> returns = new LinkedHashMap<>();
        if (pipes.isEmpty()) return returns;
        int next = 0;
        for (ServerPlayerEntity player : players(controller, world)) {
            EndPipe pipe = pipes.get(next++ % pipes.size());
            MiniGameReturns.Return back = MiniGameReturns.Return.of(player);
            MiniGamePipeLink link = new MiniGamePipeLink(pipe.mouth(), pipe.opening(), MiniGamePipeRole.PLAYERS);
            if (MiniGamePipes.emerge(world.getServer(), link, player)) returns.put(player.getUuid(), back);
        }
        return returns;
    }

    /** The players sent out come back where they stood (one away from the server: when he comes). */
    public static void bringBack(ServerWorld world, Map<UUID, MiniGameReturns.Return> returns) {
        returns.forEach((player, back) -> MiniGameReturns.bringBack(world.getServer(), player, back));
        returns.clear();
    }

    // ---------------------------------------------------------------- the hourglass

    /** The hourglass bar of the event, shown to its players while it runs. */
    public static ServerBossBar hourglass() {
        ServerBossBar bar = new ServerBossBar(Text.empty(), BossBar.Color.YELLOW, BossBar.Style.NOTCHED_10);
        bar.setPercent(1f);
        return bar;
    }

    /** The bar says {@code left} of {@code total} ticks left, to the players of the party. */
    public static void showHourglass(ServerBossBar bar, PartyControllerEntity controller, ServerWorld world, long left, int total) {
        int seconds = (int) Math.max(0, (left + 19) / 20);
        bar.setName(Text.translatable("hud.steveparty.event.hourglass", clock(seconds)));
        bar.setPercent(total <= 0 ? 0f : Math.clamp(left / (float) total, 0f, 1f));
        List<ServerPlayerEntity> players = players(controller, world);
        for (ServerPlayerEntity player : List.copyOf(bar.getPlayers())) if (!players.contains(player)) bar.removePlayer(player);
        for (ServerPlayerEntity player : players) bar.addPlayer(player);
    }

    // ---------------------------------------------------------------- the Event card's pipes

    /** The pipes an Event card is linked to (a right click on each). */
    public static List<EndPipe> pipes(ItemStack card) {
        return card.getOrDefault(ModComponents.EVENT_PIPES, List.of());
    }

    /**
     * A right click on a pipe with an Event card: that pipe's mouth (the side looked at) is added to the card's pipes,
     * or removed if it already was.
     */
    public static void clickPipe(ServerPlayerEntity player, Hand hand, ServerWorld world, BlockPos pos) {
        ItemStack card = player.getStackInHand(hand);
        Direction opening = MiniGamePipes.mouthOf(world.getBlockState(pos), pos, player.getEyePos());
        GlobalPos mouth = GlobalPos.create(world.getRegistryKey(), pos.toImmutable());
        List<EndPipe> pipes = new ArrayList<>(pipes(card));
        if (pipes.removeIf(pipe -> pipe.mouth().equals(mouth))) {
            setPipes(card, pipes);
            player.sendMessage(Text.translatable("message.steveparty.mini_game_page.pipe.unlinked", pos.getX(), pos.getY(), pos.getZ()), true);
            ModSounds.playCancel(world, pos);
            return;
        }
        if (opening == null) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_page.pipe.no_mouth").formatted(Formatting.RED), true);
            return;
        }
        if (pipes.size() >= MAX_PIPES) {
            player.sendMessage(Text.translatable("message.steveparty.event.pipes_full", MAX_PIPES).formatted(Formatting.RED), true);
            return;
        }
        pipes.add(new EndPipe(mouth, opening));
        setPipes(card, pipes);
        player.sendMessage(Text.translatable("message.steveparty.event.pipe_linked", pos.getX(), pos.getY(), pos.getZ()), true);
        ModSounds.playSelect(world, pos);
    }

    public static void setPipes(ItemStack stack, @Nullable List<EndPipe> pipes) {
        if (pipes == null || pipes.isEmpty()) stack.remove(ModComponents.EVENT_PIPES);
        else stack.set(ModComponents.EVENT_PIPES, List.copyOf(pipes));
    }
}
