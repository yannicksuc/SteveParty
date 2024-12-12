package fr.lordfinn.steveparty.blocks;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.tiles.Tile;
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
    public static final Block PARTY_CONTROLLER = register(PartyController::new, Block.Settings.create().strength(4.0f), "party_controller", true);
    public static final Block BIG_BOOK = register(BigBook::new, Block.Settings.create().strength(10.0f), "big_book", true);

    @SuppressWarnings({"unused", "SameParameterValue"})
    private static Block register(Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings, String name, boolean shouldRegisterItem) {
        Identifier identifier = Identifier.of(Steveparty.MOD_ID, name);
        RegistryKey<Block> registryKey = RegistryKey.of(RegistryKeys.BLOCK, identifier);

        Block block = Blocks.register(registryKey, factory, settings);
        Items.register(block);
        return block;
    }

    public static void registerBlocks() {
        System.out.println("Registering blocks...");
    }
}
