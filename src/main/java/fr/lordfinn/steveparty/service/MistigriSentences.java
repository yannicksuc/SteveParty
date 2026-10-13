package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrencyText;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.dice.CursedRolls;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriDieEntity;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity.Action;
import fr.lordfinn.steveparty.hud.OutcomeRoulette;
import fr.lordfinn.steveparty.components.MistigriOutcomes;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Kind;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Outcome;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Target;
import fr.lordfinn.steveparty.payloads.custom.OutcomeRoulettePayload;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.lordfinn.steveparty.service.BoardSequences.yawToward;

/**
 * What a Mistigri space does (see MistigriTileBehavior): the Mistigri leaps onto the space beside the token, rolls onto
 * his back juggling his loaded die between his fore paws, and the die passes one of the cartridge's sentences
 * ({@link MistigriOutcomes}: some picked for this landing, the one that falls drawn among them by their weights). It is
 * drawn first, then the whole party watches it come: the sentences picked are listed on an
 * {@link OutcomeRoulettes outcome roulette}, with their chances, floating just over him; the light runs down the list
 * slower and slower while he juggles, and stops on the drawn one as he tosses the die and it lands in front of him.
 * Only then does it happen ({@link #apply}). He reacts (a hiss, or a yawn for a kind one) and leaps away in a puff of
 * smoke.
 * <p>
 * A move back is walked once he is gone (the token's turn goes on until it lands: AdvanceBackMoves); every other
 * sentence ends the turn. Coins and stars are only moved, never made: what he takes goes into the party's bank, what he
 * gives comes out of it, a redistribution passes them from player to player. He and his die are board actors:
 * invulnerable, never saved, always removed at the end ({@link BoardActors}), whatever ends it (party over, server
 * stopping). Server thread only.
 */
public final class MistigriSentences {
    /**
     * His leap in; the roulette of the sentences while he juggles his die ({@link OutcomeRoulette}); the sentence;
     * his leaving.
     */
    public enum Phase { LEAP_IN, ROLL, SENTENCE, LEAVE }

    public enum Start { STARTED, NO_PLAYER, NO_SENTENCE }

    /**
     * Timing (ticks). The roll lasts the whole roulette: he rolls onto his back as the light starts running, juggles his
     * die between his fore paws while it runs, and tosses it to land in front of him when the light stops on the
     * sentence.
     */
    public static final int LEAP_TICKS = 20, ROLL_TICKS = OutcomeRoulette.TOTAL_TICKS,
            DIE_LANDS_AT = OutcomeRoulette.REVEAL_TICKS + OutcomeRoulette.SPIN_TICKS, SENTENCE_TICKS = 60, LEAVE_TICKS = 20;
    /**
     * In the roll (ticks): he rolls onto his back (his roll_on_back act), his die appears between his paws, he tosses it
     * (juggle_toss: the paws shove it after {@link #TOSS_SHOVE}) and it flies until it lands.
     */
    private static final int ROLL_OVER_AT = OutcomeRoulette.REVEAL_TICKS - 12, DIE_HELD_AT = OutcomeRoulette.REVEAL_TICKS - 2,
            TOSS_AT = DIE_LANDS_AT - 14, TOSS_SHOVE = 5;
    /** Juggled: one bounce off a fore paw every this many ticks (his on_back_juggle taps, left then right). */
    private static final int BOUNCE_TICKS = 10;
    /**
     * Where his die is juggled, from his seat (blocks): this far ahead, this high (between his fore paws, over his
     * chest); how high its bounces go; its toss's arc over the straight line to where it lands (its top: ~0.7 over
     * where he juggled it, under the panel).
     */
    public static final double DIE_HELD_AHEAD = 0.45, DIE_HELD_UP = 1.1, BOUNCE = 0.22, TOSS_UP = 1.2;
    /** The roulette's panel: this high over his seat (blocks), just over him on his back and his die's toss. */
    public static final double PANEL_ABOVE = 2.6;
    /** The whole show, at most (for the tests). */
    public static final int WHOLE = LEAP_TICKS + ROLL_TICKS + SENTENCE_TICKS + LEAVE_TICKS;
    /** His leap starts this far from the space (blocks, to the token's side). */
    private static final double APPEAR_SIDE = 4.5, SEAT_SIDE = 1.5, DIE_AHEAD = 1.9;

    private static final BoardSequences<Show> RUNNING = new BoardSequences<>();

    private MistigriSentences() {
    }

    public static void initialize() {
        RUNNING.stopWithServer(show -> show.finish(false));
    }

    public static boolean isRunning(MobEntity token) {
        return RUNNING.isRunning(token);
    }

    public static @Nullable Phase phase(MobEntity token) {
        Show show = RUNNING.get(token);
        return show == null ? null : show.phase;
    }

    public static @Nullable MistigriEntity actor(MobEntity token) {
        Show show = RUNNING.get(token);
        return show == null ? null : show.actor;
    }

    public static @Nullable MistigriDieEntity die(MobEntity token) {
        Show show = RUNNING.get(token);
        return show == null ? null : show.die;
    }

    /** The sentence drawn for {@code token}'s show. */
    public static @Nullable Outcome outcome(MobEntity token) {
        Show show = RUNNING.get(token);
        return show == null ? null : show.outcome;
    }

    /** The roulette of {@code token}'s show, null before it starts (or without a show). */
    public static @Nullable OutcomeRoulettes.Roulette roulette(MobEntity token) {
        Show show = RUNNING.get(token);
        return show == null ? null : show.roulette;
    }

    /** A sentence's line on the roulette: its icon, text, tone and chance (-1: unsaid). */
    static OutcomeRoulettePayload.Line line(Outcome outcome, int chance, PartyControllerEntity party, @Nullable Text lander) {
        ItemStack coin = party.getCurrency(PartyCurrency.COIN), star = party.getCurrency(PartyCurrency.STAR);
        return new OutcomeRoulettePayload.Line(outcome.kind().icon(coin, star), outcome.text(lander, coin, star),
                switch (outcome.kind().tone()) {
                    case 1 -> OutcomeRoulettePayload.Tone.GOOD;
                    case 0 -> OutcomeRoulettePayload.Tone.NEUTRAL;
                    default -> OutcomeRoulettePayload.Tone.BAD;
                }, chance);
    }

    /**
     * {@code token} stopped on the Mistigri space {@code tile} of {@code party}: the show starts. The roulette lists the
     * sentences of {@code cartridge} picked for this landing, and the one that falls is drawn among them by their weights
     * ({@code forced} instead, for the tests: listed too if it was not picked). {@code onDone} runs when it is over,
     * unless the token then moves back (its landing goes on with the turn). Nothing happens without the token's player
     * online, or when no sentence may fall: {@code onDone} is not called then.
     */
    public static Start start(ServerWorld world, BlockPos tile, MobEntity token, PartyControllerEntity party,
                              ItemStack cartridge, @Nullable Outcome forced, Runnable onDone) {
        if (RUNNING.isRunning(token)) return Start.STARTED;
        ServerPlayerEntity player = BoardSequences.tokenPlayer(world, token);
        if (player == null) {
            tell(party, null, Text.translatable("message.steveparty.mistigri_space.no_player").formatted(Formatting.GRAY));
            return Start.NO_PLAYER;
        }
        MistigriOutcomes outcomes = MistigriOutcomes.of(cartridge);
        List<Integer> picked = outcomes.pick(world.getRandom());
        int drawn = forced != null ? -1 : outcomes.draw(picked, world.getRandom());
        if (forced == null && drawn < 0) {
            tell(party, player, Text.translatable("message.steveparty.mistigri_space.no_sentence").formatted(Formatting.GRAY));
            return Start.NO_SENTENCE;
        }
        List<OutcomeRoulettePayload.Line> lines = new ArrayList<>();
        int result = -1;
        for (int row : picked) {
            Outcome each = outcomes.get(row);
            if (row == drawn || (forced != null && result < 0 && each.equals(forced))) result = lines.size();
            lines.add(line(each, outcomes.chanceAmong(picked, row), party, player.getName()));
        }
        if (result < 0) {
            result = lines.size();
            lines.add(line(forced, -1, party, player.getName()));
        }
        Outcome outcome = forced != null ? forced : outcomes.get(drawn);
        Show show = new Show(world, tile.toImmutable(), token, player.getUuid(), party, outcome, onDone);
        show.lines = lines;
        show.result = result;
        if (!show.spawn()) return Start.NO_SENTENCE;
        RUNNING.run(show, show::tick);
        return Start.STARTED;
    }

    // ------------------------------------------------------------------ who it falls on, what it does

    /** The party's players online, in play order. */
    public static List<ServerPlayerEntity> players(ServerWorld world, PartyControllerEntity party) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (UUID id : party.getPlayersInOrder()) {
            ServerPlayerEntity each = world.getServer().getPlayerManager().getPlayer(id);
            if (each != null && !each.isDisconnected()) players.add(each);
        }
        return players;
    }

    /**
     * The players {@code target} aims at now, among the party's players online ({@code landed}: the one who landed,
     * null if gone): ranks, coins and stars as they hold them now, every tied one for a rank or a most.
     */
    public static List<ServerPlayerEntity> targets(ServerWorld world, PartyControllerEntity party,
                                                   @Nullable ServerPlayerEntity landed, Target target, Random random) {
        List<ServerPlayerEntity> players = players(world, party);
        if (landed != null && !players.contains(landed)) players.addFirst(landed);
        ItemStack coin = party.getCurrency(PartyCurrency.COIN), star = party.getCurrency(PartyCurrency.STAR);
        Comparator<ServerPlayerEntity> coins = Comparator.comparingInt(p -> InventoryUtils.count(p.getInventory(), coin));
        Comparator<ServerPlayerEntity> rank = Comparator.<ServerPlayerEntity>comparingInt(p -> InventoryUtils.count(p.getInventory(), star))
                .thenComparing(coins);
        return switch (target) {
            case LANDED -> landed == null ? List.of() : List.of(landed);
            case ALL -> players;
            case OTHERS -> players.stream().filter(p -> p != landed).toList();
            case FIRST -> extremes(players, rank, true);
            case LAST -> extremes(players, rank, false);
            case RICHEST -> extremes(players, coins, true);
            case POOREST -> extremes(players, coins, false);
            case RANDOM -> players.isEmpty() ? List.of() : List.of(players.get(random.nextInt(players.size())));
        };
    }

    /** Every one of {@code players} at the top ({@code most}) or at the bottom of {@code order}. */
    private static List<ServerPlayerEntity> extremes(List<ServerPlayerEntity> players, Comparator<ServerPlayerEntity> order, boolean most) {
        if (players.isEmpty()) return List.of();
        ServerPlayerEntity edge = most ? Collections.max(players, order) : Collections.min(players, order);
        return players.stream().filter(p -> order.compare(p, edge) == 0).toList();
    }

    /** What a sentence moves: who, which currency, how many (taken: below 0). */
    public interface Moves {
        Moves NONE = (player, template, count) -> {
        };

        void moved(ServerPlayerEntity player, ItemStack template, int count);
    }

    /**
     * Passes {@code outcome} now on the players it aims at (a move back is the show's: not here). Coins and stars are
     * only ever moved: taken into the party's bank, given out of it (as many as it holds), or passed from player to
     * player through a redistribution; never made from nothing, never more than a player holds.
     *
     * @return what it did, for the chat (null: nothing to add)
     */
    public static @Nullable MutableText apply(ServerWorld world, PartyControllerEntity party, @Nullable ServerPlayerEntity landed,
                                              Outcome outcome, Random random, Moves moves) {
        if (outcome.kind() == Kind.MOVE_BACK) return null;
        List<ServerPlayerEntity> aimed = targets(world, party, landed, outcome.target(), random);
        ItemStack coin = party.getCurrency(PartyCurrency.COIN);
        ItemStack currency = switch (outcome.kind()) {
            case LOSE_STARS, SHARE_STARS -> party.getCurrency(PartyCurrency.STAR);
            default -> coin;
        };
        Map<ServerPlayerEntity, Integer> deltas = new LinkedHashMap<>();
        switch (outcome.kind()) {
            case LOSE_COINS, LOSE_STARS -> {
                for (ServerPlayerEntity each : aimed) {
                    int held = InventoryUtils.count(each.getInventory(), currency);
                    int taken = InventoryUtils.take(each.getInventory(), currency, outcome.count(held));
                    PartyResources.deposit(party, currency.copyWithCount(taken)); // into the party's bank, never lost
                    deltas.merge(each, -taken, Integer::sum);
                }
            }
            case SHARE_COINS, SHARE_STARS -> {
                int pot = 0;
                for (ServerPlayerEntity each : aimed) {
                    int held = InventoryUtils.count(each.getInventory(), currency);
                    int taken = InventoryUtils.take(each.getInventory(), currency, outcome.count(held));
                    pot += taken;
                    deltas.merge(each, -taken, Integer::sum);
                }
                // Dealt out to those who put nothing in (everyone when all did), the poorest first, as evenly as it goes
                List<ServerPlayerEntity> takers = new ArrayList<>(players(world, party));
                takers.removeAll(aimed);
                if (takers.isEmpty()) takers.addAll(aimed);
                takers.sort(Comparator.comparingInt(p -> InventoryUtils.count(p.getInventory(), currency)));
                for (int k = 0; k < takers.size() && pot > 0; k++) {
                    int left = takers.size() - k;
                    int share = pot / left + (pot % left > 0 ? 1 : 0);
                    InventoryUtils.giveOrDrop(takers.get(k), currency, share);
                    deltas.merge(takers.get(k), share, Integer::sum);
                    pot -= share;
                }
            }
            case PITY -> {
                for (ServerPlayerEntity each : aimed) {
                    int given = PartyResources.of(party).take(coin, outcome.amount()); // out of the bank: never made up
                    InventoryUtils.giveOrDrop(each, coin, given);
                    deltas.merge(each, given, Integer::sum);
                }
            }
            case CURSED_DIE -> {
                List<Text> names = new ArrayList<>();
                for (ServerPlayerEntity each : aimed) {
                    CursedRolls.curse(each);
                    names.add(each.getDisplayName());
                }
                // On the one who landed: the sentence says it all
                if (names.isEmpty() || (aimed.size() == 1 && aimed.getFirst() == landed)) return null;
                return Text.translatable("message.steveparty.mistigri_space.cursed", Texts.join(names, Text.literal(", ")));
            }
            default -> {
            }
        }
        List<Text> parts = new ArrayList<>();
        for (Map.Entry<ServerPlayerEntity, Integer> delta : deltas.entrySet()) {
            int count = delta.getValue();
            if (count == 0) continue;
            moves.moved(delta.getKey(), currency, count);
            parts.add(Text.translatable("message.steveparty.mistigri_space.delta", delta.getKey().getDisplayName(),
                    PartyCurrencyText.amount(currency, (count > 0 ? "+" : "−") + Math.abs(count))));
        }
        if (!parts.isEmpty()) return Text.translatable("message.steveparty.mistigri_space.moved", Texts.join(parts, Text.literal(", ")));
        if (aimed.isEmpty()) return Text.translatable("message.steveparty.mistigri_space.nobody");
        return Text.translatable(outcome.kind() == Kind.PITY ? "message.steveparty.mistigri_space.bank_empty" : "message.steveparty.mistigri_space.nothing");
    }

    private static void tell(PartyControllerEntity party, @Nullable ServerPlayerEntity player, Text message) {
        BoardSequences.tell(party, message, player);
    }

    /** One show: the Mistigri, his die, the sentence. */
    private static final class Show extends BoardSequences.Sequence {
        final BlockPos tile;
        final UUID playerId;
        final PartyControllerEntity party;
        final Outcome outcome;
        MistigriEntity actor;
        @Nullable MistigriDieEntity die;
        /** The roulette's lines (the sentences picked for this landing) and the one drawn; the roulette once started. */
        List<OutcomeRoulettePayload.Line> lines = List.of();
        int result;
        @Nullable OutcomeRoulettes.Roulette roulette;
        Phase phase = Phase.LEAP_IN;
        int phaseTick;
        Vec3d from = Vec3d.ZERO, seat = Vec3d.ZERO, away = Vec3d.ZERO, dieFrom = Vec3d.ZERO, dieTo = Vec3d.ZERO;
        float yaw;

        Show(ServerWorld world, BlockPos tile, MobEntity token, UUID playerId, PartyControllerEntity party,
             Outcome outcome, Runnable onDone) {
            super(world, token, onDone);
            this.tile = tile;
            this.playerId = playerId;
            this.party = party;
            this.outcome = outcome;
        }

        @Nullable ServerPlayerEntity player() {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            return player == null || player.isDisconnected() ? null : player;
        }

        /**
         * He appears on the space's Spawn Marker, facing its way, or without one on the token's right, a few blocks
         * off; crouched for his leap to his seat by the token (toward the marker), back the same way.
         */
        boolean spawn() {
            MistigriEntity one = ModEntities.MISTIGRI.create(world);
            if (one == null) return false;
            cast(one);
            Vec3d stand = BoardSpaces.standPos(world, tile);
            yaw = token.getYaw();
            Vec3d right = Vec3d.fromPolar(0, yaw + 90);
            BoardMobSpots.Spot spot = BoardMobSpots.marker(world, tile);
            BoardMobSpots.showStarts(world, spot, task);
            float facing;
            if (spot != null) {
                from = spot.pos();
                Vec3d toward = new Vec3d(from.x - stand.x, 0, from.z - stand.z);
                double distance = toward.length();
                Vec3d unit = distance < 1.0E-3 ? right : toward.multiply(1 / distance);
                seat = distance <= SEAT_SIDE ? from : stand.add(unit.multiply(SEAT_SIDE));
                away = from.add(unit.multiply(2));
                facing = spot.yaw();
                BoardMobSpots.hold(one, spot);
            } else {
                from = stand.add(right.multiply(APPEAR_SIDE));
                seat = stand.add(right.multiply(SEAT_SIDE));
                away = stand.add(right.multiply(APPEAR_SIDE)).add(Vec3d.fromPolar(0, yaw).multiply(-2));
                facing = yawToward(from, seat);
            }
            one.refreshPositionAndAngles(from.x, from.y, from.z, facing, 0);
            one.setHeadYaw(facing);
            one.setBodyYaw(facing);
            world.spawnEntity(one);
            actor = one;
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y + 0.6, from.z, 12, 0.5, 0.4, 0.5, 0.02);
            world.spawnParticles(ParticleTypes.WITCH, from.x, from.y + 0.6, from.z, 16, 0.5, 0.4, 0.5, 0.05);
            world.playSound(null, from.x, from.y, from.z, ModSounds.MISTIGRI_MEOW, SoundCategory.NEUTRAL, 1.0f, 0.9f);
            one.act(Action.LEAP);
            return true;
        }

        void tick() {
            if (done) return;
            phaseTick++;
            if (actor == null || actor.isRemoved() || party.isRemoved() || !party.getPartyData().isStarted()) {
                finish(false);
                return;
            }
            switch (phase) {
                case LEAP_IN -> tickLeapIn();
                case ROLL -> tickRoll();
                case SENTENCE -> {
                    if (phaseTick == 1) pass();
                    ServerPlayerEntity player = player();
                    if (player != null) face(player.getEyePos());
                    if (phaseTick >= SENTENCE_TICKS) go(Phase.LEAVE);
                }
                case LEAVE -> tickLeave();
            }
        }

        void go(Phase next) {
            phase = next;
            phaseTick = 0;
        }

        /** The leap onto the space: an arc, a heavy landing. */
        void tickLeapIn() {
            float t = Math.min(1f, phaseTick / (float) LEAP_TICKS);
            float flight = MathHelper.clamp((t - 0.25f) / 0.6f, 0, 1); // crouch, fly, land (the animation's timing)
            Vec3d at = from.lerp(seat, flight).add(0, Math.sin(flight * Math.PI) * 1.6, 0);
            place(at, yawToward(from, seat));
            if (phaseTick >= LEAP_TICKS) {
                world.spawnParticles(ParticleTypes.POOF, seat.x, seat.y + 0.1, seat.z, 10, 0.5, 0.05, 0.5, 0.02);
                world.playSound(null, seat.x, seat.y, seat.z, SoundEvents.ENTITY_PLAYER_SMALL_FALL, SoundCategory.NEUTRAL, 0.8f, 0.6f);
                place(seat, yaw);
                go(Phase.ROLL);
            }
        }

        /**
         * The roulette of the sentences, shown to the whole party; as its light starts running he rolls onto his back and
         * juggles his die between his fore paws, bouncing and spinning over his chest while the light runs; as it stops
         * he tosses it up, rolls back onto his feet, and it lands in front of him on a low face. The sentence comes once
         * the roulette is over.
         */
        void tickRoll() {
            Vec3d ahead = Vec3d.fromPolar(0, yaw);
            if (phaseTick == 1) {
                face(seat.add(ahead.multiply(DIE_AHEAD)));
                ServerPlayerEntity player = player();
                // Its panel floats just over him lying on his back, clear of his die's toss
                roulette = OutcomeRoulettes.start(world, party, player, seat.add(ahead.multiply(DIE_HELD_AHEAD)).add(0, PANEL_ABOVE, 0),
                        Text.translatable("hud.steveparty.mistigri_space.roulette",
                                player == null ? Text.literal("?") : player.getDisplayName()),
                        Text.translatable("hud.steveparty.mistigri_space.roulette.caption"), lines, result);
            }
            if (phaseTick == ROLL_OVER_AT) {
                actor.act(Action.ROLL_ON_BACK);
                actor.setJuggling(true); // once rolled over
            }
            if (phaseTick == DIE_HELD_AT) {
                MistigriDieEntity one = ModEntities.MISTIGRI_DIE.create(world);
                if (one != null) {
                    castProp(one);
                    dieFrom = seat.add(ahead.multiply(DIE_HELD_AHEAD)).add(0, DIE_HELD_UP, 0);
                    dieTo = seat.add(ahead.multiply(DIE_AHEAD));
                    one.refreshPositionAndAngles(dieFrom.x, dieFrom.y, dieFrom.z, yaw, 0);
                    one.setRolling(true);
                    world.spawnEntity(one);
                    die = one;
                    world.spawnParticles(ParticleTypes.WITCH, dieFrom.x, dieFrom.y + 0.3, dieFrom.z, 12, 0.25, 0.25, 0.25, 0.04);
                    world.playSound(null, dieFrom.x, dieFrom.y, dieFrom.z, SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.NEUTRAL, 0.5f, 0.7f);
                }
            }
            if (phaseTick == TOSS_AT) {
                actor.setJuggling(false);
                actor.act(Action.JUGGLE_TOSS);
            }
            int launched = TOSS_AT + TOSS_SHOVE;
            if (die != null && phaseTick > DIE_HELD_AT && phaseTick <= DIE_LANDS_AT) {
                Vec3d at;
                if (phaseTick < TOSS_AT) {
                    // Juggled: a hop off each paw in turn, nudged a little toward the other
                    int juggled = phaseTick - DIE_HELD_AT;
                    double hop = Math.abs(Math.sin(Math.PI * juggled / BOUNCE_TICKS));
                    double sway = 0.06 * Math.sin(Math.PI * juggled / BOUNCE_TICKS - Math.PI / 2);
                    at = dieFrom.add(Vec3d.fromPolar(0, yaw + 90).multiply(sway)).add(0, hop * BOUNCE, 0);
                } else if (phaseTick < launched) {
                    at = dieFrom.add(0, -0.08, 0); // his paws load it
                } else {
                    // Tossed: an arc up over his chest and down in front of him
                    float t = (phaseTick - launched) / (float) (DIE_LANDS_AT - launched);
                    at = dieFrom.lerp(dieTo, t).add(0, 4 * TOSS_UP * t * (1 - t), 0);
                }
                // Spins fast as he starts juggling, slower and slower with the light, fast again when tossed
                float spin = phaseTick < TOSS_AT ? 25 - 18 * (phaseTick - DIE_HELD_AT) / (float) (TOSS_AT - DIE_HELD_AT) : 25;
                die.refreshPositionAndAngles(at.x, at.y, at.z, die.getYaw() + spin, 0);
                if (phaseTick == DIE_LANDS_AT) {
                    die.setRolling(false);
                    die.setFace(1 + world.getRandom().nextInt(3) / 2); // loaded: mostly a 1
                    die.refreshPositionAndAngles(dieTo.x, dieTo.y, dieTo.z, yaw, 0);
                    world.spawnParticles(ParticleTypes.WITCH, dieTo.x, dieTo.y + 0.5, dieTo.z, 20, 0.6, 0.5, 0.6, 0.05);
                    world.playSound(null, dieTo.x, dieTo.y, dieTo.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.NEUTRAL, 0.35f, 0.7f);
                }
            }
            if (phaseTick >= ROLL_TICKS) go(Phase.SENTENCE);
        }

        /** The sentence: applied to the players it aims at, told to everyone, his reaction. */
        void pass() {
            ServerPlayerEntity player = player();
            Text name = outcome.text(player == null ? null : player.getName(), party.getCurrency(PartyCurrency.COIN),
                    party.getCurrency(PartyCurrency.STAR));
            boolean harsh = outcome.kind().tone() < 0 || (outcome.kind().tone() == 0 && outcome.target() == Target.LANDED);
            if (harsh) actor.setAngry(SENTENCE_TICKS);
            else actor.act(Action.YAWN);
            world.playSound(null, actor.getX(), actor.getY(), actor.getZ(), harsh ? ModSounds.MISTIGRI_HISS : ModSounds.MISTIGRI_PURR,
                    SoundCategory.NEUTRAL, 1.0f, 1.0f);
            // The roulette announced it to everyone: no title over it, the action bar reminds the player
            if (player != null) MessageUtils.sendToPlayer(player, name.copy().formatted(Formatting.LIGHT_PURPLE), MessageUtils.MessageType.ACTION_BAR);
            MutableText result = apply(world, party, player, outcome, world.getRandom(), this::crumbs);
            tell(party, player, Text.translatable("message.steveparty.mistigri_space.passed",
                    player == null ? Text.literal("?") : player.getDisplayName(), name).formatted(Formatting.DARK_PURPLE)
                    .append(result == null ? Text.empty() : Text.literal(" ").append(result.formatted(Formatting.GRAY))));
        }

        /** What he takes flies from {@code player} to him as item crumbs ({@code count} below 0); what he gives, from him to them. */
        void crumbs(ServerPlayerEntity player, ItemStack template, int count) {
            Vec3d them = player.getPos().add(0, player.getHeight() * 0.6, 0), him = actor.getPos().add(0, 0.8, 0);
            Vec3d from = count < 0 ? them : him;
            Vec3d way = (count < 0 ? him : them).subtract(from);
            ItemStackParticleEffect crumb = new ItemStackParticleEffect(ParticleTypes.ITEM, template.copyWithCount(1));
            for (int i = 0; i < 8; i++) {
                world.spawnParticles(crumb, from.x, from.y, from.z, 0, way.x, way.y + 0.6, way.z, 0.18 + world.getRandom().nextDouble() * 0.06);
            }
            world.playSound(null, them.x, them.y, them.z, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.7f, 0.6f);
        }

        /** He leaps off and vanishes in smoke, his die with him. */
        void tickLeave() {
            if (phaseTick == 1) actor.act(Action.LEAP);
            float t = Math.min(1f, phaseTick / (float) LEAVE_TICKS);
            float flight = MathHelper.clamp((t - 0.25f) / 0.6f, 0, 1);
            Vec3d at = seat.lerp(away, flight).add(0, Math.sin(flight * Math.PI) * 1.4, 0);
            place(at, yawToward(seat, away));
            if (phaseTick >= LEAVE_TICKS) finish(true);
        }

        void place(Vec3d at, float facing) {
            actor.refreshPositionAndAngles(at.x, at.y, at.z, facing, 0);
            actor.setHeadYaw(facing);
            actor.setBodyYaw(facing);
            actor.setVelocity(Vec3d.ZERO);
        }

        void face(Vec3d target) {
            float facing = yawToward(actor.getPos(), target);
            actor.setYaw(facing);
            actor.setHeadYaw(facing);
            actor.setBodyYaw(facing);
        }

        /** Over: the actors go; the move back starts (it then holds the turn), else the turn goes on. */
        void finish(boolean completed) {
            if (!close()) return;
            if (!completed) OutcomeRoulettes.stop(roulette); // off the screens if it was still on
            for (Vec3d at : new Vec3d[]{actor == null ? null : actor.getPos(), die == null ? null : die.getPos()}) {
                if (at == null) continue;
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6, at.z, 10, 0.4, 0.4, 0.4, 0.02);
                world.spawnParticles(ParticleTypes.WITCH, at.x, at.y + 0.6, at.z, 10, 0.4, 0.4, 0.4, 0.05);
            }
            dismissActors();
            if (completed && outcome.kind() == Kind.MOVE_BACK && !token.isRemoved()
                    && AdvanceBackMoves.launch(world, token, tile, -outcome.amount()) > 0) {
                return; // its landing over there goes on with the turn
            }
            onDone.run();
        }
    }
}
