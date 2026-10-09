package fr.lordfinn.steveparty.gametest.kit;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The ground, tokens and parties the board tests are played on. */
public final class TestBoards {
    private TestBoards() {
    }

    /** A {@code size} × {@code size} stone floor at relative height 0, from the test's corner. */
    public static void floor(TestContext context, int size) {
        floor(context, size, size, 0);
    }

    /** A {@code sizeX} × {@code sizeZ} stone floor at relative height {@code y}, from the test's corner. */
    public static void floor(TestContext context, int sizeX, int sizeZ, int y) {
        for (int x = 0; x < sizeX; x++) for (int z = 0; z < sizeZ; z++) context.setBlockState(new BlockPos(x, y, z), Blocks.STONE);
    }

    /** A pig made a token of {@code owner}, in game, at the relative position {@code pos}. */
    public static PigEntity token(TestContext context, BlockPos pos, @Nullable UUID owner) {
        PigEntity pig = context.spawnMob(EntityType.PIG, pos);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner);
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        return pig;
    }

    /**
     * A Party Controller at the relative position {@code controller} (on stone) running a party of the tokens
     * {@code tokens}: {@code rounds} rounds of a turn each, in order, then the end. The party is at the first turn.
     */
    public static PartyControllerEntity party(TestContext context, BlockPos controller, int rounds, UUID... tokens) {
        context.setBlockState(controller.down(), Blocks.STONE);
        context.setBlockState(controller, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity entity = context.getBlockEntity(controller);
        PartyData data = new PartyData();
        for (UUID token : tokens) data.addToken(token);
        data.addStep(new PartyStep());
        for (int round = 0; round < rounds; round++) {
            for (UUID token : tokens) data.addStep(new TokenTurnPartyStep(token, null));
        }
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(tokens))));
        entity.setPartyData(data);
        entity.nextStep();
        entity.nextStep();
        return entity;
    }
}
