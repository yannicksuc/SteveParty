package fr.lordfinn.steveparty.blocks;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.*;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CheckPointBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyController;
import fr.lordfinn.steveparty.items.custom.EpicWithGlintBlockItem;
import net.minecraft.block.*;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;

import java.util.function.BiFunction;
import java.util.function.Function;

public class ModBlocks {
    public static final String[] COLORS = {"white", "orange", "magenta", "light_blue",
            "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black"
    };

    public static final String[] COLORS_WITH_DEFAULT = {"default",
            "white", "orange", "magenta", "light_blue",
            "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black"
    };

    public static final Block[] SWITCHER_BLOCKS = new Block[16];

    static {
        for (int i = 0; i < COLORS.length; i++) {
            String color = COLORS[i];
            String name = color + "_switcher_block";

            SWITCHER_BLOCKS[i] = register(
                    SwitchyBlock::new,
                    Block.Settings.create()
                            .strength(0.4f, 0.4f)
                            .burnable()
                            .sounds(BlockSoundGroup.WOOL)
                            .nonOpaque(),
                    name,
                    true
            );
        }
    }

    public static final Block[] POLISHED_TERRACOTTA_BLOCKS = new Block[COLORS_WITH_DEFAULT.length];

    static {
        for (int i = 0; i < COLORS_WITH_DEFAULT.length; i++) {
            String color = COLORS_WITH_DEFAULT[i];
            String name = "polished_" + color + "_terracotta";

            POLISHED_TERRACOTTA_BLOCKS[i] = register(Block::new,
                    Block.Settings.create()
                            .strength(1.25f, 4.2f)
                            .sounds(BlockSoundGroup.STONE)
                            .solid()
                            .requiresTool(),
                    name, true);
        }
    }

    // Generate all polished terracotta brick variants automatically
    public static final Block[] POLISHED_TERRACOTTA_BRICKS_BLOCKS = new Block[COLORS_WITH_DEFAULT.length];

    static {
        for (int i = 0; i < COLORS_WITH_DEFAULT.length; i++) {
            String color = COLORS_WITH_DEFAULT[i];
            String name = "polished_" + color + "_terracotta_bricks";

            POLISHED_TERRACOTTA_BRICKS_BLOCKS[i] = register(Block::new,
                    Block.Settings.create()
                            .strength(1.5f, 6.0f)
                            .sounds(BlockSoundGroup.STONE)
                            .solid()
                            .requiresTool(),
                    name, true);
        }
    }

    public static final Block[] POLISHED_TERRACOTTA_STAIRS = new StairsBlock[COLORS_WITH_DEFAULT.length];
    public static final Block[] POLISHED_TERRACOTTA_SLABS = new SlabBlock[COLORS_WITH_DEFAULT.length];
    public static final Block[] POLISHED_TERRACOTTA_WALLS = new WallBlock[COLORS_WITH_DEFAULT.length];

    public static final Block[] POLISHED_TERRACOTTA_BRICKS_STAIRS = new StairsBlock[COLORS_WITH_DEFAULT.length];
    public static final Block[] POLISHED_TERRACOTTA_BRICKS_SLABS = new SlabBlock[COLORS_WITH_DEFAULT.length];
    public static final Block[] POLISHED_TERRACOTTA_BRICKS_WALLS = new WallBlock[COLORS_WITH_DEFAULT.length];

    static {
        for (int i = 0; i < COLORS_WITH_DEFAULT.length; i++) {
            final int index = i; // <-- rend la variable finale
            String color = COLORS_WITH_DEFAULT[i];

            // Terracotta
            POLISHED_TERRACOTTA_STAIRS[i] = register(
                    (s) -> new StairsBlock(POLISHED_TERRACOTTA_BLOCKS[index].getDefaultState(), s),
                    Block.Settings.copy(POLISHED_TERRACOTTA_BLOCKS[index]),
                    color + "_polished_terracotta_stairs",
                    true
            );

            POLISHED_TERRACOTTA_SLABS[i] = register(
                    SlabBlock::new,
                    Block.Settings.copy(POLISHED_TERRACOTTA_BLOCKS[i]),
                    color + "_polished_terracotta_slab",
                    true
            );

            POLISHED_TERRACOTTA_WALLS[i] = register(
                    WallBlock::new,
                    Block.Settings.copy(POLISHED_TERRACOTTA_BLOCKS[i]),
                    color + "_polished_terracotta_wall",
                    true
            );


            // Terracotta bricks
            POLISHED_TERRACOTTA_BRICKS_STAIRS[i] = register(
                    (s) -> new StairsBlock(POLISHED_TERRACOTTA_BRICKS_BLOCKS[index].getDefaultState(), s),
                    Block.Settings.copy(POLISHED_TERRACOTTA_BRICKS_BLOCKS[index]),
                    color + "_polished_terracotta_bricks_stairs",
                    true
            );

            POLISHED_TERRACOTTA_BRICKS_SLABS[i] = register(
                    SlabBlock::new,
                    Block.Settings.copy(POLISHED_TERRACOTTA_BRICKS_BLOCKS[i]),
                    color + "_polished_terracotta_bricks_slab",
                    true
            );

            POLISHED_TERRACOTTA_BRICKS_WALLS[i] = register(
                    WallBlock::new,
                    Block.Settings.copy(POLISHED_TERRACOTTA_BRICKS_BLOCKS[i]),
                    color + "_polished_terracotta_bricks_wall",
                    true
            );

        }
    }


    public static final Block TRADING_STALL = register(TradingStallBlock::new,
            Block.Settings.create()
                    .strength(2.5f, 2.5f)
                    .sounds(BlockSoundGroup.WOOD)
                    .nonOpaque(),
            "trading_stall", true);

    public static final Block STENCIL_MAKER = register(StencilMakerBlock::new,
            Block.Settings.create()
                    .strength(3.0f, 9.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool(),
            "stencil_maker", true);

    // Create and register the TILE block
    public static final Block TILE = register(TileBlock::new,
            Block.Settings.create()
                    .strength(2f, 3600000.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool(),
            "tile", true);

    public static final Block CHECK_POINT = register(CheckPointBlock::new,
            Block.Settings.create()
                    .strength(2f, 3600000.0f)
                    .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                    .nonOpaque()
                    .luminance(state -> 5)
                    .ticksRandomly(),
            "check_point", true);

    public static final Block PARTY_CONTROLLER = register(PartyController::new,
            Block.Settings.create()
                    .strength(4.0f, 30.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool(),
            "party_controller", true);

    public static final Block TELEPORTATION_PAD = register(TeleportationPadBlock::new,
            Block.Settings.create()
                    .strength(4.0f, 30.0f)
                    .sounds(BlockSoundGroup.WOOD)
                    .nonOpaque()
                    .requiresTool()
                    .luminance(state -> 4),
            "big_book", true);

    public static final Block VILLAGER_BLOCK = register(VillagerBlock::new,
            Block.Settings.create()
                    .strength(0.5f)
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
    public static final Block HOP_SWITCH = register(HopSwitchBlock::new,
            Block.Settings.create()
                    .strength(0.5f)
                    .nonOpaque()
                    .sounds(BlockSoundGroup.BONE),
            "hop_switch", true);

    public static final Block OAK_TRAFFIC_SIGN = register(c -> new TrafficSignBlock(WoodType.OAK, c),
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).solid().noCollision().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "oak_traffic_sign", true);

    public static final Block SPRUCE_TRAFFIC_SIGN = register(c -> new TrafficSignBlock(WoodType.SPRUCE, c),
            AbstractBlock.Settings.create().mapColor(MapColor.SPRUCE_BROWN).solid().noCollision().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "spruce_traffic_sign", true);

    public static final Block BIRCH_TRAFFIC_SIGN = register(c -> new TrafficSignBlock(WoodType.BIRCH, c),
            AbstractBlock.Settings.create().mapColor(MapColor.PALE_YELLOW).solid().noCollision().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "birch_traffic_sign", true);

    public static final Block JUNGLE_TRAFFIC_SIGN = register(c -> new TrafficSignBlock(WoodType.JUNGLE, c),
            AbstractBlock.Settings.create().mapColor(MapColor.BROWN).solid().noCollision().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "jungle_traffic_sign", true);

    public static final Block ACACIA_TRAFFIC_SIGN = register(c -> new TrafficSignBlock(WoodType.ACACIA, c),
            AbstractBlock.Settings.create().mapColor(MapColor.ORANGE).solid().noCollision().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "acacia_traffic_sign", true);

    public static final Block DARK_OAK_TRAFFIC_SIGN = register(c -> new TrafficSignBlock(WoodType.DARK_OAK, c),
            AbstractBlock.Settings.create().mapColor(MapColor.DARK_RED).solid().noCollision().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "dark_oak_traffic_sign", true);

    public static final Block MANGROVE_TRAFFIC_SIGN = register(c -> new TrafficSignBlock(WoodType.MANGROVE, c),
            AbstractBlock.Settings.create().mapColor(MapColor.DARK_RED).solid().noCollision().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "mangrove_traffic_sign", true);

    public static final Block CRIMSON_TRAFFIC_SIGN = register(c -> new TrafficSignBlock(WoodType.CRIMSON, c),
            AbstractBlock.Settings.create().mapColor(MapColor.DARK_CRIMSON).solid().noCollision().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "crimson_traffic_sign", true);

    public static final Block WARPED_TRAFFIC_SIGN = register(c -> new TrafficSignBlock(WoodType.WARPED, c),
            AbstractBlock.Settings.create().mapColor(MapColor.CYAN).solid().noCollision().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "warped_traffic_sign", true);

    public static final Block CHERRY_TRAFFIC_SIGN = register(c -> new TrafficSignBlock(WoodType.CHERRY, c),
            AbstractBlock.Settings.create().mapColor(MapColor.DULL_PINK).solid().noCollision().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "cherry_traffic_sign", true);

    public static final Block GOAL_POLE_BASE = register(GoalPoleBaseBlock::new,
            Block.Settings.create()
                    .strength(2.0f, 6.0f)
                    .sounds(BlockSoundGroup.STONE)
                    .requiresTool(),
            "goal_pole_base", true);
    public static final Block GOAL_POLE = register(GoalPoleBlock::new,
            Block.Settings.create()
                    .strength(3.0f, 6.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool(),
            "goal_pole", true);

    public static final Block LOOTING_BOX = register(LootingBoxBlock::new,
            Block.Settings.create()
                    .strength(2.0f, 4.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .nonOpaque()
                    .notSolid()
                    .requiresTool(),
            "looting_box", true);

    public static final Block BLUE_STAR_FRAGMENTS_BLOCK = register(StarFragmentsBlock::new,
            Block.Settings.create()
                    .strength(0.5f)
                    .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                    .luminance(state -> 15)
                    .nonOpaque(),
            "blue_star_fragments_block", true);
    public static final Block GREEN_STAR_FRAGMENTS_BLOCK = register(StarFragmentsBlock::new,
            Block.Settings.create()
                    .strength(0.5f)
                    .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                    .luminance(state -> 15)
                    .nonOpaque(),
            "green_star_fragments_block", true);
    public static final Block RED_STAR_FRAGMENTS_BLOCK = register(StarFragmentsBlock::new,
            Block.Settings.create()
                    .strength(0.5f)
                    .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                    .luminance(state -> 15)
                    .nonOpaque(),
            "red_star_fragments_block", true);
    public static final Block YELLOW_STAR_FRAGMENTS_BLOCK = register(StarFragmentsBlock::new,
            Block.Settings.create()
                    .strength(0.5f)
                    .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                    .luminance(state -> 15)
                    .nonOpaque(),
            "yellow_star_fragments_block", true);
    public static final Block PURPLE_STAR_FRAGMENTS_BLOCK = register(StarFragmentsBlock::new,
            Block.Settings.create()
                    .strength(0.5f)
                    .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                    .luminance(state -> 15)
                    .nonOpaque(),
            "purple_star_fragments_block", true);
    public static final Block BLACK_STAR_FRAGMENTS_BLOCK = register(StarFragmentsBlock::new,
            Block.Settings.create()
                    .strength(0.5f)
                    .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                    .luminance(state -> 15)
                    .nonOpaque(),
            "black_star_fragments_block", true);

    public static final Block GRAVITY_CORE = register(GravityCoreBlock::new,
            Block.Settings.create()
                    .strength(50.0f, 80000)
                    .sounds(BlockSoundGroup.AMETHYST_CLUSTER)
                    .nonOpaque()
                    .luminance(state -> 15)
                    .emissiveLighting((state, world, pos) -> true)
                    .requiresTool(),
            "gravity_core", true, EpicWithGlintBlockItem::new);

    public static final Block DICE_FORGE = register(DiceForgeBlock::new,
            Block.Settings.create()
                    .strength(2.0f, 4.0f)
                    .sounds(BlockSoundGroup.STONE)
                    .requiresTool(),
            "dice_forge", true);

    @SuppressWarnings({"unused", "SameParameterValue"})
    private static Block register(
            Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings, String name, boolean shouldRegisterItem,
            BiFunction<Block, Item.Settings, Item> blockItemFactory
    ) {
        Identifier identifier = Steveparty.id(name);
        RegistryKey<Block> registryKey = RegistryKey.of(RegistryKeys.BLOCK, identifier);

        Block block = Blocks.register(registryKey, factory, settings);
        Items.register(block, blockItemFactory);
        return block;
    }

    @SuppressWarnings({"unused", "SameParameterValue"})
    private static Block register(Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings, String name, boolean shouldRegisterItem) {
        return register(factory, settings, name, shouldRegisterItem, BlockItem::new);
    }

    public static void initialize() {
    }
}
