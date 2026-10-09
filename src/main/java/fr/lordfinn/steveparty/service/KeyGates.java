package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.BoardRuleCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.KeyGateCartridgeItem;
import fr.lordfinn.steveparty.utils.MessageUtils;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Key gates ({@link KeyGateCartridgeItem}). A token leaving a gate's board space by a locked exit, the gate
 * closed, needs a Gate Key (an item of {@link #KEYS}: the Gate Key, and whatever a datapack adds):
 * <ul>
 *     <li>at a fork, the arrows show every way; picking a locked one asks its player (the others stay free);</li>
 *     <li>when the only way on is locked, the token stops in front of the gate and its player is asked;</li>
 *     <li>with a key: « Use the key » (used up, the gate opens, the token goes on with its steps left) or « Keep the
 *     key » (as without one);</li>
 *     <li>without one: « Another way » (at a fork, another exit is free), « Wait here » (its steps left are lost, it
 *     lands on the gate's space), « Cancel the move » (back to the space its move started from, the same roll walked
 *     again: other ways may be taken; once per move, if the cartridge allows it), « Back to the start » (a start
 *     space, its player's if there is one; no coin lost; if the cartridge allows it).</li>
 * </ul>
 * Unanswered, a prompt keeps the key then waits. A token without a player waits. A gate opened by a key may stay open
 * (KeyGateCartridgeItem#stayOpen): for that many rounds of the party, or for good; it is then a shortcut for everyone.
 * The Skeleton Key dice module does not open gates. Server thread only.
 */
public final class KeyGates {
    /** The items that open a gate (used up): the Gate Key by default. */
    public static final TagKey<Item> KEYS = TagKey.of(RegistryKeys.ITEM, Steveparty.id("gate_keys"));

    /** A token waiting for its player's answer at a gate. */
    private record Pending(BlockPos gate, BlockPos exit, long since) {
    }

    /** A question unanswered this long (its prompt gone with a stopped party...) no longer holds the token. */
    private static final long STALE_TICKS = DicePrompts.TIMEOUT_TICKS * 3L;

    private static final Map<UUID, Pending> PENDING = ServerMemory.forgetOnStop(new HashMap<>());

    private KeyGates() {
    }

    // ---------------------------------------------------------------- the gate

    /** The Key gate cartridge of {@code space}, or null. */
    public static @Nullable ItemStack gateOf(BoardSpaceBlockEntity space) {
        ItemStack stack = space.getActiveCartridgeItemStack();
        return stack.getItem() instanceof KeyGateCartridgeItem ? stack : null;
    }

    /** True while a key keeps the gate of {@code space} open (for good, or for its rounds of the party). */
    public static boolean isOpen(BoardSpaceBlockEntity space, @Nullable MobEntity token) {
        ItemStack gate = gateOf(space);
        if (gate == null) return true;
        int opened = BoardRuleCartridgeItem.state(gate, KeyGateCartridgeItem.OPENED, Integer.MIN_VALUE);
        if (opened == Integer.MIN_VALUE) return false;
        if (opened < 0) return true; // for good
        int round = currentRound(token);
        if (round < 0) return true; // no party to count its rounds
        if (round - opened < KeyGateCartridgeItem.stayOpen(gate)) return true;
        // Its rounds are over: it closes again (the clients see it closed)
        BoardRuleCartridgeItem.putState(gate, KeyGateCartridgeItem.OPENED, null);
        if (!space.getWorld().isClient) space.update();
        return false;
    }

    private static int currentRound(@Nullable MobEntity token) {
        Optional<PartyControllerEntity> party = token == null ? Optional.empty() : PartyControllerEntity.getRunningPartyOf(token.getUuid());
        return party.map(controller -> controller.getPartyData().getRoundAt(controller.getPartyData().getStepIndex())).orElse(-1);
    }

    /** True if leaving {@code space} toward {@code exit} is barred by its gate now. */
    public static boolean isBarred(BoardSpaceBlockEntity space, BlockPos exit, @Nullable MobEntity token) {
        ItemStack gate = gateOf(space);
        return gate != null && KeyGateCartridgeItem.locks(gate, space.getPos(), exit) && !isOpen(space, token);
    }

    // ---------------------------------------------------------------- the move (TokenMovementService)

    /**
     * The token leaves {@code space} (its move goes on): when its only way on is barred, it stops in front of the gate
     * and its player is asked (true: the move waits for the answer).
     */
    public static boolean holdsAtExit(MobEntity token, BoardSpaceBlockEntity space, List<BoardSpaceDestination> destinations) {
        if (destinations.size() != 1 || gateOf(space) == null) return false;
        BlockPos exit = destinations.getFirst().position();
        if (!isBarred(space, exit, token)) return false;
        ask(token, space, exit, false);
        return true;
    }

    /** The ways a Homing die may take by itself: the free ones (all of them if none is free). */
    public static List<BoardSpaceDestination> homingChoices(MobEntity token, BoardSpaceBlockEntity space, List<BoardSpaceDestination> destinations) {
        if (gateOf(space) == null) return destinations;
        List<BoardSpaceDestination> free = destinations.stream().filter(d -> !isBarred(space, d.position(), token)).toList();
        return free.isEmpty() ? destinations : free;
    }

    /** Its player picked the way toward {@code exit} at a fork: a barred one asks them first (true: held). */
    public static boolean holdsAtChoice(MobEntity token, BoardSpaceBlockEntity space, BoardSpaceDestination exit) {
        if (gateOf(space) == null || !isBarred(space, exit.position(), token)) return false;
        ask(token, space, exit.position(), true);
        return true;
    }

    /** True while the token waits for its player's answer at a gate. */
    public static boolean isAsking(MobEntity token) {
        return PENDING.containsKey(token.getUuid());
    }

    // ---------------------------------------------------------------- the choice

    /** What its player may do in front of the gate. */
    public enum Choice {
        USE_KEY, KEEP_KEY, OTHER_WAY, WAIT, CANCEL, START;

        public String key() {
            return "gui.steveparty.key_gate." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** The choices offered without a key (or keeping it), « Wait here » always among them. */
    public static List<Choice> withoutKey(MobEntity token, BoardSpaceBlockEntity space, boolean atFork) {
        ItemStack gate = gateOf(space);
        List<Choice> choices = new ArrayList<>();
        if (atFork && space.getStockedDestinations().stream().filter(BoardSpaceDestination::isTile)
                .anyMatch(d -> !isBarred(space, d.position(), token))) choices.add(Choice.OTHER_WAY);
        choices.add(Choice.WAIT);
        TurnMoves.Roll roll = TurnMoves.rollOf(token);
        if (gate != null && KeyGateCartridgeItem.allowsCancel(gate) && roll != null && roll.origin() != null
                && !TurnMoves.wasReplayed(token) && !roll.origin().equals(space.getPos())) choices.add(Choice.CANCEL);
        if (gate != null && KeyGateCartridgeItem.allowsStart(gate) && token.getWorld() instanceof ServerWorld world
                && startOf(world, token, space.getPos()) != null) choices.add(Choice.START);
        return choices;
    }

    private static void ask(MobEntity token, BoardSpaceBlockEntity space, BlockPos exit, boolean atFork) {
        if (!(token.getWorld() instanceof ServerWorld world)) return;
        Pending previous = PENDING.get(token.getUuid());
        if (previous != null && world.getTime() - previous.since() < STALE_TICKS) return;
        PENDING.put(token.getUuid(), new Pending(space.getPos().toImmutable(), exit.toImmutable(), world.getTime()));
        ServerPlayerEntity player = CommonPots.playerOf(world, token);
        refuse(world, space.getPos(), exit);
        if (player == null) {
            resolve(world, token, Choice.WAIT);
            return;
        }
        MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.key_gate.needs_key").formatted(Formatting.AQUA),
                MessageUtils.MessageType.ACTION_BAR);
        if (hasKey(player)) {
            List<Choice> choices = List.of(Choice.USE_KEY, Choice.KEEP_KEY);
            prompt(player, token, choices, 1, choice -> {
                if (choice == Choice.KEEP_KEY) askWithoutKey(world, player, token, space, atFork);
                else resolve(world, token, choice);
            });
        } else {
            askWithoutKey(world, player, token, space, atFork);
        }
    }

    private static void askWithoutKey(ServerWorld world, ServerPlayerEntity player, MobEntity token, BoardSpaceBlockEntity space, boolean atFork) {
        List<Choice> choices = withoutKey(token, space, atFork);
        prompt(player, token, choices, choices.indexOf(Choice.WAIT), choice -> resolve(world, token, choice));
    }

    private static void prompt(ServerPlayerEntity player, MobEntity token, List<Choice> choices, int fallback,
                               java.util.function.Consumer<Choice> then) {
        List<DicePrompts.Option> options = new ArrayList<>();
        for (Choice choice : choices) options.add(new DicePrompts.Option(icon(choice), Text.translatable(choice.key()).formatted(Formatting.WHITE)));
        DicePrompts.Prompt prompt = DicePrompts.ask(player, Text.translatable("gui.steveparty.key_gate.title", token.getDisplayName()),
                DicePrompts.Layout.LIST, options, DicePrompts.TIMEOUT_TICKS, fallback,
                index -> then.accept(choices.get(Math.clamp(index, 0, choices.size() - 1))));
        if (prompt == null) then.accept(choices.get(Math.max(0, fallback)));
    }

    private static ItemStack icon(Choice choice) {
        return new ItemStack(switch (choice) {
            case USE_KEY -> ModItems.GATE_KEY;
            case KEEP_KEY -> Items.CHEST;
            case OTHER_WAY -> Items.ARROW;
            case WAIT -> Items.CLOCK;
            case CANCEL -> Items.ENDER_PEARL;
            case START -> Items.RED_BED;
        });
    }

    /** Applies {@code choice} for the token waiting at its gate (tests may call it directly). */
    public static void resolve(ServerWorld world, MobEntity token, Choice choice) {
        Pending pending = PENDING.remove(token.getUuid());
        if (pending == null || token.isRemoved()) return;
        if (!(world.getBlockEntity(pending.gate()) instanceof BoardSpaceBlockEntity space)) return;
        ServerPlayerEntity player = CommonPots.playerOf(world, token);
        switch (choice) {
            case USE_KEY -> {
                if (player == null || !takeKey(player)) {
                    halt(world, token, space);
                    return;
                }
                open(world, space, token, pending.exit());
                space.hideDestinations();
                TokenMovementService.moveEntity(token, pending.exit());
            }
            case OTHER_WAY -> {
                // The arrows are still there: its player picks another
                if (player != null) MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.key_gate.other_way")
                        .formatted(Formatting.AQUA), MessageUtils.MessageType.ACTION_BAR);
            }
            case CANCEL -> {
                TurnMoves.Roll roll = TurnMoves.rollOf(token);
                if (roll == null || roll.origin() == null || !(world.getBlockEntity(roll.origin()) instanceof BoardSpaceBlockEntity)) {
                    halt(world, token, space);
                    return;
                }
                TurnMoves.markReplayed(token);
                space.hideDestinations();
                announce(world, space.getPos(), Text.translatable("message.steveparty.key_gate.cancelled", token.getDisplayName()));
                TileTeleport.teleport(world, token, space.getPos(), roll.origin(), color(space), () -> {
                    if (!token.isRemoved()) TokenMovementService.moveEntityOnBoard(token, roll.total());
                });
            }
            case START -> {
                BlockPos start = startOf(world, token, space.getPos());
                if (start == null) {
                    halt(world, token, space);
                    return;
                }
                space.hideDestinations();
                announce(world, space.getPos(), Text.translatable("message.steveparty.key_gate.to_start", token.getDisplayName()));
                TileTeleport.teleport(world, token, space.getPos(), start, color(space), () -> {
                    if (token.isRemoved()) return;
                    TokenMovementService.halt(token, start);
                    TokenMovementService.stopOnCurrentBoardSpace(token, start);
                });
            }
            default -> halt(world, token, space);
        }
    }

    /** « Wait here »: the steps left are lost, the token lands on the gate's space. */
    private static void halt(ServerWorld world, MobEntity token, BoardSpaceBlockEntity space) {
        space.hideDestinations();
        announce(world, space.getPos(), Text.translatable("message.steveparty.key_gate.waits", token.getDisplayName()));
        TokenMovementService.halt(token, space.getPos());
        TokenMovementService.stopOnCurrentBoardSpace(token, space.getPos());
    }

    // ---------------------------------------------------------------- keys

    /** The player holds an item that opens gates. */
    public static boolean hasKey(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isIn(KEYS)) return true;
        }
        return false;
    }

    /** Uses up one key of the player (none in creative is used up). */
    private static boolean takeKey(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isIn(KEYS)) continue;
            if (!player.isInCreativeMode()) stack.decrement(1);
            player.getInventory().markDirty();
            return true;
        }
        return false;
    }

    /** A key opened the gate of {@code space}: the veil parts; it stays open if the cartridge says so. */
    public static void open(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token, BlockPos exit) {
        ItemStack gate = gateOf(space);
        if (gate == null) return;
        int stay = KeyGateCartridgeItem.stayOpen(gate);
        if (stay > 0) {
            int round = currentRound(token);
            BoardRuleCartridgeItem.putState(gate, KeyGateCartridgeItem.OPENED, stay >= KeyGateCartridgeItem.FOREVER ? -1 : Math.max(0, round));
            space.update();
        }
        Vec3d at = gateCentre(world, space.getPos(), exit);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 0.8F, 1.2F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0F, 1.4F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_TRIAL_SPAWNER_OPEN_SHUTTER, SoundCategory.BLOCKS, 0.6F, 1.3F);
        world.spawnParticles(new DustParticleEffect(Vec3d.unpackRgb(color(space)).toVector3f(), 1.2F), at.x, at.y + 0.6, at.z, 30, 0.35, 0.5, 0.35, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 0.6, at.z, 8, 0.2, 0.4, 0.2, 0.02);
        announce(world, space.getPos(), Text.translatable("message.steveparty.key_gate.opened", token.getDisplayName()));
    }

    /** The gate refuses the way: a dull clunk, its veil flickers. */
    private static void refuse(ServerWorld world, BlockPos gate, BlockPos exit) {
        Vec3d at = gateCentre(world, gate, exit);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.7F, 0.7F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.6F, 0.6F);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.6, at.z, 10, 0.25, 0.4, 0.25, 0.05);
    }

    /** Where the gate of {@code gate} toward {@code exit} stands: on the edge of its space, that side. */
    public static Vec3d gateCentre(ServerWorld world, BlockPos gate, BlockPos exit) {
        Vec3d from = BoardSpaces.standPos(world, gate);
        net.minecraft.util.math.Direction side = KeyGateCartridgeItem.sideOf(gate, exit);
        return from.add(side.getOffsetX() * 0.9, 0, side.getOffsetZ() * 0.9);
    }

    private static int color(BoardSpaceBlockEntity space) {
        return space.getActiveCartridgeItemStack().getOrDefault(ModComponents.COLOR, KeyGateCartridgeItem.COLOR);
    }

    private static void announce(ServerWorld world, BlockPos at, Text message) {
        MessageUtils.sendToNearby(world, BoardSpaces.standPos(world, at), 100, message.copy().formatted(Formatting.AQUA),
                MessageUtils.MessageType.ACTION_BAR);
    }

    // ---------------------------------------------------------------- back to the start

    /** The start space a token goes back to: its player's (bound to them) if there is one, else the nearest. */
    public static @Nullable BlockPos startOf(ServerWorld world, MobEntity token, BlockPos from) {
        UUID owner = ((TokenizedEntityInterface) token).steveparty$getTokenOwner();
        BlockPos nearest = null, own = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : PartyControllerEntity.findBoardSpaces(world, from, BoardSpaceType.TILE_START)) {
            if (!(world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity start)) continue;
            if (!ABoardSpaceBlock.countsAsStep(start.getCachedState().getBlock())) continue;
            String startOwner = start.getActiveCartridgeItemStack().get(ModComponents.TB_START_OWNER);
            if (owner != null && owner.toString().equals(startOwner)) own = pos;
            double distance = pos.getSquaredDistance(from);
            if (distance < best) {
                best = distance;
                nearest = pos;
            }
        }
        return own != null ? own : nearest;
    }
}
