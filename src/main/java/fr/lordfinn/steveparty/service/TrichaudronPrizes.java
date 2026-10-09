package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.TrichaudronCartridgeItem;
import fr.lordfinn.steveparty.powerups.PowerUpLimit;
import fr.lordfinn.steveparty.powerups.PowerUpService;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.MessageUtils;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.InventoryInteractorTileBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.TrichaudronTileBehavior;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.inventory.Inventory;
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
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.lordfinn.steveparty.service.BoardSequences.yawToward;

/**
 * What a Trichaudron space does (see TrichaudronTileBehavior): the Trichaudron rises from the ground beside the token
 * in a cloud of steam, its three heads dive into its tank and each comes out with a hidden prize drawn from the
 * cartridge's linked chests (what they really hold, filtered by its menu; empty heads once fewer than three). A slow die rolls under the heads, cycling 1, 2, 3
 * (left, centre, right head; the head its face points to glows); the token's player hits it to stop it (left alone it
 * stops by itself). The chosen head turns to the player and gives its prize, taken out of the chests; the other heads
 * show what they held, then the Trichaudron sinks back into a puff of steam and the turn goes on.
 * <p>
 * The Trichaudron and its die are board actors: invulnerable, never saved, always removed at the end
 * ({@link BoardActors}), whatever ends it (party over, server stopping). Server thread only.
 */
public final class TrichaudronPrizes {
    public enum Phase { RISE, DIVE, EMERGE, CHOOSE, GIVE, LEAVE }

    public enum Start { STARTED, NO_PLAYER, EMPTY }

    /** Timing (ticks). */
    public static final int RISE_TICKS = 30, DIVE_TICKS = 40, EMERGE_TICKS = 20, GIVE_TICKS = 50, LEAVE_TICKS = 30;
    /** Choosing: at most this long (the die stops by itself before; then a head at random). */
    public static final int CHOOSE_TICKS = 300;
    /** In the give phase, when the chosen head hands its prize over. */
    public static final int GIVE_AT = 16;
    /** The whole show, at most (for the tests). */
    public static final int WHOLE = RISE_TICKS + DIVE_TICKS + EMERGE_TICKS + CHOOSE_TICKS + GIVE_TICKS + LEAVE_TICKS;
    /** The die's faces 1, 2, 3, left to right: the left head, the centre one, the right one ({@link TrichaudronEntity#HEADS}). */
    public static final int[] HEAD_OF_FACE = {1, 0, 2};
    /** Where it stands (blocks to the token's right), its heads reaching over beside the token; the die, ahead of it. */
    private static final double SIDE = 11.0, DIE_AHEAD = 9.5, DIE_UP = 1.2;

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

    public static @Nullable DiceEntity die(MobEntity token) {
        Show show = RUNNING.get(token);
        return show == null ? null : show.die;
    }

    /** What each face's head holds (face 1 to 3, an empty stack for an empty head); empty when no show. */
    public static List<ItemStack> heads(MobEntity token) {
        Show show = RUNNING.get(token);
        if (show == null) return List.of();
        return Arrays.stream(show.held).map(ItemStack::copy).toList();
    }

    /** Tests: the die stops on {@code face} (1 to 3) as if the player hit it there. */
    public static void pick(MobEntity token, int face) {
        Show show = RUNNING.get(token);
        if (show != null && show.phase == Phase.CHOOSE) show.chosen(face);
    }

    /** The die of the choice: faces 1, 2, 3 and the Slow module (its player stops it). */
    public static ItemStack choiceDie() {
        ItemStack die = new ItemStack(ModItems.DEFAULT_DICE);
        die.set(DiceFacesComponent.TYPE, new DiceFacesComponent(List.of(new DiceFace(DiceFacesComponent.Kind.NORMAL, 1),
                new DiceFace(DiceFacesComponent.Kind.NORMAL, 2), new DiceFace(DiceFacesComponent.Kind.NORMAL, 3))));
        return DiceModules.set(die, Map.of(DiceModules.SLOW, 1));
    }

    /**
     * {@code token} stopped on the Trichaudron space {@code tile} of {@code party}: the show starts, its prizes drawn
     * from the tile's cartridge. {@code onDone} runs when it is over. Nothing happens (and {@code onDone} is not called)
     * without the token's player online, or with nothing to give in its chests.
     */
    public static Start start(ServerWorld world, BlockPos tile, MobEntity token, PartyControllerEntity party, Runnable onDone) {
        if (RUNNING.isRunning(token)) return Start.STARTED;
        BoardSpaceBlockEntity space = ABoardSpaceBlock.getBoardSpaceEntity(world, tile);
        // What its chests really hold (never anything made from nothing)
        List<ItemStack> stock = space == null ? List.of() : TrichaudronCartridgeItem.available(space.getActiveCartridgeItemStack(), world);
        if (space != null) TrichaudronTileBehavior.refreshSleep(space, !stock.isEmpty());
        if (stock.isEmpty()) return Start.EMPTY;
        ServerPlayerEntity player = BoardSequences.tokenPlayer(world, token);
        if (player == null) {
            BoardSequences.tell(party, Text.translatable("message.steveparty.trichaudron_space.no_player").formatted(Formatting.GRAY));
            return Start.NO_PLAYER;
        }
        // Three heads, each a prize drawn from the stock (never the same one twice), empty past them
        java.util.Random random = new java.util.Random(world.getRandom().nextLong());
        List<ItemStack> drawn = new ArrayList<>(stock);
        Collections.shuffle(drawn, random);
        List<ItemStack> heads = new ArrayList<>(drawn.subList(0, Math.min(3, drawn.size())));
        while (heads.size() < 3) heads.add(ItemStack.EMPTY);
        Collections.shuffle(heads, random); // an empty head may be any of them
        ItemStack[] held = heads.toArray(ItemStack[]::new);
        Show show = new Show(world, tile.toImmutable(), token, player.getUuid(), party, held, onDone);
        if (!show.spawn()) return Start.EMPTY;
        RUNNING.run(show, show::tick);
        return Start.STARTED;
    }

    /** One show: the Trichaudron, its die, the prizes in its heads. */
    private static final class Show extends BoardSequences.Sequence {
        final BlockPos tile;
        final UUID playerId;
        final PartyControllerEntity party;
        /** The prize of each face's head (face 1 to 3), empty for an empty head. */
        final ItemStack[] held;
        TrichaudronEntity actor;
        @Nullable DiceEntity die;
        Phase phase = Phase.RISE;
        int phaseTick;
        /** The face the die stopped on (1 to 3), 0 until then. */
        int face;
        Vec3d seat = Vec3d.ZERO, diePos = Vec3d.ZERO;
        float yaw;

        Show(ServerWorld world, BlockPos tile, MobEntity token, UUID playerId, PartyControllerEntity party,
             ItemStack[] held, Runnable onDone) {
            super(world, token, onDone);
            this.tile = tile;
            this.playerId = playerId;
            this.party = party;
            this.held = held;
        }

        @Nullable ServerPlayerEntity player() {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
            return player == null || player.isDisconnected() ? null : player;
        }

        /** It appears under the ground on the token's right, facing the token: its heads will reach over beside it. */
        boolean spawn() {
            TrichaudronEntity one = ModEntities.TRICHAUDRON.create(world);
            if (one == null) return false;
            cast(one);
            Vec3d stand = BoardSpaces.standPos(world, tile);
            Vec3d right = Vec3d.fromPolar(0, token.getYaw() + 90);
            seat = stand.add(right.multiply(SIDE));
            yaw = yawToward(seat, stand);
            diePos = seat.add(Vec3d.fromPolar(0, yaw).multiply(DIE_AHEAD)).add(0, DIE_UP, 0);
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
                case GIVE -> tickGive();
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

        /** The heads come out, dripping, each holding something (or not: nobody knows yet). */
        void tickEmerge() {
            if (phaseTick == EMERGE_TICKS / 2) {
                for (int head = 0; head < TrichaudronEntity.HEADS.length; head++) {
                    Vec3d at = actor.nozzle(head);
                    world.spawnParticles(ParticleTypes.DRIPPING_LAVA, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);
                    world.spawnParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.02);
                }
                world.playSound(null, seat.x, seat.y, seat.z, ModSounds.TRICHAUDRON_PUFF, SoundCategory.NEUTRAL, 1.0f, 0.8f);
            }
            if (phaseTick >= EMERGE_TICKS) {
                spawnDie();
                go(Phase.CHOOSE);
            }
        }

        /** The slow die, under the heads, rolled for the token's player: its result picks a head. */
        void spawnDie() {
            DiceEntity one = ModEntities.DICE_ENTITY.create(world);
            ServerPlayerEntity player = player();
            if (player != null) {
                MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.trichaudron_space.title")
                        .formatted(Formatting.GOLD), MessageUtils.MessageType.TITLE);
                MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.trichaudron_space.hit")
                        .formatted(Formatting.YELLOW), MessageUtils.MessageType.ACTION_BAR);
            }
            if (one == null) return; // a head at random when the time is up
            castProp(one);
            one.rollForShow(choiceDie(), playerId, faces -> {
                int value = faces.isEmpty() ? 0 : faces.getFirst().value();
                chosen(value >= 1 && value <= 3 ? value : 1 + world.getRandom().nextInt(3));
            });
            one.refreshPositionAndAngles(diePos.x, diePos.y, diePos.z, yaw, 0);
            one.setNoGravity(true);
            world.spawnEntity(one);
            die = one;
            one.startRoll();
            for (int head = 0; head < TrichaudronEntity.HEADS.length; head++) actor.setHeadTarget(head, one);
        }

        /** The head the die's face points to glows; the die gone (or too long): a head at random. */
        void tickChoose() {
            if (die != null && !die.isRemoved()) {
                int shown = die.getRollValue();
                for (int value = 1; value <= 3; value++) {
                    actor.setVent(HEAD_OF_FACE[value - 1], value == shown ? TrichaudronEntity.VENT_CHARGING : TrichaudronEntity.VENT_IDLE);
                }
            }
            if ((die == null || die.isRemoved()) && phaseTick > 1 || phaseTick >= CHOOSE_TICKS) {
                chosen(1 + world.getRandom().nextInt(3));
            }
        }

        /** The die stopped on {@code value} (1 to 3): that head will give its prize. */
        void chosen(int value) {
            if (done || phase != Phase.CHOOSE) return;
            face = value;
            go(Phase.GIVE);
        }

        /** The chosen head turns to the player and gives its prize; the others show what they held. */
        void tickGive() {
            int head = HEAD_OF_FACE[face - 1];
            ServerPlayerEntity player = player();
            if (phaseTick == 1) {
                for (int h = 0; h < TrichaudronEntity.HEADS.length; h++) {
                    actor.setVent(h, TrichaudronEntity.VENT_IDLE);
                    actor.setHeadTarget(h, h == head ? (player != null ? player : token) : null);
                }
                world.playSound(null, seat.x, seat.y, seat.z, ModSounds.TRICHAUDRON_AMBIENT, SoundCategory.NEUTRAL, 1.0f, 1.2f);
            }
            if (phaseTick == GIVE_AT) give(player, head);
            if (phaseTick >= GIVE_TICKS) {
                for (int h = 0; h < TrichaudronEntity.HEADS.length; h++) actor.setHeadTarget(h, null);
                go(Phase.LEAVE);
            }
        }

        void give(@Nullable ServerPlayerEntity player, int head) {
            ItemStack prize = held[face - 1];
            Vec3d from = actor.nozzle(head);
            Text name = player == null ? Text.literal("?") : player.getDisplayName();
            boolean given = false;
            int moved = prize.isEmpty() || player == null ? 0 : handFromChests(player, prize);
            if (moved > 0) {
                prize = prize.copyWithCount(moved);
                given = true;
                Vec3d them = player.getPos().add(0, player.getHeight() * 0.6, 0);
                Vec3d way = them.subtract(from);
                ItemStackParticleEffect crumb = new ItemStackParticleEffect(ParticleTypes.ITEM, prize.copyWithCount(1));
                for (int i = 0; i < 10; i++) {
                    world.spawnParticles(crumb, from.x, from.y, from.z, 0, way.x, way.y + 0.4, way.z, 0.15 + world.getRandom().nextDouble() * 0.05);
                }
                world.playSound(null, them.x, them.y, them.z, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 0.8f);
                world.playSound(null, them.x, them.y, them.z, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.4f);
                MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.trichaudron_space.won", prizeText(prize))
                        .formatted(Formatting.GOLD), MessageUtils.MessageType.ACTION_BAR);
                BoardSequences.tell(party, Text.translatable("message.steveparty.trichaudron_space.won_by", name, prizeText(prize))
                        .formatted(Formatting.GOLD), player);
            } else {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 12, 0.2, 0.2, 0.2, 0.02);
                world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.NEUTRAL, 0.8f, 0.7f);
                if (player != null) MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.trichaudron_space.empty_head")
                        .formatted(Formatting.GRAY), MessageUtils.MessageType.ACTION_BAR);
                BoardSequences.tell(party, Text.translatable("message.steveparty.trichaudron_space.empty_head_of", name)
                        .formatted(Formatting.GRAY), player);
            }
            // The other heads show what they held (left in the chests)
            List<Text> others = new ArrayList<>();
            for (int value = 1; value <= 3; value++) {
                if (value == face) continue;
                ItemStack other = held[value - 1];
                others.add(other.isEmpty() ? Text.translatable("message.steveparty.trichaudron_space.nothing") : prizeText(other));
                Vec3d at = actor.nozzle(HEAD_OF_FACE[value - 1]);
                if (!other.isEmpty()) world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, other.copyWithCount(1)),
                        at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.05);
            }
            BoardSequences.tell(party, Text.translatable("message.steveparty.trichaudron_space.others", others.get(0), others.get(1))
                    .formatted(Formatting.GRAY), player);
            if (!given && !prize.isEmpty() && player != null) {
                // its chests emptied meanwhile: nothing given
                MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.trichaudron_space.gone")
                        .formatted(Formatting.GRAY), MessageUtils.MessageType.CHAT);
            }
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
            Inventory chests = cartridge.getItem() instanceof TrichaudronCartridgeItem ? TrichaudronCartridgeItem.chests(cartridge, world) : null;
            if (chests == null) return 0;
            int count = PowerUpService.itemsGained(player, prize, prize.getCount()); // Double Coins
            int allowed = PowerUpLimit.allowed(player, prize.copyWithCount(count));
            if (allowed < count) PowerUpLimit.tellFull(player);
            if (allowed <= 0) return 0;
            int moved = InventoryInteractorTileBehavior.extractMatching(prize.copyWithCount(allowed), chests, toMove -> {
                int given = toMove.getCount();
                player.getInventory().offerOrDrop(toMove);
                return given;
            });
            player.getInventory().markDirty();
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
            if (actor != null && !actor.isRemoved()) steam(actor.getPos(), 20);
            dismissActors();
            onDone.run();
        }
    }
}
