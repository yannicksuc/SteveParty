package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.powerups.effects.StarRelocator;
import fr.lordfinn.steveparty.powerups.effects.StarWhistleEffect;
import fr.lordfinn.steveparty.powerups.effects.StarWhistleEffect.Outcome;
import fr.lordfinn.steveparty.powerups.effects.StarWhistleEffect.Result;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Star Whistle power-up's effect: the Star leaves its space for another active Star space (never the same one),
 * stays put when there is no other one, and nothing happens without a Star; only a move uses the power-up up.
 */
public class StarWhistleGameTests implements FabricGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 1);

    /** A Star kept in memory: the active Star spaces and the one carrying the Star (the Star cartridge's stand-in). */
    private static final class TestStar implements StarRelocator {
        final List<BlockPos> spaces = new ArrayList<>();
        @Nullable BlockPos star;
        int moves;

        @Override
        public Optional<BlockPos> currentStarSpace(PartyControllerEntity party) {
            return Optional.ofNullable(star);
        }

        @Override
        public List<BlockPos> activeStarSpaces(PartyControllerEntity party) {
            return spaces;
        }

        @Override
        public boolean moveStarTo(PartyControllerEntity party, BlockPos space) {
            if (!spaces.contains(space)) return false;
            star = space;
            moves++;
            return true;
        }
    }

    private static PartyControllerEntity party(TestContext context) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        return context.getBlockEntity(CONTROLLER);
    }

    private static TestStar star(TestContext context, int count) {
        TestStar star = new TestStar();
        for (int i = 0; i < count; i++) star.spaces.add(context.getAbsolutePos(new BlockPos(3 + 2 * i, 1, 3)));
        star.star = star.spaces.isEmpty() ? null : star.spaces.get(0);
        return star;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void starWhistleMovesTheStarToAnotherSpace(TestContext context) {
        PartyControllerEntity party = party(context);
        TestStar star = star(context, 3);
        StarWhistleEffect effect = new StarWhistleEffect(star);

        // In a row: every whistle sends the Star to another active space
        Random random = Random.create(42L);
        for (int i = 0; i < 60; i++) {
            BlockPos before = star.star;
            Result result = effect.use(party, null, null, random);
            context.assertTrue(result.outcome() == Outcome.MOVED, "the Star moves: " + result);
            context.assertTrue(result.consumed(), "a move uses the whistle up");
            context.assertTrue(before.equals(result.from()), "it leaves the space carrying it");
            context.assertTrue(result.to() != null && !result.to().equals(before), "never the same space: " + result);
            context.assertTrue(star.spaces.contains(result.to()), "it lands on an active Star space");
            context.assertTrue(result.to().equals(star.star), "the Star now stands where the result says");
        }
        context.assertTrue(star.moves == 60, "one move per whistle, got " + star.moves);

        // From the same space: both other spaces come out, the space itself never
        Set<BlockPos> reached = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            star.star = star.spaces.get(0);
            reached.add(effect.use(party, null, null, random).to());
        }
        context.assertTrue(reached.equals(Set.of(star.spaces.get(1), star.spaces.get(2))),
                "the Star goes to any other space at random, got " + reached);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void starWhistleWithNoOtherSpaceIsNotUsed(TestContext context) {
        PartyControllerEntity party = party(context);
        TestStar star = star(context, 1);
        BlockPos only = star.star;
        Result result = new StarWhistleEffect(star).use(party, null, null, Random.create(7L));
        context.assertTrue(result.outcome() == Outcome.NO_OTHER_SPACE, "no other Star space: " + result);
        context.assertTrue(!result.consumed(), "the whistle is not used up");
        context.assertTrue(only.equals(star.star) && star.moves == 0, "the Star stays put");

        // The same space listed twice is still no other space
        star.spaces.add(only);
        context.assertTrue(!star.moveStarElsewhere(party, Random.create(7L)), "never back onto its own space");
        context.assertTrue(new StarWhistleEffect(star).use(party, null, null, Random.create(7L)).outcome() == Outcome.NO_OTHER_SPACE,
                "a duplicate of its own space is not another space");
        context.assertTrue(only.equals(star.star) && star.moves == 0, "the Star still stays put");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void starWhistleWithNoStarIsNotUsed(TestContext context) {
        PartyControllerEntity party = party(context);
        TestStar star = star(context, 3);
        star.star = null;
        Result result = new StarWhistleEffect(star).use(party, null, null, Random.create(3L));
        context.assertTrue(result.outcome() == Outcome.NO_STAR, "no Star on the board: " + result);
        context.assertTrue(!result.consumed(), "the whistle is not used up");
        context.assertTrue(star.star == null && star.moves == 0, "no Star appears");

        Result none = new StarWhistleEffect(StarRelocator.NONE).use(party, null, null, Random.create(3L));
        context.assertTrue(none.outcome() == Outcome.NO_STAR && !none.consumed(), "no Star at all: " + none);
        context.complete();
    }
}
