package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Kind;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Played;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * What the dice tests share: a board, tokens owned by the test's player (a roll only moves its roller's tokens, so
 * the tests of a batch never move each other's), a party at the turn of a token, dice thrown and hit.
 */
final class DiceTestKit {
    static final BlockPos CONTROLLER = new BlockPos(0, 1, 7);
    /** A path of tiles 2 blocks apart: along x on z = 1, then back along x on z = 3. */
    static final List<BlockPos> PATH = List.of(
            new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1), new BlockPos(7, 1, 1),
            new BlockPos(7, 1, 3), new BlockPos(5, 1, 3), new BlockPos(3, 1, 3), new BlockPos(1, 1, 3));

    private DiceTestKit() {
    }

    // ---------------------------------------------------------------- end of test

    private static final Map<TestContext, List<Runnable>> AT_END = new WeakHashMap<>();

    /** What to undo when the test ends (a test has one final task: they are run together). */
    static void atEnd(TestContext context, Runnable task) {
        List<Runnable> tasks = AT_END.get(context);
        if (tasks == null) {
            List<Runnable> created = new ArrayList<>();
            AT_END.put(context, created);
            context.addFinalTask(() -> created.forEach(Runnable::run));
            tasks = created;
        }
        tasks.add(task);
    }

    /** Runs {@code then} as soon as {@code condition} holds, failing after {@code ticks}. */
    static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        if (condition.getAsBoolean()) {
            then.run();
            return;
        }
        context.assertTrue(ticks > 0, "timed out: " + what);
        context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
    }

    // ---------------------------------------------------------------- players, board, tokens

    @SuppressWarnings("removal")
    static ServerPlayerEntity player(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        atEnd(context, () -> context.getWorld().getServer().getPlayerManager().remove(player));
        return player;
    }

    static ItemStack plain() {
        return new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
    }

    static BoardSpaceBlockEntity tile(TestContext context, BlockPos pos, ItemStack cartridge, BlockPos... to) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        List<BlockPos> destinations = new ArrayList<>();
        for (BlockPos next : to) destinations.add(context.getAbsolutePos(next));
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(destinations, ""));
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        tile.setStack(0, cartridge);
        return tile;
    }

    /** The first {@code count} tiles of {@link #PATH}, each linked to the next; {@code special} at index {@code at}. */
    static void path(TestContext context, int count, int at, @Nullable ItemStack special) {
        for (int i = 0; i < count; i++) {
            BlockPos[] next = i + 1 < count ? new BlockPos[]{PATH.get(i + 1)} : new BlockPos[0];
            tile(context, PATH.get(i), i == at && special != null ? special : plain(), next);
        }
    }

    static PigEntity token(TestContext context, BlockPos on, @Nullable UUID owner) {
        PigEntity pig = context.spawnMob(EntityType.PIG, on);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner);
        atEnd(context, () -> token.steveparty$setTokenized(false));
        return pig;
    }

    static void assertOn(TestContext context, MobEntity token, BlockPos at, String what) {
        BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(token);
        context.assertTrue(on != null && on.getPos().equals(context.getAbsolutePos(at)),
                what + ": on " + (on == null ? "nothing" : on.getPos()) + ", expected " + context.getAbsolutePos(at));
    }

    static boolean isOn(TestContext context, MobEntity token, BlockPos at) {
        BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(token);
        return on != null && on.getPos().equals(context.getAbsolutePos(at))
                && ((TokenizedEntityInterface) token).steveparty$getNbSteps() == 0;
    }

    // ---------------------------------------------------------------- party

    /**
     * A party of these tokens, all played by {@code owner}, at the turn of the first one (step 1); each token then
     * has a turn, and the first one a last turn.
     */
    static PartyControllerEntity party(TestContext context, UUID owner, MobEntity... tokens) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        for (MobEntity token : tokens) {
            data.addToken(token.getUuid());
            ((TokenizedEntityInterface) token).steveparty$setStatus(TokenStatus.IN_GAME);
        }
        data.addStep(new PartyStep());
        for (MobEntity token : tokens) data.addStep(new TokenTurnPartyStep(token.getUuid(), owner));
        data.addStep(new TokenTurnPartyStep(tokens[0].getUuid(), owner));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        context.assertEquals(data.getStepIndex(), 1, "the turn of the first token");
        atEnd(context, () -> context.removeBlock(CONTROLLER));
        return controller;
    }

    static BooleanSupplier turnEnded(PartyControllerEntity controller) {
        return () -> controller.getPartyData().getStepIndex() >= 2;
    }

    static List<Played> record(TestContext context) {
        List<Played> played = new ArrayList<>();
        Consumer<Played> listener = played::add;
        TileFeedback.LISTENERS.add(listener);
        atEnd(context, () -> TileFeedback.LISTENERS.remove(listener));
        return played;
    }

    static boolean played(List<Played> played, TestContext context, Kind kind, BlockPos at) {
        BlockPos absolute = context.getAbsolutePos(at);
        return played.stream().anyMatch(event -> event.kind() == kind && event.tile().equals(absolute));
    }

    // ---------------------------------------------------------------- dice

    static Item face(String path) {
        Item item = Registries.ITEM.get(Steveparty.id(path));
        if (item == net.minecraft.item.Items.AIR) throw new AssertionError("no such face item: " + path);
        return item;
    }

    /** A forged die with these faces (item paths), each of weight 1. */
    static ItemStack die(String... faces) {
        List<ItemStack> stacks = new ArrayList<>();
        for (String face : faces) stacks.add(new ItemStack(face(face)));
        return DiceFacesComponent.createDie(stacks);
    }

    static ItemStack with(ItemStack die, DiceModule module, int count) {
        Map<DiceModule, Integer> modules = new LinkedHashMap<>(DiceModules.of(die));
        modules.put(module, count);
        return DiceModules.set(die, modules);
    }

    /** {@code roller} throws {@code die}: the die floats over {@code at} and rolls. */
    static DiceEntity thrown(TestContext context, ServerPlayerEntity roller, ItemStack die, BlockPos at) {
        DiceEntity dice = context.spawnEntity(ModEntities.DICE_ENTITY, at.up(2));
        dice.setNoGravity(true);
        dice.setOwner(roller.getUuid());
        dice.setItemReference(die.copyWithCount(1));
        dice.startRoll();
        atEnd(context, () -> {
            if (!dice.isRemoved()) dice.discard();
        });
        return dice;
    }

    /** {@code player} hits the die (not sneaking). */
    static void hit(TestContext context, DiceEntity dice, ServerPlayerEntity player) {
        ServerWorld world = context.getWorld();
        dice.damage(world, world.getDamageSources().playerAttack(player), 1F);
    }
}
