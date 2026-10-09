package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.dice.CursedRolls;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriDieEntity;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity.Action;
import fr.lordfinn.steveparty.items.custom.cartridges.MistigriCartridgeItem;
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
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static fr.lordfinn.steveparty.service.BoardSequences.yawToward;

/**
 * What a Mistigri space does (see MistigriTileBehavior): the Mistigri leaps onto the space beside the token, swats his
 * giant loaded die into the air, and the die passes a sentence on the token's player, drawn by the cartridge's weights
 * ({@link Sentence}). He reacts (a hiss, or a yawn for the lenient one) and leaps away in a puff of smoke.
 * <p>
 * A move back is walked once he is gone (the token's turn goes on until it lands: AdvanceBackMoves); every other
 * sentence ends the turn. He and his die are board actors: invulnerable, never saved, always removed at the end
 * ({@link BoardActors}), whatever ends it (party over, server stopping). Server thread only.
 */
public final class MistigriSentences {
    /** The sentences, with their default weight. */
    public enum Sentence {
        /** Loses the small fine (10 coins by default). */
        COINS_SMALL("coins_small", 4, true),
        /** Loses the big fine (20 coins by default). */
        COINS_BIG("coins_big", 2, true),
        /** Loses half their coins (rounded down). */
        COINS_HALF("coins_half", 1, true),
        /** Loses a star: rare. */
        STAR("star", 1, true),
        /** Every player of the party pays (5 coins by default). */
        EVERYONE("everyone", 2, true),
        /** Moves back (3 spaces by default). */
        BACK("back", 3, true),
        /** Their next roll is cursed: 1 to 3 only (CursedRolls). */
        CURSED("cursed", 2, true),
        /** The lenient one: he only takes one coin, out of pity. */
        JOKE("joke", 2, false);

        public final String id;
        public final int defaultWeight;
        /** A harsh one: he puffs up and hisses; the lenient one: a yawn. */
        public final boolean harsh;

        Sentence(String id, int defaultWeight, boolean harsh) {
            this.id = id;
            this.defaultWeight = defaultWeight;
            this.harsh = harsh;
        }

        /** Its number as set on {@code cartridge} (coins, spaces), for the texts. */
        public int amount(ItemStack cartridge) {
            return switch (this) {
                case COINS_SMALL -> MistigriCartridgeItem.amount(cartridge, MistigriCartridgeItem.COINS_SMALL);
                case COINS_BIG -> MistigriCartridgeItem.amount(cartridge, MistigriCartridgeItem.COINS_BIG);
                case EVERYONE -> MistigriCartridgeItem.amount(cartridge, MistigriCartridgeItem.EVERYONE);
                case BACK -> MistigriCartridgeItem.amount(cartridge, MistigriCartridgeItem.BACK);
                case CURSED -> 3;
                default -> 1;
            };
        }
    }

    public enum Phase { LEAP_IN, ROLL, SENTENCE, LEAVE }

    public enum Start { STARTED, NO_PLAYER, NO_SENTENCE }

    /** Timing (ticks). */
    public static final int LEAP_TICKS = 20, ROLL_TICKS = 40, DIE_LANDS_AT = 30, SENTENCE_TICKS = 60, LEAVE_TICKS = 20;
    /** The whole show, at most (for the tests). */
    public static final int WHOLE = LEAP_TICKS + ROLL_TICKS + SENTENCE_TICKS + LEAVE_TICKS;
    /** His leap starts this far from the space (blocks, to the token's side). */
    private static final double APPEAR_SIDE = 4.5, SEAT_SIDE = 1.5, DIE_AHEAD = 2.2;

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

    public static @Nullable Sentence sentence(MobEntity token) {
        Show show = RUNNING.get(token);
        return show == null ? null : show.sentence;
    }

    /** A sentence drawn by the weights of {@code cartridge}; null if every weight is 0. */
    public static @Nullable Sentence draw(ItemStack cartridge, Random random) {
        int total = 0;
        for (Sentence sentence : Sentence.values()) total += MistigriCartridgeItem.weight(cartridge, sentence);
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (Sentence sentence : Sentence.values()) {
            roll -= MistigriCartridgeItem.weight(cartridge, sentence);
            if (roll < 0) return sentence;
        }
        return null;
    }

    /**
     * {@code token} stopped on the Mistigri space {@code tile} of {@code party}: the show starts, the sentence drawn from
     * {@code cartridge} ({@code forced} instead, for the tests). {@code onDone} runs when it is over, unless the token
     * then moves back (its landing goes on with the turn). Nothing happens without the token's player online, or when
     * no sentence may be drawn: {@code onDone} is not called then.
     */
    public static Start start(ServerWorld world, BlockPos tile, MobEntity token, PartyControllerEntity party,
                              ItemStack cartridge, @Nullable Sentence forced, Runnable onDone) {
        if (RUNNING.isRunning(token)) return Start.STARTED;
        ServerPlayerEntity player = BoardSequences.tokenPlayer(world, token);
        if (player == null) {
            tell(party, null, Text.translatable("message.steveparty.mistigri_space.no_player").formatted(Formatting.GRAY));
            return Start.NO_PLAYER;
        }
        Sentence sentence = forced != null ? forced : draw(cartridge, world.getRandom());
        if (sentence == null) {
            tell(party, player, Text.translatable("message.steveparty.mistigri_space.no_sentence").formatted(Formatting.GRAY));
            return Start.NO_SENTENCE;
        }
        Show show = new Show(world, tile.toImmutable(), token, player.getUuid(), party, sentence, sentence.amount(cartridge), onDone);
        if (!show.spawn()) return Start.NO_SENTENCE;
        RUNNING.run(show, show::tick);
        return Start.STARTED;
    }

    private static void tell(PartyControllerEntity party, @Nullable ServerPlayerEntity player, Text message) {
        BoardSequences.tell(party, message, player);
    }

    /** One show: the Mistigri, his die, the sentence. */
    private static final class Show extends BoardSequences.Sequence {
        final BlockPos tile;
        final UUID playerId;
        final PartyControllerEntity party;
        final Sentence sentence;
        final int amount;
        MistigriEntity actor;
        @Nullable MistigriDieEntity die;
        Phase phase = Phase.LEAP_IN;
        int phaseTick;
        Vec3d from = Vec3d.ZERO, seat = Vec3d.ZERO, away = Vec3d.ZERO, dieFrom = Vec3d.ZERO, dieTo = Vec3d.ZERO;
        float yaw;

        Show(ServerWorld world, BlockPos tile, MobEntity token, UUID playerId, PartyControllerEntity party,
             Sentence sentence, int amount, Runnable onDone) {
            super(world, token, onDone);
            this.tile = tile;
            this.playerId = playerId;
            this.party = party;
            this.sentence = sentence;
            this.amount = amount;
        }

        @Nullable ServerPlayerEntity player() {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            return player == null || player.isDisconnected() ? null : player;
        }

        /** He appears on the token's right, a few blocks off, crouched for his leap. */
        boolean spawn() {
            MistigriEntity one = ModEntities.MISTIGRI.create(world);
            if (one == null) return false;
            cast(one);
            Vec3d stand = BoardSpaces.standPos(world, tile);
            yaw = token.getYaw();
            Vec3d right = Vec3d.fromPolar(0, yaw + 90);
            from = stand.add(right.multiply(APPEAR_SIDE));
            seat = stand.add(right.multiply(SEAT_SIDE));
            away = stand.add(right.multiply(APPEAR_SIDE)).add(Vec3d.fromPolar(0, yaw).multiply(-2));
            float facing = yawToward(from, seat);
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

        /** He swats his die up: it tumbles in an arc and lands in front of him on a low face. */
        void tickRoll() {
            if (phaseTick == 1) {
                face(seat.add(Vec3d.fromPolar(0, yaw).multiply(DIE_AHEAD)));
                actor.act(Action.SWAT);
            }
            if (phaseTick == 8) {
                MistigriDieEntity one = ModEntities.MISTIGRI_DIE.create(world);
                if (one != null) {
                    castProp(one);
                    dieFrom = seat.add(Vec3d.fromPolar(0, yaw).multiply(0.9)).add(0, 1.0, 0);
                    dieTo = seat.add(Vec3d.fromPolar(0, yaw).multiply(DIE_AHEAD));
                    one.refreshPositionAndAngles(dieFrom.x, dieFrom.y, dieFrom.z, yaw, 0);
                    one.setRolling(true);
                    world.spawnEntity(one);
                    die = one;
                    world.playSound(null, dieFrom.x, dieFrom.y, dieFrom.z, SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.NEUTRAL, 0.5f, 0.7f);
                }
            }
            if (die != null && phaseTick > 8 && phaseTick <= DIE_LANDS_AT) {
                float t = (phaseTick - 8) / (float) (DIE_LANDS_AT - 8);
                Vec3d at = dieFrom.lerp(dieTo, t).add(0, Math.sin(t * Math.PI) * 2.5, 0);
                die.refreshPositionAndAngles(at.x, at.y, at.z, yaw + phaseTick * 25, 0);
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

        /** The sentence: applied to the token's player, told to everyone, his reaction. */
        void pass() {
            ServerPlayerEntity player = player();
            Text name = Text.translatable("message.steveparty.mistigri_space.sentence." + sentence.id, amount);
            if (sentence.harsh) actor.setAngry(SENTENCE_TICKS);
            else actor.act(Action.YAWN);
            world.playSound(null, actor.getX(), actor.getY(), actor.getZ(), sentence.harsh ? ModSounds.MISTIGRI_HISS : ModSounds.MISTIGRI_PURR,
                    SoundCategory.NEUTRAL, 1.0f, 1.0f);
            if (player != null) {
                MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.mistigri_space.title")
                        .formatted(Formatting.DARK_PURPLE), MessageUtils.MessageType.TITLE);
                MessageUtils.sendToPlayer(player, name.copy().formatted(Formatting.LIGHT_PURPLE), MessageUtils.MessageType.ACTION_BAR);
            }
            MutableText result = apply(player);
            tell(party, player, Text.translatable("message.steveparty.mistigri_space.passed",
                    player == null ? Text.literal("?") : player.getDisplayName(), name).formatted(Formatting.DARK_PURPLE)
                    .append(result == null ? Text.empty() : Text.literal(" ").append(result.formatted(Formatting.GRAY))));
        }

        /** Applies the sentence (a move back waits for his leaving); what it did, for the chat. */
        @Nullable MutableText apply(@Nullable ServerPlayerEntity player) {
            ItemStack coin = party.getCurrency(PartyCurrency.COIN);
            switch (sentence) {
                case COINS_SMALL, COINS_BIG, JOKE -> {
                    return take(player, coin, amount);
                }
                case COINS_HALF -> {
                    int held = player == null ? 0 : InventoryUtils.count(player.getInventory(), coin);
                    return take(player, coin, held / 2);
                }
                case STAR -> {
                    return take(player, party.getCurrency(PartyCurrency.STAR), 1);
                }
                case EVERYONE -> {
                    int total = 0;
                    for (UUID id : party.getPlayersInOrder()) {
                        ServerPlayerEntity each = world.getServer().getPlayerManager().getPlayer(id);
                        if (each == null) continue;
                        total += InventoryUtils.take(each.getInventory(), coin, amount);
                        crumbs(each, coin);
                    }
                    return Text.translatable("message.steveparty.mistigri_space.taken", total, coin.getName());
                }
                case CURSED -> {
                    if (player != null) CursedRolls.curse(player);
                    return null;
                }
                case BACK -> {
                    return null; // walked once he has gone
                }
            }
            return null;
        }

        MutableText take(@Nullable ServerPlayerEntity player, ItemStack template, int count) {
            int taken = player == null || count <= 0 ? 0 : InventoryUtils.take(player.getInventory(), template, count);
            if (player != null && taken > 0) crumbs(player, template);
            return taken > 0 ? Text.translatable("message.steveparty.mistigri_space.taken", taken, template.getName())
                    : Text.translatable("message.steveparty.mistigri_space.nothing", template.getName());
        }

        /** What he takes flies from {@code player} to him, as item crumbs. */
        void crumbs(ServerPlayerEntity player, ItemStack template) {
            Vec3d them = player.getPos().add(0, player.getHeight() * 0.6, 0);
            Vec3d way = actor.getPos().add(0, 0.8, 0).subtract(them);
            ItemStackParticleEffect crumb = new ItemStackParticleEffect(ParticleTypes.ITEM, template.copyWithCount(1));
            for (int i = 0; i < 8; i++) {
                world.spawnParticles(crumb, them.x, them.y, them.z, 0, way.x, way.y + 0.6, way.z, 0.18 + world.getRandom().nextDouble() * 0.06);
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
            for (Vec3d at : new Vec3d[]{actor == null ? null : actor.getPos(), die == null ? null : die.getPos()}) {
                if (at == null) continue;
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6, at.z, 10, 0.4, 0.4, 0.4, 0.02);
                world.spawnParticles(ParticleTypes.WITCH, at.x, at.y + 0.6, at.z, 10, 0.4, 0.4, 0.4, 0.05);
            }
            dismissActors();
            if (completed && sentence == Sentence.BACK && !token.isRemoved()
                    && AdvanceBackMoves.launch(world, token, tile, -amount) > 0) {
                return; // its landing over there goes on with the turn
            }
            onDone.run();
        }
    }
}
