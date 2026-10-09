package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.TeleportTileBehavior;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TeleportNetwork;
import fr.lordfinn.steveparty.components.TeleportSettingsComponent;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.utils.Argb;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;

/**
 * The « Téléportation » tile: a token that <b>lands</b> on it (its move ends there) is sent to another
 * Teleport tile of the same network (the colour of its cartridge, see {@link TeleportNetwork}) on the same board,
 * picked at random or in turn.
 * <ul>
 *     <li>the token spins and shrinks into a swirl of sparkles in the network's colour, vanishes with a chorus "whoop",
 *     and pops back on the other tile, where tokens stand on it (lowered, sloped and large tiles included:
 *     {@link BoardSpaces#standPos});</li>
 *     <li>there it stays (its move ends on the arrival tile, which never sends it on: no chains), or, if the cartridge
 *     says so, it is pushed one space further along the arrival tile's path (the owner chooses at a fork), where its
 *     move ends; that space triggers its effect or not, as the cartridge says (a Teleport tile never does);</li>
 *     <li>a tile alone in its network sends no one; a token only going over the tile is not teleported.</li>
 * </ul>
 * In a party, the turn goes on once the token has reappeared (or landed after the push: {@link TeleportTileBehavior});
 * in free play (dice outside a party) a token ending its move there is teleported too.
 */
public final class TileTeleport {
    /** Default colour of a teleport tile: the violet network's. */
    public static final int COLOR = TeleportNetwork.VIOLET.color();
    /** The cyan of the swirl and of the pop. */
    public static final int ACCENT = 0x5FE6FF;
    /** Ticks of the animation: shrinking and spinning, gone, popping back. */
    public static final int SHRINK_TICKS = 16, GONE_TICKS = 5, GROW_TICKS = 7;
    public static final int TOTAL_TICKS = SHRINK_TICKS + GONE_TICKS + GROW_TICKS;
    /** How far around the tile the board is read to find the other tiles of its network. */
    public static final int NETWORK_RANGE = 96;
    /** Smallest size of the token (fraction of its own) while it is away. */
    private static final double MIN_SCALE = 0.05;
    private static final Identifier SHRINK_MODIFIER = Steveparty.id("teleport_shrink");

    /** Tokens being teleported, with the task of their animation (server thread). */
    private static final Map<UUID, UUID> TELEPORTING = ServerMemory.forgetOnStop(new HashMap<>());
    /**
     * Tokens pushed one space on after a teleport, until they land: whether that space triggers its effect (server
     * thread, not saved: a restart in the middle ends it as an ordinary move).
     */
    private static final Map<UUID, Boolean> PUSHED = ServerMemory.forgetOnStop(new HashMap<>());

    /** A teleport, for the GameTests: the token, where it left from and where it arrived. Empty in normal play. */
    public record Teleported(UUID token, BlockPos from, BlockPos to) {
    }

    public static final List<Consumer<Teleported>> LISTENERS = new CopyOnWriteArrayList<>();

    private TileTeleport() {
    }

    /** Free play: a token ending its move on a teleport tile outside a party is teleported too. */
    public static void initialize() {
        TileReachedEvent.EVENT.register((token, tile) -> {
            if (token.getWorld() instanceof ServerWorld world && tile != null && lands(token, tile)
                    && !TileFeedback.isInRunningParty(token.getUuid())) {
                // Pushed after a teleport: it stops there (no chains)
                boolean pushed = PUSHED.remove(token.getUuid()) != null;
                if (!pushed && tile.getBoardSpaceBehavior() instanceof TeleportTileBehavior) {
                    ItemStack cartridge = tile.getActiveCartridgeItemStack();
                    BlockPos target = pick(world, tile, cartridge);
                    if (target != null) {
                        TeleportSettingsComponent settings = settings(cartridge);
                        teleport(world, token, tile.getPos(), target, settings.network().color(), () -> {
                            if (settings.push()) push(world, token, target, settings.pushTriggers());
                        });
                    }
                }
            }
            return ActionResult.PASS;
        });
    }

    /** The token's move ends on {@code tile} (a tile, not a check point), and it isn't being teleported. */
    private static boolean lands(MobEntity token, BoardSpaceBlockEntity tile) {
        return token instanceof TokenizedEntityInterface tokenized && tokenized.steveparty$getNbSteps() == 0
                && ABoardSpaceBlock.countsAsStep(tile.getCachedState().getBlock())
                && !isTeleporting(token);
    }

    // ---------------------------------------------------------------- settings and network

    public static TeleportSettingsComponent settings(@Nullable ItemStack cartridge) {
        if (cartridge == null || cartridge.isEmpty()) return TeleportSettingsComponent.DEFAULT;
        return cartridge.getOrDefault(ModComponents.TELEPORT_SETTINGS, TeleportSettingsComponent.DEFAULT);
    }

    /**
     * The other Teleport tiles of {@code tile}'s network on its board (see {@link BoardGraph#teleportPartners}), in
     * loaded chunks (never loads one) within {@link #NETWORK_RANGE} blocks, in a stable order.
     */
    public static List<BlockPos> partners(World world, BlockPos tile) {
        return BoardGraph.collect(world, tile, NETWORK_RANGE).teleportPartners(tile.toImmutable());
    }

    /**
     * Where a token landing on {@code tile} now is sent: at random, or the next one in turn (the turn is kept by the
     * tile, per cartridge slot). Null if it is alone in its network.
     */
    public static @Nullable BlockPos pick(ServerWorld world, BoardSpaceBlockEntity tile, @Nullable ItemStack cartridge) {
        List<BlockPos> partners = partners(world, tile.getPos());
        if (partners.isEmpty()) return null;
        if (!settings(cartridge).cycle()) return partners.get(world.random.nextInt(partners.size()));
        int index = Math.floorMod(tile.getCycleIndex(), partners.size());
        tile.setCycleIndex((index + 1) % partners.size());
        return partners.get(index);
    }

    // ---------------------------------------------------------------- pushed one space on

    /**
     * After a teleport, with the "move on one space" option: the token walks one space along the arrival tile's path
     * (an ordinary move of one step: the owner chooses at a fork, a Stop space halts it), where its move ends.
     *
     * @return false if the arrival tile leads nowhere (the token stays there)
     */
    public static boolean push(ServerWorld world, MobEntity token, BlockPos arrival, boolean triggers) {
        BoardSpaceBlockEntity tile = ABoardSpaceBlock.getBoardSpaceEntity(world, arrival);
        if (tile == null || tile.getStockedDestinations().stream().noneMatch(BoardSpaceDestination::isTile)) return false;
        PUSHED.put(token.getUuid(), triggers);
        if (AdvanceBackMoves.launch(world, token, arrival, 1) == 0) {
            PUSHED.remove(token.getUuid());
            return false;
        }
        return true;
    }

    /** True while the token is pushed one space on after a teleport (until it lands). */
    public static boolean isPushed(MobEntity token) {
        return PUSHED.containsKey(token.getUuid());
    }

    /**
     * The token lands (in a party) where {@code behavior} would play: if it was pushed there after a teleport, that
     * ends the push.
     *
     * @return true if that space must not trigger its effect (the option says no, or it is a Teleport tile: no chains)
     */
    public static boolean endPush(MobEntity token, @Nullable ABoardSpaceBehavior behavior) {
        Boolean triggers = PUSHED.remove(token.getUuid());
        return triggers != null && (!triggers || behavior instanceof TeleportTileBehavior);
    }

    /** A new move of the token (a dice roll): nothing left of a push. */
    public static void cancelPush(MobEntity token) {
        PUSHED.remove(token.getUuid());
    }

    // ---------------------------------------------------------------- the teleport

    public static boolean isTeleporting(MobEntity token) {
        return TELEPORTING.containsKey(token.getUuid());
    }

    /**
     * The party of the token was stopped: its teleport stops where it is (back to its size, nothing more happens: no
     * arrival, no push).
     */
    public static void cancel(MobEntity token) {
        PUSHED.remove(token.getUuid());
        UUID task = TELEPORTING.remove(token.getUuid());
        if (task == null) return;
        SCHEDULER.cancel(task);
        setScale(token, 1);
    }

    /**
     * Sends {@code token} from the tile at {@code from} to the board space at {@code to}, with the warp animation.
     * {@code onArrived} runs once it has reappeared there (also if the token went away meanwhile: the game goes on).
     */
    public static void teleport(ServerWorld world, MobEntity token, BlockPos from, BlockPos to, int color, Runnable onArrived) {
        UUID id = token.getUuid();
        if (TELEPORTING.containsKey(id)) return;
        Vec3d start = BoardSpaces.standPos(world, from), end = BoardSpaces.standPos(world, to);
        float yaw = token.getYaw();
        int[] tick = {0};
        UUID task = UUID.randomUUID();
        TELEPORTING.put(id, task);
        world.playSound(null, start.x, start.y, start.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.3F, 1.5F);
        SCHEDULER.repeat(task, 1, () -> {
            int t = ++tick[0];
            if (t <= SHRINK_TICKS) shrinking(world, token, start, yaw, t, color);
            else if (t <= SHRINK_TICKS + GONE_TICKS) gone(world, token, start, end, t - SHRINK_TICKS, color);
            else growing(world, token, end, t - SHRINK_TICKS - GONE_TICKS, color);
        }, () -> tick[0] < TOTAL_TICKS && !token.isRemoved(), () -> {
            TELEPORTING.remove(id);
            setScale(token, 1);
            if (!token.isRemoved()) {
                // Standing on the arrival for good (a token gone meanwhile still lets the game go on)
                token.requestTeleport(end.x, end.y, end.z);
                token.setVelocity(Vec3d.ZERO);
                TokenMovementService.faceYaw(token, yaw);
            }
            if (!LISTENERS.isEmpty()) {
                Teleported teleported = new Teleported(id, from.toImmutable(), to.toImmutable());
                LISTENERS.forEach(listener -> listener.accept(teleported));
            }
            onArrived.run();
        });
    }

    /** Spinning faster and faster, shrinking into a swirl of sparkles going down into the tile. */
    private static void shrinking(ServerWorld world, MobEntity token, Vec3d at, float yaw, int t, int tileColor) {
        double p = t / (double) SHRINK_TICKS;
        setScale(token, 1 - (1 - MIN_SCALE) * p * p);
        TokenMovementService.faceYaw(token, yaw + (float) (t * (10 + 30 * p)));
        token.setVelocity(Vec3d.ZERO);
        // Two arms of a spiral closing in and going down with the token
        for (int arm = 0; arm < 2; arm++) {
            double angle = t * 0.55 + arm * Math.PI;
            double radius = 0.2 + 0.6 * (1 - p);
            double y = at.y + 0.15 + 0.9 * (1 - p);
            int color = arm == 0 ? tileColor : ACCENT;
            world.spawnParticles(new MulaSparkleEffect(Argb.lighten(color, 0.25F) & 0xFFFFFF, 0.9F, MulaSparkleEffect.TWINKLE),
                    at.x + Math.cos(angle) * radius, y, at.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
            world.spawnParticles(new DustParticleEffect(Vec3d.unpackRgb(color).toVector3f(), 0.9F),
                    at.x + Math.cos(angle + 0.5) * radius * 1.2, y - 0.1, at.z + Math.sin(angle + 0.5) * radius * 1.2, 1, 0, 0, 0, 0);
        }
        if (t % 3 == 0) world.spawnParticles(ParticleTypes.PORTAL, at.x, at.y + 0.6, at.z, 6, 0.25, 0.3, 0.25, 0.4);
        if (t == SHRINK_TICKS - 3) {
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.BLOCKS, 0.45F, 1.35F);
        }
    }

    /** Gone: a puff where it left, the arrival's portal opening (a ring of cyan sparkles narrowing). */
    private static void gone(ServerWorld world, MobEntity token, Vec3d start, Vec3d end, int t, int color) {
        if (t == 1) {
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, start.x, start.y + 0.2, start.z, 20, 0.15, 0.15, 0.15, 0.06);
            world.spawnParticles(new MulaSparkleEffect(0xFFFFFF, 1.1F, MulaSparkleEffect.STAR_BIT), start.x, start.y + 0.25, start.z, 4, 0.2, 0.1, 0.2, 0);
            token.requestTeleport(end.x, end.y, end.z);
        }
        token.setVelocity(Vec3d.ZERO);
        double radius = 0.8 - 0.12 * t;
        int points = 10;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points + t * 0.4;
            world.spawnParticles(new MulaSparkleEffect(Argb.lighten(i % 2 == 0 ? ACCENT : color, 0.3F) & 0xFFFFFF, 0.8F, MulaSparkleEffect.TWINKLE),
                    end.x + Math.cos(angle) * radius, end.y + 0.12, end.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Pops back, a bit too big first, with a burst of sparkles. */
    private static void growing(ServerWorld world, MobEntity token, Vec3d at, int t, int color) {
        double q = t / (double) GROW_TICKS;
        // Ease out with an overshoot: up to about 1.15 times its size, back to its size
        double c = 2.2, back = 1 + (c + 1) * Math.pow(q - 1, 3) + c * Math.pow(q - 1, 2);
        setScale(token, MIN_SCALE + (1 - MIN_SCALE) * back);
        token.setVelocity(Vec3d.ZERO);
        if (t == 1) {
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS, 0.6F, 1.2F);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.4F, 2.0F);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 0.25F, 1.7F);
            world.spawnParticles(new MulaSparkleEffect(Argb.lighten(ACCENT, 0.2F) & 0xFFFFFF, 1.3F, MulaSparkleEffect.STAR_BIT), at.x, at.y + 0.4, at.z, 8, 0.3, 0.3, 0.3, 0.0);
            world.spawnParticles(new MulaSparkleEffect(Argb.lighten(color, 0.35F) & 0xFFFFFF, 1.1F, MulaSparkleEffect.TWINKLE), at.x, at.y + 0.3, at.z, 10, 0.4, 0.25, 0.4, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 5, 0.1, 0.1, 0.1, 0.08);
        }
    }

    /** The token at {@code factor} times its own size (a transient modifier: never saved with the token). */
    private static void setScale(MobEntity token, double factor) {
        EntityAttributeInstance scale = token.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (scale == null) return;
        scale.removeModifier(SHRINK_MODIFIER);
        if (Math.abs(factor - 1) > 1.0E-4) {
            scale.addTemporaryModifier(new EntityAttributeModifier(SHRINK_MODIFIER, factor - 1,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
