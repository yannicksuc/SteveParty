package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

import java.util.concurrent.CompletableFuture;

import static fr.lordfinn.steveparty.blocks.ModBlocks.GOAL_POLE;
import static fr.lordfinn.steveparty.blocks.ModBlocks.SWITCHER_BLOCKS;

public class StevepartyReferenceBlockTagProvider extends FabricTagProvider<Block> {
    public static final TagKey<Block> SWITCHABLE = TagKey.of(RegistryKeys.BLOCK, Steveparty.id("switchable"));

    public StevepartyReferenceBlockTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, RegistryKeys.BLOCK, registriesFuture);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        for (Block switcher : SWITCHER_BLOCKS)
            getOrCreateTagBuilder(SWITCHABLE).add(switcher);
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
                        ModBlocks.HOP_SWITCH
                );
        getOrCreateTagBuilder(BlockTags.AXE_MINEABLE)
                .add(
                        ModBlocks.GOAL_POLE_BASE,
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
                .add(ModBlocks.TELEPORTATION_PAD);
        getOrCreateTagBuilder(BlockTags.CLIMBABLE)
                .add(GOAL_POLE);
    }
}