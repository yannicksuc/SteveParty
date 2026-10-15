package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.HopSwitchBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.blocks.custom.PartyController.StepPulse;
import fr.lordfinn.steveparty.blocks.custom.PiggyBankBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.copy.BlockSettings;
import fr.lordfinn.steveparty.board.copy.BlockSettingsKinds;
import fr.lordfinn.steveparty.board.copy.WrenchCopy;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.podium.PodiumSignal;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Wrench copies the settings of a block (sneaking) and pastes them on the blocks of its family: settings only, so
 * nothing is created or taken in survival; in creative, copies of the source's items fill what the target has empty.
 */
public class WrenchCopyGameTests implements SteveGameTest {

    /** A block at {@code relative} on stone; its absolute position. */
    private static BlockPos place(TestContext context, Block block, int x, int z) {
        BlockPos relative = new BlockPos(x, 1, z);
        context.setBlockState(relative.down(), Blocks.STONE);
        context.setBlockState(relative, block);
        return context.getAbsolutePos(relative);
    }

    /** {@code test} by a player in {@code mode}, standing in the middle, a Wrench in hand. */
    private static void as(TestContext context, GameMode mode, Consumer<ServerPlayerEntity> test) {
        ServerPlayerEntity player = TestPlayers.mock(context, mode);
        Vec3d at = context.getAbsolute(new Vec3d(4.5, 1, 4.5));
        player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        player.getInventory().clear();
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
        try {
            test.accept(player);
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /** A sneak + right click with the Wrench on {@code pos}, as the game routes it. */
    private static void sneakClick(ServerPlayerEntity player, BlockPos pos) {
        player.setSneaking(true);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        ActionResult result = UseBlockCallback.EVENT.invoker().interact(player, player.getWorld(), Hand.MAIN_HAND, hit);
        player.setSneaking(false);
        if (result != ActionResult.SUCCESS) throw new AssertionError("the Wrench took no click on " + pos);
    }

    /** {@link WrenchCopy#use} on {@code pos} (what a sneak click does, without its repeat guard). */
    private static void use(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getServerWorld();
        BlockSettings kind = BlockSettingsKinds.of(world.getBlockState(pos));
        if (kind == null) throw new AssertionError("no settings at " + pos);
        WrenchCopy.use(player, player.getMainHandStack(), world, kind.settingsPos(world, pos), kind);
    }

    private static ItemStack shop(int purchases, BlockPos... links) {
        ItemStack shop = new ItemStack(ModItems.SHOP_CARTRIDGE);
        shop.set(ModComponents.SHOP_PURCHASES, purchases);
        if (links.length > 0) shop.set(ModComponents.DESTINATIONS_COMPONENT,
                new fr.lordfinn.steveparty.components.DestinationsComponent(List.of(links), "minecraft:overworld"));
        return shop;
    }

    private static BoardSpaceBlockEntity tile(TestContext context, BlockPos pos) {
        return (BoardSpaceBlockEntity) context.getWorld().getBlockEntity(pos);
    }

    // ---------------------------------------------------------------- cartridges, survival

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void survivalPastesSettingsOnlyNeverItems(TestContext context) {
        BlockPos source = place(context, ModBlocks.TILE, 1, 1), empty = place(context, ModBlocks.TILE, 3, 1),
                sameKind = place(context, ModBlocks.TILE, 5, 1), otherKind = place(context, ModBlocks.TILE, 7, 1),
                checkpoint = place(context, ModBlocks.CHECK_POINT, 1, 6);
        BlockPos a = context.getAbsolutePos(new BlockPos(1, 1, 3)), b = context.getAbsolutePos(new BlockPos(5, 1, 3));
        tile(context, source).setStack(0, shop(3, a));
        tile(context, sameKind).setStack(0, shop(1, b));
        tile(context, otherKind).setStack(0, new ItemStack(ModItems.STAR_CARTRIDGE));
        ((CartridgeContainerBlockEntity) context.getWorld().getBlockEntity(checkpoint)).setStack(4, shop(1));
        as(context, GameMode.SURVIVAL, player -> {
            sneakClick(player, source);
            ItemStack wrench = player.getMainHandStack();
            context.assertTrue(wrench.contains(ModComponents.WRENCH_COPY), "copied into the Wrench");
            context.assertTrue(wrench.get(ModComponents.WRENCH_COPY).label().getString().equals(new ItemStack(ModItems.SHOP_CARTRIDGE).getName().getString()),
                    "shown as its cartridge");
            context.assertTrue(BoardLinks.links(tile(context, source).getStack(0)).equals(List.of(a)), "the source untouched");

            // An empty tile: nothing is created
            use(player, empty);
            context.assertTrue(tile(context, empty).getStack(0).isEmpty(), "survival: no cartridge created");
            context.assertTrue(player.getInventory().count(ModItems.SHOP_CARTRIDGE) == 0, "nor given");

            // A shop already there: its settings, its own links kept
            use(player, sameKind);
            ItemStack pasted = tile(context, sameKind).getStack(0);
            context.assertTrue(pasted.isOf(ModItems.SHOP_CARTRIDGE) && pasted.get(ModComponents.SHOP_PURCHASES) == 3, "settings pasted");
            context.assertTrue(BoardLinks.links(pasted).equals(List.of(b)), "its own links kept: " + BoardLinks.links(pasted));

            // Another kind of cartridge: kept as it is, never removed
            use(player, otherKind);
            context.assertTrue(tile(context, otherKind).getStack(0).isOf(ModItems.STAR_CARTRIDGE), "the star cartridge stays");

            // Another holder of the family (a Checkpoint): its cartridge of the same kind, whatever its slot
            use(player, checkpoint);
            ItemStack inCheckpoint = ((CartridgeContainerBlockEntity) context.getWorld().getBlockEntity(checkpoint)).getStack(4);
            context.assertTrue(inCheckpoint.isOf(ModItems.SHOP_CARTRIDGE) && inCheckpoint.get(ModComponents.SHOP_PURCHASES) == 3,
                    "the Checkpoint's shop takes them");
            context.assertTrue(tile(context, source).getStack(0).getCount() == 1, "the source keeps one cartridge");
        });
    }

    // ---------------------------------------------------------------- cartridges, creative

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeFillsTheEmptySlotsWithCopies(TestContext context) {
        BlockPos source = place(context, ModBlocks.TILE, 1, 1), empty = place(context, ModBlocks.TILE, 3, 1),
                otherKind = place(context, ModBlocks.TILE, 5, 1);
        tile(context, source).setStack(0, shop(4, context.getAbsolutePos(new BlockPos(1, 1, 3))));
        tile(context, otherKind).setStack(0, new ItemStack(ModItems.STAR_CARTRIDGE));
        as(context, GameMode.CREATIVE, player -> {
            use(player, source);
            use(player, empty);
            ItemStack copy = tile(context, empty).getStack(0);
            context.assertTrue(copy.isOf(ModItems.SHOP_CARTRIDGE) && copy.get(ModComponents.SHOP_PURCHASES) == 4, "creative: a copy placed");
            context.assertTrue(BoardLinks.links(copy).isEmpty(), "without the source's links");
            use(player, otherKind);
            context.assertTrue(tile(context, otherKind).getStack(0).isOf(ModItems.STAR_CARTRIDGE), "its own cartridge is not replaced");
        });
    }

    // ---------------------------------------------------------------- blocks' own settings

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blockSettingsPasteOnTheSameBlock(TestContext context) {
        BlockPos switchA = place(context, ModBlocks.HOP_SWITCH, 1, 1), switchB = place(context, ModBlocks.HOP_SWITCH, 3, 1);
        BlockPos pigA = place(context, ModBlocks.PIGGY_BANK, 1, 3), pigB = place(context, ModBlocks.PIGGY_BANK, 3, 3);
        HopSwitchBlockEntity hopA = (HopSwitchBlockEntity) context.getWorld().getBlockEntity(switchA);
        HopSwitchBlockEntity hopB = (HopSwitchBlockEntity) context.getWorld().getBlockEntity(switchB);
        hopA.setMode(HopSwitchBlockEntity.Mode.FORCE_DISAPPEAR);
        hopA.setDurationTicks(140);
        PiggyBankBlockEntity bankA = (PiggyBankBlockEntity) context.getWorld().getBlockEntity(pigA);
        PiggyBankBlockEntity bankB = (PiggyBankBlockEntity) context.getWorld().getBlockEntity(pigB);
        PiggyBankBlockEntity.Target target = PiggyBankBlockEntity.Target.values()[PiggyBankBlockEntity.Target.values().length - 1];
        bankA.setTarget(target);
        as(context, GameMode.SURVIVAL, player -> {
            use(player, switchA);
            use(player, switchB);
            context.assertTrue(hopB.getMode() == HopSwitchBlockEntity.Mode.FORCE_DISAPPEAR && hopB.getDurationTicks() == 140, "mode and duration");
            use(player, pigB); // same family (cartridges), another block: its own target is not the switch's
            player.getMainHandStack().remove(ModComponents.WRENCH_COPY);
            use(player, pigA);
            use(player, pigB);
            context.assertTrue(bankB.getTarget() == target, "the target");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void podiumsBubblesMagnetosAntennasSigns(TestContext context) {
        BlockPos redstone = place(context, ModBlocks.PODIUM, 1, 1), redstone2 = place(context, ModBlocks.PODIUM, 2, 1),
                gold = place(context, ModBlocks.GOLD_PODIUM, 3, 1);
        BlockPos bubbleA = place(context, ModBlocks.SPAWN_MARKER, 1, 3), bubbleB = place(context, ModBlocks.SPAWN_MARKER, 3, 3);
        BlockPos magnetoA = place(context, ModBlocks.STEP_CONTROLLER, 1, 5), magnetoB = place(context, ModBlocks.STEP_CONTROLLER, 3, 5);
        BlockPos bellA = place(context, ModBlocks.PARTY_BELL, 1, 7), bellB = place(context, ModBlocks.PARTY_BELL, 3, 7);
        BlockPos signA = place(context, ModBlocks.PLASTIC_ROAD_SIGN, 6, 1), signB = place(context, ModBlocks.PLASTIC_ROAD_SIGN, 6, 3);
        ServerWorld world = context.getWorld();
        ((PodiumBlockEntity) world.getBlockEntity(redstone)).setSignal(PodiumSignal.CLEAR);
        SpawnMarkerBlockEntity markerA = (SpawnMarkerBlockEntity) world.getBlockEntity(bubbleA);
        markerA.setResident(true);
        markerA.setLiftSteps(5);
        world.setBlockState(bubbleA, world.getBlockState(bubbleA).with(SpawnMarkerBlock.ROTATION, 7));
        ((StepControllerBlockEntity) world.getBlockEntity(magnetoA)).selectMode(StepPulse.Mode.PREVIOUS, null);
        world.setBlockState(bellA, world.getBlockState(bellA).with(PartyBellBlock.MOMENT, PartyMoment.DICE_ROLLED));
        world.setBlockState(bellB, world.getBlockState(bellB).with(PartyBellBlock.MOMENT, PartyMoment.TURN_END).with(PartyBellBlock.WAITING, true));
        world.setBlockState(signA, world.getBlockState(signA).with(PlasticRoadSignBlock.PLATE, PlasticRoadSignBlock.Plate.STAR));
        as(context, GameMode.SURVIVAL, player -> {
            use(player, redstone);
            use(player, redstone2);
            context.assertTrue(((PodiumBlockEntity) world.getBlockEntity(redstone2)).getSignal() == PodiumSignal.CLEAR, "a podium's mode");
            use(player, gold);
            context.assertTrue(((PodiumBlockEntity) world.getBlockEntity(gold)).getSignal() == PodiumSignal.REGISTER,
                    "a pulse-only mode is refused on a classic podium");

            use(player, bubbleA); // another family: copied instead
            use(player, bubbleB);
            SpawnMarkerBlockEntity markerB = (SpawnMarkerBlockEntity) world.getBlockEntity(bubbleB);
            context.assertTrue(markerB.isResident() && markerB.getLiftSteps() == 5 && world.getBlockState(bubbleB).get(SpawnMarkerBlock.ROTATION) == 7,
                    "a bubble's showing, height and facing");

            use(player, magnetoA);
            use(player, magnetoB);
            context.assertTrue(((StepControllerBlockEntity) world.getBlockEntity(magnetoB)).getMode() == StepPulse.Mode.PREVIOUS, "a Magnéto's mode");

            use(player, bellA);
            use(player, bellB);
            context.assertTrue(world.getBlockState(bellB).get(PartyBellBlock.MOMENT) == PartyMoment.DICE_ROLLED
                    && !world.getBlockState(bellB).get(PartyBellBlock.WAITING), "the moment, never waiting");

            use(player, signA);
            use(player, signB);
            context.assertTrue(world.getBlockState(signB).get(PlasticRoadSignBlock.PLATE) == PlasticRoadSignBlock.Plate.STAR, "a sign's plate");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void goalPoleBasesAndPartyControllers(TestContext context) {
        BlockPos baseA = place(context, ModBlocks.GOAL_POLE_BASE, 1, 1), baseB = place(context, ModBlocks.GOAL_POLE_BASE, 3, 1);
        BlockPos controllerA = place(context, ModBlocks.PARTY_CONTROLLER, 1, 4), controllerB = place(context, ModBlocks.PARTY_CONTROLLER, 4, 4);
        ServerWorld world = context.getWorld();
        GoalPoleBaseBlockEntity a = (GoalPoleBaseBlockEntity) world.getBlockEntity(baseA);
        a.setPlayers(GoalPoleBaseBlockEntity.Players.RADIUS, 12);
        PartyControllerEntity partyA = (PartyControllerEntity) world.getBlockEntity(controllerA);
        PartyControllerEntity partyB = (PartyControllerEntity) world.getBlockEntity(controllerB);
        partyA.setFastParty(true);
        partyA.setMaxPowerUps(2);
        partyA.setRounds(7);
        ItemStack bankB = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        bankB.set(ModComponents.INVENTORY_CONTAINERS, List.of(net.minecraft.util.math.GlobalPos.create(world.getRegistryKey(), controllerB.up(3))));
        partyB.setBank(bankB);
        as(context, GameMode.SURVIVAL, player -> {
            use(player, baseA);
            use(player, baseB);
            GoalPoleBaseBlockEntity b = (GoalPoleBaseBlockEntity) world.getBlockEntity(baseB);
            context.assertTrue(b.getPlayers() == GoalPoleBaseBlockEntity.Players.RADIUS && b.getRadius() == 12, "whose points and radius");

            use(player, controllerA);
            use(player, controllerB);
            context.assertTrue(partyB.isFastParty() && partyB.getMaxPowerUps() == 2, "its rules");
            context.assertTrue(partyB.writeRules(world.getRegistryManager()).getInt("Rounds") == 7, "its rounds");
            context.assertTrue(partyB.getBank().getOrDefault(ModComponents.INVENTORY_CONTAINERS, List.of()).size() == 1, "its bank's chests kept");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void miniGameControllerPagesInCreativeOnly(TestContext context) {
        BlockPos a = place(context, ModBlocks.MINI_GAME_CONTROLLER, 1, 1), b = place(context, ModBlocks.MINI_GAME_CONTROLLER, 3, 1);
        MiniGameControllerBlockEntity controllerA = (MiniGameControllerBlockEntity) context.getWorld().getBlockEntity(a);
        MiniGameControllerBlockEntity controllerB = (MiniGameControllerBlockEntity) context.getWorld().getBlockEntity(b);
        controllerA.setPage(new ItemStack(ModItems.MINI_GAME_PAGE));
        ServerPlayerEntity survival = TestPlayers.mock(context, GameMode.SURVIVAL);
        survival.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
        use(survival, a);
        context.assertTrue(!survival.getMainHandStack().contains(ModComponents.WRENCH_COPY), "survival: a page is an item, nothing to copy");
        TestPlayers.remove(context, survival);
        as(context, GameMode.CREATIVE, player -> {
            use(player, a);
            use(player, b);
            context.assertTrue(!controllerB.getPage().isEmpty() && !controllerA.getPage().isEmpty(), "creative: a copy of the page");
        });
    }

    // ---------------------------------------------------------------- the gestures

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anotherFamilyIsCopiedAndTheAirClears(TestContext context) {
        BlockPos podium = place(context, ModBlocks.PODIUM, 1, 1), tilePos = place(context, ModBlocks.TILE, 3, 1);
        tile(context, tilePos).setStack(0, shop(2));
        as(context, GameMode.SURVIVAL, player -> {
            ItemStack wrench = player.getMainHandStack();
            sneakClick(player, tilePos);
            context.assertTrue("cartridges".equals(wrench.get(ModComponents.WRENCH_COPY).family()), "the tile copied");
            sneakClick(player, podium);
            context.assertTrue("podium".equals(wrench.get(ModComponents.WRENCH_COPY).family()), "a podium: copied in its place");
            player.setSneaking(true);
            wrench.getItem().use(context.getWorld(), player, Hand.MAIN_HAND);
            player.setSneaking(false);
            context.assertTrue(!wrench.contains(ModComponents.WRENCH_COPY), "sneak + right click in the air: cleared");
            context.assertTrue(UseBlockCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(tilePos), Direction.UP, tilePos, false)) == ActionResult.PASS, "a plain click is the block's");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pastingNeedsTheRightToEdit(TestContext context) {
        BlockPos a = place(context, ModBlocks.SPAWN_MARKER, 1, 1), b = place(context, ModBlocks.SPAWN_MARKER, 3, 1);
        ((SpawnMarkerBlockEntity) context.getWorld().getBlockEntity(a)).setLiftSteps(6);
        as(context, GameMode.ADVENTURE, player -> {
            use(player, a);
            use(player, b);
            context.assertTrue(((SpawnMarkerBlockEntity) context.getWorld().getBlockEntity(b)).getLiftSteps() != 6, "adventure: refused");
        });
    }
}
