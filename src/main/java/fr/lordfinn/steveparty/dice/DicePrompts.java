package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.custom.DicePromptPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * The small pickers of the dice: a player is asked to choose one option of a list (the face of a Choice die, one of
 * the results of a Lucky die, keep or reroll, the token to swap with). Server-authoritative: the client only shows
 * the options and sends back an index; a player has one prompt at a time, and a prompt left unanswered answers itself
 * (its default option) once its time is over, so nothing ever waits for an absent player.
 */
public final class DicePrompts {
    /** Time given to answer a prompt: 15 seconds. */
    public static final int TIMEOUT_TICKS = 300;

    /** How the options are laid out: a grid of icons (faces) or a list of rows (icon and label). */
    public enum Layout { GRID, LIST }

    /** An option: its icon (may be empty) and its label. */
    public record Option(ItemStack icon, Text label) {
    }

    /** A prompt waiting for its answer. */
    public static final class Prompt {
        private final int id;
        private final @Nullable UUID player;
        private final Text title;
        private final Layout layout;
        private final List<Option> options;
        private final int timeoutTicks;
        private final int defaultIndex;
        private final IntConsumer onAnswer;
        private final UUID taskId = UUID.randomUUID();
        private boolean done;

        private Prompt(int id, @Nullable UUID player, Text title, Layout layout, List<Option> options, int timeoutTicks,
                       int defaultIndex, IntConsumer onAnswer) {
            this.id = id;
            this.player = player;
            this.title = title;
            this.layout = layout;
            this.options = List.copyOf(options);
            this.timeoutTicks = timeoutTicks;
            this.defaultIndex = defaultIndex;
            this.onAnswer = onAnswer;
        }

        public int id() {
            return id;
        }

        public @Nullable UUID player() {
            return player;
        }

        public Text title() {
            return title;
        }

        public List<Option> options() {
            return options;
        }

        public int defaultIndex() {
            return defaultIndex;
        }

        public boolean isDone() {
            return done;
        }
    }

    private static final Map<UUID, Prompt> PENDING = new HashMap<>();
    private static int nextId = 1;

    static {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    private DicePrompts() {
    }

    /**
     * Asks {@code player} to choose one of {@code options}. {@code onAnswer} is called exactly once, with the index
     * chosen, or with {@code defaultIndex} once {@code timeoutTicks} are over (also right away if the player gets
     * another prompt). Without a player (offline roller), only the time-out answers.
     *
     * @return the prompt, or null if there is nothing to choose from ({@code onAnswer} is not called)
     */
    public static @Nullable Prompt ask(@Nullable ServerPlayerEntity player, Text title, Layout layout, List<Option> options,
                                       int timeoutTicks, int defaultIndex, IntConsumer onAnswer) {
        if (options.isEmpty()) return null;
        int fallback = Math.clamp(defaultIndex, 0, options.size() - 1);
        Prompt prompt = new Prompt(nextId++, player == null ? null : player.getUuid(), title, layout, options,
                timeoutTicks, fallback, onAnswer);
        if (player != null) {
            Prompt previous = PENDING.get(player.getUuid());
            if (previous != null) resolve(previous, previous.defaultIndex);
            PENDING.put(player.getUuid(), prompt);
            send(player, prompt);
        }
        Steveparty.SCHEDULER.schedule(prompt.taskId, Math.max(1, timeoutTicks), () -> resolve(prompt, prompt.defaultIndex));
        return prompt;
    }

    /** The prompt the player has to answer, null if none. */
    public static @Nullable Prompt pending(ServerPlayerEntity player) {
        return PENDING.get(player.getUuid());
    }

    /** Shows the player their pending prompt again (they closed it). */
    public static void resend(ServerPlayerEntity player) {
        Prompt prompt = PENDING.get(player.getUuid());
        if (prompt != null) send(player, prompt);
    }

    /**
     * The player's answer (their client's packet, or a test).
     *
     * @return false if it is not the answer of their pending prompt (nothing done)
     */
    public static boolean answer(ServerPlayerEntity player, int promptId, int index) {
        Prompt prompt = PENDING.get(player.getUuid());
        if (prompt == null || prompt.id != promptId || index < 0 || index >= prompt.options.size()) return false;
        resolve(prompt, index);
        return true;
    }

    /** Drops a prompt without answering it (what asked it is gone). */
    public static void cancel(@Nullable Prompt prompt) {
        if (prompt == null || prompt.done) return;
        prompt.done = true;
        Steveparty.SCHEDULER.cancel(prompt.taskId);
        close(prompt);
    }

    private static void resolve(Prompt prompt, int index) {
        if (prompt.done) return;
        prompt.done = true;
        Steveparty.SCHEDULER.cancel(prompt.taskId);
        close(prompt);
        prompt.onAnswer.accept(index);
    }

    private static void close(Prompt prompt) {
        if (prompt.player == null || PENDING.get(prompt.player) != prompt) return;
        PENDING.remove(prompt.player);
        ServerPlayerEntity player = Steveparty.SERVER == null ? null
                : Steveparty.SERVER.getPlayerManager().getPlayer(prompt.player);
        if (player != null && ServerPlayNetworking.canSend(player, DicePromptPayload.ID))
            ServerPlayNetworking.send(player, DicePromptPayload.close(prompt.id));
    }

    private static void send(ServerPlayerEntity player, Prompt prompt) {
        if (!ServerPlayNetworking.canSend(player, DicePromptPayload.ID)) return;
        ServerPlayNetworking.send(player, new DicePromptPayload(prompt.id, prompt.title, prompt.layout == Layout.LIST,
                prompt.options, prompt.timeoutTicks, prompt.defaultIndex));
    }
}
