package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;

/**
 * What the players hear and see when something moves on the board, decided by the server:
 * <ul>
 *     <li><b>pass</b>: a token of a running party goes over a tile: a soft pop (random pitch, so a run of steps
 *     patters), with the tile's twinkles (see {@link BoardSpaceBlockEntity#onTileReached});</li>
 *     <li><b>land</b>: a token stops on its destination (or is halted by a stop tile): a short jingle, a burst of
 *     particles and a ring pulse, themed by the tile's role ({@link Landing}, from its behaviour), and a notice in
 *     the action bar of the party's players;</li>
 *     <li><b>ambient</b>: outside a game, a token moved onto a tile (players walking make no sound): the same soft pop,
 *     only when it steps onto another tile and at most once per {@link #AMBIENT_COOLDOWN_TICKS}; no notice.</li>
 * </ul>
 * Vanilla sounds only (note blocks, chimes... layered and pitched). Particles go through
 * {@link ServerWorld#spawnParticles}, so every player around sees them.
 */
public final class TileFeedback {
    /** Ticks between two ambient pops of one entity (a sprinting player crosses a tile in about 4 ticks). */
    public static final int AMBIENT_COOLDOWN_TICKS = 5;
    private static final float POP_VOLUME = 0.22F;

    private TileFeedback() {
    }

    // ---------------------------------------------------------------- the landing kinds

    /** One sound of a jingle: played {@code delay} ticks after the landing. */
    public record Layer(SoundEvent sound, float volume, float pitch, int delay) {
        static Layer of(RegistryEntry<SoundEvent> sound, float volume, float pitch, int delay) {
            return new Layer(sound.value(), volume, pitch, delay);
        }
    }

    /** How a landing feels, by the tile's role (see {@link ABoardSpaceBehavior#landing}). */
    public enum Landing {
        /** A plain tile (or an unknown role): a pleasant two-note confirmation in the tile's colour. */
        DEFAULT("default", 0x7FD8FF, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.45F, 1.0F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.4F, 1.498F, 3),
                new Layer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.3F, 0))),
        /** A bonus (item given): a bright rising arpeggio and a coin-like sparkle, gold and blue sparkles. */
        GOOD("good", 0xFFD54A, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.0F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.26F, 2),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.498F, 4),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.55F, 2.0F, 6),
                new Layer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35F, 1.35F, 6))),
        /** A penalty (item taken): a falling "womp" and red puffs. */
        BAD("bad", 0xD42A2A, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.84F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.707F, 4),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.55F, 0.53F, 8),
                new Layer(SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.15F, 1.7F, 8))),
        /** An item tile with nothing to give or take yet: a light wooden two-step, orange sparkles. */
        ITEM("item", 0xFF9A1F, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.45F, 1.0F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.45F, 1.335F, 3),
                new Layer(SoundEvents.ENTITY_ITEM_PICKUP, 0.25F, 1.4F, 3))),
        /** A shop tile: a shop bell and a coin clink, lime green sparkles. */
        SHOP("shop", 0xA6E22E, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.498F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.45F, 2.0F, 3),
                new Layer(SoundEvents.ENTITY_VILLAGER_TRADE, 0.45F, 1.1F, 2),
                new Layer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3F, 1.6F, 5))),
        /** The start tile: a little pling fanfare and a few fireworks sparks. */
        START("start", 0xFFB347, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.45F, 0.749F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.45F, 1.0F, 3),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.45F, 1.26F, 6),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.55F, 1.498F, 9),
                new Layer(SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE_FAR, 0.3F, 1.2F, 9))),
        /** A stop tile: a firm two-tone halt, slate grey dust. */
        STOP("stop", 0x454B5A, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM, 0.5F, 1.0F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL, 0.5F, 1.0F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL, 0.5F, 0.749F, 4))),
        /** Move Forward (the token moves on): a quick rising run of chiptune notes over a piston push, green gusts. */
        ADVANCE("advance", 0x2DB84C, List.of(
                new Layer(SoundEvents.BLOCK_PISTON_EXTEND, 0.35F, 1.3F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.45F, 1.0F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.45F, 1.26F, 2),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.45F, 1.498F, 4),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.5F, 2.0F, 6))),
        /** Move Back (the token goes back): the same run falling, a piston pulling back, a pink "rewind". */
        BACK("back", 0xE23C9A, List.of(
                new Layer(SoundEvents.BLOCK_PISTON_CONTRACT, 0.35F, 0.9F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.45F, 1.498F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.45F, 1.26F, 2),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.45F, 1.0F, 4),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.5F, 0.749F, 6))),
        /** A Replay tile giving another turn: a bright rising arpeggio, then an "en-core!" two-note call; a cyan swirl. */
        REPLAY("replay", 0x1CC6D6, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.45F, 1.0F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.45F, 1.26F, 2),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.45F, 1.498F, 4),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.5F, 2.0F, 6),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.498F, 10),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.55F, 2.0F, 13),
                new Layer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.5F, 13))),
        /** A Replay tile reached by the replay move itself: no further turn, a soft falling two-step. */
        REPLAY_SPENT("replay_spent", 0x7FB8BF, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.35F, 1.498F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BIT, 0.35F, 1.0F, 3))),
        /** A star space: a twinkling rising chime, gold sparkles (buying the star has its own fanfare: see PartyStars). */
        STAR("star", 0xFFD83D, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.45F, 1.498F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.45F, 2.0F, 3),
                new Layer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.8F, 3))),
        /** A teleport tile: a flute whirl going down while the token spins away (see TileTeleport). */
        TELEPORT("teleport", 0xA35CFF, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE, 0.45F, 1.498F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE, 0.45F, 1.26F, 2),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE, 0.45F, 1.0F, 4),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE, 0.45F, 0.749F, 6),
                new Layer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5F, 1.4F, 0))),
        /** A Glandouille space: two grumpy woody stomps and a low bassoon "hmph" (see GlandouilleTileBehavior). */
        GLANDOUILLE("glandouille", 0x9A5A2A, List.of(
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.749F, 0),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.749F, 4),
                Layer.of(SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.45F, 1.0F, 8)));

        private final String key;
        /** Particle colour when the tile's own colour doesn't fit (plain white tile, or a themed burst). */
        private final int accent;
        private final List<Layer> layers;

        Landing(String key, int accent, List<Layer> layers) {
            this.key = key;
            this.accent = accent;
            this.layers = layers;
        }

        public List<Layer> layers() {
            return layers;
        }

        public int accent() {
            return accent;
        }

        public String noticeKey() {
            return "message.steveparty.tile_landed." + key;
        }
    }

    // ---------------------------------------------------------------- test probe

    public enum Kind { PASS, LAND, AMBIENT }

    /** What was played, for the GameTests. */
    public record Played(Kind kind, BlockPos tile, @Nullable Landing landing, int noticeRecipients) {
    }

    /** Notified of every feedback played (server thread). Empty in normal play. */
    public static final List<Consumer<Played>> LISTENERS = new CopyOnWriteArrayList<>();

    private static void report(Kind kind, BlockPos tile, @Nullable Landing landing, int recipients) {
        if (LISTENERS.isEmpty()) return;
        Played played = new Played(kind, tile.toImmutable(), landing, recipients);
        LISTENERS.forEach(listener -> listener.accept(played));
    }

    // ---------------------------------------------------------------- setup

    public static void initialize() {
        // Tokens moved outside a game (dice in free play, commands): a pop on each tile they reach
        TileReachedEvent.EVENT.register((token, tile) -> {
            if (token.getWorld() instanceof ServerWorld world && !isInRunningParty(token.getUuid())) {
                ambientStep(world, token, tile.getPos());
                freePlayRouterSignal(world, token, tile);
            }
            return ActionResult.PASS;
        });
        // A player walking on the board makes no sound: only tokens pop
    }

    /**
     * Outside a game there is no landing feedback, but a Router still tells its comparator where a token stops or goes
     * over (same rules as in a game: see {@link BoardSpaceBlockEntity#onTileReached}).
     */
    private static void freePlayRouterSignal(ServerWorld world, MobEntity token, BoardSpaceBlockEntity tile) {
        int steps = token instanceof fr.lordfinn.steveparty.entities.TokenizedEntityInterface tokenized ? tokenized.steveparty$getNbSteps() : 0;
        ABoardSpaceBehavior behavior = tile.getBoardSpaceBehavior();
        boolean stops = steps == 0 && (ABoardSpaceBlock.countsAsStep(tile.getCachedState().getBlock())
                || behavior != null && behavior.needToStop(world, tile.getPos()));
        if (stops) BoardSpaceRedstoneRouterBlockEntity.onTokenStopped(world, tile);
        else BoardSpaceRedstoneRouterBlockEntity.onTokenPassed(world, tile.getPos());
    }

    /** True if a party with a current step counts this token among its tokens: the party gives its feedback. */
    public static boolean isInRunningParty(UUID token) {
        for (PartyControllerEntity controller : PartyControllerEntity.getActivePartyControllers()) {
            if (!controller.isRemoved() && controller.getPartyData().getCurrentStep() != null
                    && controller.getPartyData().getTokens().contains(token)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- pass / ambient: the soft pop

    private static void pop(ServerWorld world, Vec3d at, float volume) {
        // A water-drop "plop" under a faint high pickup click; random pitch so a run of steps patters
        float pitch = 0.95F + world.random.nextFloat() * 0.35F;
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS,
                volume, pitch);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS,
                volume * 0.35F, 1.6F + pitch * 0.3F);
    }

    /** A token of a running party goes over this tile without stopping. */
    public static void pass(ServerWorld world, BlockPos tile) {
        pop(world, BoardSpaces.standPos(world, tile), POP_VOLUME);
        BoardSpaceRedstoneRouterBlockEntity.onTokenPassed(world, tile);
        report(Kind.PASS, tile, null, 0);
    }

    private static final class Tracked {
        @Nullable BlockPos tile;
        long lastPop = Long.MIN_VALUE / 2;
    }

    /** Last tile of each entity walking on the board (outside a game), weakly held. Server thread only. */
    private static final Map<Entity, Tracked> TRACKED = new WeakHashMap<>();

    /**
     * {@code entity} now stands on {@code tile} (null: not on a tile). Pops when it is another tile than the last one
     * and the entity's cooldown is over.
     *
     * @return true if it popped
     */
    public static boolean ambientStep(ServerWorld world, Entity entity, @Nullable BlockPos tile) {
        Tracked tracked = TRACKED.get(entity);
        if (tracked == null) {
            if (tile == null) return false; // nothing to remember for an entity off the board
            tracked = new Tracked();
            TRACKED.put(entity, tracked);
        }
        BlockPos previous = tracked.tile;
        tracked.tile = tile == null ? null : tile.toImmutable();
        if (tile == null || tile.equals(previous)) return false;
        long now = world.getTime();
        if (now - tracked.lastPop < AMBIENT_COOLDOWN_TICKS) return false;
        tracked.lastPop = now;
        pop(world, BoardSpaces.standPos(world, tile), POP_VOLUME * 0.8F);
        report(Kind.AMBIENT, tile, null, 0);
        return true;
    }

    // ---------------------------------------------------------------- land

    /** The landing kind of this tile: its behaviour decides (a tile without cartridge acts as a default one). */
    public static Landing landingOf(BoardSpaceBlockEntity tile) {
        ItemStack stack = tile.getActiveCartridgeItemStack();
        ABoardSpaceBehavior behavior = tile.getBoardSpaceBehavior(stack);
        if (behavior == null) return Landing.DEFAULT;
        Landing landing = behavior.landing(tile, stack == null ? ItemStack.EMPTY : stack);
        return landing == null ? Landing.DEFAULT : landing;
    }

    /**
     * A token stopped on this tile: the jingle (to everyone around), the burst and the ring pulse, and the notice to
     * the party's players (none without a party).
     */
    public static void land(ServerWorld world, BoardSpaceBlockEntity tile, MobEntity token, @Nullable PartyControllerEntity party) {
        Landing landing = landingOf(tile);
        land(world, tile, token, party, landing, landing.noticeKey());
    }

    /**
     * A landing of the given kind (not necessarily the tile's own) with the notice {@code noticeKey}, whose arguments
     * are the token's name then {@code noticeArgs}.
     */
    public static void land(ServerWorld world, BoardSpaceBlockEntity tile, MobEntity token, @Nullable PartyControllerEntity party,
                            Landing landing, String noticeKey, Object... noticeArgs) {
        BlockPos pos = tile.getPos();
        Vec3d at = BoardSpaces.standPos(world, pos);

        for (Layer layer : landing.layers()) {
            if (layer.delay() <= 0) {
                world.playSound(null, at.x, at.y, at.z, layer.sound(), SoundCategory.BLOCKS, layer.volume(), layer.pitch());
            } else {
                SCHEDULER.schedule(UUID.randomUUID(), layer.delay(), () ->
                        world.playSound(null, at.x, at.y, at.z, layer.sound(), SoundCategory.BLOCKS, layer.volume(), layer.pitch()));
            }
        }

        int tileColor = tileColor(tile);
        int color = tileColor == 0xFFFFFF ? landing.accent() : tileColor;
        // Lighter than the face, so the ring and the sparkles read over it
        int light = lighten(color, 0.45F);
        double radius = ringRadius(world, pos);
        burst(world, landing, at, color, light);
        ring(world, at, light, radius);
        SCHEDULER.schedule(UUID.randomUUID(), 4, () -> ring(world, at, light, radius * 1.3));

        int recipients = 0;
        if (party != null && !party.isRemoved()) {
            List<ServerPlayerEntity> audience = party.getPartyAudience();
            Text name = token.getCustomName() != null ? token.getCustomName() : token.getName();
            Object[] args = new Object[noticeArgs.length + 1];
            args[0] = name;
            System.arraycopy(noticeArgs, 0, args, 1, noticeArgs.length);
            Text notice = Text.translatable(noticeKey, args)
                    .styled(style -> style.withColor(TextColor.fromRgb(noticeColor(color))));
            MessageUtils.sendToPlayers(audience, notice, MessageUtils.MessageType.ACTION_BAR);
            recipients = audience.size();
        }
        BoardSpaceRedstoneRouterBlockEntity.onTokenStopped(world, tile);
        report(Kind.LAND, pos, landing, recipients);
    }

    /** The colour of the tile's face (its active cartridge's), white if none. */
    public static int tileColor(BoardSpaceBlockEntity tile) {
        ItemStack stack = tile.getActiveCartridgeItemStack();
        if (stack == null || stack.isEmpty()) return 0xFFFFFF;
        return stack.getOrDefault(ModComponents.COLOR, 0xFFFFFF) & 0xFFFFFF;
    }

    private static double ringRadius(ServerWorld world, BlockPos pos) {
        if (world.getBlockState(pos).getBlock() instanceof ATileBlock) {
            return switch (world.getBlockState(pos).get(ATileBlock.SIZE).size()) {
                case LARGE -> 1.15;
                case SMALL -> 0.4;
                default -> 0.62;
            };
        }
        return 0.62;
    }

    /** The notice's text colour: the tile's colour a little lighter, much lighter for a dark one (the Stop anthracite). */
    public static int noticeColor(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        boolean dark = 0.299F * r + 0.587F * g + 0.114F * b < 100;
        return lighten(color, dark ? 0.6F : 0.2F);
    }

    /** {@code color} moved toward white by {@code amount} (0..1). */
    public static int lighten(int color, float amount) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }

    /** A thin ring of dust just around the tile's edge: the tile "pulses". */
    private static void ring(ServerWorld world, Vec3d at, int color, double radius) {
        DustParticleEffect dust = new DustParticleEffect(Vec3d.unpackRgb(color).toVector3f(), 1.1F);
        int points = radius > 0.8 ? 28 : 18;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            world.spawnParticles(dust, at.x + Math.cos(angle) * radius, at.y + 0.08, at.z + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    private static void burst(ServerWorld world, Landing landing, Vec3d at, int color, int light) {
        double y = at.y + 0.25;
        switch (landing) {
            case GOOD -> {
                world.spawnParticles(new MulaSparkleEffect(0xFFD54A, 1.4F, MulaSparkleEffect.STAR_BIT), at.x, y, at.z, 9, 0.3, 0.2, 0.3, 0.0);
                world.spawnParticles(new MulaSparkleEffect(color == landing.accent() ? 0x9FE3FF : light, 1.2F, MulaSparkleEffect.TWINKLE), at.x, y + 0.3, at.z, 10, 0.35, 0.3, 0.35, 0.0);
                world.spawnParticles(ParticleTypes.WAX_OFF, at.x, y + 0.3, at.z, 5, 0.3, 0.3, 0.3, 0.3);
            }
            case BAD -> {
                world.spawnParticles(new DustParticleEffect(Vec3d.unpackRgb(lighten(landing.accent(), 0.25F)).toVector3f(), 1.8F), at.x, y + 0.2, at.z, 14, 0.35, 0.2, 0.35, 0.0);
                world.spawnParticles(ParticleTypes.SMOKE, at.x, y, at.z, 5, 0.3, 0.05, 0.3, 0.01);
                world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, at.x, y + 0.6, at.z, 1, 0.1, 0.1, 0.1, 0.0);
            }
            case START -> {
                world.spawnParticles(ParticleTypes.FIREWORK, at.x, y + 0.2, at.z, 12, 0.15, 0.2, 0.15, 0.08);
                world.spawnParticles(new MulaSparkleEffect(light, 1.3F, MulaSparkleEffect.STAR_BIT), at.x, y, at.z, 8, 0.35, 0.2, 0.35, 0.0);
            }
            case STOP -> {
                world.spawnParticles(new DustParticleEffect(Vec3d.unpackRgb(light).toVector3f(), 1.3F), at.x, y, at.z, 10, 0.3, 0.1, 0.3, 0.0);
                world.spawnParticles(ParticleTypes.CRIT, at.x, y + 0.2, at.z, 6, 0.25, 0.2, 0.25, 0.1);
            }
            case ADVANCE -> {
                world.spawnParticles(new DustParticleEffect(Vec3d.unpackRgb(light).toVector3f(), 1.4F), at.x, y, at.z, 12, 0.35, 0.1, 0.35, 0.0);
                world.spawnParticles(new MulaSparkleEffect(0xB8FFC4, 1.2F, MulaSparkleEffect.STAR_BIT), at.x, y + 0.2, at.z, 8, 0.3, 0.25, 0.3, 0.0);
                world.spawnParticles(ParticleTypes.SMALL_GUST, at.x, y + 0.1, at.z, 3, 0.3, 0.05, 0.3, 0.0);
            }
            case BACK -> {
                world.spawnParticles(new DustParticleEffect(Vec3d.unpackRgb(light).toVector3f(), 1.4F), at.x, y, at.z, 12, 0.35, 0.1, 0.35, 0.0);
                world.spawnParticles(new MulaSparkleEffect(light, 1.2F, MulaSparkleEffect.TWINKLE), at.x, y + 0.2, at.z, 10, 0.3, 0.3, 0.3, 0.0);
            }
            case REPLAY -> {
                // A cyan swirl climbing around the token (the pictogram's circular arrow), and happy sparks
                DustParticleEffect swirl = new DustParticleEffect(Vec3d.unpackRgb(light).toVector3f(), 1.2F);
                for (int i = 0; i < 24; i++) {
                    double angle = Math.PI * 3 * i / 24;
                    world.spawnParticles(swirl, at.x + Math.cos(angle) * 0.5, y + i * 0.05, at.z + Math.sin(angle) * 0.5, 1, 0, 0, 0, 0);
                }
                world.spawnParticles(ParticleTypes.GLOW, at.x, y + 0.4, at.z, 8, 0.35, 0.3, 0.35, 0.0);
                world.spawnParticles(new MulaSparkleEffect(0xB8F4FF, 1.3F, MulaSparkleEffect.STAR_BIT), at.x, y + 1.2, at.z, 6, 0.2, 0.1, 0.2, 0.0);
            }
            case REPLAY_SPENT -> world.spawnParticles(new DustParticleEffect(Vec3d.unpackRgb(light).toVector3f(), 1.0F), at.x, y, at.z, 8, 0.3, 0.1, 0.3, 0.0);
            case TELEPORT -> {
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, at.x, y, at.z, 16, 0.3, 0.1, 0.3, 0.02);
                world.spawnParticles(new MulaSparkleEffect(light, 1.2F, MulaSparkleEffect.TWINKLE), at.x, y + 0.2, at.z, 8, 0.35, 0.25, 0.35, 0.0);
            }
            case GLANDOUILLE -> {
                world.spawnParticles(ParticleTypes.POOF, at.x, y, at.z, 8, 0.35, 0.1, 0.35, 0.02);
                world.spawnParticles(new DustParticleEffect(Vec3d.unpackRgb(light).toVector3f(), 1.3F), at.x, y, at.z, 10, 0.3, 0.1, 0.3, 0.0);
            }
            case ITEM, DEFAULT -> world.spawnParticles(new MulaSparkleEffect(light, 1.2F, MulaSparkleEffect.TWINKLE),
                    at.x, y + 0.1, at.z, 12, 0.35, 0.25, 0.35, 0.0);
        }
    }
}
