package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The start of a mini-game round: everyone comes out of the pipes at once, then the players stand still (they can't
 * walk, jump, hit or use anything) while
 * <ol>
 *     <li>the chat tells the mini-game: its name, then its description sentence by sentence, each given the time to be
 *     read ({@link MiniGameText#readingTicks}). Each player says he is ready whenever he wants ({@link #vote}: the
 *     key, the chat's button, « Y » typed in the chat); once all of them are, the rest is told at once and the
 *     countdown starts;</li>
 *     <li>a discreet countdown ({@value #SECONDS} seconds, {@value #FAST_SECONDS} in a fast party): the numbers as a
 *     subtitle, the arena in full view;</li>
 *     <li>« Go! »: they are free. So nobody is ahead because he came out of a pipe first.</li>
 * </ol>
 * The modifiers are temporary (never saved): a player who disconnects or a server that stops loses them.
 */
public final class MiniGameFreeze {
    public static final int SECONDS = 5, FAST_SECONDS = 3;
    private static final Identifier MODIFIER = Steveparty.id("mini_game_freeze");
    private static final int FADE_IN = 0, STAY = 25, FADE_OUT = 5;
    /** Ticks before the chat speaks: the card of the mini-game is still fading out, everyone is still coming out. */
    private static final int LEAD_TICKS = 10;
    private static final int GOLD = 0xFFC52E;

    /**
     * How a round starts.
     *
     * @param inChat  its name and description are told in the chat (false: already told, a round started again)
     * @param paced   its description is told sentence by sentence until everyone is ready (false: all at once)
     * @param seconds the countdown
     */
    public record Pace(boolean inChat, boolean paced, int seconds) {
        /** The first start of a mini-game: told in the chat, sentence by sentence, then {@value #SECONDS} seconds. */
        public static final Pace FIRST = new Pace(true, true, SECONDS);
        /** A round started again: the countdown alone. */
        public static final Pace AGAIN = new Pace(false, false, SECONDS);

        /** The start of a fast party's mini-game: no reading time, {@value #FAST_SECONDS} seconds. */
        public Pace fast() {
            return new Pace(inChat, false, FAST_SECONDS);
        }
    }

    /** A start: those it freezes, those who see it, what the chat tells, the votes, and whether the round is still on. */
    private static final class Start {
        final Set<UUID> frozen, audience;
        final Text name;
        final List<String> sentences;
        final Pace pace;
        final BooleanSupplier stillOn;
        final @Nullable Runnable onGo;
        final int start;
        final Set<UUID> ready = new LinkedHashSet<>();
        boolean begun;
        int told, nextAt;
        /** The tick the countdown started, -1 while the chat is still telling the description. */
        int countFrom = -1;

        Start(Set<UUID> frozen, Set<UUID> audience, Text name, List<String> sentences, Pace pace, BooleanSupplier stillOn, @Nullable Runnable onGo, int start) {
            this.frozen = frozen;
            this.audience = audience;
            this.name = name;
            this.sentences = sentences;
            this.pace = pace;
            this.stillOn = stillOn;
            this.onGo = onGo;
            this.start = start;
        }

        boolean describing() {
            return countFrom < 0;
        }
    }

    private static final List<Start> RUNNING = new ArrayList<>();
    private static final Set<UUID> FROZEN = new HashSet<>();

    private MiniGameFreeze() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(MiniGameFreeze::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            for (Start start : RUNNING) {
                start.frozen.remove(handler.player.getUuid());
                start.ready.remove(handler.player.getUuid());
            }
            release(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            RUNNING.clear();
            FROZEN.clear();
        });
        // Frozen: no hit, no use (the server says no; the client may show the swing)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) -> isFrozen(player)
                ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
    }

    public static boolean isFrozen(PlayerEntity player) {
        return !player.getWorld().isClient() && FROZEN.contains(player.getUuid());
    }

    /**
     * The round starts: {@code players} are frozen until « Go! »; {@code audience} (the players too) is told the
     * mini-game in the chat and sees the countdown.
     *
     * @param name    the mini-game's name (empty: none)
     * @param page    its page (its description), null for none
     * @param stillOn false once the round is over: the players are freed at once
     */
    public static void start(MinecraftServer server, Collection<ServerPlayerEntity> players, Collection<ServerPlayerEntity> audience,
                             String name, @Nullable MiniGamePageData page, Pace pace, BooleanSupplier stillOn) {
        start(server, players, audience, name, page, pace, stillOn, null);
    }

    /** @param onGo run at « Go! » (null: nothing) */
    public static void start(MinecraftServer server, Collection<ServerPlayerEntity> players, Collection<ServerPlayerEntity> audience,
                             String name, @Nullable MiniGamePageData page, Pace pace, BooleanSupplier stillOn, @Nullable Runnable onGo) {
        Set<UUID> frozen = new LinkedHashSet<>(), watching = new LinkedHashSet<>();
        for (ServerPlayerEntity player : players) {
            // A new round's start takes him from the one before (a practice round started again)
            for (Start before : RUNNING) {
                before.frozen.remove(player.getUuid());
                before.ready.remove(player.getUuid());
            }
            frozen.add(player.getUuid());
            freeze(player);
        }
        for (ServerPlayerEntity player : audience) watching.add(player.getUuid());
        watching.addAll(frozen);
        Text title = name.isEmpty() ? Text.empty() : Text.literal(name).styled(style -> style.withColor(GOLD).withBold(true));
        List<String> sentences = page == null ? List.of() : MiniGameText.sentences(page.description());
        RUNNING.add(new Start(frozen, watching, title, sentences, pace, stillOn, onGo, server.getTicks() + LEAD_TICKS));
    }

    /** Kept for the callers of the countdown alone (no chat): see {@link #start(MinecraftServer, Collection, Collection, String, MiniGamePageData, Pace, BooleanSupplier)}. */
    public static void start(MinecraftServer server, Collection<ServerPlayerEntity> players, Collection<ServerPlayerEntity> audience,
                             String name, BooleanSupplier stillOn) {
        start(server, players, audience, name, null, Pace.AGAIN, stillOn);
    }

    // ------------------------------------------------------------------ the votes

    /** @return true if {@code player} stands still before a round whose description is being told: he may say he is ready. */
    public static boolean canVote(ServerPlayerEntity player) {
        return describingStartOf(player) != null;
    }

    /**
     * {@code player} says he is ready (or no longer is) while the description of his round is told; once every
     * connected player of it is, the rest is told at once and the countdown starts.
     *
     * @return false if he has no vote here
     */
    public static boolean vote(ServerPlayerEntity player) {
        Start start = describingStartOf(player);
        if (start == null) return false;
        if (!start.ready.remove(player.getUuid())) start.ready.add(player.getUuid());
        MinecraftServer server = player.getServer();
        if (server == null) return true;
        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.6f, start.ready.contains(player.getUuid()) ? 1.6f : 1f);
        if (everyoneReady(server, start)) skip(server, start);
        else sendVote(server, start);
        return true;
    }

    /** The players of the start {@code player} is frozen in whose description is still told, null if none. */
    private static @Nullable Start describingStartOf(ServerPlayerEntity player) {
        for (Start start : RUNNING) {
            if (start.frozen.contains(player.getUuid()) && start.begun && start.describing() && start.pace.paced()) return start;
        }
        return null;
    }

    private static boolean everyoneReady(MinecraftServer server, Start start) {
        boolean anyone = false;
        for (UUID uuid : start.frozen) {
            if (server.getPlayerManager().getPlayer(uuid) == null) continue;
            if (!start.ready.contains(uuid)) return false;
            anyone = true;
        }
        return anyone;
    }

    /** Everyone is ready: the rest of the description at once, then the countdown. */
    private static void skip(MinecraftServer server, Start start) {
        while (start.told < start.sentences.size()) tell(server, start, start.sentences.get(start.told++));
        startCountdown(server, start);
    }

    /** The countdown starts: its first number at once. */
    private static void startCountdown(MinecraftServer server, Start start) {
        start.countFrom = server.getTicks();
        sendVoteHidden(server, start);
        show(server, start, start.pace.seconds());
    }

    /** The chip of the vote ({@link MiniGamePagePayloads.StartVote}) to everyone who sees the start. */
    private static void sendVote(MinecraftServer server, Start start) {
        int total = 0;
        for (UUID uuid : start.frozen) if (server.getPlayerManager().getPlayer(uuid) != null) total++;
        int ready = (int) start.ready.stream().filter(uuid -> server.getPlayerManager().getPlayer(uuid) != null).count();
        for (UUID uuid : start.audience) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null || !ServerPlayNetworking.canSend(player, MiniGamePagePayloads.StartVote.ID)) continue;
            ServerPlayNetworking.send(player, new MiniGamePagePayloads.StartVote(true, ready, total, start.frozen.contains(uuid), start.ready.contains(uuid)));
        }
    }

    private static void sendVoteHidden(MinecraftServer server, Start start) {
        if (!start.pace.paced() || start.sentences.isEmpty()) return;
        for (UUID uuid : start.audience) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null && ServerPlayNetworking.canSend(player, MiniGamePagePayloads.StartVote.ID))
                ServerPlayNetworking.send(player, MiniGamePagePayloads.StartVote.HIDDEN);
        }
    }

    // ------------------------------------------------------------------ the sequence

    private static void tick(MinecraftServer server) {
        if (RUNNING.isEmpty()) return;
        int now = server.getTicks();
        for (Start start : new ArrayList<>(RUNNING)) {
            if (!start.stillOn.getAsBoolean()) {
                end(server, start, false);
                continue;
            }
            if (now < start.start) continue;
            if (!start.begun) begin(server, start, now);
            if (start.describing()) {
                if (now < start.nextAt) continue;
                if (start.told < start.sentences.size()) {
                    String sentence = start.sentences.get(start.told++);
                    tell(server, start, sentence);
                    start.nextAt = now + MiniGameText.readingTicks(sentence);
                    continue;
                }
                startCountdown(server, start);
            }
            int elapsed = now - start.countFrom;
            if (elapsed <= 0 || elapsed % 20 != 0) continue;
            int left = start.pace.seconds() - elapsed / 20;
            if (left > 0) show(server, start, left);
            else end(server, start, true);
        }
    }

    /** The chat tells the mini-game: its name, how to skip its description, the description (at once when not paced). */
    private static void begin(MinecraftServer server, Start start, int now) {
        start.begun = true;
        start.nextAt = now;
        boolean told = start.pace.inChat() && !start.name.getString().isEmpty();
        if (!told) {
            start.told = start.sentences.size();
            return;
        }
        boolean paced = start.pace.paced() && !start.sentences.isEmpty();
        for (UUID uuid : start.audience) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            player.sendMessage(Text.translatable("message.steveparty.minigame.title", start.name), false);
        }
        if (paced) {
            for (UUID uuid : start.frozen) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) player.sendMessage(MiniGameReady.skipLine(), false);
            }
            sendVote(server, start);
            return;
        }
        while (start.told < start.sentences.size()) tell(server, start, start.sentences.get(start.told++));
    }

    /** One sentence of the description, in grey, to everyone who sees the start. */
    private static void tell(MinecraftServer server, Start start, String sentence) {
        Text line = MiniGameText.parse(sentence, Style.EMPTY.withColor(Formatting.GRAY));
        for (UUID uuid : start.audience) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) player.sendMessage(line, false);
        }
    }

    /** {@code left} as a subtitle (the arena stays in full view), and a tick of sound. */
    private static void show(MinecraftServer server, Start start, int left) {
        Text number = Text.literal(Integer.toString(left)).styled(style -> style.withColor(GOLD).withBold(true));
        for (UUID uuid : start.audience) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            subtitle(player, number, STAY);
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 1f, 1f);
        }
    }

    private static void subtitle(ServerPlayerEntity player, Text text, int stay) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(FADE_IN, stay, FADE_OUT));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(text));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));
    }

    /** « Go! » (when it ran to its end) and everyone freed. */
    private static void end(MinecraftServer server, Start start, boolean go) {
        RUNNING.remove(start);
        if (start.describing()) sendVoteHidden(server, start);
        Text goText = Text.translatableWithFallback("message.steveparty.minigame.go", "Go!").styled(style -> style.withColor(0x55FF55).withBold(true));
        for (UUID uuid : start.audience) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            if (start.frozen.contains(uuid)) release(player);
            if (!go) continue;
            subtitle(player, goText, 20);
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1f, 1.4f);
        }
        if (go && start.onGo != null) start.onGo.run();
    }

    private static void freeze(ServerPlayerEntity player) {
        FROZEN.add(player.getUuid());
        for (RegistryEntry<EntityAttribute> attribute : List.of(EntityAttributes.GENERIC_MOVEMENT_SPEED, EntityAttributes.GENERIC_JUMP_STRENGTH)) {
            EntityAttributeInstance instance = player.getAttributeInstance(attribute);
            if (instance != null && instance.getModifier(MODIFIER) == null) {
                instance.addTemporaryModifier(new EntityAttributeModifier(MODIFIER, -1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    /** {@code player} may move again (nothing if he was not frozen). */
    public static void release(ServerPlayerEntity player) {
        FROZEN.remove(player.getUuid());
        for (RegistryEntry<EntityAttribute> attribute : List.of(EntityAttributes.GENERIC_MOVEMENT_SPEED, EntityAttributes.GENERIC_JUMP_STRENGTH)) {
            EntityAttributeInstance instance = player.getAttributeInstance(attribute);
            if (instance != null) instance.removeModifier(MODIFIER);
        }
    }
}
