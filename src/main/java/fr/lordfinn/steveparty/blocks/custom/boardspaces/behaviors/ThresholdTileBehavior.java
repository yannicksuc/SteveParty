package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.items.custom.cartridges.ThresholdCartridgeItem;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * The role of a Threshold obstacle Cartridge (see {@link ThresholdCartridgeItem}): a token reaching this board space
 * with steps left is tested against the roll of its move. It gets over (a bright chime, its move goes on, a Router
 * pulses 15) or it is stopped here (a dull thud, a barrier, its steps left are lost: it lands here, check point
 * included). On a Tile too the test is made when the token reaches it, not when it stops (a wall): a token that ends
 * its move exactly here is not tested. Its next move starts from here without a new test (it is not reaching it).
 * A token going back (Move Back, a reversed roll) is never stopped. No roll known (a move not made by a die): it gets
 * over. The Skeleton Key dice module does not open it (it is not a Stop space).
 */
public class ThresholdTileBehavior extends ABoardSpaceBehavior {
    /** The last test on each obstacle: true got over (or no test), false stopped. For its Router. */
    private static final Map<GlobalPos, Boolean> LAST = ServerMemory.forgetOnStop(new HashMap<>());

    public ThresholdTileBehavior() {
        super(BoardSpaceType.TILE_THRESHOLD);
    }

    @Override
    public boolean onTokenReached(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token, int stepsLeft) {
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (!(cartridge.getItem() instanceof ThresholdCartridgeItem)) return false;
        if (stepsLeft <= 0 || AdvanceBackMoves.isRouted(token)) {
            // Stopping here (or going back) is no test
            if (stepsLeft <= 0 && !TurnMoves.isHaltedOn(token, space.getPos()))
                LAST.put(GlobalPos.create(world.getRegistryKey(), space.getPos().toImmutable()), true);
            return false;
        }
        TurnMoves.Roll roll = TurnMoves.rollOf(token);
        boolean passes = roll == null || ThresholdCartridgeItem.passes(cartridge, roll);
        LAST.put(GlobalPos.create(world.getRegistryKey(), space.getPos().toImmutable()), passes);
        Vec3d at = BoardSpaces.standPos(world, space.getPos());
        Text name = token.getCustomName() != null ? token.getCustomName() : token.getName();
        Text condition = ThresholdCartridgeItem.label(cartridge);
        if (passes) {
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.6F, 1.5F);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 0.35F, 1.4F);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.5, at.z, 8, 0.3, 0.3, 0.3, 0.0);
            MessageUtils.sendToNearby(world, at, 100, Text.translatable("message.steveparty.threshold.passed", name, condition)
                    .formatted(Formatting.GREEN), MessageUtils.MessageType.ACTION_BAR);
            BoardSpaceRedstoneRouterBlockEntity.onBoardSpaceEvent(world, space.getPos(), 15);
            return false;
        }
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.35F, 0.6F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.7F, 0.6F);
        // The barrier stands over the space for a moment
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK_MARKER, Blocks.BARRIER.getDefaultState()),
                at.x, at.y + 0.9, at.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SMOKE, at.x, at.y + 0.3, at.z, 8, 0.3, 0.2, 0.3, 0.01);
        MessageUtils.sendToNearby(world, at, 100, Text.translatable("message.steveparty.threshold.blocked", name, condition,
                roll.total()).formatted(Formatting.RED), MessageUtils.MessageType.ACTION_BAR);
        return true;
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

    /** The condition a roll must meet to get over. */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (stack.getItem() instanceof ThresholdCartridgeItem)
            info.line(TileInfo.line("threshold", TileInfo.value(ThresholdCartridgeItem.label(stack))));
    }
}
