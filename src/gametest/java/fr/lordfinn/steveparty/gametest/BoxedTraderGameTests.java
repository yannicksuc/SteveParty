package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderBoxes;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.BandanaItem;
import fr.lordfinn.steveparty.items.custom.BoxCostumeItem;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
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

    private static void floor(TestContext context) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static BoxedTraderEntity trader(TestContext context) {
        return context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(3, 1, 3));
    }

    /** A survival player right next to the trader (so he comes out). */
    private static ServerPlayerEntity playerNear(TestContext context, BoxedTraderEntity trader) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        player.setPosition(trader.getPos().add(0, 0, 2));
        return player;
    }

    private static void disconnect(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getServer().getPlayerManager().remove(player);
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
            disconnect(context, player);
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
        floor(context);
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
                disconnect(context, player);
                throw e;
            }
            context.waitAndRun(BoxedTraderEntity.SHOCK_TICKS + 3, () -> checks(context, player, () -> {
                context.assertTrue(trader.isHidden(), "closed after the shock, although the player is still next to him");
                context.assertTrue(trader.isTheftHidden(), "theft cooldown running");
            }));
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shearsDoNothingOnAClosedOrGlitchedMerchant(TestContext context) {
        floor(context);
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
        floor(context);
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
                disconnect(context, player);
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
                    disconnect(context, player);
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
        floor(context);
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
        floor(context);
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
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
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
        floor(context);
        java.util.Set<net.minecraft.block.Block> seen = new java.util.HashSet<>();
        for (int i = 0; i < 12; i++) {
            BoxedTraderEntity trader = ModEntities.BOXED_TRADER_ENTITY.spawnFromItemStack(context.getWorld(), new ItemStack(ModItems.BOXED_TRADER_SPAWN_EGG),
                    null, context.getAbsolutePos(new BlockPos(3, 1, 3)), SpawnReason.SPAWN_ITEM_USE, false, false);
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
        BoxedTraderEntity loaded = ModEntities.BOXED_TRADER_ENTITY.create(context.getWorld(), SpawnReason.LOAD);
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
        net.minecraft.entity.Entity loaded = net.minecraft.entity.EntityType.loadEntityWithPassengers(saved, context.getWorld(), SpawnReason.LOAD, entity -> entity);
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
        BoxedTraderEntity loaded = ModEntities.BOXED_TRADER_ENTITY.create(context.getWorld(), SpawnReason.LOAD);
        loaded.readNbt(saved);
        context.assertTrue(loaded.isBoxGlitched(), "box still glitched");
        context.assertTrue(loaded.getBlockState().isOf(Blocks.BRICKS), "block kept");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBoxCostumeIsWornOnTheChest(TestContext context) {
        ItemStack costume = BoxCostumeItem.create(Blocks.OAK_PLANKS.getDefaultState());
        EquippableComponent equippable = costume.get(DataComponentTypes.EQUIPPABLE);
        context.assertTrue(equippable != null && equippable.slot() == EquipmentSlot.CHEST, "equippable in the chest slot");
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        checks(context, player, () -> {
            context.assertEquals(player.getPreferredEquipmentSlot(costume), EquipmentSlot.CHEST, "goes to the chest");
            player.setStackInHand(Hand.MAIN_HAND, costume.copy());
            player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
            ItemStack worn = BoxCostumeItem.getWorn(player);
            context.assertFalse(worn.isEmpty(), "right click puts it on");
            context.assertTrue(BoxCostumeItem.getBlock(worn).isOf(Blocks.OAK_PLANKS), "keeps its block");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sneakingHidesTheWearerInTheBox(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
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
        floor(context);
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
        BoxedTraderEntity loaded = ModEntities.BOXED_TRADER_ENTITY.create(context.getWorld(), SpawnReason.LOAD);
        loaded.readNbt(saved);
        context.assertFalse(loaded.hasBandana(), "still bald");
        context.assertEquals(loaded.getBandanaColor(), 2, "colour kept");
        context.assertTrue(loaded.isTheftHidden(), "theft cooldown kept");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heStaysHiddenFromASneakingPlayer(TestContext context) {
        // Checked on the rule itself: a neighbouring test's player within 15 blocks would open a real trader
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
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
        floor(context);
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
        EquippableComponent equippable = bandana.get(DataComponentTypes.EQUIPPABLE);
        context.assertTrue(equippable != null && equippable.slot() == EquipmentSlot.HEAD, "equippable in the head slot");
        context.assertTrue(equippable.model().isPresent() && equippable.model().get().equals(Steveparty.id("bandana_pink")), "pink equipment model");
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        checks(context, player, () -> {
            context.assertEquals(player.getPreferredEquipmentSlot(bandana), EquipmentSlot.HEAD, "goes to the head");
            context.assertTrue(player.canEquip(bandana, EquipmentSlot.HEAD), "the head slot accepts it");
            player.setStackInHand(Hand.MAIN_HAND, bandana.copy());
            player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
            context.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.BANDANA), "right click equips it");
            ArmorStandEntity stand = context.spawnEntity(net.minecraft.entity.EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
            context.assertTrue(stand.canEquip(bandana, EquipmentSlot.HEAD), "armour stands can wear it");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700)
    public void anUnassignedMerchantWandersAround(TestContext context) {
        floor(context);
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
        floor(context);
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
        floor(context);
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
}
