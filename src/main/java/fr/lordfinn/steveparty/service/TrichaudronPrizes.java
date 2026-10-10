package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.TrichaudronTileBehavior;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.TrichaudronCartridgeItem;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.powerups.PowerUpLimit;
import fr.lordfinn.steveparty.powerups.PowerUpService;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.s2c.play.DamageTiltS2CPacket;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.service.BoardSequences.yawToward;

/**
 * What a Trichaudron space does (see TrichaudronTileBehavior): the Trichaudron rises from the ground beside the token
 * in a cloud of steam with one head per prize on offer (1 to 5: TrichaudronEntity#headsShown), its heads dive into its
 * tank and come out each with one of the prizes. Then it holds them out, swaying, and the token's player picks one by
 * hitting or clicking it (its hit boxes: TrichaudronPartEntity). It never picks by itself: left alone for
 * {@link #CHOOSE_TICKS}, the other players get a button in the chat to pick a head at random ({@link #pickAtRandom}).
 * <ul>
 *     <li><b>A blind pick</b> (the cartridge's default): the prizes are shuffled into the heads each time it rises and
 *     nothing shows which is where; the prize is only revealed when the head spits it.</li>
 *     <li><b>A true choice</b> (cartridge option): each head shows its prize floating at its mouth.</li>
 * </ul>
 * The chosen head recoils for a long breath (its spit animation's wind-up), then spits at the token's face: a big
 * splash, the prize flying out with it, given to the player (taken out of the chests); whoever stands at the token gets
 * splashed too, harmlessly (a start, a jolt of the view). The other heads' prizes are told. Then it sinks back into a
 * puff of steam and the turn goes on. A prize won is consumed for the rest of the party ({@link Won}), unless the
 * cartridge always offers the same prizes.
 * <p>
 * The Trichaudron is a board actor: invulnerable, never saved, always removed at the end ({@link BoardActors}),
 * whatever ends it (party over, server stopping). Server thread only.
 */
public final class TrichaudronPrizes {
    public enum Phase { RISE, DIVE, EMERGE, CHOOSE, SPIT, LEAVE }

    public enum Start { STARTED, NO_PLAYER, EMPTY }

    /** Timing (ticks). */
    public static final int RISE_TICKS = 30, DIVE_TICKS = 40, EMERGE_TICKS = 20, LEAVE_TICKS = 30;
    /** Choosing: after this long, the others may pick a head at random for its player (a button in the chat). */
    public static final int CHOOSE_TICKS = 600;
    /** The chosen head's spit: its whip (the animation's, when the spit leaves), the splash at the token, the end. */
    public static final int SPIT_AT = TrichaudronEntity.SPIT_WHIP_TICKS, SPLASH_AT = SPIT_AT + 4, SPIT_TICKS = SPLASH_AT + 30;
    /** The whole show, at most, its head picked once the others may (for the tests). */
    public static final int WHOLE = RISE_TICKS + DIVE_TICKS + EMERGE_TICKS + CHOOSE_TICKS + SPIT_TICKS + LEAVE_TICKS;
    /** Whoever stands this close to the token's face (blocks) is splashed too. */
    public static final double SPLASH_REACH = 3.0;
    /** Where it stands (blocks to the token's right), its heads reaching over beside the token. */
    private static final double SIDE = 11.0;

    private static final BoardSequences<Show> RUNNING = new BoardSequences<>();

    private TrichaudronPrizes() {
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

    public static @Nullable TrichaudronEntity actor(MobEntity token) {
        Show show = RUNNING.get(token);
        return show == null ? null : show.actor;
    }

    /**
     * What each head holds, by head ({@link TrichaudronEntity#ALL_HEADS}; an empty stack for a head not shown); empty
     * when no show.
     */
    public static List<ItemStack> heads(MobEntity token) {
        Show show = RUNNING.get(token);
        if (show == null) return List.of();
        return Arrays.stream(show.held).map(ItemStack::copy).toList();
    }

    /** The head picked (its index in {@link TrichaudronEntity#ALL_HEADS}), -1 until then or without a show. */
    public static int picked(MobEntity token) {
        Show show = RUNNING.get(token);
        return show == null ? -1 : show.head;
    }

    /** Tests: the token's player picks {@code head} (as a click on it would). */
    public static void pick(MobEntity token, int head) {
        Show show = RUNNING.get(token);
        if (show != null && show.phase == Phase.CHOOSE) show.chosen(head);
    }

    /**
     * {@code by} (the button in the chat) picks a head at random for the player of the token {@code tokenId}, who has
     * been choosing for {@link #CHOOSE_TICKS} at least; true if a head was picked.
     */
    public static boolean pickAtRandom(UUID tokenId, ServerPlayerEntity by) {
        Show show = RUNNING.get(tokenId);
        if (show == null || show.done || show.phase != Phase.CHOOSE || show.phaseTick < CHOOSE_TICKS) {
            by.sendMessage(Text.translatable("message.steveparty.trichaudron_space.random_late").formatted(Formatting.GRAY), true);
            return false;
        }
        int[] shown = show.actor.shownHeads();
        show.chosen(shown[show.world.getRandom().nextInt(shown.length)]);
        BoardSequences.tell(show.party, Text.translatable("message.steveparty.trichaudron_space.random_picked", by.getDisplayName())
                .formatted(Formatting.GRAY));
        return true;
    }

    /** Tests: the show stops now (as if its party ended), its actor removed; its {@code onDone} runs. */
    public static void stop(MobEntity token) {
        Show show = RUNNING.get(token);
        if (show != null) show.finish(false);
    }

    /**
     * {@code token} stopped on the Trichaudron space {@code tile} of {@code party}: the show starts, its prizes those
     * the tile's cartridge offers now (TrichaudronCartridgeItem#offered). {@code onDone} runs when it is over. Nothing
     * happens (and {@code onDone} is not called) without the token's player online, or with nothing to offer.
     */
    public static Start start(ServerWorld world, BlockPos tile, MobEntity token, PartyControllerEntity party, Runnable onDone) {
        if (RUNNING.isRunning(token)) return Start.STARTED;
        BoardSpaceBlockEntity space = ABoardSpaceBlock.getBoardSpaceEntity(world, tile);
        ItemStack cartridge = space == null ? ItemStack.EMPTY : space.getActiveCartridgeItemStack();
        // What its chests really hold (never anything made from nothing), less what was already won
        List<TrichaudronCartridgeItem.Prize> stock = space == null ? List.of()
                : TrichaudronCartridgeItem.offeredPrizes(cartridge, world, tile, party);
        if (space != null) TrichaudronTileBehavior.refreshSleep(space, !stock.isEmpty());
        if (stock.isEmpty()) return Start.EMPTY;
        ServerPlayerEntity player = BoardSequences.tokenPlayer(world, token);
        if (player == null) {
            BoardSequences.tell(party, Text.translatable("message.steveparty.trichaudron_space.no_player").formatted(Formatting.GRAY));
            return Start.NO_PLAYER;
        }
        // One head per prize, the prizes shuffled into them each time (a head's place never tells its prize)
        List<TrichaudronCartridgeItem.Prize> prizes = new ArrayList<>(stock.subList(0, Math.min(TrichaudronEntity.MAX_HEADS, stock.size())));
        Collections.shuffle(prizes, new java.util.Random(world.getRandom().nextLong()));
        int[] shown = TrichaudronEntity.headsShown(prizes.size());
        ItemStack[] held = new ItemStack[TrichaudronEntity.MAX_HEADS];
        String[] keys = new String[TrichaudronEntity.MAX_HEADS];
        Arrays.fill(held, ItemStack.EMPTY);
        for (int i = 0; i < shown.length; i++) {
            held[shown[i]] = prizes.get(i).stack();
            keys[shown[i]] = prizes.get(i).key();
        }
        Show show = new Show(world, tile.toImmutable(), token, player.getUuid(), party, held, keys, prizes.size(),
                TrichaudronCartridgeItem.trueChoice(cartridge), TrichaudronCartridgeItem.samePrizes(cartridge), onDone);
        if (!show.spawn()) return Start.EMPTY;
        RUNNING.run(show, show::tick);
        return Start.STARTED;
    }

    /** One show: the Trichaudron, the prizes in its heads. */
    private static final class Show extends BoardSequences.Sequence {
        final BlockPos tile;
        final UUID playerId;
        final PartyControllerEntity party;
        /** The prize of each head (ALL_HEADS), empty for a head not shown. */
        final ItemStack[] held;
        /** The key of each head's prize in the party's record of prizes won (TrichaudronCartridgeItem#prizeKeys). */
        final String[] keys;
        final int count;
        final boolean trueChoice, samePrizes;
        TrichaudronEntity actor;
        Phase phase = Phase.RISE;
        int phaseTick;
        /** The head picked, -1 until then. */
        int head = -1;
        Vec3d seat = Vec3d.ZERO;
        float yaw;

        Show(ServerWorld world, BlockPos tile, MobEntity token, UUID playerId, PartyControllerEntity party,
             ItemStack[] held, String[] keys, int count, boolean trueChoice, boolean samePrizes, Runnable onDone) {
            super(world, token, onDone);
            this.tile = tile;
            this.playerId = playerId;
            this.party = party;
            this.held = held;
            this.keys = keys;
            this.count = count;
            this.trueChoice = trueChoice;
            this.samePrizes = samePrizes;
        }

        @Nullable ServerPlayerEntity player() {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            return player == null || player.isDisconnected() ? null : player;
        }

        /** It appears under the ground on the space's Spawn Marker (else on the token's right, facing it): its heads will reach over. */
        boolean spawn() {
            TrichaudronEntity one = ModEntities.TRICHAUDRON.create(world);
            if (one == null) return false;
            cast(one);
            one.setHeadCount(count);
            one.setOnHeadPicked(this::clicked);
            Vec3d stand = BoardSpaces.standPos(world, tile);
            Vec3d right = Vec3d.fromPolar(0, token.getYaw() + 90);
            // On the space's Spawn Marker, facing its way; without one, on the token's right, facing it
            BoardMobSpots.Spot spot = BoardMobSpots.marker(world, tile);
            BoardMobSpots.showStarts(world, spot, task);
            seat = spot != null ? spot.pos() : stand.add(right.multiply(SIDE));
            yaw = spot != null ? spot.yaw() : yawToward(seat, stand);
            BoardMobSpots.hold(one, spot);
            one.setTank(TrichaudronEntity.TANK_MAX);
            place(one, seat.add(0, -TrichaudronEntity.HEIGHT, 0));
            world.spawnEntity(one);
            actor = one;
            steam(seat, 30);
            world.playSound(null, seat.x, seat.y, seat.z, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.NEUTRAL, 1.0f, 0.6f);
            world.playSound(null, seat.x, seat.y, seat.z, ModSounds.TRICHAUDRON_GURGLE, SoundCategory.NEUTRAL, 1.2f, 0.8f);
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
                case RISE -> {
                    float t = Math.min(1f, phaseTick / (float) RISE_TICKS);
                    place(actor, seat.add(0, -TrichaudronEntity.HEIGHT * (1 - t) * (1 - t), 0));
                    if (phaseTick % 4 == 0) steam(seat, 6);
                    if (phaseTick >= RISE_TICKS) go(Phase.DIVE);
                }
                case DIVE -> tickDive();
                case EMERGE -> tickEmerge();
                case CHOOSE -> tickChoose();
                case SPIT -> tickSpit();
                case LEAVE -> {
                    float t = Math.min(1f, phaseTick / (float) LEAVE_TICKS);
                    place(actor, seat.add(0, -TrichaudronEntity.HEIGHT * t * t, 0));
                    if (phaseTick == 1) {
                        world.playSound(null, seat.x, seat.y, seat.z, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.NEUTRAL, 1.2f, 0.5f);
                        steam(seat, 40);
                    } else if (phaseTick % 3 == 0) steam(seat, 8);
                    if (phaseTick >= LEAVE_TICKS) finish(true);
                }
            }
        }

        void go(Phase next) {
            phase = next;
            phaseTick = 0;
        }

        /** Its heads dive into its tank (its pumping gulp): bubbles and a gurgle. */
        void tickDive() {
            if (phaseTick == 1) {
                actor.setDiving(true);
                world.playSound(null, seat.x, seat.y, seat.z, ModSounds.TRICHAUDRON_PUMP, SoundCategory.NEUTRAL, 1.2f, 0.9f);
            }
            if (phaseTick % 5 == 0) {
                world.spawnParticles(ParticleTypes.LAVA, seat.x, seat.y + TrichaudronEntity.HEIGHT, seat.z, 2, 0.6, 0.1, 0.6, 0.0);
            }
            if (phaseTick == DIVE_TICKS / 2) {
                world.playSound(null, seat.x, seat.y, seat.z, ModSounds.TRICHAUDRON_GURGLE, SoundCategory.NEUTRAL, 1.0f, 1.1f);
            }
            if (phaseTick >= DIVE_TICKS) {
                actor.setDiving(false);
                go(Phase.EMERGE);
            }
        }

        /** The heads come out, dripping, each holding its prize (a true choice: shown at its mouth). */
        void tickEmerge() {
            if (phaseTick == EMERGE_TICKS / 2) {
                for (int h : actor.shownHeads()) {
                    Vec3d at = actor.nozzle(h);
                    world.spawnParticles(ParticleTypes.DRIPPING_LAVA, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);
                    world.spawnParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.02);
                    if (trueChoice) actor.setHeldPrize(h, held[h]);
                }
                world.playSound(null, seat.x, seat.y, seat.z, ModSounds.TRICHAUDRON_PUFF, SoundCategory.NEUTRAL, 1.0f, 0.8f);
            }
            if (phaseTick >= EMERGE_TICKS) {
                actor.setOffering(true);
                ServerPlayerEntity player = player();
                if (player != null) {
                    MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.trichaudron_space.title")
                            .formatted(Formatting.GOLD), MessageUtils.MessageType.TITLE);
                    MessageUtils.sendToPlayer(player, Text.translatable(trueChoice ? "message.steveparty.trichaudron_space.pick_true"
                            : "message.steveparty.trichaudron_space.pick").formatted(Formatting.YELLOW), MessageUtils.MessageType.ACTION_BAR);
                }
                go(Phase.CHOOSE);
            }
        }

        /** Holding its heads out, as long as it takes; too long and the others get a button to pick one at random. */
        void tickChoose() {
            if (phaseTick != CHOOSE_TICKS) return;
            ServerPlayerEntity picker = player();
            Text name = picker == null ? token.getDisplayName() : picker.getDisplayName();
            MutableText message = Text.translatable("message.steveparty.trichaudron_space.waiting", name).formatted(Formatting.GOLD)
                    .append(" ")
                    .append(Text.translatable("message.steveparty.trichaudron_space.random_button").styled(style -> style
                            .withColor(Formatting.YELLOW).withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/steveparty trichaudron_random " + token.getUuid()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Text.translatable("message.steveparty.trichaudron_space.random_hover")))));
            for (ServerPlayerEntity other : party.getPartyAudience()) {
                if (!other.getUuid().equals(playerId)) MessageUtils.sendToPlayer(other, message, MessageUtils.MessageType.CHAT);
            }
        }

        /** A head of it was hit or clicked: the token's player picks it; anyone else is told whose pick it is. */
        void clicked(PlayerEntity player, int clicked) {
            if (done || phase != Phase.CHOOSE) return;
            if (player.getUuid().equals(playerId)) {
                chosen(clicked);
                return;
            }
            ServerPlayerEntity picker = player();
            if (player instanceof ServerPlayerEntity other) MessageUtils.sendToPlayer(other, Text.translatable(
                    "message.steveparty.trichaudron_space.not_yours", picker == null ? Text.literal("?") : picker.getDisplayName())
                    .formatted(Formatting.GRAY), MessageUtils.MessageType.ACTION_BAR);
        }

        /** {@code picked} (a head it shows) will spit its prize. */
        void chosen(int picked) {
            if (done || phase != Phase.CHOOSE || !actor.showsHead(picked)) return;
            head = picked;
            go(Phase.SPIT);
        }

        /**
         * The chosen head turns to the token and recoils for a long breath (its spit's wind-up), the others still; then
         * it spits at the token's face: the splash, the prize flying out with it, given; the others' prizes told.
         */
        void tickSpit() {
            if (phaseTick == 1) {
                actor.setOffering(false);
                for (int h : actor.shownHeads()) {
                    actor.setHeadTarget(h, h == head ? token : null);
                    actor.setVent(h, h == head ? TrichaudronEntity.VENT_CHARGING : TrichaudronEntity.VENT_IDLE);
                }
                actor.playSpit(head);
                world.playSound(null, seat.x, seat.y, seat.z, ModSounds.TRICHAUDRON_CHARGE, SoundCategory.NEUTRAL, 1.0f, 0.9f);
            }
            if (phaseTick == SPIT_AT) spit();
            if (phaseTick == SPLASH_AT) splash();
            if (phaseTick >= SPIT_TICKS) {
                for (int h = 0; h < TrichaudronEntity.MAX_HEADS; h++) {
                    actor.setHeadTarget(h, null);
                    actor.setHeldPrize(h, ItemStack.EMPTY);
                    actor.setVent(h, TrichaudronEntity.VENT_IDLE);
                }
                go(Phase.LEAVE);
            }
        }

        /** The token's face: where the spit lands. */
        Vec3d face() {
            return token.getEyePos();
        }

        /** The spit leaves the mouth: a jet of steam and lava drops toward the token, the prize tumbling in it. */
        void spit() {
            actor.setVent(head, TrichaudronEntity.VENT_SPITTING);
            Vec3d from = actor.nozzle(head);
            Vec3d way = face().subtract(from);
            world.playSound(null, from.x, from.y, from.z, ModSounds.TRICHAUDRON_BLAST, SoundCategory.NEUTRAL, 1.0f, 1.3f);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_LLAMA_SPIT, SoundCategory.NEUTRAL, 1.5f, 0.5f);
            ItemStack prize = held[head];
            ItemStackParticleEffect crumb = prize.isEmpty() ? null : new ItemStackParticleEffect(ParticleTypes.ITEM, prize.copyWithCount(1));
            for (int i = 0; i <= 12; i++) {
                Vec3d at = from.add(way.multiply(i / 12.0)).add(0, Math.sin(Math.PI * i / 12.0) * 0.6, 0);
                world.spawnParticles(ModParticles.THERMAL_POOF, at.x, at.y, at.z, 2, 0.15, 0.15, 0.15, 0.01);
                if (i % 3 == 0) world.spawnParticles(ParticleTypes.FALLING_LAVA, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.0);
                if (crumb != null && i % 2 == 0) world.spawnParticles(crumb, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }

        /**
         * It hits the token's face: a big splash (and whoever stands there: harmless, a start and a jolt of the view),
         * the prize given to the player, the others' told.
         */
        void splash() {
            Vec3d face = face();
            world.spawnParticles(ParticleTypes.EXPLOSION, face.x, face.y, face.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ModParticles.THERMAL_POOF, face.x, face.y, face.z, 24, 0.5, 0.4, 0.5, 0.06);
            world.spawnParticles(ParticleTypes.SPLASH, face.x, face.y, face.z, 60, 0.6, 0.4, 0.6, 0.3);
            world.spawnParticles(ParticleTypes.LAVA, face.x, face.y, face.z, 8, 0.3, 0.2, 0.3, 0.0);
            ItemStack prize = held[head];
            if (!prize.isEmpty()) world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, prize.copyWithCount(1)),
                    face.x, face.y, face.z, 16, 0.3, 0.3, 0.3, 0.15);
            world.playSound(null, face.x, face.y, face.z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.NEUTRAL, 1.2f, 0.8f);
            world.playSound(null, face.x, face.y, face.z, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.NEUTRAL, 0.8f, 1.4f);
            for (ServerPlayerEntity near : world.getPlayers(p -> !p.isSpectator() && p.getEyePos().distanceTo(face) <= SPLASH_REACH)) {
                splashed(near);
            }
            give(player());
        }

        /** {@code player} got splashed too: no harm, steam in his face, a start, a jolt of his view. */
        void splashed(ServerPlayerEntity player) {
            Vec3d eye = player.getEyePos().add(player.getRotationVector().multiply(0.4));
            player.networkHandler.sendPacket(new DamageTiltS2CPacket(player.getId(), 0));
            world.spawnParticles(player, ModParticles.THERMAL_POOF, true, eye.x, eye.y, eye.z, 14, 0.25, 0.2, 0.25, 0.02);
            world.spawnParticles(player, ParticleTypes.SPLASH, true, eye.x, eye.y, eye.z, 30, 0.3, 0.2, 0.3, 0.2);
            player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.0f, 1.2f);
            player.playSoundToPlayer(SoundEvents.ENTITY_PUFFER_FISH_BLOW_UP, SoundCategory.PLAYERS, 0.8f, 1.3f);
        }

        void give(@Nullable ServerPlayerEntity player) {
            ItemStack prize = held[head];
            Text name = player == null ? Text.literal("?") : player.getDisplayName();
            int moved = prize.isEmpty() || player == null ? 0 : handFromChests(player, prize);
            if (moved > 0) {
                ItemStack won = prize.copyWithCount(moved);
                if (!samePrizes) {
                    party.getPartyData().getTrichaudronWon().add(tile, keys[head]);
                    party.markDirty();
                    BoardSpaceBlockEntity space = ABoardSpaceBlock.getBoardSpaceEntity(world, tile);
                    if (space != null) TrichaudronTileBehavior.refreshSleep(world, space);
                }
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 0.8f);
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.4f);
                MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.trichaudron_space.won", prizeText(won))
                        .formatted(Formatting.GOLD), MessageUtils.MessageType.ACTION_BAR);
                BoardSequences.tell(party, Text.translatable("message.steveparty.trichaudron_space.won_by", name, prizeText(won))
                        .formatted(Formatting.GOLD), player);
            } else if (player != null) {
                // its chests emptied meanwhile: nothing given
                MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.trichaudron_space.gone")
                        .formatted(Formatting.GRAY), MessageUtils.MessageType.CHAT);
            }
            // The other heads' prizes, told (left in the chests)
            List<Text> others = new ArrayList<>();
            for (int h : actor.shownHeads()) if (h != head && !held[h].isEmpty()) others.add(prizeText(held[h]));
            if (!others.isEmpty()) BoardSequences.tell(party, Text.translatable("message.steveparty.trichaudron_space.others",
                    Texts.join(others, Text.literal(", "))).formatted(Formatting.GRAY), player);
        }

        /**
         * The prize taken out of the space's chests into the player's inventory (what doesn't fit falls at their feet):
         * the party's coins as coins (Double Coins: the extra taken from the chests too), power-ups within the party's
         * limit; never more than the chests hold. How many were given (0: none left).
         */
        int handFromChests(ServerPlayerEntity player, ItemStack prize) {
            BoardSpaceBlockEntity space = ABoardSpaceBlock.getBoardSpaceEntity(world, tile);
            if (space == null) return 0;
            ItemStack cartridge = space.getActiveCartridgeItemStack();
            PartyResources chests = cartridge.getItem() instanceof TrichaudronCartridgeItem
                    ? TrichaudronCartridgeItem.chests(cartridge, world, tile) : PartyResources.NONE;
            if (chests.isNone()) return 0;
            int count = PowerUpService.itemsGained(player, prize, prize.getCount()); // Double Coins
            int allowed = PowerUpLimit.allowed(player, prize.copyWithCount(count));
            if (allowed < count) PowerUpLimit.tellFull(player);
            if (allowed <= 0) return 0;
            int moved = CartridgeTransfers.transfer(prize.copyWithCount(allowed), chests, player);
            TrichaudronTileBehavior.refreshSleep(world, space);
            return moved;
        }

        MutableText prizeText(ItemStack prize) {
            return Text.translatable("message.steveparty.trichaudron_space.prize", prize.getCount(), prize.getName());
        }

        void place(TrichaudronEntity one, Vec3d at) {
            one.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
            one.setHeadYaw(yaw);
            one.setBodyYaw(yaw);
            one.setVelocity(Vec3d.ZERO);
        }

        void steam(Vec3d at, int count) {
            world.spawnParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, count, 1.4, 0.4, 1.4, 0.03);
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, Math.max(1, count / 4), 1.2, 0.3, 1.2, 0.01);
        }

        /** Over: the actors go, the turn goes on. */
        void finish(boolean completed) {
            if (!close()) return;
            if (actor != null && !actor.isRemoved()) {
                actor.setOnHeadPicked(null);
                steam(actor.getPos(), 20);
            }
            dismissActors();
            onDone.run();
        }
    }

    /**
     * The prizes won at each Trichaudron space during a party (TrichaudronCartridgeItem#prizeKey), so that a prize won
     * is no longer offered there for the rest of it (TrichaudronCartridgeItem#offered). Kept in the party's data, saved
     * with it, cleared when it starts and ends.
     */
    public static final class Won {
        /** NBT key in the party's data. */
        public static final String NBT_KEY = "TrichaudronWon";
        private final Map<BlockPos, Set<String>> won = new LinkedHashMap<>();

        /** The prizes won at the space at {@code pos} (read only). */
        public Set<String> wonAt(BlockPos pos) {
            Set<String> keys = won.get(pos);
            return keys == null ? Set.of() : Collections.unmodifiableSet(keys);
        }

        /** The prize {@code key} was won at {@code pos}. */
        public void add(BlockPos pos, String key) {
            won.computeIfAbsent(pos.toImmutable(), p -> new LinkedHashSet<>()).add(key);
        }

        public void reset() {
            won.clear();
        }

        /** Writes them under {@link #NBT_KEY} (nothing if none was won). */
        public void writeNbt(NbtCompound nbt) {
            if (won.isEmpty()) return;
            NbtList list = new NbtList();
            won.forEach((pos, keys) -> {
                NbtCompound entry = new NbtCompound();
                entry.putLong("Pos", pos.asLong());
                NbtList ids = new NbtList();
                keys.forEach(key -> ids.add(NbtString.of(key)));
                entry.put("Won", ids);
                list.add(entry);
            });
            nbt.put(NBT_KEY, list);
        }

        /** Reads what {@link #writeNbt} wrote (replaces the current ones). */
        public void readNbt(NbtCompound nbt) {
            won.clear();
            for (NbtElement element : nbt.getList(NBT_KEY, NbtElement.COMPOUND_TYPE)) {
                NbtCompound entry = (NbtCompound) element;
                Set<String> keys = new LinkedHashSet<>();
                for (NbtElement key : entry.getList("Won", NbtElement.STRING_TYPE)) keys.add(key.asString());
                if (!keys.isEmpty()) won.put(BlockPos.fromLong(entry.getLong("Pos")), keys);
            }
        }
    }
}
