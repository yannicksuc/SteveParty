package fr.lordfinn.steveparty.blocks;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;

import java.util.function.Function;

public class ModBlocks {
    // Create and register the TILE block
    public static final Block TILE = register(Tile::new, Block.Settings.create().strength(4.0f), "tile", true);

    private static Block register(Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings, String name,  boolean shouldRegisterItem) {
        Identifier identifier = Identifier.of(Steveparty.MOD_ID, name);
        RegistryKey<Block> registryKey = RegistryKey.of(RegistryKeys.BLOCK, identifier);

        Block block = Blocks.register(registryKey, factory, settings);
        Items.register(block);
        return block;
    }

    public static void registerBlocks() {
        // This ensures that the TILE block is registered when the mod is initialized.
        System.out.println("Registering blocks...");
    }
}
