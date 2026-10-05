package fr.lordfinn.steveparty.powerups.effects;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * The Trap power-up: its user sets a hidden trap on the board space their token stands on. The next token of another
 * player that <b>stops</b> there (the end of its move, not passing over it) gives the trap's owner {@link #COINS} coins,
 * at most what its player holds; the trap is then gone.
 * <ul>
 *     <li>One trap per board space: a new one replaces the one there, whoever set it.</li>
 *     <li>Its owner never springs it (neither the player nor the token that set it).</li>
 *     <li>Hidden: nothing on the board; only its owner sees a few faint red specks over it ({@link #initialize}).</li>
 *     <li>Kept in the party's data ({@link TrapState}, see {@code PartyData#getTraps}): saved with the Party Controller,
 *     cleared when a new party starts.</li>
 * </ul>
 * The coins are items of the party's coin ({@link PartyCurrency#COIN}) moved from the victim's inventory to the owner's.
 * While the owner or the victim is not connected, the trap does not spring and waits for the next stop.
 * <p>
 * Wiring: {@link #use} when the power-up is used, {@link #onTokenStopped} when a party token ends its move on a board
 * space ({@code BoardSpaceBlockEntity#onDestinationReached} calls it), {@link #initialize} once at start-up (the marker).
 */
public final class TrapEffect {
    /** Coins a sprung trap takes from the victim's player (at most what they hold). */
    public static final int COINS = 10;
    /** How often (ticks) the owner's marker shows over each of their traps. */
    public static final int MARKER_INTERVAL_TICKS = 20;

    private static final DustParticleEffect MARKER = new DustParticleEffect(new Vector3f(0.75F, 0.1F, 0.1F), 0.6F);

    /** What setting a trap did. */
    public enum Placed {
        /** A trap was set on an empty board space. */
        SET,
        /** A trap was set and replaced the one that was on that space (the user is not told). */
        REPLACED,
        /** The token stands on no board space: nothing set, the power-up should not be spent. */
        NO_SPACE,
        /** The token has no player: nothing set. */
        NO_PLAYER
    }

    /** What a token stopping on a board space did to its trap. */
    public enum Outcome {
        /** No trap there (or the token is not in the party). */
        NONE,
        /** The trap is the token's player's own: nothing happens, it stays. */
        OWN,
        /** The owner or the victim is not connected: the trap stays for the next stop. */
        WAITING,
        /** The victim was protected (Padlock): the trap is gone, no coins moved. */
        DISARMED,
        /** The trap sprang: {@link Result#coins} coins went to its owner (0 if the victim had none), it is gone. */
        SPRUNG
    }

    /** @param coins coins moved from the victim to the owner (only for {@link Outcome#SPRUNG}) */
    public record Result(Outcome outcome, int coins) {
        public static final Result NONE = new Result(Outcome.NONE, 0);
    }

    private TrapEffect() {
    }

    // ---------------------------------------------------------------- setting

    /**
     * Uses the Trap power-up: sets a trap on the board space {@code token} stands on, owned by its player. The player is
     * told privately (message and a soft click); nobody else is.
     *
     * @return {@link Placed#SET} or {@link Placed#REPLACED} if a trap was set (the power-up is spent), else why not
     */
    public static Placed use(PartyControllerEntity controller, MobEntity token) {
        BoardSpaceBlockEntity space = BoardSpaces.boardSpaceOf(token);
        UUID owner = token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : null;
        ServerPlayerEntity player = owner == null || token.getServer() == null ? null : token.getServer().getPlayerManager().getPlayer(owner);
        Placed placed = owner == null ? Placed.NO_PLAYER
                : space == null ? Placed.NO_SPACE
                : place(controller, space.getPos(), owner, token.getUuid());
        if (player != null) {
            boolean set = placed == Placed.SET || placed == Placed.REPLACED;
            player.sendMessage(Text.translatable(set ? "message.steveparty.powerup.trap.set" : "message.steveparty.powerup.trap.no_space")
                    .formatted(set ? Formatting.DARK_RED : Formatting.GRAY), true);
            if (set) player.playSoundToPlayer(SoundEvents.BLOCK_TRIPWIRE_ATTACH, SoundCategory.PLAYERS, 0.6F, 0.8F);
        }
        return placed;
    }

    /**
     * Sets a trap on the board space at {@code space} (its position, for a large tile its main block), replacing the
     * one there. No feedback: see {@link #use}.
     *
     * @param owner      the player who gets the coins
     * @param ownerToken their token (it never springs the trap), or null
     */
    public static Placed place(PartyControllerEntity controller, BlockPos space, UUID owner, @Nullable UUID ownerToken) {
        TrapState.Trap previous = controller.getPartyData().getTraps().set(space, new TrapState.Trap(owner, ownerToken));
        controller.markDirty();
        return previous == null ? Placed.SET : Placed.REPLACED;
    }

    /** The trap on the board space at {@code space} in this party, or null. */
    public static @Nullable TrapState.Trap trapAt(PartyControllerEntity controller, BlockPos space) {
        return controller.getPartyData().getTraps().get(space);
    }

    // ---------------------------------------------------------------- springing

    /**
     * A token of the party ended its move on {@code space} (a landing, not a pass): springs the trap there if it is
     * another player's. Called before the space's own landing role.
     */
    public static Result onTokenStopped(PartyControllerEntity controller, BoardSpaceBlockEntity space, MobEntity token) {
        if (!(space.getWorld() instanceof ServerWorld world)) return Result.NONE;
        TrapState traps = controller.getPartyData().getTraps();
        BlockPos pos = space.getPos();
        TrapState.Trap trap = traps.get(pos);
        if (trap == null || !controller.getPartyData().getTokens().contains(token.getUuid())) return Result.NONE;
        UUID victimId = token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : null;
        if (trap.isOwnedBy(victimId, token.getUuid())) return new Result(Outcome.OWN, 0);

        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(trap.placer());
        ServerPlayerEntity victim = victimId == null ? null : world.getServer().getPlayerManager().getPlayer(victimId);
        if (owner == null || victim == null) return new Result(Outcome.WAITING, 0);

        Vec3d at = BoardSpaces.standPos(world, pos);
        if (isProtected(controller, token, victim)) {
            // PADLOCK: the victim is protected → the trap is disarmed and their Padlock is consumed. Consume it in
            // isProtected (or here), then this branch removes the trap and tells everyone around.
            traps.remove(pos);
            controller.markDirty();
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 1F, 1.4F);
            world.spawnParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 8, 0.3, 0.1, 0.3, 0.01);
            announce(world, at, Text.translatable("message.steveparty.powerup.trap.disarmed",
                    victim.getDisplayName(), owner.getDisplayName()).formatted(Formatting.AQUA));
            return new Result(Outcome.DISARMED, 0);
        }

        ItemStack coin = controller.getCurrency(PartyCurrency.COIN);
        int taken = InventoryUtils.take(victim.getInventory(), coin, COINS);
        if (taken > 0) InventoryUtils.giveOrDrop(owner, coin, taken);
        traps.remove(pos);
        controller.markDirty();

        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_TRIPWIRE_CLICK_ON, SoundCategory.PLAYERS, 1F, 0.6F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.PLAYERS, 0.8F, 1.2F);
        world.spawnParticles(ParticleTypes.CRIT, at.x, at.y + 0.3, at.z, 20, 0.35, 0.3, 0.35, 0.2);
        world.spawnParticles(ParticleTypes.SMOKE, at.x, at.y + 0.1, at.z, 10, 0.3, 0.05, 0.3, 0.01);
        Text message = taken > 0
                ? Text.translatable("message.steveparty.powerup.trap.sprung", victim.getDisplayName(), owner.getDisplayName(), taken, coin.getName())
                : Text.translatable("message.steveparty.powerup.trap.sprung_empty", victim.getDisplayName(), owner.getDisplayName(), coin.getName());
        announce(world, at, message.copy().formatted(Formatting.RED));
        return new Result(Outcome.SPRUNG, taken);
    }

    /**
     * Whether the victim is protected from traps.
     * <p>
     * PADLOCK: return true here if {@code victim} (or {@code token}) holds an active Padlock, and consume it; the caller
     * then disarms the trap ({@link Outcome#DISARMED}).
     */
    private static boolean isProtected(PartyControllerEntity controller, MobEntity token, ServerPlayerEntity victim) {
        return false;
    }

    private static void announce(ServerWorld world, Vec3d at, Text message) {
        MessageUtils.sendToNearby(world, at, PartyControllerEntity.PARTY_AUDIENCE_RADIUS, message, MessageUtils.MessageType.CHAT);
    }

    // ---------------------------------------------------------------- owner's marker

    /**
     * Registers the owner's marker: every {@link #MARKER_INTERVAL_TICKS} ticks, a few faint red specks over each trap of
     * a running party, sent to its owner only (no other player receives them). Call once at start-up.
     */
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % MARKER_INTERVAL_TICKS != 0) return;
            List<PartyControllerEntity> controllers = PartyControllerEntity.getActivePartyControllers();
            for (PartyControllerEntity controller : controllers) {
                if (controller.isRemoved() || !(controller.getWorld() instanceof ServerWorld world)) continue;
                TrapState traps = controller.getPartyData().getTraps();
                if (traps.isEmpty() || !controller.getPartyData().isStarted()) continue;
                traps.all().forEach((pos, trap) -> {
                    ServerPlayerEntity owner = server.getPlayerManager().getPlayer(trap.placer());
                    if (owner == null || owner.getServerWorld() != world || !world.isChunkLoaded(pos)) return;
                    Vec3d at = BoardSpaces.standPos(world, pos);
                    world.spawnParticles(owner, MARKER, false, at.x, at.y + 0.08, at.z, 3, 0.2, 0.0, 0.2, 0.0);
                });
            }
        });
    }
}
