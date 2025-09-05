package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;

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
    }
}