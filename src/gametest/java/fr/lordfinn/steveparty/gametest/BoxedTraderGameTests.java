package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderBoxes;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.BandanaItem;
import fr.lordfinn.steveparty.items.custom.BoxCostumeBlock;
import fr.lordfinn.steveparty.items.custom.BoxCostumeItem;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Equipment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;

/** Boxed Trader: bandana and box theft, Box Costume, wearable Bandana, who he pays attention to, wandering, grid alignment. */
public class BoxedTraderGameTests implements FabricGameTest {

    private static BoxedTraderEntity trader(TestContext context) {
        return context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(3, 1, 3));
    }

    /** A survival player right next to the trader (so he comes out). */
    private static ServerPlayerEntity playerNear(TestContext context, BoxedTraderEntity trader) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        player.setPosition(trader.getPos().add(0, 0, 2));
        return player;
    }

    /** Runs the checks, always disconnecting the mock player (a stray player would open other tests' traders). */
    private static void checks(TestContext context, ServerPlayerEntity player, Runnable checks) {
        checks(context, player, checks, true);
    }

    /** @param complete false when the checks go on (and complete the test) after the player left */
    private static void checks(TestContext context, ServerPlayerEntity player, Runnable checks, boolean complete) {
        try {
            checks.run();
        } finally {
            TestPlayers.remove(context, player);
        }
        if (complete) context.complete();
    }

    private static List<ItemEntity> droppedCostumes(TestContext context, BoxedTraderEntity trader) {
        return context.getWorld().getEntitiesByClass(ItemEntity.class, trader.getBoundingBox().expand(3),
                item -> item.getStack().isOf(ModItems.BOX_COSTUME));
    }

    private static List<ItemEntity> droppedBandanas(TestContext context, BoxedTraderEntity trader) {
        return context.getWorld().getEntitiesByClass(ItemEntity.class, trader.getBoundingBox().expand(3),
                item -> item.getStack().isOf(ModItems.BANDANA));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shearsStealTheBandanaOfAnOpenMerchant(TestContext context) {
        TestBoards.floor(context, 8);
        BoxedTraderEntity trader = trader(context);
        ServerPlayerEntity player = playerNear(context, trader);
        context.waitAndRun(3, () -> {
            try {
                context.assertFalse(trader.isHidden(), "open with a player next to him");
                int color = trader.getBandanaColor();
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
                trader.interact(player, Hand.MAIN_HAND);
                context.assertFalse(trader.hasBandana(), "bald after the theft");
                context.assertEquals(player.getMainHandStack().getDamage(), 1, "shears took 1 damage");
                List<ItemEntity> drops = droppedBandanas(context, trader);
                context.assertEquals(drops.size(), 1, "one bandana dropped");
                context.assertEquals(BandanaItem.getColor(drops.getFirst().getStack()), color, "in the merchant's colour");
            } catch (RuntimeException e) {
                TestPlayers.remove(context, player);
                throw e;
            }
            context.waitAndRun(BoxedTraderEntity.SHOCK_TICKS + 3, () -> checks(context, player, () -> {
                context.assertTrue(trader.isHidden(), "closed after the shock, although the player is still next to him");
                context.assertTrue(trader.isTheftHidden(), "theft cooldown running");
            }));
        });
    }

    /** Shorn of his bandana, then of his box's look: each time he hides for the theft, then comes out by himself. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 2 * (BoxedTraderEntity.SHOCK_TICKS + BoxedTraderEntity.THEFT_HIDE_TICKS) + 100)
    public void afterEachTheftHeComesOutByHimself(TestContext context) {
        TestBoards.floor(context, 8);
        BoxedTraderEntity trader = trader(context);
        ServerPlayerEntity player = playerNear(context, trader);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
        int window = BoxedTraderEntity.SHOCK_TICKS + BoxedTraderEntity.THEFT_HIDE_TICKS;
        context.waitAndRun(3, () -> {
            try {
                trader.interact(player, Hand.MAIN_HAND);
                context.assertFalse(trader.hasBandana(), "the bandana taken");
            } catch (RuntimeException e) {
                TestPlayers.remove(context, player);
                throw e;
            }
            context.waitAndRun(window + 5, () -> {
                try {
                    context.assertFalse(trader.isHidden(), "out again after the first theft");
                    trader.interact(player, Hand.MAIN_HAND);
                    context.assertTrue(trader.isBoxGlitched(), "the look of his box taken");
                } catch (RuntimeException e) {
                    TestPlayers.remove(context, player);
                    throw e;
                }
                context.waitAndRun(window + 5, () -> checks(context, player,
                        () -> context.assertFalse(trader.isHidden(), "out again after the second theft")));
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shearsDoNothingOnAClosedOrGlitchedMerchant(TestContext context) {
        TestBoards.floor(context, 8);
        BoxedTraderEntity closed = trader(context);
        BoxedTraderEntity glitched = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(5, 1, 3));
        ServerPlayerEntity player = playerNear(context, closed);
        closed.startTheftHiding(0);
        glitched.setHasBandana(false);
        glitched.setBoxGlitched(true);
        context.waitAndRun(3, () -> checks(context, player, () -> {
            context.assertTrue(closed.isHidden(), "closed");
            context.assertFalse(glitched.isHidden(), "the one with the glitched box is open");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
            closed.interact(player, Hand.MAIN_HAND);
            context.assertTrue(closed.hasBandana() && !closed.isBoxGlitched(), "closed: keeps his bandana and his box's look");
            glitched.interact(player, Hand.MAIN_HAND);
            context.assertTrue(!glitched.hasBandana() && glitched.isBoxGlitched(), "glitched: still bald, box still glitched");
            context.assertEquals(player.getMainHandStack().getDamage(), 0, "shears not used");
            context.assertTrue(droppedBandanas(context, closed).isEmpty() && droppedBandanas(context, glitched).isEmpty()
                    && droppedCostumes(context, closed).isEmpty() && droppedCostumes(context, glitched).isEmpty(), "nothing dropped");
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void shearingTwiceTakesTheBandanaThenTheLookOfTheBox(TestContext context) {
        TestBoards.floor(context, 8);
        BoxedTraderEntity trader = trader(context);
        trader.setBlockState(Blocks.OAK_PLANKS.getDefaultState());
        ServerPlayerEntity player = playerNear(context, trader);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
        context.waitAndRun(3, () -> {
            try {
                trader.interact(player, Hand.MAIN_HAND);
                context.assertFalse(trader.hasBandana(), "first shear: the bandana");
                context.assertFalse(trader.isBoxGlitched(), "his box keeps its look");
            } catch (RuntimeException e) {
                TestPlayers.remove(context, player);
                throw e;
            }
            context.waitAndRun(BoxedTraderEntity.SHOCK_TICKS + 3, () -> {
                try {
                    context.assertTrue(trader.isHidden(), "hidden after the theft");
                    // Shears on the closed box: nothing
                    trader.interact(player, Hand.MAIN_HAND);
                    context.assertFalse(trader.isBoxGlitched(), "closed: his box keeps its look");
                    // The theft cooldown is over: he comes out again
                    trader.startTheftHiding(-BoxedTraderEntity.THEFT_HIDE_TICKS - 1);
                } catch (RuntimeException e) {
                    TestPlayers.remove(context, player);
                    throw e;
                }
                context.waitAndRun(2, () -> checks(context, player, () -> {
                    context.assertFalse(trader.isHidden(), "out again");
                    trader.interact(player, Hand.MAIN_HAND);
                    context.assertTrue(trader.isBoxGlitched(), "second shear: the look of his box");
                    List<ItemEntity> costumes = droppedCostumes(context, trader);
                    context.assertEquals(costumes.size(), 1, "one costume dropped");
                    context.assertTrue(BoxCostumeItem.getBlock(costumes.getFirst().getStack()).isOf(Blocks.OAK_PLANKS), "of his block");
                    // Only the flag changed: his block (sounds, the costume given back) and bandana colour stay valid
                    context.assertTrue(BoxedTraderEntity.isValidBoxBlock(trader.getBlockState()) && trader.getBlockState().isOf(Blocks.OAK_PLANKS),
                            "block kept: " + trader.getBlockState());
                    context.assertTrue(trader.getBandanaColor() >= 0 && trader.getBandanaColor() < BoxedTraderEntity.BANDANA_COLORS, "bandana colour in range");
                    context.assertEquals(player.getMainHandStack().getDamage(), 2, "shears used twice");
                    trader.interact(player, Hand.MAIN_HAND);
                    context.assertEquals(droppedCostumes(context, trader).size(), 1, "a third shear takes nothing more");
                    context.assertEquals(player.getMainHandStack().getDamage(), 2, "nor uses the shears");
                    context.waitAndRun(BoxedTraderEntity.SHOCK_TICKS + 3, () -> {
                        context.assertTrue(trader.isHidden(), "he still hides in his glitched box");
                        context.complete();
                    });
                }, false));
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBoxCostumeGivesTheBoxItsLookBack(TestContext context) {
        TestBoards.floor(context, 8);
        BoxedTraderEntity trader = trader(context);
        trader.setHasBandana(false);
        trader.setBoxGlitched(true);
        ServerPlayerEntity player = playerNear(context, trader);
        context.waitAndRun(3, () -> checks(context, player, () -> {
            context.assertFalse(trader.isHidden(), "open, glitched box or not");
            player.setStackInHand(Hand.MAIN_HAND, BoxCostumeItem.create(Blocks.BRICKS.getDefaultState()));
            trader.interact(player, Hand.MAIN_HAND);
            context.assertFalse(trader.isBoxGlitched(), "his box has a look again");
            context.assertTrue(trader.getBlockState().isOf(Blocks.BRICKS), "the costume's block: " + trader.getBlockState());
            context.assertTrue(player.getMainHandStack().isEmpty(), "costume used up");
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBlockGivesAGlitchedBoxANewLookInsteadOfTrading(TestContext context) {
        TestBoards.floor(context, 8);
        BoxedTraderEntity trader = trader(context);
        trader.setHasBandana(false);
        trader.setBoxGlitched(true);
        ServerPlayerEntity player = playerNear(context, trader);
        context.waitAndRun(3, () -> checks(context, player, () -> {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.TORCH, 3));
            trader.interact(player, Hand.MAIN_HAND);
            context.assertTrue(trader.isBoxGlitched(), "a torch is no box");
            context.assertEquals(player.getMainHandStack().getCount(), 3, "torch kept");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.MELON, 3));
            trader.interact(player, Hand.MAIN_HAND);
            context.assertFalse(trader.isBoxGlitched(), "his box has a look again");
            context.assertTrue(trader.getBlockState().isOf(Blocks.MELON), "the block given: " + trader.getBlockState());
            context.assertEquals(player.getMainHandStack().getCount(), 2, "one melon used up");
            context.assertTrue(player.currentScreenHandler == player.playerScreenHandler, "no shop opened");
            // His box is fine again: a block in hand is just a block
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 3));
            trader.interact(player, Hand.MAIN_HAND);
            context.assertTrue(trader.getBlockState().isOf(Blocks.MELON), "box kept");
            context.assertEquals(player.getMainHandStack().getCount(), 3, "stone kept");
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heDoesNotNoticePlayersInABoxCostume(TestContext context) {
        // Checked on the rules themselves: a neighbouring test's player within 15 blocks would open a real trader
        ServerPlayerEntity player = TestPlayers.mock(context);
        checks(context, player, () -> {
            player.changeGameMode(GameMode.SURVIVAL);
            context.assertTrue(BoxedTraderEntity.drawsHimOut(player) && BoxedTraderEntity.isAttentionTarget(player), "noticed without it");
            player.equipStack(EquipmentSlot.CHEST, BoxCostumeItem.create(Blocks.GOLD_BLOCK.getDefaultState()));
            context.assertFalse(BoxedTraderEntity.drawsHimOut(player), "standing in a Box Costume: he doesn't come out");
            context.assertFalse(BoxedTraderEntity.isAttentionTarget(player), "nor pays attention");
            player.setSneaking(true);
            context.assertFalse(BoxedTraderEntity.drawsHimOut(player) || BoxedTraderEntity.isAttentionTarget(player), "nor sneaking in it");
            player.setSneaking(false);
            player.equipStack(EquipmentSlot.HEAD, BandanaItem.create(1));
            context.assertFalse(BoxedTraderEntity.drawsHimOut(player), "the costume wins over the bandana");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spawnEggBoxesAreDrawnPerLineThenPerMember(TestContext context) {
        List<List<BlockState>> lines = BoxedTraderBoxes.lines();
        context.assertEquals(lines.size(), 31, "lines of box_blocks.json");
        for (List<BlockState> line : lines) {
            for (BlockState state : line) context.assertTrue(BoxedTraderEntity.isValidBoxBlock(state), "valid box: " + state);
        }
        java.util.function.Function<net.minecraft.block.Block, List<BlockState>> lineOf = block -> lines.stream()
                .filter(line -> line.stream().anyMatch(state -> state.isOf(block))).findFirst().orElseThrow();
        context.assertEquals(lineOf.apply(Blocks.WHITE_WOOL).size(), 16, "wool colours");
        context.assertEquals(lineOf.apply(Blocks.RED_GLAZED_TERRACOTTA).size(), 16, "glazed terracotta colours");
        context.assertEquals(lineOf.apply(Blocks.FIRE_CORAL_BLOCK).size(), 5, "live coral blocks");
        context.assertEquals(lineOf.apply(Blocks.CHISELED_COPPER).size(), 4, "chiseled copper oxidation stages");
        context.assertEquals(lineOf.apply(Blocks.IRON_BLOCK).size(), 9, "storage blocks");
        context.assertTrue(lineOf.apply(Blocks.OAK_LOG).size() >= 10 && lineOf.apply(Blocks.OAK_LOG).stream().anyMatch(state -> state.isOf(Blocks.WARPED_STEM)), "logs and stems");
        context.assertTrue(lineOf.apply(Blocks.OAK_PLANKS).size() >= 11, "planks");
        context.assertEquals(lineOf.apply(Blocks.CHISELED_TUFF).size(), 1, "chiseled tuff alone");
        context.assertTrue(lineOf.apply(Blocks.BARREL).getFirst().get(net.minecraft.block.BarrelBlock.FACING) == net.minecraft.util.math.Direction.UP, "barrel lid up");
        // Each line has the same chance, whatever its size; inside a line, each member too
        net.minecraft.util.math.random.Random random = net.minecraft.util.math.random.Random.create(42);
        int draws = 31 * 2000;
        java.util.Map<List<BlockState>, Integer> perLine = new java.util.HashMap<>();
        java.util.Map<BlockState, Integer> perWool = new java.util.HashMap<>();
        for (int i = 0; i < draws; i++) {
            BlockState picked = BoxedTraderBoxes.pick(random);
            List<BlockState> line = lines.stream().filter(candidate -> candidate.contains(picked)).findFirst().orElseThrow();
            perLine.merge(line, 1, Integer::sum);
            if (line.contains(Blocks.WHITE_WOOL.getDefaultState())) perWool.merge(picked, 1, Integer::sum);
        }
        for (List<BlockState> line : lines) {
            int count = perLine.getOrDefault(line, 0);
            context.assertTrue(count > 1700 && count < 2300, "line " + line.getFirst() + " drawn " + count + " times out of " + draws);
        }
        context.assertEquals(perWool.size(), 16, "every wool colour drawn");
        for (int count : perWool.values()) context.assertTrue(count > 60 && count < 200, "a wool colour drawn " + count + " times");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aMerchantFromHisSpawnEggGetsARandomBox(TestContext context) {
        TestBoards.floor(context, 8);
        java.util.Set<net.minecraft.block.Block> seen = new java.util.HashSet<>();
        for (int i = 0; i < 12; i++) {
            BoxedTraderEntity trader = ModEntities.BOXED_TRADER_ENTITY.spawnFromItemStack(context.getWorld(), new ItemStack(ModItems.BOXED_TRADER_SPAWN_EGG),
                    null, context.getAbsolutePos(new BlockPos(3, 1, 3)), SpawnReason.SPAWN_EGG, false, false);
            context.assertTrue(trader != null, "spawned");
            BlockState box = trader.getBlockState();
            context.assertTrue(BoxedTraderBoxes.lines().stream().anyMatch(line -> line.contains(box)), "a box of the list: " + box);
            seen.add(box.getBlock());
            trader.discard();
        }
        context.assertTrue(seen.size() >= 4, "random boxes: " + seen);
        BoxedTraderEntity summoned = trader(context);
        context.assertTrue(summoned.getBlockState().isOf(Blocks.GOLD_BLOCK), "summoned otherwise: his gold box");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBoxCanNeverLookLikeAirOrAnInvisibleBlock(TestContext context) {
        BoxedTraderEntity trader = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(1, 1, 1));
        trader.setBlockState(Blocks.AIR.getDefaultState());
        trader.setBlockState(Blocks.BARRIER.getDefaultState());
        context.assertTrue(trader.getBlockState().isOf(Blocks.GOLD_BLOCK), "air and barrier refused: " + trader.getBlockState());
        NbtCompound saved = trader.writeNbt(new NbtCompound());
        saved.putString("blockState", "{\"Name\":\"minecraft:air\"}");
        BoxedTraderEntity loaded = ModEntities.BOXED_TRADER_ENTITY.create(context.getWorld());
        loaded.readNbt(saved);
        context.assertTrue(loaded.getBlockState().isOf(Blocks.GOLD_BLOCK), "air from NBT refused");
        context.assertTrue(BoxCostumeItem.getBlock(BoxCostumeItem.create(Blocks.AIR.getDefaultState())).isOf(Blocks.BEEHIVE), "costume of air: its default beehive");
        context.assertTrue(BoxCostumeItem.getBlock(new ItemStack(ModItems.BOX_COSTUME)).isOf(Blocks.BEEHIVE), "plain costume: a beehive");
        context.complete();
    }

    /** Worlds saved before the rename: the Hiding Trader's ids still load as the Boxed Trader's. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oldHidingTraderIdsStillLoad(TestContext context) {
        context.assertTrue(net.minecraft.registry.Registries.ENTITY_TYPE.get(Steveparty.id("hiding_trader")) == ModEntities.BOXED_TRADER_ENTITY,
                "old entity id");
        context.assertTrue(net.minecraft.registry.Registries.ITEM.get(Steveparty.id("hiding_trader_spawn_egg")) == ModItems.BOXED_TRADER_SPAWN_EGG,
                "old spawn egg id");
        NbtCompound saved = new NbtCompound();
        saved.putString("id", "steveparty:hiding_trader");
        saved.putBoolean(BoxedTraderEntity.BOX_GLITCHED_NBT, true);
        net.minecraft.entity.Entity loaded = net.minecraft.entity.EntityType.loadEntityWithPassengers(saved, context.getWorld(), entity -> entity);
        context.assertTrue(loaded instanceof BoxedTraderEntity trader && trader.isBoxGlitched(), "a saved Hiding Trader loads as a Boxed Trader: " + loaded);
        context.assertTrue(net.minecraft.registry.Registries.ENTITY_TYPE.getId(ModEntities.BOXED_TRADER_ENTITY).equals(Steveparty.id("boxed_trader")), "saved under the new id");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void boxStateIsSaved(TestContext context) {
        BoxedTraderEntity trader = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(1, 1, 1));
        trader.setBlockState(Blocks.BRICKS.getDefaultState());
        trader.setBoxGlitched(true);
        NbtCompound saved = trader.writeNbt(new NbtCompound());
        BoxedTraderEntity loaded = ModEntities.BOXED_TRADER_ENTITY.create(context.getWorld());
        loaded.readNbt(saved);
        context.assertTrue(loaded.isBoxGlitched(), "box still glitched");
        context.assertTrue(loaded.getBlockState().isOf(Blocks.BRICKS), "block kept");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBoxCostumeIsWornOnTheChest(TestContext context) {
        ItemStack costume = BoxCostumeItem.create(Blocks.OAK_PLANKS.getDefaultState());
        context.assertTrue(costume.getItem() instanceof Equipment equipment && equipment.getSlotType() == EquipmentSlot.CHEST,
                "equippable in the chest slot");
        ServerPlayerEntity player = TestPlayers.mock(context);
        checks(context, player, () -> {
            context.assertEquals(player.getPreferredEquipmentSlot(costume), EquipmentSlot.CHEST, "goes to the chest");
            player.setStackInHand(Hand.MAIN_HAND, costume.copy());
            player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
            ItemStack worn = BoxCostumeItem.getWorn(player);
            context.assertFalse(worn.isEmpty(), "right click puts it on");
            context.assertTrue(BoxCostumeItem.getBlock(worn).isOf(Blocks.OAK_PLANKS), "keeps its block");
        });
    }

    /** A survival player hidden in a Box Costume, on the ground at this place of the test structure. */
    private static ServerPlayerEntity hiddenPlayerAt(TestContext context, Vec3d relative) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        Vec3d pos = context.getAbsolute(relative);
        player.setPosition(pos);
        player.setOnGround(true);
        player.equipStack(EquipmentSlot.CHEST, BoxCostumeItem.create(Blocks.GOLD_BLOCK.getDefaultState()));
        player.setSneaking(true);
        return player;
    }

    private static void costumeTicks(ServerPlayerEntity player, int ticks) {
        for (int i = 0; i < ticks; i++) BoxCostumeBlock.tick(player, true);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void aHiddenPlayerWhoStopsBecomesABlockOfTheGrid(TestContext context) {
        TestBoards.floor(context, 8);
        ServerPlayerEntity player = hiddenPlayerAt(context, new Vec3d(3.3, 1, 2.8));
        Vec3d start = player.getPos();
        Vec3d centre = context.getAbsolute(new Vec3d(3.5, 1, 2.5));
        try {
            costumeTicks(player, BoxCostumeBlock.STILL_TICKS - 2);
            context.assertTrue(player.getPos().equals(start), "not pushed before 2 s");
            context.assertFalse(BoxCostumeBlock.isBlockAligned(player), "nor a block off the grid");
            // He walks a little: the wait starts again
            player.setPosition(start.add(0.05, 0, 0));
            costumeTicks(player, BoxCostumeBlock.STILL_TICKS - 2);
            context.assertTrue(player.getPos().equals(start.add(0.05, 0, 0)), "moving restarts the 2 s");
            costumeTicks(player, 40);
            context.assertTrue(player.getPos().squaredDistanceTo(centre) < 1e-12, "pushed to the centre of his cell: " + player.getPos() + " / " + centre);
            context.assertTrue(BoxCostumeBlock.isBlockAligned(player), "a block of the grid");
            net.minecraft.util.math.Box cube = new net.minecraft.util.math.Box(BlockPos.ofFloored(centre));
            context.assertTrue(player.getBoundingBox().equals(cube), "his box is the cell's cube: " + player.getBoundingBox());
            context.assertTrue(player.isCollidable() && !player.isPushable(), "a hard obstacle, not pushed");
        } catch (RuntimeException e) {
            TestPlayers.remove(context, player);
            throw e;
        }
        // Something dropped on him lands on the cube
        ArmorStandEntity stand = context.spawnEntity(net.minecraft.entity.EntityType.ARMOR_STAND, new Vec3d(3.5, 3, 2.5));
        context.waitAndRun(30, () -> {
            try {
                context.assertTrue(Math.abs(stand.getY() - (centre.y + 1)) < 0.01, "the armour stand stands on him: y " + stand.getY() + ", his feet " + centre.y);
                // He stands up: an ordinary player again
                player.setSneaking(false);
                costumeTicks(player, 1);
                context.assertFalse(BoxCostumeBlock.isBlockAligned(player), "standing: no longer a block");
                context.assertTrue(Math.abs(player.getBoundingBox().getLengthX() - 0.6) < 1e-4 && !player.isCollidable(), "his usual size, no obstacle");
            } catch (RuntimeException e) {
                TestPlayers.remove(context, player);
                throw e;
            }
            context.waitAndRun(30, () -> checks(context, player,
                    () -> context.assertTrue(Math.abs(stand.getY() - centre.y) < 0.01, "the armour stand fell to the ground: y " + stand.getY())));
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aHiddenPlayerOffTheGridOrOnABadCellIsNoBlock(TestContext context) {
        TestBoards.floor(context, 8);
        // In water
        context.setBlockState(new BlockPos(5, 1, 5), Blocks.WATER);
        ServerPlayerEntity wet = hiddenPlayerAt(context, new Vec3d(5.3, 1, 5.3));
        // On the upper step of stairs: its top is not a whole square
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE_STAIRS);
        ServerPlayerEntity onSlab = hiddenPlayerAt(context, new Vec3d(1.3, 2, 1.3));
        // A fence post in the cell
        context.setBlockState(new BlockPos(1, 1, 5), Blocks.OAK_FENCE);
        ServerPlayerEntity fenced = hiddenPlayerAt(context, new Vec3d(1.9, 1, 5.9));
        // In the air (jumping as a block)
        ServerPlayerEntity jumping = hiddenPlayerAt(context, new Vec3d(3.3, 1, 3.3));
        jumping.setOnGround(false);
        // On a good cell but not sneaking
        ServerPlayerEntity standing = hiddenPlayerAt(context, new Vec3d(5.3, 1, 1.3));
        standing.setSneaking(false);
        List<ServerPlayerEntity> players = List.of(wet, onSlab, fenced, jumping, standing);
        try {
            for (ServerPlayerEntity player : players) {
                Vec3d start = player.getPos();
                costumeTicks(player, BoxCostumeBlock.STILL_TICKS * 3);
                context.assertTrue(player.getPos().equals(start), "not pushed: " + start + " -> " + player.getPos());
                context.assertFalse(BoxCostumeBlock.isBlockAligned(player) || player.isCollidable(), "not a block at " + start);
            }
        } finally {
            players.forEach(player -> TestPlayers.remove(context, player));
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sneakingHidesTheWearerInTheBox(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        checks(context, player, () -> {
            player.changeGameMode(GameMode.SURVIVAL);
            player.setSneaking(true);
            context.assertFalse(BoxCostumeItem.isHiddenInBox(player), "sneaking without the costume");
            player.equipStack(EquipmentSlot.CHEST, BoxCostumeItem.create(Blocks.GOLD_BLOCK.getDefaultState()));
            context.assertTrue(BoxCostumeItem.isHiddenInBox(player), "sneaking with it: hidden");
            ZombieEntity zombie = context.spawnEntity(net.minecraft.entity.EntityType.ZOMBIE, new BlockPos(1, 1, 1));
            context.assertTrue(player.getAttackDistanceScalingFactor(zombie) <= BoxCostumeItem.HIDDEN_DETECTION_FACTOR + 1e-6, "monsters hardly notice him");
            player.setSneaking(false);
            context.assertFalse(BoxCostumeItem.isHiddenInBox(player), "standing: out of the box");
            context.assertTrue(player.getAttackDistanceScalingFactor(zombie) > 0.5, "noticed again");
            player.setSneaking(true);
            player.getAbilities().flying = true;
            context.assertFalse(BoxCostumeItem.isHiddenInBox(player), "flying down: not hidden");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBandanaCanBeGivenBackToABaldMerchant(TestContext context) {
        TestBoards.floor(context, 8);
        BoxedTraderEntity trader = trader(context);
        trader.setHasBandana(false);
        trader.setBandanaColor(0);
        ServerPlayerEntity player = playerNear(context, trader);
        context.waitAndRun(3, () -> checks(context, player, () -> {
            player.setStackInHand(Hand.MAIN_HAND, BandanaItem.create(3));
            trader.interact(player, Hand.MAIN_HAND);
            context.assertTrue(trader.hasBandana(), "wears a bandana again");
            context.assertEquals(trader.getBandanaColor(), 3, "the given bandana's colour");
            context.assertTrue(player.getMainHandStack().isEmpty(), "bandana used up");
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bandanaStateIsSaved(TestContext context) {
        BoxedTraderEntity trader = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(1, 1, 1));
        trader.setBandanaColor(2);
        trader.setHasBandana(false);
        trader.startTheftHiding(0);
        NbtCompound saved = trader.writeNbt(new NbtCompound());
        BoxedTraderEntity loaded = ModEntities.BOXED_TRADER_ENTITY.create(context.getWorld());
        loaded.readNbt(saved);
        context.assertFalse(loaded.hasBandana(), "still bald");
        context.assertEquals(loaded.getBandanaColor(), 2, "colour kept");
        context.assertTrue(loaded.isTheftHidden(), "theft cooldown kept");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heStaysHiddenFromASneakingPlayer(TestContext context) {
        // Checked on the rule itself: a neighbouring test's player within 15 blocks would open a real trader
        ServerPlayerEntity player = TestPlayers.mock(context);
        checks(context, player, () -> {
            player.changeGameMode(GameMode.SURVIVAL);
            context.assertTrue(BoxedTraderEntity.drawsHimOut(player), "a player standing makes him come out");
            player.setSneaking(true);
            context.assertFalse(BoxedTraderEntity.drawsHimOut(player), "a sneaking player doesn't");
            player.equipStack(EquipmentSlot.HEAD, BandanaItem.create(1));
            context.assertTrue(BoxedTraderEntity.drawsHimOut(player), "a sneaking Bandana wearer does");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heIgnoresPlayersWearingABandana(TestContext context) {
        TestBoards.floor(context, 8);
        BoxedTraderEntity trader = trader(context);
        ServerPlayerEntity wearer = playerNear(context, trader);
        wearer.equipStack(EquipmentSlot.HEAD, BandanaItem.create(1));
        context.waitAndRun(3, () -> checks(context, wearer, () -> {
            context.assertFalse(trader.isHidden(), "a Bandana wearer still makes him come out");
            context.assertFalse(BoxedTraderEntity.isAttentionTarget(wearer), "but he doesn't pay attention to him");
            context.assertTrue(trader.findAttentionTarget(BoxedTraderEntity.OPEN_RANGE) != wearer, "never his look target");
            wearer.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
            context.assertTrue(BoxedTraderEntity.isAttentionTarget(wearer), "without it, he does");
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBandanaIsWornOnTheHead(TestContext context) {
        ItemStack bandana = BandanaItem.create(2);
        context.assertTrue(bandana.getItem() instanceof Equipment equipment && equipment.getSlotType() == EquipmentSlot.HEAD,
                "equippable in the head slot");
        context.assertTrue(BandanaItem.textureLayer(BandanaItem.getColor(bandana)).equals(Steveparty.id("bandana_pink")), "pink armour layer");
        ServerPlayerEntity player = TestPlayers.mock(context);
        checks(context, player, () -> {
            context.assertEquals(player.getPreferredEquipmentSlot(bandana), EquipmentSlot.HEAD, "goes to the head");
            context.assertTrue(player.canEquip(bandana), "the head slot accepts it");
            player.setStackInHand(Hand.MAIN_HAND, bandana.copy());
            player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
            context.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.BANDANA), "right click equips it");
            ArmorStandEntity stand = context.spawnEntity(net.minecraft.entity.EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
            context.assertTrue(stand.canEquip(bandana), "armour stands can wear it");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700)
    public void anUnassignedMerchantWandersAround(TestContext context) {
        TestBoards.floor(context, 8);
        BoxedTraderEntity trader = trader(context);
        ServerPlayerEntity player = playerNear(context, trader);
        Vec3d start = trader.getPos();
        context.runAtEveryTick(() -> {
            if (trader.getPos().subtract(start).horizontalLength() > 0.75) {
                checks(context, player, () -> context.assertFalse(trader.isAssigned(), "unassigned"));
            }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void anAssignedMerchantStaysPut(TestContext context) {
        TestBoards.floor(context, 8);
        BoxedTraderEntity trader = trader(context);
        VendorLinkPersistentState.get(context.getWorld().getServer()).linkBlock(trader.getUuid(),
                GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(new BlockPos(0, 1, 0))));
        ServerPlayerEntity player = playerNear(context, trader);
        Vec3d start = trader.getPos();
        context.waitAndRun(320, () -> checks(context, player, () -> {
            context.assertTrue(trader.isAssigned(), "assigned");
            context.assertTrue(trader.getPos().subtract(start).horizontalLength() < 0.3, "did not wander");
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aClosedMerchantIsAlignedWithTheGrid(TestContext context) {
        TestBoards.floor(context, 8);
        BoxedTraderEntity trader = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new Vec3d(3.3, 1, 2.8));
        trader.setYaw(37);
        trader.setBodyYaw(37);
        trader.setHeadYaw(37);
        trader.startTheftHiding(0);
        context.waitAndRun(3, () -> {
            context.assertTrue(trader.isHidden(), "closed");
            context.assertTrue(Math.abs(MathHelper.fractionalPart(trader.getX()) - 0.5) < 1e-6
                    && Math.abs(MathHelper.fractionalPart(trader.getZ()) - 0.5) < 1e-6, "on a block centre: " + trader.getPos());
            context.assertTrue(Math.abs(MathHelper.wrapDegrees(trader.getYaw()) % 90) < 1e-3, "yaw on a quarter turn: " + trader.getYaw());
            context.assertTrue(Math.abs(MathHelper.wrapDegrees(trader.getBodyYaw()) % 90) < 1e-3, "body yaw on a quarter turn: " + trader.getBodyYaw());
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void aHiddenPlayerOnANonFullFloorBecomesABlockToo(TestContext context) {
        TestBoards.floor(context, 8);
        context.setBlockState(new BlockPos(1, 0, 1), Blocks.DIRT_PATH);
        context.setBlockState(new BlockPos(5, 1, 1), Blocks.STONE_SLAB);
        context.setBlockState(new BlockPos(1, 1, 5), Blocks.WHITE_CARPET);
        context.setBlockState(new BlockPos(5, 0, 5), Blocks.FARMLAND);
        List<Vec3d> starts = List.of(new Vec3d(1.3, 15 / 16.0, 1.2), new Vec3d(5.3, 1.5, 1.2), new Vec3d(1.3, 1 + 1 / 16.0, 5.2),
                new Vec3d(5.3, 15 / 16.0, 5.2));
        List<ServerPlayerEntity> players = starts.stream().map(start -> hiddenPlayerAt(context, start)).toList();
        try {
            for (int i = 0; i < players.size(); i++) {
                ServerPlayerEntity player = players.get(i);
                Vec3d start = context.getAbsolute(starts.get(i));
                costumeTicks(player, BoxCostumeBlock.STILL_TICKS + 40);
                Vec3d centre = new Vec3d(Math.floor(start.x) + 0.5, start.y, Math.floor(start.z) + 0.5);
                context.assertTrue(player.getPos().squaredDistanceTo(centre) < 1e-12, "pushed to the centre, on his floor: " + player.getPos() + " / " + centre);
                context.assertTrue(BoxCostumeBlock.isBlockAligned(player), "a block on " + context.getWorld().getBlockState(BlockPos.ofFloored(start.subtract(0, 0.01, 0))));
                context.assertTrue(Math.abs(player.getBoundingBox().minY - start.y) < 1e-9, "the cube stands on that floor: " + player.getBoundingBox());
            }
        } finally {
            players.forEach(player -> TestPlayers.remove(context, player));
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void aHiddenBlockStopsOthersAndStaysOneUnderThem(TestContext context) {
        TestBoards.floor(context, 8);
        ServerPlayerEntity hider = hiddenPlayerAt(context, new Vec3d(3.5, 1, 3.5));
        costumeTicks(hider, 2);
        ServerPlayerEntity walker = TestPlayers.mock(context, GameMode.SURVIVAL);
        try {
            context.assertTrue(BoxCostumeBlock.isBlockAligned(hider), "still on the centre: a block");
            // Walking into him: stopped against the cube
            walker.setPosition(context.getAbsolute(new Vec3d(2.0, 1, 3.5)));
            walker.move(net.minecraft.entity.MovementType.SELF, new Vec3d(1.0, 0, 0));
            double face = context.getAbsolute(new Vec3d(3.0, 1, 3.5)).x;
            context.assertTrue(Math.abs(walker.getBoundingBox().maxX - face) < 1e-6, "stopped by the cube: " + walker.getBoundingBox().maxX + " / " + face);
            // Standing on him, then something lands in his cell: he stays a block, on every side
            walker.setPosition(context.getAbsolute(new Vec3d(3.5, 2.2, 3.5)));
            walker.move(net.minecraft.entity.MovementType.SELF, new Vec3d(0, -1.0, 0));
            context.assertTrue(Math.abs(walker.getY() - context.getAbsolute(new Vec3d(3.5, 2, 3.5)).y) < 1e-6, "standing on him: y " + walker.getY());
            ArmorStandEntity stand = context.spawnEntity(net.minecraft.entity.EntityType.ARMOR_STAND, new Vec3d(3.9, 1, 3.9));
            hider.setOnGround(false);
            for (int i = 0; i < 20; i++) BoxCostumeBlock.tick(hider, i % 2 == 0);
            context.assertTrue(BoxCostumeBlock.isBlockAligned(hider) && hider.isCollidable(), "still a block under him and with someone in his cell");
            walker.move(net.minecraft.entity.MovementType.SELF, new Vec3d(0, -0.5, 0));
            context.assertTrue(Math.abs(walker.getY() - context.getAbsolute(new Vec3d(3.5, 2, 3.5)).y) < 1e-6, "does not fall through: y " + walker.getY());
            stand.discard();
        } finally {
            TestPlayers.remove(context, walker);
            TestPlayers.remove(context, hider);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anObserverSeesTheHiddenBlockWithoutHisGroundFlag(TestContext context) {
        TestBoards.floor(context, 8);
        // As another client sees him: right on the centre, the ground flag not (yet) synced, never moved by this side
        ServerPlayerEntity hider = hiddenPlayerAt(context, new Vec3d(3.5, 1, 3.5));
        hider.setOnGround(false);
        try {
            for (int i = 0; i < 3; i++) BoxCostumeBlock.tick(hider, false);
            context.assertTrue(BoxCostumeBlock.isBlockAligned(hider), "a block for those who watch him");
        } finally {
            TestPlayers.remove(context, hider);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBoxKeepsTheStateOfTheBlockItemAndRefusesTinyBlocks(TestContext context) {
        var tiles = (fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesBlock) fr.lordfinn.steveparty.blocks.ModBlocks.POLISHED_CONCRETE_TILES;
        var red = fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesColor.RED;
        var blue = fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesColor.BLUE;
        BlockState box = BoxedTraderEntity.boxBlockOf(tiles.stack(red, blue, 1));
        context.assertTrue(box != null && box.equals(tiles.with(red, blue)), "the tiles' colours are kept: " + box);
        context.assertTrue(BoxedTraderEntity.boxBlockOf(new ItemStack(Items.DRAGON_EGG)) != null, "a dragon egg can be a box");
        context.assertTrue(BoxedTraderEntity.boxBlockOf(new ItemStack(Items.PISTON)) != null, "a piston can be a box");
        context.assertTrue(BoxedTraderEntity.boxBlockOf(new ItemStack(Items.TORCH)) == null, "a torch can't");
        context.assertTrue(BoxedTraderEntity.boxBlockOf(new ItemStack(Items.OAK_FENCE)) == null, "a fence can't");
        context.assertTrue(BoxedTraderEntity.boxBlockOf(new ItemStack(Items.CHEST)) == null, "a chest (not drawn as a model) can't");
        context.complete();
    }
}
