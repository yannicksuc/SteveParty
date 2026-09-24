package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.switchable.Switchables;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

import java.util.concurrent.CompletableFuture;

import static fr.lordfinn.steveparty.blocks.ModBlocks.GOAL_POLE;

public class StevepartyReferenceBlockTagProvider extends FabricTagProvider<Block> {

    public StevepartyReferenceBlockTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, RegistryKeys.BLOCK, registriesFuture);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        // Plastic blocks and studs: the hop switch can switch them (datapacks and the server config can add more)
        for (Block[] plastic : new Block[][]{ModBlocks.PLASTIC_BLOCKS, ModBlocks.PLASTIC_STUDS})
            for (Block block : plastic) {
                getOrCreateTagBuilder(Switchables.PLASTIC).add(block);
                getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE).add(block);
            }
        getOrCreateTagBuilder(Switchables.SWITCHABLE).addTag(Switchables.PLASTIC);
        getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE)
                .add(
                        ModBlocks.GOAL_POLE_BASE,
                        ModBlocks.GOAL_POLE,
                        ModBlocks.TILE,
                        ModBlocks.CHECK_POINT,
                        ModBlocks.CASH_REGISTER,
                        ModBlocks.PARTY_CONTROLLER,
                        ModBlocks.STEP_CONTROLLER,
                        ModBlocks.BOARD_SPACE_REDSTONE_ROUTER,
                        ModBlocks.STENCIL_MAKER,
                        ModBlocks.HOP_SWITCH,
                        ModBlocks.LOOTING_BOX,
                        ModBlocks.GRAVITY_CORE,
                        // requiresTool() blocks that had no mineable tag (unharvestable)
                        ModBlocks.SIMPLE_TILE,
                        ModBlocks.DICE_FORGE,
                        ModBlocks.ROCK_SIGN,
                        ModBlocks.PLASTIC_ROAD_SIGN
                );
        for (Block b : ModBlocks.POLISHED_TERRACOTTA_SLABS)
            getOrCreateTagBuilder(BlockTags.SLABS).add(b);
        for (Block b : ModBlocks.POLISHED_TERRACOTTA_STAIRS)
            getOrCreateTagBuilder(BlockTags.STAIRS).add(b);
        for (Block b : ModBlocks.POLISHED_TERRACOTTA_WALLS)
            getOrCreateTagBuilder(BlockTags.WALLS).add(b);
        for (Block b : ModBlocks.POLISHED_TERRACOTTA_BRICKS_SLABS)
            getOrCreateTagBuilder(BlockTags.SLABS).add(b);
        for (Block b : ModBlocks.POLISHED_TERRACOTTA_BRICKS_STAIRS)
            getOrCreateTagBuilder(BlockTags.STAIRS).add(b);
        for (Block b : ModBlocks.POLISHED_TERRACOTTA_BRICKS_WALLS)
            getOrCreateTagBuilder(BlockTags.WALLS).add(b);
        for (Block b : ModBlocks.POLISHED_TERRACOTTA_BLOCKS)
            getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE).add(b);
        for (Block b : ModBlocks.POLISHED_TERRACOTTA_BRICKS_BLOCKS)
            getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE).add(b);
        // Stairs / slabs / walls copy the settings (requiresTool) of their base block: same tool
        for (Block[] variants : new Block[][]{
                ModBlocks.POLISHED_TERRACOTTA_STAIRS, ModBlocks.POLISHED_TERRACOTTA_SLABS, ModBlocks.POLISHED_TERRACOTTA_WALLS,
                ModBlocks.POLISHED_TERRACOTTA_BRICKS_STAIRS, ModBlocks.POLISHED_TERRACOTTA_BRICKS_SLABS, ModBlocks.POLISHED_TERRACOTTA_BRICKS_WALLS})
            for (Block b : variants)
                getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE).add(b);
        for (Block b : ModBlocks.POLISHED_CONCRETE_SLABS) getOrCreateTagBuilder(BlockTags.SLABS).add(b);
        for (Block b : ModBlocks.POLISHED_CONCRETE_BRICKS_SLABS) getOrCreateTagBuilder(BlockTags.SLABS).add(b);
        for (Block b : ModBlocks.POLISHED_CONCRETE_STAIRS) getOrCreateTagBuilder(BlockTags.STAIRS).add(b);
        for (Block b : ModBlocks.POLISHED_CONCRETE_BRICKS_STAIRS) getOrCreateTagBuilder(BlockTags.STAIRS).add(b);
        for (Block b : ModBlocks.POLISHED_CONCRETE_WALLS) getOrCreateTagBuilder(BlockTags.WALLS).add(b);
        for (Block b : ModBlocks.POLISHED_CONCRETE_BRICKS_WALLS) getOrCreateTagBuilder(BlockTags.WALLS).add(b);
        for (Block[] variants : new Block[][]{
                ModBlocks.POLISHED_CONCRETE_BLOCKS, ModBlocks.POLISHED_CONCRETE_BRICKS_BLOCKS,
                ModBlocks.POLISHED_CONCRETE_STAIRS, ModBlocks.POLISHED_CONCRETE_SLABS, ModBlocks.POLISHED_CONCRETE_WALLS,
                ModBlocks.POLISHED_CONCRETE_BRICKS_STAIRS, ModBlocks.POLISHED_CONCRETE_BRICKS_SLABS, ModBlocks.POLISHED_CONCRETE_BRICKS_WALLS})
            for (Block b : variants)
                getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE).add(b);
        getOrCreateTagBuilder(BlockTags.AXE_MINEABLE)
                .add(
                        ModBlocks.TELEPORTATION_PAD,
                        ModBlocks.SPRUCE_TRAFFIC_SIGN,
                        ModBlocks.OAK_TRAFFIC_SIGN,
                        ModBlocks.BIRCH_TRAFFIC_SIGN,
                        ModBlocks.JUNGLE_TRAFFIC_SIGN,
                        ModBlocks.ACACIA_TRAFFIC_SIGN,
                        ModBlocks.DARK_OAK_TRAFFIC_SIGN,
                        ModBlocks.MANGROVE_TRAFFIC_SIGN,
                        ModBlocks.CHERRY_TRAFFIC_SIGN,
                        ModBlocks.CRIMSON_TRAFFIC_SIGN,
                        ModBlocks.WARPED_TRAFFIC_SIGN,
                        ModBlocks.TRAFFIC_SIGN,
                        ModBlocks.WOODEN_PANEL,
                        ModBlocks.WOODEN_CUTOUT_PANEL,
                        ModBlocks.TRADING_STALL
                );
        getOrCreateTagBuilder(BlockTags.NEEDS_STONE_TOOL)
                .add(
                        ModBlocks.TILE
                );
        getOrCreateTagBuilder(BlockTags.NEEDS_IRON_TOOL)
                .add(
                        ModBlocks.GOAL_POLE,
                        ModBlocks.CASH_REGISTER,
                        ModBlocks.PARTY_CONTROLLER,
                        ModBlocks.STEP_CONTROLLER,
                        ModBlocks.BOARD_SPACE_REDSTONE_ROUTER,
                        ModBlocks.STENCIL_MAKER
                );
        getOrCreateTagBuilder(BlockTags.NEEDS_DIAMOND_TOOL)
                .add(ModBlocks.TELEPORTATION_PAD)
                .add(ModBlocks.GRAVITY_CORE);
        getOrCreateTagBuilder(BlockTags.CLIMBABLE)
                .add(GOAL_POLE);
    }
}