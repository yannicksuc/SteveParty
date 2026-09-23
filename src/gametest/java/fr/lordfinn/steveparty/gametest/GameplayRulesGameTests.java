package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.effect.ModEffects.SQUISHED;

/** Gameplay rules: stacking, villager fall, Game Master wand, Mula taming, squish end scale. */
public class GameplayRulesGameTests implements FabricGameTest {

    // ---------------------------------------------------------------- stacking

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void valueComponentItemsStackOthersDoNot(TestContext context) {
        for (Item item : List.of(ModItems.STENCIL, ModItems.BOARD_SPACE_BEHAVIOR, ModItems.TILE_BEHAVIOR_START,
                ModItems.BOARD_SPACE_BEHAVIOR_STOP, ModItems.INVENTORY_CARTRIDGE, ModItems.MINI_GAME_PAGE)) {
            context.assertEquals(new ItemStack(item).getMaxCount(), 64, item + " max count");
        }
        for (Item item : List.of(ModItems.MINI_GAMES_CATALOGUE, ModItems.WRENCH, ModItems.TOKENIZER_WAND,
                ModItems.SHOPKEEPER_KEY, ModItems.HERE_WE_GO_BOOK, ModItems.HERE_WE_COME_BOOK)) {
            context.assertEquals(new ItemStack(item).getMaxCount(), 1, item + " max count");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void onlyIdenticalStencilsMerge(TestContext context) {
        byte[] shape = new byte[256];
        shape[17] = 1;
        ItemStack first = new ItemStack(ModItems.STENCIL);
        StencilItem.setShape(shape, first);
        ItemStack same = new ItemStack(ModItems.STENCIL);
        StencilItem.setShape(shape.clone(), same);
        ItemStack other = new ItemStack(ModItems.STENCIL);
        shape[18] = 1;
        StencilItem.setShape(shape, other);

        SimpleInventory inventory = new SimpleInventory(3);
        inventory.addStack(first);
        inventory.addStack(same);
        inventory.addStack(other);
        context.assertEquals(inventory.getStack(0).getCount(), 2, "identical stencils merged");
        context.assertEquals(inventory.getStack(1).getCount(), 1, "different stencil kept apart");
        context.assertTrue(StencilItem.getShape(inventory.getStack(1))[18] == 1, "different stencil keeps its shape");
        context.complete();
    }

    // ---------------------------------------------------------------- villager fall

    private static final BlockPos VILLAGER_POS = new BlockPos(1, 2, 1);

    /** Places a villager block over {@code below} and a falling player right above it. */
    private static ServerPlayerEntity fallOnVillager(TestContext context, Block below, GameMode gameMode, float fallDistance) {
        context.setBlockState(VILLAGER_POS.down(), below);
        context.setBlockState(VILLAGER_POS, ModBlocks.VILLAGER_BLOCK);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(gameMode);
        BlockPos abs = context.getAbsolutePos(VILLAGER_POS.up());
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY() + 0.1, abs.getZ() + 0.5, 0, 0);
        player.setOnGround(false);
        player.fallDistance = fallDistance;
        return player;
    }

    private static void disconnect(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getServer().getPlayerManager().remove(player);
    }

    private static void assertVillagerFall(TestContext context, Block below, GameMode gameMode, float fallDistance, boolean shouldBreak) {
        ServerPlayerEntity player = fallOnVillager(context, below, gameMode, fallDistance);
        context.waitAndRun(3, () -> {
            try {
                if (shouldBreak) {
                    context.expectBlock(Blocks.AIR, VILLAGER_POS);
                    context.expectBlock(Blocks.AIR, VILLAGER_POS.down());
                } else {
                    context.expectBlock(ModBlocks.VILLAGER_BLOCK, VILLAGER_POS);
                    context.expectBlock(below, VILLAGER_POS.down());
                }
            } finally {
                disconnect(context, player);
            }
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void villagerFallTooShortForStone(TestContext context) {
        // stone: hardness 1.5 -> 13 blocks required
        assertVillagerFall(context, Blocks.STONE, GameMode.SURVIVAL, 12.5F, false);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void villagerFallHighEnoughForStone(TestContext context) {
        assertVillagerFall(context, Blocks.STONE, GameMode.SURVIVAL, 13.5F, true);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void villagerFallTooShortForObsidian(TestContext context) {
        // obsidian: hardness 50 -> 110 blocks required
        assertVillagerFall(context, Blocks.OBSIDIAN, GameMode.SURVIVAL, 100F, false);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void villagerFallForbiddenInAdventure(TestContext context) {
        assertVillagerFall(context, Blocks.STONE, GameMode.ADVENTURE, 50F, false);
    }

    // ---------------------------------------------------------------- tokenizer wand / game master

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gameMasterWandControlsOtherPlayersTokens(TestContext context) {
        RegistryEntry<Enchantment> gameMaster = context.getWorld().getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(TokenizerWandItem.GAME_MASTER).orElse(null);
        context.assertTrue(gameMaster != null, "steveparty:game_master is registered");

        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(1, 1, 1));
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(UUID.randomUUID());

        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            ItemStack wand = new ItemStack(ModItems.TOKENIZER_WAND);
            context.assertTrue(!TokenizerWandItem.hasGameMaster(wand, context.getWorld()), "plain wand");
            if (!player.hasPermissionLevel(2)) {
                context.assertTrue(!TokenizerWandItem.canControlToken(player, wand, pig), "other player's token refused");
            }
            wand.addEnchantment(gameMaster, 1);
            context.assertTrue(TokenizerWandItem.hasGameMaster(wand, context.getWorld()), "enchanted wand");
            context.assertTrue(TokenizerWandItem.canControlToken(player, wand, pig), "game master wand allowed");

            token.steveparty$setTokenOwner(player);
            context.assertTrue(TokenizerWandItem.canControlToken(player, new ItemStack(ModItems.TOKENIZER_WAND), pig), "own token allowed");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    // ---------------------------------------------------------------- mula taming

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mulaIsTamedWithItsOwnFragment(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 2, 1));
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            player.changeGameMode(GameMode.SURVIVAL);
            Item fragment = mula.getVariant().getFragmentItem();
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(fragment, 64));

            // Not sneaking: regular feeding path, never tames
            player.setSneaking(false);
            mula.interactMob(player, Hand.MAIN_HAND);
            context.assertTrue(!mula.isTamed(), "not tamed without sneaking");

            player.setSneaking(true);
            int attempts = 0;
            while (!mula.isTamed() && attempts < 64) {
                mula.interactMob(player, Hand.MAIN_HAND);
                attempts++;
            }
            context.assertTrue(mula.isTamed(), "tamed after " + attempts + " fragments");
            context.assertTrue(mula.isOwner(player), "owner set");
            context.assertEquals(player.getMainHandStack().getCount(), 64 - attempts, "one fragment consumed per attempt");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    // ---------------------------------------------------------------- squish

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void squishedEntityGetsItsFinalScaleAtOnce(TestContext context) {
        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(1, 1, 1));
        pig.setAiDisabled(true);
        // amplifier 10: the entity ends 1 block high
        float initialHeight = pig.getHeight();
        pig.addStatusEffect(new StatusEffectInstance(SQUISHED, 20, 10));
        double scale = pig.getAttributeBaseValue(EntityAttributes.SCALE);
        context.assertTrue(Math.abs(scale - 1.0 / initialHeight) < 0.01, "final scale applied at once: " + scale);
        // The hitbox follows on the next entity tick (dirty attributes are processed in LivingEntity#tick)
        context.runAtTick(2, () -> context.assertTrue(Math.abs(pig.getHeight() - 1.0F) < 0.01F,
                "final height right away: " + pig.getHeight()));
        context.waitAndRun(25, () -> {
            context.assertTrue(!pig.hasStatusEffect(SQUISHED), "effect over");
            context.assertTrue(Math.abs(pig.getHeight() - 1.0F) < 0.01F, "final height kept: " + pig.getHeight());
            context.assertTrue(!pig.isGlowing(), "glowing removed");
            context.complete();
        });
    }
}
