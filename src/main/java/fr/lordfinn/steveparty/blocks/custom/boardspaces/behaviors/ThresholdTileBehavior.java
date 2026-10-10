package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.items.custom.cartridges.ThresholdCartridgeItem;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.DiceRollEffects;
import fr.lordfinn.steveparty.service.ThresholdGates;
import fr.lordfinn.steveparty.service.TurnMoves;
import fr.lordfinn.steveparty.utils.MessageUtils;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The role of a Threshold obstacle Cartridge (see {@link ThresholdCartridgeItem}): a token reaching this board space
 * with steps left is tested against the roll of its move. It gets over (a bright chime, its move goes on, a Router
 * pulses 15) or it is stopped here (a dull thud, a barrier, its steps left are lost: it lands here, check point
 * included). On a Tile too the test is made when the token reaches it, not when it stops (a wall): a token that ends
 * its move exactly here is not tested. Its next move starts from here without a new test (it is not reaching it).
 * A token going back (Move Back, a reversed roll) is never stopped. No roll known (a move not made by a die): it gets
 * over. The Skeleton Key dice module does not open it (it is not a Stop space).
 * <p>
 * In barrier mode it is cleared once ({@link ThresholdGates}): a token it does not let through yet is tested passing
 * by or stopping here; stopped, it stays blocked here and each of its next turns is a new try with its throw
 * ({@link #retry}): cleared, it moves with that throw; missed, its turn ends there. Opened for all, the first success
 * opens it for every token (its barrier mark goes away); else each token has to clear it once.
 */
public class ThresholdTileBehavior extends ABoardSpaceBehavior {
    /** The last test on each obstacle: true got over (or no test), false stopped. For its Router. */
    private static final Map<GlobalPos, Boolean> LAST = ServerMemory.forgetOnStop(new HashMap<>());
    /** How often a closed barrier shows its mark again (a block marker particle lasts 80 ticks). */
    private static final int BARRIER_MARK_TICKS = 70;

    public ThresholdTileBehavior() {
        super(BoardSpaceType.TILE_THRESHOLD);
    }

    @Override
    public boolean onTokenReached(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token, int stepsLeft) {
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (!(cartridge.getItem() instanceof ThresholdCartridgeItem)) return false;
        GlobalPos key = GlobalPos.create(world.getRegistryKey(), space.getPos().toImmutable());
        if (ThresholdCartridgeItem.isBarrier(cartridge)) return reachBarrier(world, space, cartridge, token, stepsLeft, key);
        if (stepsLeft <= 0 || AdvanceBackMoves.isRouted(token)) {
            // Stopping here (or going back) is no test
            if (stepsLeft <= 0 && !TurnMoves.isHaltedOn(token, space.getPos())) LAST.put(key, true);
            return false;
        }
        TurnMoves.Roll roll = TurnMoves.rollOf(token);
        boolean passes = roll == null || ThresholdCartridgeItem.passes(cartridge, roll);
        LAST.put(key, passes);
        Text name = nameOf(token);
        Text condition = ThresholdCartridgeItem.label(cartridge);
        if (passes) {
            getOver(world, space, Text.translatable("message.steveparty.threshold.passed", name, condition));
            return false;
        }
        bump(world, space, Text.translatable("message.steveparty.threshold.blocked", name, condition, roll.total()));
        return true;
    }

    /**
     * A barrier: a token it does not let through yet (see {@link ThresholdGates#isOpenFor}) is tested, passing by or
     * stopping here. It clears it (it goes on, the barrier remembers it) or it is stopped here, its steps left lost.
     */
    private static boolean reachBarrier(ServerWorld world, BoardSpaceBlockEntity space, ItemStack cartridge, MobEntity token,
                                        int stepsLeft, GlobalPos key) {
        BlockPos pos = space.getPos();
        if (AdvanceBackMoves.isRouted(token) || TurnMoves.isHaltedOn(token, pos)
                || ThresholdGates.isOpenFor(world, pos, token, ThresholdCartridgeItem.opensForAll(cartridge))) {
            if (stepsLeft <= 0 && !TurnMoves.isHaltedOn(token, pos)) LAST.put(key, true);
            return false;
        }
        TurnMoves.Roll roll = TurnMoves.rollOf(token);
        if (roll == null) return false; // a move not made by a die: it goes by, the barrier still closed for it
        boolean passes = ThresholdCartridgeItem.passes(cartridge, roll);
        LAST.put(key, passes);
        if (passes) {
            cleared(world, space, cartridge, token);
            return false;
        }
        bump(world, space, Text.translatable("message.steveparty.threshold.barrier_blocked", nameOf(token),
                condition(cartridge), roll.total()));
        // Stopping here exactly: a « Stop » landing too (with steps left the move service halts it)
        if (stepsLeft <= 0) TurnMoves.halt(token, pos);
        return true;
    }

    /**
     * {@code token} rolled {@code outcome} for its move: if it stands blocked at a barrier (one that does not let it
     * through yet), this throw is its new try. Cleared: it moves with this throw as usual. Missed: it stays and its turn
     * ends (see {@link DiceRollEffects#skipMove}). A throw that walks no step forward (coins, a swap, going back) is
     * no try.
     *
     * @return true if the throw is spent (missed: the token does not move)
     */
    public static boolean retry(ServerWorld world, MobEntity token, DiceOutcome outcome) {
        if (outcome.isSpecial() || outcome.steps() < 0) return false;
        BoardSpaceBlockEntity space = BoardSpaces.boardSpaceOf(token);
        if (space == null) return false;
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (!(cartridge.getItem() instanceof ThresholdCartridgeItem) || !ThresholdCartridgeItem.isBarrier(cartridge)) return false;
        BlockPos pos = space.getPos();
        if (ThresholdGates.isOpenFor(world, pos, token, ThresholdCartridgeItem.opensForAll(cartridge))) return false;
        TurnMoves.Roll roll = TurnMoves.rollOf(token);
        if (roll == null) return false;
        boolean passes = ThresholdCartridgeItem.passes(cartridge, roll);
        LAST.put(GlobalPos.create(world.getRegistryKey(), pos.toImmutable()), passes);
        if (passes) {
            cleared(world, space, cartridge, token);
            return false;
        }
        bump(world, space, Text.translatable("message.steveparty.threshold.retry_missed", nameOf(token),
                condition(cartridge), roll.total()));
        DiceRollEffects.skipMove(world, token);
        return true;
    }

    /** At its turn, a token blocked at a barrier is told what its throw must make. */
    public static void announce(ServerWorld world, MobEntity token) {
        BoardSpaceBlockEntity space = BoardSpaces.boardSpaceOf(token);
        if (space == null) return;
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (!(cartridge.getItem() instanceof ThresholdCartridgeItem) || !ThresholdCartridgeItem.isBarrier(cartridge)
                || ThresholdGates.isOpenFor(world, space.getPos(), token, ThresholdCartridgeItem.opensForAll(cartridge))) return;
        Vec3d at = BoardSpaces.standPos(world, space.getPos());
        MessageUtils.sendToNearby(world, at, 100, Text.translatable("message.steveparty.threshold.barrier_turn",
                nameOf(token), condition(cartridge)).formatted(Formatting.GOLD), MessageUtils.MessageType.ACTION_BAR);
    }

    /** {@code token} cleared the barrier: it goes on; opened for all, it opens for everyone. */
    private static void cleared(ServerWorld world, BoardSpaceBlockEntity space, ItemStack cartridge, MobEntity token) {
        boolean first = ThresholdGates.clear(world, space.getPos(), token);
        boolean opens = first && ThresholdCartridgeItem.opensForAll(cartridge);
        getOver(world, space, Text.translatable(opens ? "message.steveparty.threshold.barrier_opened"
                : "message.steveparty.threshold.barrier_cleared", nameOf(token), condition(cartridge)));
        if (opens) {
            Vec3d at = BoardSpaces.standPos(world, space.getPos());
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 0.8F, 0.8F);
            world.spawnParticles(ParticleTypes.CLOUD, at.x, at.y + 0.9, at.z, 10, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private static Text nameOf(MobEntity token) {
        return token.getCustomName() != null ? token.getCustomName() : token.getName();
    }

    /** Got over: a bright chime, a Router pulses 15. */
    private static void getOver(ServerWorld world, BoardSpaceBlockEntity space, MutableText message) {
        Vec3d at = BoardSpaces.standPos(world, space.getPos());
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.6F, 1.5F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 0.35F, 1.4F);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.5, at.z, 8, 0.3, 0.3, 0.3, 0.0);
        MessageUtils.sendToNearby(world, at, 100, message.formatted(Formatting.GREEN), MessageUtils.MessageType.ACTION_BAR);
        BoardSpaceRedstoneRouterBlockEntity.onBoardSpaceEvent(world, space.getPos(), 15);
    }

    /** Stopped: a dull thud, the barrier stands over the space for a moment. */
    private static void bump(ServerWorld world, BoardSpaceBlockEntity space, MutableText message) {
        Vec3d at = BoardSpaces.standPos(world, space.getPos());
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.35F, 0.6F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.7F, 0.6F);
        barrierMark(world, at);
        world.spawnParticles(ParticleTypes.SMOKE, at.x, at.y + 0.3, at.z, 8, 0.3, 0.2, 0.3, 0.01);
        MessageUtils.sendToNearby(world, at, 100, message.formatted(Formatting.RED), MessageUtils.MessageType.ACTION_BAR);
    }

    private static void barrierMark(ServerWorld world, Vec3d at) {
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK_MARKER, Blocks.BARRIER.getDefaultState()),
                at.x, at.y + 0.9, at.z, 1, 0, 0, 0, 0);
    }

    /** A barrier not opened for all keeps its barrier mark over the space, while a player is near. */
    @Override
    public void tick(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, int ticks) {
        if (ticks % BARRIER_MARK_TICKS != 0 || !(stack.getItem() instanceof ThresholdCartridgeItem)
                || !ThresholdCartridgeItem.isBarrier(stack)) return;
        BlockPos pos = space.getPos();
        if (world.getClosestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 32, false) == null) return;
        if (ThresholdCartridgeItem.opensForAll(stack) && ThresholdGates.isOpen(world, pos)) return;
        barrierMark(world, BoardSpaces.standPos(world, pos));
    }

    /** Stopped by the obstacle: a « Stop » landing that says why; else a plain one. */
    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity,
                                     PartyControllerEntity partyController) {
        if (!(world instanceof ServerWorld serverWorld) || boardSpaceEntity == null) return;
        if (TurnMoves.isHaltedOn(token, pos)) {
            TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, TileFeedback.Landing.STOP,
                    "message.steveparty.tile_landed.threshold", ThresholdCartridgeItem.label(boardSpaceEntity.getActiveCartridgeItemStack()));
        } else {
            landPlain(serverWorld, boardSpaceEntity, token, partyController);
        }
    }

    /** A Router reading it: 15 when the last token got over, nothing when it was stopped. */
    @Override
    public int comparatorLevel(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        if (!(boardSpaceEntity.getWorld() instanceof ServerWorld world)) return 15;
        Boolean last = LAST.get(GlobalPos.create(world.getRegistryKey(), boardSpaceEntity.getPos()));
        return last == null || last ? 15 : 0;
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return comparatorLevel(boardSpaceEntity, stack) == 0 ? TileFeedback.Landing.STOP : TileFeedback.Landing.DEFAULT;
    }

    /**
     * In game: the condition a roll must meet to get over; a barrier says so, then whether it is open (for all) or how
     * many of the party's tokens cleared it (each); the Explorer's Helmet names who cleared it.
     */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (!(stack.getItem() instanceof ThresholdCartridgeItem)) return;
        info.line(TileInfo.Glyph.CONDITION, TileInfo.line("threshold", TileInfo.value(condition(stack))));
        if (!ThresholdCartridgeItem.isBarrier(stack)) return;
        Set<UUID> cleared = ThresholdGates.cleared(world, space.getPos());
        if (ThresholdCartridgeItem.opensForAll(stack)) {
            if (cleared.isEmpty()) info.line(TileInfo.Glyph.CLOSED, TileInfo.line("threshold.barrier",
                    TileInfo.bad(Text.translatable("hud.steveparty.tile_info.threshold.closed"))));
            else info.line(TileInfo.Glyph.OPEN, TileInfo.line("threshold.barrier",
                    TileInfo.good(Text.translatable("hud.steveparty.tile_info.threshold.open"))));
        } else {
            PartyControllerEntity party = ThresholdGates.partyAt(world, space.getPos());
            int of = party == null ? 0 : party.getPartyData().getTokens().size();
            Text count = TileInfo.value(of > 0 ? cleared.size() + "/" + of : String.valueOf(cleared.size()));
            info.line(TileInfo.Glyph.CLOSED, TileInfo.line("threshold.barrier_each", count));
        }
        List<Text> names = new ArrayList<>();
        for (UUID id : cleared) {
            if (world.getEntity(id) instanceof MobEntity token) names.add(nameOf(token));
        }
        if (!names.isEmpty()) info.line(TileInfo.Glyph.OPEN, TileInfo.line("threshold.cleared_by",
                Texts.join(names, Text.literal(", "))), TileInfo.Layer.DETAIL);
    }

    /** The condition, short: « ≥ 7 », « DOUBLE ». */
    public static Text condition(ItemStack stack) {
        ThresholdCartridgeItem.Operator operator = ThresholdCartridgeItem.operator(stack);
        return switch (operator) {
            case AT_LEAST -> Text.literal("≥ " + ThresholdCartridgeItem.value(stack));
            case AT_MOST -> Text.literal("≤ " + ThresholdCartridgeItem.value(stack));
            case MORE -> Text.literal("> " + ThresholdCartridgeItem.value(stack));
            case LESS -> Text.literal("< " + ThresholdCartridgeItem.value(stack));
            case EXACTLY -> Text.literal("= " + ThresholdCartridgeItem.value(stack));
            default -> ThresholdCartridgeItem.label(stack);
        };
    }
}
