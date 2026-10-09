package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A space whose landing summons mobs for a show (a {@code BoardSequences} service): the token's turn waits for the
 * show ({@link #keepsTurn}, said by the subclass) and goes on once it is over ({@link #resumeTurn}). A space that only
 * plays in a running party ({@link #partyOnly}) is a plain landing outside one. Its cartridge's colour, unless dyed.
 */
public abstract class MobTileBehavior extends ABoardSpaceBehavior {
    private final int color;

    protected MobTileBehavior(BoardSpaceType type, int color) {
        super(type);
        this.color = color;
    }

    /** Whether it only plays in a running party (its coins and stars are the party's); true by default. */
    protected boolean partyOnly() {
        return true;
    }

    /**
     * {@code token} stopped on {@code tile}: its show starts (and gives the landing's feedback), {@code onDone} to be
     * run once it is over.
     */
    protected abstract void startShow(ServerWorld world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity tile,
                                      @Nullable PartyControllerEntity party, Runnable onDone);

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity,
                                     @Nullable PartyControllerEntity partyController) {
        if (!(world instanceof ServerWorld serverWorld) || boardSpaceEntity == null) return;
        if (partyOnly() && (partyController == null || partyController.isRemoved() || !partyController.getPartyData().isStarted())) {
            landPlain(serverWorld, boardSpaceEntity, token, partyController);
            return;
        }
        startShow(serverWorld, pos, token, boardSpaceEntity, partyController, resumeTurn(partyController));
    }

    /** A plain landing (nothing summoned). */
    protected static void landPlain(ServerWorld world, BoardSpaceBlockEntity tile, MobEntity token,
                                    @Nullable PartyControllerEntity party) {
        TileFeedback.land(world, tile, token, party, TileFeedback.Landing.DEFAULT, TileFeedback.Landing.DEFAULT.noticeKey());
    }

    /** The party's next step once the show is over, if the landing's step is still the current one (none: nothing). */
    protected static Runnable resumeTurn(@Nullable PartyControllerEntity party) {
        PartyStep step = party == null ? null : party.getPartyData().getCurrentStep();
        return () -> {
            if (party != null && !party.isRemoved() && step != null && party.getPartyData().getCurrentStep() == step) {
                party.nextStep();
            }
        };
    }

    @Override
    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        if (!stack.contains(ModComponents.COLOR)) setColor(boardSpaceBlockEntity, color);
    }
}
