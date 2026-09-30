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
        // Everything made of plastic (Switchables.PLASTIC): the wrench and shears take it apart quickly, the hop switch
        // can switch it (datapacks and the server config can add more)
        for (Block[] plastic : new Block[][]{ModBlocks.PLASTIC_BLOCKS, ModBlocks.PLASTIC_STUDS})
            for (Block block : plastic) {
                getOrCreateTagBuilder(Switchables.PLASTIC).add(block);
                getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE).add(block);
            }
        getOrCreateTagBuilder(Switchables.SWITCHABLE).addTag(Switchables.PLASTIC);
        // Plastic fences and the plastic road sign: plastic too (the road sign has a block entity: never switchable)
        for (Block fence : ModBlocks.PLASTIC_FENCES) {
            getOrCreateTagBuilder(Switchables.PLASTIC).add(fence);
            getOrCreateTagBuilder(BlockTags.FENCES).add(fence);
            getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE).add(fence);
        }
        getOrCreateTagBuilder(Switchables.PLASTIC).add(ModBlocks.PLASTIC_ROAD_SIGN);
        // Plastic slabs, stairs and walls: plastic too (taken apart in one hit with the wrench, switchable)
        for (int i = 0; i < ModBlocks.COLORS.length; i++) {
            getOrCreateTagBuilder(BlockTags.SLABS).add(ModBlocks.PLASTIC_SLABS[i]);
            getOrCreateTagBuilder(BlockTags.STAIRS).add(ModBlocks.PLASTIC_STAIRS[i]);
            getOrCreateTagBuilder(BlockTags.WALLS).add(ModBlocks.PLASTIC_WALLS[i]);
            for (Block shape : new Block[]{ModBlocks.PLASTIC_SLABS[i], ModBlocks.PLASTIC_STAIRS[i], ModBlocks.PLASTIC_WALLS[i]}) {
                getOrCreateTagBuilder(Switchables.PLASTIC).add(shape);
                getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE).add(shape);
            }
        }
        getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE)
                .add(
                        ModBlocks.GOAL_POLE_BASE,
                        ModBlocks.GOAL_POLE,
                        ModBlocks.ADVANCED_TILE,
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
                        ModBlocks.TILE,
                        ModBlocks.DICE_FORGE,
                        ModBlocks.ROCK_SIGN,
                        ModBlocks.PLASTIC_ROAD_SIGN,
                        ModBlocks.PARTY_BELL,
                        ModBlocks.PODIUM,
                        ModBlocks.PIGGY_BANK
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
                        ModBlocks.SPRUCE_EASEL_SIGN,
                        ModBlocks.OAK_EASEL_SIGN,
                        ModBlocks.BIRCH_EASEL_SIGN,
                        ModBlocks.JUNGLE_EASEL_SIGN,
                        ModBlocks.ACACIA_EASEL_SIGN,
                        ModBlocks.DARK_OAK_EASEL_SIGN,
                        ModBlocks.MANGROVE_EASEL_SIGN,
                        ModBlocks.CHERRY_EASEL_SIGN,
                        ModBlocks.CRIMSON_EASEL_SIGN,
                        ModBlocks.WARPED_EASEL_SIGN,
                        ModBlocks.EASEL_SIGN,
                        ModBlocks.WOODEN_PANEL,
                        ModBlocks.WOODEN_CUTOUT_PANEL,
                        ModBlocks.TRADING_STALL
                );
        getOrCreateTagBuilder(BlockTags.NEEDS_STONE_TOOL)
                .add(
                        ModBlocks.ADVANCED_TILE
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