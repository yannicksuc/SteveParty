package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.utils.InventoryUtils;
import com.mojang.authlib.properties.PropertyMap;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;

/**
 * What a roll does besides walking forward ({@link DiceOutcome}), and what the modules of the die change to the move
 * of the token.
 * <p>
 * A roll that is not a plain number of steps is resolved in this order, {@link #APPRECIATE_TICKS} after the dice
 * stopped:
 * <ol>
 *     <li><b>coins</b> (coin / debt faces): the roller gains or loses items of the party's coin (the default coin
 *     outside a party), never losing more than they hold;</li>
 *     <li><b>swap</b>: the roller picks another token (of the party; outside a party, a token within
 *     {@link #SWAP_RANGE} blocks; no answer: a random one), and the two tokens swap places, each standing on the
 *     other's board space without landing on it;</li>
 *     <li><b>the move</b>: steps forward as usual, steps backward (Reversed: the way the token came, see
 *     {@link AdvanceBackMoves}); without steps, a roll with a coin or swap face ends the turn where the token stands
 *     (no landing), and a face 0 makes the tile it stands on play its landing again.</li>
 * </ol>
 * While it is resolved the token can't be rolled for again ({@link #isResolving}). Server thread only, not saved.
 */
public final class DiceRollEffects {
    /** Ticks between the dice stopping and the first effect, like before a move: the roll is read first. */
    public static final int APPRECIATE_TICKS = 30;
    /** Ticks between the last effect and the end of a turn that ends without a move. */
    public static final int END_TURN_TICKS = 30;
    /** Outside a party: how far around the token the tokens it may swap with are looked for (blocks). */
    public static final double SWAP_RANGE = 50;

    private static final Set<UUID> RESOLVING = new HashSet<>();
    /** The modules of the die that moves each token, until its move ends. */
    private static final Map<UUID, Map<DiceModule, Integer>> MOVE_MODULES = new HashMap<>();

    static {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            RESOLVING.clear();
            MOVE_MODULES.clear();
        });
    }

    private DiceRollEffects() {
    }

    // ---------------------------------------------------------------- modules of the move

    /** The token is about to move for a roll of a die carrying these modules. */
    public static void setMoveModules(MobEntity token, Map<DiceModule, Integer> modules) {
        if (modules.isEmpty()) MOVE_MODULES.remove(token.getUuid());
        else MOVE_MODULES.put(token.getUuid(), Map.copyOf(modules));
    }

    /** The move is over (or a new roll replaces it). */
    public static void clearMoveModules(UUID token) {
        MOVE_MODULES.remove(token);
    }

    public static Map<DiceModule, Integer> moveModules(MobEntity token) {
        return MOVE_MODULES.getOrDefault(token.getUuid(), Map.of());
    }

    /** Skeleton Key: this move goes through the Stop spaces and the shop check points. */
    public static boolean ignoresStops(MobEntity token) {
        for (DiceModule module : moveModules(token).keySet()) {
            if (module.ignoresStops()) return true;
        }
        return false;
    }

    /** Homing: the branch the token takes by itself at this fork, null to ask its player. */
    public static @Nullable BoardSpaceDestination chooseFork(MobEntity token, List<BoardSpaceDestination> forks) {
        for (DiceModule module : moveModules(token).keySet()) {
            BoardSpaceDestination choice = module.chooseFork(token, forks, token.getRandom());
            if (choice != null) return choice;
        }
        return null;
    }

    // ---------------------------------------------------------------- resolution

    /** True while the roll of this token is being resolved (coins, swap picker...): no other roll moves it. */
    public static boolean isResolving(UUID token) {
        return RESOLVING.contains(token);
    }

    private static void later(int ticks, Runnable action) {
        SCHEDULER.schedule(UUID.randomUUID(), Math.max(1, ticks), action);
    }

    /** Resolves a roll for {@code token}, with the usual time to answer the swap picker. */
    public static void resolve(ServerWorld world, MobEntity token, UUID roller, DiceOutcome outcome) {
        resolve(world, token, roller, outcome, APPRECIATE_TICKS, DicePrompts.TIMEOUT_TICKS);
    }

    /**
     * @param delay         ticks before the first effect
     * @param promptTimeout ticks given to pick the token to swap with
     */
    public static void resolve(ServerWorld world, MobEntity token, UUID roller, DiceOutcome outcome, int delay, int promptTimeout) {
        UUID id = token.getUuid();
        if (!RESOLVING.add(id)) return;
        later(delay, () -> {
            if (!isToken(token)) {
                RESOLVING.remove(id);
                return;
            }
            if (outcome.coinFace()) applyCoins(world, token, roller, outcome.coins());
            if (outcome.swap()) askSwap(world, token, roller, promptTimeout, () -> move(world, token, outcome));
            else move(world, token, outcome);
        });
    }

    private static boolean isToken(MobEntity token) {
        return token.isAlive() && !token.isRemoved() && ((TokenizedEntityInterface) token).steveparty$isTokenized();
    }

    private static void move(ServerWorld world, MobEntity token, DiceOutcome outcome) {
        UUID id = token.getUuid();
        if (!isToken(token)) {
            RESOLVING.remove(id);
            return;
        }
        int steps = outcome.steps();
        if (steps > 0) {
            RESOLVING.remove(id);
            TokenMovementService.moveEntityOnBoard(token, steps);
        } else if (steps < 0) {
            RESOLVING.remove(id);
            BoardSpaceBlockEntity tile = BoardSpaces.boardSpaceOf(token);
            int walked = tile == null ? 0 : AdvanceBackMoves.launch(world, token, tile.getPos(), steps, false);
            if (walked == 0) {
                // Nowhere to go back to (a start tile, off the board): the turn ends where it stands
                MessageUtils.sendToNearby(world, token.getPos(), 100,
                        Text.translatable("message.steveparty.dice.no_way_back", token.getDisplayName()).formatted(Formatting.GRAY),
                        MessageUtils.MessageType.ACTION_BAR);
                RESOLVING.add(id);
                later(END_TURN_TICKS, () -> endTurn(world, token));
            }
        } else if (outcome.isSpecial()) {
            later(END_TURN_TICKS, () -> endTurn(world, token));
        } else if (outcome.zero()) {
            RESOLVING.remove(id);
            landInPlace(world, token);
        } else {
            RESOLVING.remove(id);
        }
    }

    /**
     * Face 0: the token stays where it is and its tile plays its landing again, as if it had just ended a move there
     * (off the board, or on a space that is only gone through, the turn simply ends).
     */
    private static void landInPlace(ServerWorld world, MobEntity token) {
        BoardSpaceBlockEntity tile = BoardSpaces.boardSpaceOf(token);
        if (tile != null && (ABoardSpaceBlock.countsAsStep(tile.getCachedState().getBlock())
                || TokenMovementService.isForcedStop(world, tile))) {
            TokenMovementService.stopOnCurrentBoardSpace(token, tile.getPos());
        } else {
            RESOLVING.add(token.getUuid());
            endTurn(world, token);
        }
    }

    /** The roll moved nothing: the turn of the token ends where it stands, without landing. */
    private static void endTurn(ServerWorld world, MobEntity token) {
        UUID id = token.getUuid();
        RESOLVING.remove(id);
        clearMoveModules(id);
        Optional<PartyControllerEntity> party = PartyControllerEntity.getRunningPartyOf(id);
        if (party.isPresent()) party.get().endTurnOf(id);
        else PartyControllerEntity.onFreeTokenArrived(world, token);
    }

    // ---------------------------------------------------------------- coins

    /**
     * The roller gains ({@code coins} > 0) or loses coins: items of the coin of the token's party, of the default coin
     * outside a party. A loss never takes more than the roller holds.
     *
     * @return the coins really gained (negative: lost); 0 if the roller is not connected
     */
    public static int applyCoins(ServerWorld world, MobEntity token, UUID roller, int coins) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(roller);
        Optional<PartyControllerEntity> party = PartyControllerEntity.getRunningPartyOf(token.getUuid());
        ItemStack coin = party.map(controller -> controller.getCurrency(PartyCurrency.COIN)).orElseGet(PartyCurrency.COIN::defaultStack);
        int applied = 0;
        if (player != null) {
            if (coins > 0) {
                // Doubled by the Double Coins power-up of the roller's turn
                applied = fr.lordfinn.steveparty.powerups.PowerUpService.coinsGained(roller, coins);
                InventoryUtils.giveOrDrop(player, coin, applied);
            } else if (coins < 0) {
                applied = -InventoryUtils.take(player.getInventory(), coin, -coins);
            }
        }
        Text who = player != null ? player.getDisplayName() : token.getDisplayName();
        Vec3d at = token.getPos();
        MutableText message;
        if (applied > 0) {
            message = Text.translatable("message.steveparty.dice.coins.gained", who, applied, coin.getName()).formatted(Formatting.YELLOW);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1.2f);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.4f, 1.6f);
            world.spawnParticles(ParticleTypes.WAX_ON, at.x, at.y + token.getHeight() / 2, at.z, 12 + 2 * applied, 0.35, 0.4, 0.35, 0.0);
        } else if (applied < 0) {
            message = Text.translatable("message.steveparty.dice.coins.lost", who, -applied, coin.getName()).formatted(Formatting.RED);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1f, 0.8f);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.6f, 1f);
            world.spawnParticles(ParticleTypes.SMOKE, at.x, at.y + token.getHeight() / 2, at.z, 12 - 2 * applied, 0.35, 0.4, 0.35, 0.01);
        } else {
            message = Text.translatable("message.steveparty.dice.coins.none", who, coin.getName()).formatted(Formatting.GRAY);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.8f, 0.8f);
        }
        MessageUtils.sendToNearby(world, at, 100, message, MessageUtils.MessageType.ACTION_BAR);
        int shown = applied;
        party.ifPresent(controller -> controller.noteRollCoins(token.getUuid(), shown));
        return applied;
    }

    // ---------------------------------------------------------------- swap

    /**
     * The tokens {@code token} may swap places with: the other tokens of its running party (loaded and alive); outside
     * a party, the tokens within {@link #SWAP_RANGE} blocks that are not in a running party.
     */
    public static List<MobEntity> swapCandidates(ServerWorld world, MobEntity token) {
        List<MobEntity> candidates = new ArrayList<>();
        Optional<PartyControllerEntity> party = PartyControllerEntity.getRunningPartyOf(token.getUuid());
        if (party.isPresent()) {
            for (UUID other : party.get().getPartyData().getTokens()) {
                if (!other.equals(token.getUuid()) && world.getEntity(other) instanceof MobEntity mob && isToken(mob)) candidates.add(mob);
            }
        } else {
            candidates.addAll(world.getEntitiesByClass(MobEntity.class, Box.of(token.getPos(), 2 * SWAP_RANGE, 2 * SWAP_RANGE, 2 * SWAP_RANGE),
                    mob -> mob != token && isToken(mob) && PartyControllerEntity.getRunningPartyOf(mob.getUuid()).isEmpty()));
            candidates.sort(java.util.Comparator.comparingDouble(mob -> mob.squaredDistanceTo(token)));
        }
        return candidates;
    }

    private static void askSwap(ServerWorld world, MobEntity token, UUID roller, int promptTimeout, Runnable then) {
        List<MobEntity> candidates = swapCandidates(world, token);
        if (candidates.isEmpty()) {
            MessageUtils.sendToNearby(world, token.getPos(), 100,
                    Text.translatable("message.steveparty.dice.swap.nobody", token.getDisplayName()).formatted(Formatting.GRAY),
                    MessageUtils.MessageType.ACTION_BAR);
            then.run();
            return;
        }
        List<DicePrompts.Option> options = new ArrayList<>();
        for (MobEntity candidate : candidates) options.add(new DicePrompts.Option(iconOf(world, candidate), labelOf(world, candidate)));
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(roller);
        DicePrompts.ask(player, Text.translatable("gui.steveparty.dice_prompt.swap"), DicePrompts.Layout.LIST, options,
                promptTimeout, token.getRandom().nextInt(candidates.size()), index -> {
                    MobEntity other = candidates.get(index);
                    if (isToken(token) && isToken(other)) swap(world, token, other);
                    later(END_TURN_TICKS / 2, then);
                });
    }

    /** The head of the token's owner, else the spawn egg of the mob, else a token. */
    private static ItemStack iconOf(ServerWorld world, MobEntity token) {
        UUID owner = ((TokenizedEntityInterface) token).steveparty$getTokenOwner();
        if (owner != null) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponentTypes.PROFILE, player != null ? new ProfileComponent(player.getGameProfile())
                    : new ProfileComponent(Optional.empty(), Optional.of(owner), new PropertyMap()));
            return head;
        }
        // its own egg (a Glandouille's of its kind, not the last one registered for the type)
        ItemStack egg = token.getPickBlockStack();
        return egg != null && !egg.isEmpty() ? egg : new ItemStack(ModItems.TOKEN);
    }

    private static Text labelOf(ServerWorld world, MobEntity token) {
        MutableText label = token.getDisplayName().copy();
        UUID owner = ((TokenizedEntityInterface) token).steveparty$getTokenOwner();
        ServerPlayerEntity player = owner == null ? null : world.getServer().getPlayerManager().getPlayer(owner);
        if (player != null && !player.getNameForScoreboard().equals(label.getString()))
            label.append(Text.literal(" (" + player.getNameForScoreboard() + ")").formatted(Formatting.GRAY));
        return label;
    }

    /**
     * The two tokens swap places: each ends on the other's board space (where it stands, off the board), facing the
     * way the other did. Nothing lands: no tile effect, no step. Their paths are forgotten (going back the way they
     * came no longer means anything).
     */
    public static void swap(ServerWorld world, MobEntity a, MobEntity b) {
        Vec3d posA = standOf(world, a), posB = standOf(world, b);
        float yawA = a.getYaw(), yawB = b.getYaw();
        place(world, a, posB, yawB);
        place(world, b, posA, yawA);
        MessageUtils.sendToNearby(world, posA, 100,
                Text.translatable("message.steveparty.dice.swapped", a.getDisplayName(), b.getDisplayName()).formatted(Formatting.LIGHT_PURPLE),
                MessageUtils.MessageType.ACTION_BAR);
        PartyControllerEntity.getRunningPartyOf(a.getUuid())
                .ifPresent(controller -> controller.noteRollSwap(a.getUuid(), b.getDisplayName().getString()));
    }

    private static Vec3d standOf(ServerWorld world, MobEntity token) {
        BoardSpaceBlockEntity tile = BoardSpaces.boardSpaceOf(token);
        return tile == null ? token.getPos() : BoardSpaces.standPos(world, tile.getPos());
    }

    private static void place(ServerWorld world, MobEntity token, Vec3d pos, float yaw) {
        Vec3d from = token.getPos();
        world.spawnParticles(ParticleTypes.PORTAL, from.x, from.y + token.getHeight() / 2, from.z, 40, 0.3, 0.5, 0.3, 0.4);
        AdvanceBackMoves.cancel(token);
        AdvanceBackMoves.forgetTrail(token.getUuid());
        TileTeleport.cancelPush(token);
        token.setVelocity(Vec3d.ZERO);
        token.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0);
        TokenMovementService.faceYaw(token, yaw);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        world.spawnParticles(ParticleTypes.PORTAL, pos.x, pos.y + token.getHeight() / 2, pos.z, 40, 0.3, 0.5, 0.3, 0.4);
    }
}
