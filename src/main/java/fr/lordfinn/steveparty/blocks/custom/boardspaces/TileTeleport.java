package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.TeleportTileBehavior;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TeleportTargetsComponent;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import fr.lordfinn.steveparty.service.TokenMovementService;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;

/**
 * The « Téléportation » tile (warp pipe): a token that <b>lands</b> on it (its move ends there) is sent to one of the
 * arrivals of its Teleport Cartridge ({@link TeleportTargetsComponent}), picked at random or in turn.
 * <ul>
 *     <li>the token spins and shrinks into a swirl of purple and cyan sparkles, vanishes with a chorus "whoop", and pops
 *     back at the arrival, where tokens stand on it (lowered, sloped and large tiles included: {@link BoardSpaces#standPos});</li>
 *     <li>its move ends there <b>without</b> landing on the arrival: no role of the arrival plays (no bonus, no item),
 *     unless the cartridge's option says so; even then an arrival that is a teleport tile never sends it on (no chains);</li>
 *     <li>a token only going over the tile is not teleported.</li>
 * </ul>
 * In a party, the turn goes on once the token has reappeared ({@link TeleportTileBehavior}); in free play (dice outside
 * a party) a token ending its move there is teleported too.
 */
public final class TileTeleport {
    /** Default colour of a teleport tile (a dye changes it). */
    public static final int COLOR = 0x8E4BFF;
    /** The cyan of the swirl and of the pop. */
    public static final int ACCENT = 0x5FE6FF;
    /** Ticks of the animation: shrinking and spinning, gone, popping back. */
    public static final int SHRINK_TICKS = 16, GONE_TICKS = 5, GROW_TICKS = 7;
    public static final int TOTAL_TICKS = SHRINK_TICKS + GONE_TICKS + GROW_TICKS;
    /** Smallest size of the token (fraction of its own) while it is away. */
    private static final double MIN_SCALE = 0.05;
    private static final Identifier SHRINK_MODIFIER = Steveparty.id("teleport_shrink");

    /** Tokens being teleported (server thread). */
    private static final Set<UUID> TELEPORTING = new HashSet<>();

    /** A teleport, for the GameTests: the token, where it left from and where it arrived. Empty in normal play. */
    public record Teleported(UUID token, BlockPos from, BlockPos to) {
    }

    public static final List<Consumer<Teleported>> LISTENERS = new CopyOnWriteArrayList<>();

    private TileTeleport() {
    }

    /** Free play: a token ending its move on a teleport tile outside a party is teleported too. */
    public static void initialize() {
        TileReachedEvent.EVENT.register((token, tile) -> {
            if (token.getWorld() instanceof ServerWorld world && tile != null && landsOn(token, tile)
                    && !TileFeedback.isInRunningParty(token.getUuid())) {
                BlockPos target = pick(world, tile, tile.getActiveCartridgeItemStack());
                if (target != null) teleport(world, token, tile.getPos(), target, () -> {
                });
            }
            return ActionResult.PASS;
        });
    }

    /** The token's move ends on {@code tile}, a teleport tile, and it isn't being teleported already. */
    private static boolean landsOn(MobEntity token, BoardSpaceBlockEntity tile) {
        return token instanceof TokenizedEntityInterface tokenized && tokenized.steveparty$getNbSteps() == 0
                && ABoardSpaceBlock.countsAsStep(tile.getCachedState().getBlock())
                && tile.getBoardSpaceBehavior() instanceof TeleportTileBehavior
                && !isTeleporting(token);
    }

    // ---------------------------------------------------------------- targets

    public static TeleportTargetsComponent settings(@Nullable ItemStack cartridge) {
        if (cartridge == null || cartridge.isEmpty()) return TeleportTargetsComponent.DEFAULT;
        return cartridge.getOrDefault(ModComponents.TELEPORT_TARGETS, TeleportTargetsComponent.DEFAULT);
    }

    /**
     * The arrivals a token can be sent to now: board spaces in loaded chunks (never loads one), other than the tile
     * itself, in the cartridge's order.
     */
    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos): a teleport must not load a chunk
    public static List<BlockPos> validTargets(World world, BlockPos tile, @Nullable ItemStack cartridge) {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos target : settings(cartridge).targets()) {
            if (target.equals(tile) || !world.isChunkLoaded(target)) continue;
            if (BoardLinks.isBoardSpace(world, target)) valid.add(target);
        }
        return valid;
    }

    /**
     * The arrival for a token landing on {@code tile} now: at random, or the next one in turn (the turn is kept by the
     * tile, per cartridge slot). Null if it has none.
     */
    public static @Nullable BlockPos pick(ServerWorld world, BoardSpaceBlockEntity tile, @Nullable ItemStack cartridge) {
        List<BlockPos> valid = validTargets(world, tile.getPos(), cartridge);
        if (valid.isEmpty()) return null;
        if (!settings(cartridge).cycle()) return valid.get(world.random.nextInt(valid.size()));
        int index = Math.floorMod(tile.getCycleIndex(), valid.size());
        tile.setCycleIndex((index + 1) % valid.size());
        return valid.get(index);
    }

    // ---------------------------------------------------------------- the teleport

    public static boolean isTeleporting(MobEntity token) {
        return TELEPORTING.contains(token.getUuid());
    }

    /**
     * Sends {@code token} from the tile at {@code from} to the board space at {@code to}, with the warp animation.
     * {@code onArrived} runs once it has reappeared there (also if the token went away meanwhile: the game goes on).
     */
    public static void teleport(ServerWorld world, MobEntity token, BlockPos from, BlockPos to, Runnable onArrived) {
        UUID id = token.getUuid();
        if (!TELEPORTING.add(id)) return;
        Vec3d start = BoardSpaces.standPos(world, from), end = BoardSpaces.standPos(world, to);
        float yaw = token.getYaw();
        int[] tick = {0};
        UUID task = UUID.randomUUID();
        world.playSound(null, start.x, start.y, start.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.3F, 1.5F);
        SCHEDULER.repeat(task, 1, () -> {
            int t = ++tick[0];
            if (t <= SHRINK_TICKS) shrinking(world, token, start, yaw, t);
            else if (t <= SHRINK_TICKS + GONE_TICKS) gone(world, token, start, end, t - SHRINK_TICKS);
            else growing(world, token, end, t - SHRINK_TICKS - GONE_TICKS);
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
    private static void shrinking(ServerWorld world, MobEntity token, Vec3d at, float yaw, int t) {
        double p = t / (double) SHRINK_TICKS;
        setScale(token, 1 - (1 - MIN_SCALE) * p * p);
        TokenMovementService.faceYaw(token, yaw + (float) (t * (10 + 30 * p)));
        token.setVelocity(Vec3d.ZERO);
        // Two arms of a spiral closing in and going down with the token
        for (int arm = 0; arm < 2; arm++) {
            double angle = t * 0.55 + arm * Math.PI;
            double radius = 0.2 + 0.6 * (1 - p);
            double y = at.y + 0.15 + 0.9 * (1 - p);
            int color = arm == 0 ? TileTeleport.COLOR : ACCENT;
            world.spawnParticles(new MulaSparkleEffect(TileFeedback.lighten(color, 0.25F), 0.9F, MulaSparkleEffect.TWINKLE),
                    at.x + Math.cos(angle) * radius, y, at.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
            world.spawnParticles(new DustParticleEffect(color, 0.9F),
                    at.x + Math.cos(angle + 0.5) * radius * 1.2, y - 0.1, at.z + Math.sin(angle + 0.5) * radius * 1.2, 1, 0, 0, 0, 0);
        }
        if (t % 3 == 0) world.spawnParticles(ParticleTypes.PORTAL, at.x, at.y + 0.6, at.z, 6, 0.25, 0.3, 0.25, 0.4);
        if (t == SHRINK_TICKS - 3) {
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.BLOCKS, 0.45F, 1.35F);
        }
    }

    /** Gone: a puff where it left, the arrival's portal opening (a ring of cyan sparkles narrowing). */
    private static void gone(ServerWorld world, MobEntity token, Vec3d start, Vec3d end, int t) {
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
            world.spawnParticles(new MulaSparkleEffect(TileFeedback.lighten(i % 2 == 0 ? ACCENT : COLOR, 0.3F), 0.8F, MulaSparkleEffect.TWINKLE),
                    end.x + Math.cos(angle) * radius, end.y + 0.12, end.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Pops back, a bit too big first, with a burst of sparkles. */
    private static void growing(ServerWorld world, MobEntity token, Vec3d at, int t) {
        double q = t / (double) GROW_TICKS;
        // Ease out with an overshoot: up to about 1.15 times its size, back to its size
        double c = 2.2, back = 1 + (c + 1) * Math.pow(q - 1, 3) + c * Math.pow(q - 1, 2);
        setScale(token, MIN_SCALE + (1 - MIN_SCALE) * back);
        token.setVelocity(Vec3d.ZERO);
        if (t == 1) {
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS, 0.6F, 1.2F);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.4F, 2.0F);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 0.25F, 1.7F);
            world.spawnParticles(new MulaSparkleEffect(TileFeedback.lighten(ACCENT, 0.2F), 1.3F, MulaSparkleEffect.STAR_BIT), at.x, at.y + 0.4, at.z, 8, 0.3, 0.3, 0.3, 0.0);
            world.spawnParticles(new MulaSparkleEffect(TileFeedback.lighten(COLOR, 0.35F), 1.1F, MulaSparkleEffect.TWINKLE), at.x, at.y + 0.3, at.z, 10, 0.4, 0.25, 0.4, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 5, 0.1, 0.1, 0.1, 0.08);
        }
    }

    /** The token at {@code factor} times its own size (a transient modifier: never saved with the token). */
    private static void setScale(MobEntity token, double factor) {
        EntityAttributeInstance scale = token.getAttributeInstance(EntityAttributes.SCALE);
        if (scale == null) return;
        scale.removeModifier(SHRINK_MODIFIER);
        if (Math.abs(factor - 1) > 1.0E-4) {
            scale.addTemporaryModifier(new EntityAttributeModifier(SHRINK_MODIFIER, factor - 1,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
