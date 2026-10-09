package fr.lordfinn.steveparty.powerups.effects;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.TrapSetupComponent;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Trap power-up: its user sets a trap on the board space their token stands on (where it stopped). The next token
 * of another player that <b>stops</b> there (the end of its move, not passing over it) springs it, for the trap's
 * setter; the trap is then gone. What it does is the Trap's ({@link TrapKind}): an unsigned Trap steals
 * {@link #COINS} coins, a signed one does what its signer chose (coins stolen, spaces back, the next turn lost, an
 * item stolen; see {@link TrapSetupComponent}).
 * <ul>
 *     <li>One trap per board space: setting one on a trapped space is refused (the Trap is kept).</li>
 *     <li>Its setter never springs it: neither the player nor their tokens (on the board, a player's tokens are their
 *     team).</li>
 *     <li>Visible to everyone: its board space shows a frame in its setter's colour and an icon of what it does (the
 *     space's mark, {@link BoardSpaceBlockEntity#setTrapMark}, drawn by the tile's renderer). Nothing ticks for it.</li>
 *     <li>Kept in the party's data ({@link TrapState}, see {@code PartyData#getTraps}): saved with the Party Controller,
 *     gone (marks too) when a party starts or ends ({@link #clearAll}).</li>
 * </ul>
 * Coins and items move from the victim's inventory to the setter's, never through the bank or a pot; while one of them
 * is not connected, a theft does not spring and waits for the next stop.
 * <p>
 * Wiring: {@link #use} when the power-up is used, {@link #onTokenStopped} when a party token ends its move on a board
 * space ({@code BoardSpaceBlockEntity#onDestinationReached} calls it).
 */
public final class TrapEffect {
    /** Coins an unsigned Trap takes from the victim's player (at most what they hold). */
    public static final int COINS = TrapKind.COINS.defaultAmount;

    /** What setting a trap did. */
    public enum Placed {
        /** A trap was set. */
        SET,
        /** A trap is already on that space: nothing set, the power-up should not be spent. */
        OCCUPIED,
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
        /** A theft whose setter or victim is not connected: the trap stays for the next stop. */
        WAITING,
        /** The victim was protected (Padlock): the trap is gone, nothing happened. */
        DISARMED,
        /** The trap sprang and is gone. */
        SPRUNG
    }

    /**
     * @param kind   what the trap did ({@link Outcome#SPRUNG} only)
     * @param amount coins moved to the setter, spaces the token goes back, 1 for an item stolen (0: nothing to steal)
     */
    public record Result(Outcome outcome, @Nullable TrapKind kind, int amount) {
        public static final Result NONE = new Result(Outcome.NONE, null, 0);

        private static Result of(Outcome outcome) {
            return new Result(outcome, null, 0);
        }

        /** The token was sent back: it lands elsewhere, the space's own role must not play. */
        public boolean movesToken() {
            return outcome == Outcome.SPRUNG && kind == TrapKind.BACK && amount > 0;
        }
    }

    private TrapEffect() {
    }

    // ---------------------------------------------------------------- setting

    /** Why {@code token} can't set a trap where it stands, null if it can. */
    public static @Nullable Text refusal(PartyControllerEntity controller, MobEntity token) {
        BoardSpaceBlockEntity space = BoardSpaces.boardSpaceOf(token);
        if (space == null) return Text.translatable("message.steveparty.powerup.trap.no_space");
        if (trapAt(controller, space.getPos()) != null) return Text.translatable("message.steveparty.powerup.trap.occupied");
        return null;
    }

    /**
     * Uses the Trap power-up: sets a trap doing {@code effect} on the board space {@code token} stands on, for its
     * player, in their token's colour. A soft click for them (the party is told by the power-up's announcement).
     *
     * @return {@link Placed#SET} if a trap was set (the power-up is spent), else why not
     */
    public static Placed use(PartyControllerEntity controller, MobEntity token, TrapSetupComponent.Effect effect) {
        BoardSpaceBlockEntity space = BoardSpaces.boardSpaceOf(token);
        UUID owner = ownerOf(token);
        if (owner == null) return Placed.NO_PLAYER;
        if (space == null) return Placed.NO_SPACE;
        Placed placed = place(controller, space.getPos(), new TrapState.Trap(owner, token.getUuid(), effect, colorOf(token, owner)));
        ServerPlayerEntity player = token.getServer() == null ? null : token.getServer().getPlayerManager().getPlayer(owner);
        if (player != null && placed == Placed.SET)
            player.playSoundToPlayer(SoundEvents.BLOCK_TRIPWIRE_ATTACH, SoundCategory.PLAYERS, 0.6F, 0.8F);
        return placed;
    }

    /**
     * Sets {@code trap} on the board space at {@code space} (its position, for a large tile its main block), unless a
     * trap is there, and shows it on the space. No feedback: see {@link #use}.
     */
    public static Placed place(PartyControllerEntity controller, BlockPos space, TrapState.Trap trap) {
        if (!controller.getPartyData().getTraps().set(space, trap)) return Placed.OCCUPIED;
        controller.markDirty();
        mark(controller, space, trap);
        return Placed.SET;
    }

    /** Same, the default trap (coins) in red: tests. */
    public static Placed place(PartyControllerEntity controller, BlockPos space, UUID owner, @Nullable UUID ownerToken) {
        return place(controller, space, new TrapState.Trap(owner, ownerToken));
    }

    /** The trap on the board space at {@code space} in this party, or null. */
    public static @Nullable TrapState.Trap trapAt(PartyControllerEntity controller, BlockPos space) {
        return controller.getPartyData().getTraps().get(space);
    }

    /** Removes every trap of the party, and their marks on the board (a party starting or ending). */
    public static void clearAll(PartyControllerEntity controller) {
        TrapState traps = controller.getPartyData().getTraps();
        if (traps.isEmpty()) return;
        for (BlockPos pos : List.copyOf(traps.all().keySet())) mark(controller, pos, null);
        traps.clear();
        controller.markDirty();
    }

    /** Shows (or, with null, takes off) the trap on its board space's block entity, sent to the clients. */
    private static void mark(PartyControllerEntity controller, BlockPos pos, @Nullable TrapState.Trap trap) {
        if (controller.getWorld() instanceof ServerWorld world && world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity space)
            space.setTrapMark(trap == null ? null : new BoardSpaceBlockEntity.TrapMark(trap.effect().kind(), trap.color()));
    }

    private static void remove(PartyControllerEntity controller, BlockPos pos) {
        controller.getPartyData().getTraps().remove(pos);
        controller.markDirty();
        mark(controller, pos, null);
    }

    // ---------------------------------------------------------------- springing

    /**
     * A token of the party ended its move on {@code space} (a landing, not a pass): springs the trap there if it is
     * another player's. Called before the space's own landing role, which must not play if the token was sent back
     * ({@link Result#movesToken}).
     */
    public static Result onTokenStopped(PartyControllerEntity controller, BoardSpaceBlockEntity space, MobEntity token) {
        if (!(space.getWorld() instanceof ServerWorld world)) return Result.NONE;
        BlockPos pos = space.getPos();
        TrapState.Trap trap = trapAt(controller, pos);
        if (trap == null || !controller.getPartyData().getTokens().contains(token.getUuid())) return Result.NONE;
        UUID victimId = ownerOf(token);
        if (trap.isOwnedBy(victimId, token.getUuid())) return Result.of(Outcome.OWN);

        TrapKind kind = trap.effect().kind();
        ServerPlayerEntity setter = world.getServer().getPlayerManager().getPlayer(trap.placer());
        ServerPlayerEntity victim = victimId == null ? null : world.getServer().getPlayerManager().getPlayer(victimId);
        if (kind.isTheft() && (setter == null || victim == null)) return Result.of(Outcome.WAITING);

        Vec3d at = BoardSpaces.standPos(world, pos);
        remove(controller, pos);
        if (PowerUpProtection.consume(controller, token.getUuid(), PowerUpProtection.Attack.TRAP)) {
            // PADLOCK: the victim was protected, their Padlock is used up (and announced): the trap is disarmed
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 1F, 1.4F);
            world.spawnParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 8, 0.3, 0.1, 0.3, 0.01);
            return Result.of(Outcome.DISARMED);
        }

        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_TRIPWIRE_CLICK_ON, SoundCategory.PLAYERS, 1F, 0.6F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.PLAYERS, 0.8F, 1.2F);
        world.spawnParticles(ParticleTypes.CRIT, at.x, at.y + 0.3, at.z, 20, 0.35, 0.3, 0.35, 0.2);
        world.spawnParticles(ParticleTypes.SMOKE, at.x, at.y + 0.1, at.z, 10, 0.3, 0.05, 0.3, 0.01);
        Text victimName = token.getDisplayName();
        Text setterName = setter != null ? setter.getDisplayName() : Text.translatable("message.steveparty.powerup.trap.someone");
        String key = "message.steveparty.powerup.trap.sprung." + kind.id();
        int amount;
        Text message;
        switch (kind) {
            case COINS -> {
                ItemStack coin = controller.getCurrency(PartyCurrency.COIN);
                amount = InventoryUtils.take(victim.getInventory(), coin, trap.effect().amount());
                if (amount > 0) InventoryUtils.giveOrDrop(setter, coin, amount);
                message = amount > 0 ? Text.translatable(key, victimName, setterName, amount, coin.getName())
                        : Text.translatable(key + ".empty", victimName, setterName, coin.getName());
            }
            case BACK -> {
                amount = AdvanceBackMoves.launch(world, token, pos, -trap.effect().amount());
                message = Text.translatable(key, victimName, setterName, amount);
            }
            case SKIP_TURN -> {
                amount = 0;
                controller.getPartyData().getSkippedTokens().add(token.getUuid());
                controller.markDirty();
                message = Text.translatable(key, victimName, setterName);
            }
            default -> {
                ItemStack stolen = stealItem(world, victim, controller);
                amount = stolen.isEmpty() ? 0 : 1;
                if (!stolen.isEmpty()) setter.getInventory().offerOrDrop(stolen);
                message = stolen.isEmpty() ? Text.translatable(key + ".empty", victimName, setterName)
                        : Text.translatable(key, victimName, setterName, stolen.toHoverableText());
            }
        }
        MessageUtils.sendToNearby(world, at, PartyControllerEntity.PARTY_AUDIENCE_RADIUS, message.copy().formatted(Formatting.RED),
                MessageUtils.MessageType.CHAT);
        return new Result(Outcome.SPRUNG, kind, amount);
    }

    /** One item (not the party's coins or stars) of the victim's inventory, taken; empty if none. */
    private static ItemStack stealItem(ServerWorld world, ServerPlayerEntity victim, PartyControllerEntity controller) {
        ItemStack coin = controller.getCurrency(PartyCurrency.COIN), star = controller.getCurrency(PartyCurrency.STAR);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < victim.getInventory().main.size(); slot++) {
            ItemStack stack = victim.getInventory().main.get(slot);
            if (stack.isEmpty() || ItemStack.areItemsAndComponentsEqual(stack, coin) || ItemStack.areItemsAndComponentsEqual(stack, star))
                continue;
            slots.add(slot);
        }
        if (slots.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = victim.getInventory().main.get(slots.get(world.random.nextInt(slots.size()))).split(1);
        victim.getInventory().markDirty();
        return taken;
    }

    private static @Nullable UUID ownerOf(MobEntity token) {
        return token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : null;
    }

    /** A trap's colour: its setter's token's colour, else a dye colour given by its player. */
    public static int colorOf(MobEntity token, UUID player) {
        int color = token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenColor() : -1;
        if (color >= 0) return color & 0xFFFFFF;
        DyeColor dye = DyeColor.values()[Math.floorMod(player.hashCode(), DyeColor.values().length)];
        return dye.getEntityColor();
    }
}
