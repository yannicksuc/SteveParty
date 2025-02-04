package fr.lordfinn.steveparty.blocks;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.*;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CheckPointBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyController;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;

import java.util.function.Function;

public class ModBlocks {
    // Create and register the TILE block
    public static final Block TILE = register(TileBlock::new,
            Block.Settings.create()
                    .strength(2f, 3600000.0f)
                    .sounds(BlockSoundGroup.METAL)  // Stone-like sound
                    .requiresTool(),  // Requires a pickaxe to break
            "tile", true);

    public static final Block CHECK_POINT = register(CheckPointBlock::new,
            Block.Settings.create()
                    .strength(2f, 3600000.0f)
                    .sounds(BlockSoundGroup.AMETHYST_BLOCK)  // Metallic sound, assuming redstone-like behavior
                    .nonOpaque()  // Likely a visual indicator block
                    .luminance(state -> 5)  // Emits light to signify importance or activity
                    .ticksRandomly(),  // Useful for any passive behavior like redstone activation
            "check_point", true);

    public static final Block PARTY_CONTROLLER = register(PartyController::new,
            Block.Settings.create()
                    .strength(4.0f, 30.0f)  // More durable due to its central importance
                    .sounds(BlockSoundGroup.METAL)  // Controller blocks often have a tech/metallic feel
                    .requiresTool(),  // Prevent accidental breaking
            "party_controller", true);

    public static final Block TELEPORTATION_PAD = register(TeleportationPadBlock::new,
            Block.Settings.create()
                    .strength(4.0f, 30.0f)  // Very durable, like obsidian
                    .sounds(BlockSoundGroup.WOOD)  // Wood sound for a bookshelf-like aesthetic
                    .nonOpaque()  // For visual designs where the book might have transparency
                    .requiresTool()  // Needs a tool to break due to its high durability
                    .luminance(state -> 4),  // Slight glow to suggest magical properties
            "big_book", true);

    public static final Block VILLAGER_BLOCK = register(VillagerBlock::new,
            Block.Settings.create()
                    .strength(0.5f)  // Very fragile, can be broken by hand
                    .nonOpaque()  // Fully transparent/visible (for entity-like behavior)
                    .sounds(BlockSoundGroup.WOOL)  // Soft sound to match its potential villager theme
                    .breakInstantly(),  // Breaks instantly, emphasizing its decorative nature
            "villager_block", true);

    public static final Block CASH_REGISTER = register(CashRegisterBlock::new,
            Block.Settings.create()
                    .strength(2.0f, 6.0f)  // Reasonably durable
                    .sounds(BlockSoundGroup.METAL)  // Metallic sound for a register
                    .nonOpaque()  // Allows for visual transparency, if any
                    .luminance(state -> 3)  // Low glow to suggest activity or power
                    .requiresTool(),  // Needs a pickaxe or equivalent tool to break
            "cash_register", true);
    public static final Block STEP_CONTROLLER = register(StepControllerBlock::new,
            Block.Settings.create()
                    .strength(3.0f, 9.0f)  // Stronger than the cash register due to its mechanical components
                    .sounds(BlockSoundGroup.METAL)  // Stone-like sound for a mechanical device
                    .luminance(state -> 5)  // Slightly brighter to signify active operation
                    .nonOpaque()  // Non-opaque to allow transparency for hourglass visualization
                    .requiresTool(),  // Requires a pickaxe or equivalent tool to break
            "step_controller", true);
    public static final Block BOARD_SPACE_REDSTONE_ROUTER = register(BoardSpaceRedstoneRouterBlock::new,
            Block.Settings.create()
                    .strength(3.0f, 9.0f)  // Stronger than the cash register due to its mechanical components
                    .sounds(BlockSoundGroup.METAL)  // Stone-like sound for a mechanical device
                    .requiresTool(),  // Requires a pickaxe or equivalent tool to break
            "board_space_redstone_router", true);

    @SuppressWarnings({"unused", "SameParameterValue"})
    private static Block register(Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings, String name, boolean shouldRegisterItem) {
        Identifier identifier = Identifier.of(Steveparty.MOD_ID, name);
        RegistryKey<Block> registryKey = RegistryKey.of(RegistryKeys.BLOCK, identifier);

        Block block = Blocks.register(registryKey, factory, settings);
        Items.register(block);
        return block;
    }

    public static void registerBlocks() {
    }
}
