package fr.lordfinn.steveparty.blocks;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.*;
import fr.lordfinn.steveparty.blocks.custom.signs.*;
import fr.lordfinn.steveparty.items.custom.StencilSignItem;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CheckPointBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.AdvancedTileBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyController;
import fr.lordfinn.steveparty.blocks.switchable.SwitchedOffBlock;
import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesBlock;
import fr.lordfinn.steveparty.items.custom.EpicWithGlintBlockItem;
import fr.lordfinn.steveparty.items.custom.PolishedTilesItem;
import net.minecraft.block.*;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.DyeColor;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ModBlocks {
    /**
     * The explosion resistance of the blocks a game is built with (board spaces, controllers, podiums, goal poles,
     * mini-game pipes, the shop's blocks...), bedrock's: no explosion breaks a board, and they are still mined as
     * usual. The same blocks are in the {@code minecraft:wither_immune} and {@code minecraft:dragon_immune} tags.
     */
    public static final float BOARD_RESISTANCE = 3_600_000f;
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

    public static final Block[] PLASTIC_BLOCKS = new Block[COLORS.length];

    static {
        for (int i = 0; i < COLORS.length; i++) {
            String color = COLORS[i];
            PLASTIC_BLOCKS[i] = register(
                    PlasticBlock::new,
                    // Moulded plastic: quicker with a pickaxe but still harvestable by hand, not flammable, floats
                    Block.Settings.create()
                            .mapColor(DyeColor.byName(color, DyeColor.WHITE))
                            .strength(1.0f, 1.0f)
                            .sounds(BlockSoundGroup.BAMBOO_WOOD),
                    color + "_plastic_block",
                    true
            );
        }
    }

    // A switchable block while the hop switch keeps it switched off (no item). Its collision shape is empty but
    // it counts as solid, so fluids cannot wash it away; explosion-proof and piston-proof so it cannot be used to
    // get the original block's drops in a way the original block would not allow.
    public static final Block SWITCHED_OFF_BLOCK = Blocks.register(
            RegistryKey.of(RegistryKeys.BLOCK, Steveparty.id("switched_off_block")),
            new SwitchedOffBlock(Block.Settings.create()
                    .solid()
                    .nonOpaque()
                    .strength(1.0f, 3_600_000f)
                    .sounds(BlockSoundGroup.BAMBOO_WOOD)
                    .pistonBehavior(PistonBehavior.BLOCK)));

    // Plastic studs: 8x8x4 pieces of the plastic block, on the floor, a wall or the ceiling
    public static final Block[] PLASTIC_STUDS = new Block[COLORS.length];

    static {
        for (int i = 0; i < COLORS.length; i++) {
            String color = COLORS[i];
            PLASTIC_STUDS[i] = register(
                    PlotBlock::new,
                    Block.Settings.create()
                            .mapColor(DyeColor.byName(color, DyeColor.WHITE))
                            .strength(1.0f, 1.0f)
                            .sounds(BlockSoundGroup.BAMBOO_WOOD)
                            .nonOpaque(),
                    color + "_plastic_stud",
                    true
            );
        }
    }

    // Plastic fences: posts for the plastic road signs (and fences like any other)
    public static final Block[] PLASTIC_FENCES = new Block[COLORS.length];

    static {
        for (int i = 0; i < COLORS.length; i++) {
            String color = COLORS[i];
            PLASTIC_FENCES[i] = register(
                    PlasticFenceBlock::new,
                    Block.Settings.create()
                            .mapColor(DyeColor.byName(color, DyeColor.WHITE))
                            .solid()
                            .strength(1.0f, 1.0f)
                            .sounds(BlockSoundGroup.BAMBOO_WOOD),
                    color + "_plastic_fence",
                    true
            );
        }
    }

    // Plastic slabs, stairs and walls, like the vanilla stone ones (they do not float: only full plastic pieces do)
    public static final Block[] PLASTIC_SLABS = new Block[COLORS.length];
    public static final Block[] PLASTIC_STAIRS = new Block[COLORS.length];
    public static final Block[] PLASTIC_WALLS = new Block[COLORS.length];

    static {
        for (int i = 0; i < COLORS.length; i++) {
            final int index = i;
            String color = COLORS[i];
            PLASTIC_SLABS[i] = register(SlabBlock::new, Block.Settings.copy(PLASTIC_BLOCKS[i]),
                    color + "_plastic_slab", true);
            // Stairs only take their blast resistance and pathfinding from their base block
            PLASTIC_STAIRS[i] = register(s -> new StairsBlock(PLASTIC_BLOCKS[index].getDefaultState(), s),
                    Block.Settings.copy(PLASTIC_BLOCKS[i]), color + "_plastic_stairs", true);
            PLASTIC_WALLS[i] = register(WallBlock::new, Block.Settings.copy(PLASTIC_BLOCKS[i]).solid(),
                    color + "_plastic_wall", true);
        }
    }

    /**
     * Travel pipes, by kind ({@link fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind#ordinal()}) then colour (the plain
     * glass pipe: one block): never suffocating whoever travels inside.
     */
    public static final Block[][] PIPES = new Block[fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind.values().length][];

    static {
        for (fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind kind : fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind.values()) {
            PIPES[kind.ordinal()] = new Block[kind.count()];
            for (int i = 0; i < kind.count(); i++) {
                final int color = i;
                final boolean miniGame = kind.isMiniGame();
                PIPES[kind.ordinal()][i] = register(
                        settings -> miniGame ? new fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlock(kind, settings)
                                : new fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock(kind, color, settings),
                        Block.Settings.create()
                                .mapColor(miniGame ? MapColor.GOLD : kind.colored ? DyeColor.byName(COLORS[i], DyeColor.WHITE).getMapColor() : MapColor.CLEAR)
                                .strength(miniGame ? 1.5f : kind.isPlastic() ? 1.0f : 0.3f, miniGame ? BOARD_RESISTANCE : 1.0f)
                                .sounds(miniGame ? BlockSoundGroup.METAL : kind.isPlastic() ? BlockSoundGroup.BAMBOO_WOOD : BlockSoundGroup.GLASS)
                                .nonOpaque()
                                .suffocates((state, world, pos) -> false)
                                .blockVision((state, world, pos) -> false)
                                .allowsSpawning((state, world, pos, type) -> false)
                                .pistonBehavior(PistonBehavior.BLOCK),
                        kind.id(COLORS, i),
                        true
                );
            }
        }
    }

    /** The mini-game pipes: copper reaches 100 blocks, iron its whole dimension, gold every dimension. */
    public static final Block COPPER_MINIGAME_PIPE = PIPES[fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind.COPPER.ordinal()][0];
    public static final Block IRON_MINIGAME_PIPE = PIPES[fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind.IRON.ordinal()][0];
    public static final Block GOLDEN_MINIGAME_PIPE = PIPES[fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind.GOLDEN.ordinal()][0];

    /** The plain glass pipe. */
    public static final Block GLASS_PIPE = PIPES[fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind.GLASS.ordinal()][0];

    public static final Block[] POLISHED_TERRACOTTA_BLOCKS = new Block[COLORS_WITH_DEFAULT.length];

    /** The vanilla terracotta a polished terracotta is made from ("default" = plain terracotta): same map colour. */
    private static Block vanillaTerracotta(String color) {
        return color.equals("default") ? Blocks.TERRACOTTA : Registries.BLOCK.get(Identifier.ofVanilla(color + "_terracotta"));
    }

    static {
        for (int i = 0; i < COLORS_WITH_DEFAULT.length; i++) {
            String color = COLORS_WITH_DEFAULT[i];
            String name = "polished_" + color + "_terracotta";

            POLISHED_TERRACOTTA_BLOCKS[i] = register(Block::new,
                    Block.Settings.create()
                            .mapColor(vanillaTerracotta(color).getDefaultMapColor())
                            .instrument(NoteBlockInstrument.BASEDRUM)
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
                            .mapColor(vanillaTerracotta(color).getDefaultMapColor())
                            .instrument(NoteBlockInstrument.BASEDRUM)
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

    // Polished concrete: same pattern as the polished terracotta, in the vanilla concrete colours
    public static final Block[] POLISHED_CONCRETE_BLOCKS = new Block[COLORS.length];
    public static final Block[] POLISHED_CONCRETE_BRICKS_BLOCKS = new Block[COLORS.length];

    public static final Block[] POLISHED_CONCRETE_STAIRS = new StairsBlock[COLORS.length];
    public static final Block[] POLISHED_CONCRETE_SLABS = new SlabBlock[COLORS.length];
    public static final Block[] POLISHED_CONCRETE_WALLS = new WallBlock[COLORS.length];

    public static final Block[] POLISHED_CONCRETE_BRICKS_STAIRS = new StairsBlock[COLORS.length];
    public static final Block[] POLISHED_CONCRETE_BRICKS_SLABS = new SlabBlock[COLORS.length];
    public static final Block[] POLISHED_CONCRETE_BRICKS_WALLS = new WallBlock[COLORS.length];

    static {
        for (int i = 0; i < COLORS.length; i++) {
            final int index = i;
            String color = COLORS[i];
            DyeColor dyeColor = DyeColor.byName(color, DyeColor.WHITE);

            POLISHED_CONCRETE_BLOCKS[i] = register(Block::new,
                    Block.Settings.create()
                            .mapColor(dyeColor)
                            .strength(1.8f, 1.8f)
                            .sounds(BlockSoundGroup.STONE)
                            .solid()
                            .requiresTool(),
                    "polished_" + color + "_concrete", true);

            POLISHED_CONCRETE_BRICKS_BLOCKS[i] = register(Block::new,
                    Block.Settings.create()
                            .mapColor(dyeColor)
                            .strength(2.0f, 2.5f)
                            .sounds(BlockSoundGroup.STONE)
                            .solid()
                            .requiresTool(),
                    "polished_" + color + "_concrete_bricks", true);

            // Concrete
            POLISHED_CONCRETE_STAIRS[i] = register(
                    (s) -> new StairsBlock(POLISHED_CONCRETE_BLOCKS[index].getDefaultState(), s),
                    Block.Settings.copy(POLISHED_CONCRETE_BLOCKS[index]),
                    color + "_polished_concrete_stairs",
                    true
            );

            POLISHED_CONCRETE_SLABS[i] = register(
                    SlabBlock::new,
                    Block.Settings.copy(POLISHED_CONCRETE_BLOCKS[i]),
                    color + "_polished_concrete_slab",
                    true
            );

            POLISHED_CONCRETE_WALLS[i] = register(
                    WallBlock::new,
                    Block.Settings.copy(POLISHED_CONCRETE_BLOCKS[i]),
                    color + "_polished_concrete_wall",
                    true
            );

            // Concrete bricks
            POLISHED_CONCRETE_BRICKS_STAIRS[i] = register(
                    (s) -> new StairsBlock(POLISHED_CONCRETE_BRICKS_BLOCKS[index].getDefaultState(), s),
                    Block.Settings.copy(POLISHED_CONCRETE_BRICKS_BLOCKS[index]),
                    color + "_polished_concrete_bricks_stairs",
                    true
            );

            POLISHED_CONCRETE_BRICKS_SLABS[i] = register(
                    SlabBlock::new,
                    Block.Settings.copy(POLISHED_CONCRETE_BRICKS_BLOCKS[i]),
                    color + "_polished_concrete_bricks_slab",
                    true
            );

            POLISHED_CONCRETE_BRICKS_WALLS[i] = register(
                    WallBlock::new,
                    Block.Settings.copy(POLISHED_CONCRETE_BRICKS_BLOCKS[i]),
                    color + "_polished_concrete_bricks_wall",
                    true
            );
        }
    }

    // Decorative tilings: a 2x2 checker of two colours of polished concrete / terracotta, the colours in the state.
    // As hard as the polished block they are made of.
    public static final Block POLISHED_CONCRETE_TILES = register(PolishedTilesBlock.Concrete::new,
            Block.Settings.create()
                    .mapColor(PolishedTilesBlock::mapColor)
                    .strength(1.8f, 1.8f)
                    .sounds(BlockSoundGroup.STONE)
                    .solid()
                    .requiresTool(),
            "polished_concrete_tiles", true, PolishedTilesItem::new);
    public static final Block POLISHED_TERRACOTTA_TILES = register(PolishedTilesBlock.Terracotta::new,
            Block.Settings.create()
                    .mapColor(PolishedTilesBlock::mapColor)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(1.25f, 4.2f)
                    .sounds(BlockSoundGroup.STONE)
                    .solid()
                    .requiresTool(),
            "polished_terracotta_tiles", true, PolishedTilesItem::new);
    public static final PolishedTilesBlock[] POLISHED_TILES = {
            (PolishedTilesBlock) POLISHED_CONCRETE_TILES, (PolishedTilesBlock) POLISHED_TERRACOTTA_TILES};

    public static final Block TRADING_STALL = register(TradingStallBlock::new,
            Block.Settings.create()
                    .strength(2.5f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.WOOD)
                    .nonOpaque(),
            "trading_stall", true);

    public static final Block STENCIL_MAKER = register(StencilMakerBlock::new,
            Block.Settings.create()
                    .strength(3.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool(),
            "stencil_maker", true);

    public static final Block ADVANCED_TILE = register(AdvancedTileBlock::new,
            Block.Settings.create()
                    .strength(2f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool(),
            "advanced_tile", true, fr.lordfinn.steveparty.items.custom.TileBlockItem::new);

    public static final Block TILE = register(TileBlock::new,
            Block.Settings.create()
                    .strength(2f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool(),
            "tile", true, fr.lordfinn.steveparty.items.custom.TileBlockItem::new);

    /** The 3 other blocks of a large (2x2) tile (no item: placed and removed with the tile). */
    public static final Block TILE_PART = Blocks.register(RegistryKey.of(RegistryKeys.BLOCK, Steveparty.id("tile_part")),
            new fr.lordfinn.steveparty.blocks.custom.boardspaces.TilePartBlock(Block.Settings.create()
                    .strength(2f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)
                    .nonOpaque()
                    .dynamicBounds()
                    .dropsNothing()
                    .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)));

    public static final Block CHECK_POINT = register(CheckPointBlock::new,
            Block.Settings.create()
                    .strength(2f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                    .nonOpaque()
                    .luminance(state -> 5)
                    .ticksRandomly(),
            "check_point", true);

    public static final Block PARTY_CONTROLLER = register(PartyController::new,
            Block.Settings.create()
                    .strength(4.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool(),
            "party_controller", true);

    public static final Block MINI_GAME_CONTROLLER = register(MiniGameControllerBlock::new,
            Block.Settings.create()
                    .strength(4.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)
                    .nonOpaque()
                    .requiresTool(),
            "mini_game_controller", true);

    public static final Block VILLAGER_BLOCK = register(VillagerBlock::new,
            Block.Settings.create()
                    // No tool needed (no requiresTool: it drops by hand), but breaking it takes a moment (1.5 s by
                    // hand), long enough for it to plead and cry (see VillagerBlock#onBlockBreakStart)
                    .strength(1.0f)
                    .nonOpaque()  // Fully transparent/visible (for entity-like behavior)
                    .sounds(BlockSoundGroup.WOOL),  // (its own villager sounds: VillagerBlock#getSoundGroup)
            "villager_block", true);

    /** A tamed Frousseux asleep as a candle holder (FrousseuxCandleHolderBlock): its light is its flame's. */
    public static final Block FROUSSEUX_CANDLE_HOLDER = register(
            fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlock::new,
            Block.Settings.create()
                    .strength(0.3f)
                    .nonOpaque()
                    .sounds(BlockSoundGroup.CANDLE)
                    .burnable() // wax: lava sets it alight, fire burns it away (the Frousseux with it)
                    .luminance(fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlock::lightOf)
                    .pistonBehavior(PistonBehavior.DESTROY),
            "frousseux_candle_holder", true);

    /** The Candle Saucer, a little gold tray: placed on its own, or under a candle holder (CandleSaucerBlock). */
    public static final Block CANDLE_SAUCER = register(fr.lordfinn.steveparty.blocks.custom.frousseux.CandleSaucerBlock::new,
            Block.Settings.create()
                    .strength(0.5f)
                    .nonOpaque()
                    .sounds(BlockSoundGroup.METAL)
                    .pistonBehavior(PistonBehavior.DESTROY),
            "candle_saucer", true);

    public static final Block CASH_REGISTER = register(CashRegisterBlock::new,
            Block.Settings.create()
                    .strength(2.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)  // Metallic sound for a register
                    .nonOpaque()  // Allows for visual transparency, if any
                    .luminance(state -> 3)  // Low glow to suggest activity or power
                    .requiresTool(),  // Needs a pickaxe or equivalent tool to break
            "cash_register", true);
    public static final Block STEP_CONTROLLER = register(StepControllerBlock::new,
            Block.Settings.create()
                    .strength(3.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)  // Stone-like sound for a mechanical device
                    .luminance(state -> 5)  // Slightly brighter to signify active operation
                    .nonOpaque()  // Non-opaque to allow transparency for hourglass visualization
                    .requiresTool(),  // Requires a pickaxe or equivalent tool to break
            "step_controller", true);
    public static final Block PARTY_BELL = register(PartyBellBlock::new,
            Block.Settings.create()
                    .strength(1.5f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)
                    .nonOpaque()
                    .requiresTool(),
            "party_bell", true);
    /** Podiums: four looks (classic, gold, silver, bronze); the place of a podium is the height of its column. */
    public static final Block PODIUM = register(settings -> new PodiumBlock(settings, PodiumBlock.Style.CLASSIC),
            Block.Settings.create()
                    .strength(2.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.STONE)
                    .nonOpaque()
                    .requiresTool(),
            "podium", true);
    public static final Block GOLD_PODIUM = register(settings -> new PodiumBlock(settings, PodiumBlock.Style.GOLD),
            Block.Settings.create()
                    .strength(2.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.STONE)
                    .nonOpaque()
                    .requiresTool(),
            "gold_podium", true);
    public static final Block SILVER_PODIUM = register(settings -> new PodiumBlock(settings, PodiumBlock.Style.SILVER),
            Block.Settings.create()
                    .strength(2.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.STONE)
                    .nonOpaque()
                    .requiresTool(),
            "silver_podium", true);
    public static final Block BRONZE_PODIUM = register(settings -> new PodiumBlock(settings, PodiumBlock.Style.BRONZE),
            Block.Settings.create()
                    .strength(2.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.STONE)
                    .nonOpaque()
                    .requiresTool(),
            "bronze_podium", true);
    public static final Block PIGGY_BANK = register(PiggyBankBlock::new,
            Block.Settings.create()
                    .strength(1.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.DECORATED_POT)
                    .nonOpaque()
                    .requiresTool(),
            "piggy_bank", true);
    public static final Block BOARD_SPACE_REDSTONE_ROUTER = register(BoardSpaceRedstoneRouterBlock::new,
            Block.Settings.create()
                    .strength(3.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)  // Stone-like sound for a mechanical device
                    .requiresTool(),  // Requires a pickaxe or equivalent tool to break
            "board_space_redstone_router", true);
    public static final Block HOP_SWITCH = register(HopSwitchBlock::new,
            Block.Settings.create()
                    .strength(0.5f, BOARD_RESISTANCE)
                    .nonOpaque()
                    .sounds(BlockSoundGroup.BONE),
            "hop_switch", true);

    public static final Block OAK_EASEL_SIGN = register(c -> new EaselSignBlock(WoodType.OAK, c),
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).solid().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "oak_easel_sign", true, StencilSignItem::new);

    public static final Block SPRUCE_EASEL_SIGN = register(c -> new EaselSignBlock(WoodType.SPRUCE, c),
            AbstractBlock.Settings.create().mapColor(MapColor.SPRUCE_BROWN).solid().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "spruce_easel_sign", true, StencilSignItem::new);

    public static final Block BIRCH_EASEL_SIGN = register(c -> new EaselSignBlock(WoodType.BIRCH, c),
            AbstractBlock.Settings.create().mapColor(MapColor.PALE_YELLOW).solid().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "birch_easel_sign", true, StencilSignItem::new);

    public static final Block JUNGLE_EASEL_SIGN = register(c -> new EaselSignBlock(WoodType.JUNGLE, c),
            AbstractBlock.Settings.create().mapColor(MapColor.BROWN).solid().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "jungle_easel_sign", true, StencilSignItem::new);

    public static final Block ACACIA_EASEL_SIGN = register(c -> new EaselSignBlock(WoodType.ACACIA, c),
            AbstractBlock.Settings.create().mapColor(MapColor.ORANGE).solid().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "acacia_easel_sign", true, StencilSignItem::new);

    public static final Block DARK_OAK_EASEL_SIGN = register(c -> new EaselSignBlock(WoodType.DARK_OAK, c),
            AbstractBlock.Settings.create().mapColor(MapColor.DARK_RED).solid().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "dark_oak_easel_sign", true, StencilSignItem::new);

    public static final Block MANGROVE_EASEL_SIGN = register(c -> new EaselSignBlock(WoodType.MANGROVE, c),
            AbstractBlock.Settings.create().mapColor(MapColor.DARK_RED).solid().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "mangrove_easel_sign", true, StencilSignItem::new);

    public static final Block CRIMSON_EASEL_SIGN = register(c -> new EaselSignBlock(WoodType.CRIMSON, c),
            AbstractBlock.Settings.create().mapColor(MapColor.DARK_CRIMSON).solid().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "crimson_easel_sign", true, StencilSignItem::new);

    public static final Block WARPED_EASEL_SIGN = register(c -> new EaselSignBlock(WoodType.WARPED, c),
            AbstractBlock.Settings.create().mapColor(MapColor.CYAN).solid().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "warped_easel_sign", true, StencilSignItem::new);

    public static final Block CHERRY_EASEL_SIGN = register(c -> new EaselSignBlock(WoodType.CHERRY, c),
            AbstractBlock.Settings.create().mapColor(MapColor.DULL_PINK).solid().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "cherry_easel_sign", true, StencilSignItem::new);

    public static final Block GOAL_POLE_BASE = register(GoalPoleBaseBlock::new,
            Block.Settings.create()
                    .strength(2.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.STONE)
                    .requiresTool(),
            "goal_pole_base", true);
    public static final Block GOAL_POLE = register(GoalPoleBlock::new,
            Block.Settings.create()
                    .strength(3.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)
                    .requiresTool(),
            "goal_pole", true);

    public static final Block LOOTING_BOX = register(LootingBoxBlock::new,
            Block.Settings.create()
                    .strength(2.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.METAL)
                    .nonOpaque()
                    .notSolid()
                    .requiresTool(),
            "looting_box", true);

    /** Glowing translucent crystal built like glass: see-through, lets light pass, never suffocates or blocks vision. */
    private static Block starFragmentsBlock(int colour, DyeColor dye) {
        return register(settings -> new StarFragmentsBlock(colour, settings),
                Block.Settings.create()
                        .mapColor(dye)
                        .strength(0.5f)
                        .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                        .luminance(state -> 15)
                        .nonOpaque()
                        .allowsSpawning((state, world, pos, type) -> false)
                        .solidBlock((state, world, pos) -> false)
                        .suffocates((state, world, pos) -> false)
                        .blockVision((state, world, pos) -> false),
                dye.getName() + "_star_fragments_block", true);
    }

    public static final Block BLUE_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.BLUE, DyeColor.BLUE);
    public static final Block GREEN_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.GREEN, DyeColor.GREEN);
    public static final Block PURPLE_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.PURPLE, DyeColor.PURPLE);
    public static final Block RED_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.RED, DyeColor.RED);
    public static final Block YELLOW_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.YELLOW, DyeColor.YELLOW);
    public static final Block BLACK_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.BLACK, DyeColor.BLACK);
    public static final Block WHITE_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.WHITE, DyeColor.WHITE);
    public static final Block ORANGE_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.ORANGE, DyeColor.ORANGE);
    public static final Block MAGENTA_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.MAGENTA, DyeColor.MAGENTA);
    public static final Block LIGHT_BLUE_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.LIGHT_BLUE, DyeColor.LIGHT_BLUE);
    public static final Block LIME_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.LIME, DyeColor.LIME);
    public static final Block PINK_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.PINK, DyeColor.PINK);
    public static final Block GRAY_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.GRAY, DyeColor.GRAY);
    public static final Block LIGHT_GRAY_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.LIGHT_GRAY, DyeColor.LIGHT_GRAY);
    public static final Block CYAN_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.CYAN, DyeColor.CYAN);
    public static final Block BROWN_STAR_FRAGMENTS_BLOCK = starFragmentsBlock(StarFragmentsBlock.BROWN, DyeColor.BROWN);
    /** The 16 star fragments blocks in dye order (index = DyeColor id). 6 are made of Mula drops, 10 of mixed fragments. */
    public static final List<Block> STAR_FRAGMENTS_BLOCKS = List.of(
            WHITE_STAR_FRAGMENTS_BLOCK, ORANGE_STAR_FRAGMENTS_BLOCK, MAGENTA_STAR_FRAGMENTS_BLOCK, LIGHT_BLUE_STAR_FRAGMENTS_BLOCK,
            YELLOW_STAR_FRAGMENTS_BLOCK, LIME_STAR_FRAGMENTS_BLOCK, PINK_STAR_FRAGMENTS_BLOCK, GRAY_STAR_FRAGMENTS_BLOCK,
            LIGHT_GRAY_STAR_FRAGMENTS_BLOCK, CYAN_STAR_FRAGMENTS_BLOCK, PURPLE_STAR_FRAGMENTS_BLOCK, BLUE_STAR_FRAGMENTS_BLOCK,
            BROWN_STAR_FRAGMENTS_BLOCK, GREEN_STAR_FRAGMENTS_BLOCK, RED_STAR_FRAGMENTS_BLOCK, BLACK_STAR_FRAGMENTS_BLOCK);

    public static Block starFragmentsBlock(DyeColor dye) {
        return STAR_FRAGMENTS_BLOCKS.get(dye.getId());
    }

    public static final Block GRAVITY_CORE = register(GravityCoreBlock::new,
            Block.Settings.create()
                    // Broken by hand in a second and a half, and dropped (an orbiting player must get it back)
                    .strength(1.0f, 1200)
                    .sounds(BlockSoundGroup.AMETHYST_CLUSTER)
                    .nonOpaque()
                    .luminance(state -> 15)
                    .emissiveLighting((state, world, pos) -> true),
            "gravity_core", true, EpicWithGlintBlockItem::new);

    public static final Block DICE_FORGE = register(DiceForgeBlock::new,
            Block.Settings.create()
                    .strength(2.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.STONE)
                    .requiresTool(),
            "dice_forge", true);

    /** A copper telescope on a tripod: to replay the past nights of shooting stars and find where the Mulas came down. */
    public static final Block TELESCOPE = register(TelescopeBlock::new,
            Block.Settings.create()
                    .mapColor(MapColor.ORANGE)
                    .strength(1.0f, BOARD_RESISTANCE)
                    .sounds(BlockSoundGroup.COPPER)
                    .nonOpaque()
                    .pistonBehavior(PistonBehavior.DESTROY),
            "telescope", true);

    // ---------------------------------------------------------------- stencil signs

    /** Easel sign of any planks (the wood is kept by the block entity and the item). */
    public static final Block EASEL_SIGN = register(MaterialEaselSignBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).solid().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "easel_sign", true, StencilSignItem::new);

    public static final Block WOODEN_PANEL = register(WoodenPanelBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).solid().dynamicBounds().nonOpaque().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "wooden_panel", true, StencilSignItem::new);

    public static final Block WOODEN_CUTOUT_PANEL = register(WoodenCutoutPanelBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).solid().dynamicBounds().nonOpaque().strength(1.0F).burnable().sounds(BlockSoundGroup.WOOD),
            "wooden_cutout_panel", true, StencilSignItem::new);

    public static final Block ROCK_SIGN = register(RockSignBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).nonOpaque().requiresTool().strength(1.5F, 6.0F).sounds(BlockSoundGroup.STONE),
            "rock_sign", true, StencilSignItem::new);

    public static final Block PLASTIC_ROAD_SIGN = register(PlasticRoadSignBlock::new,
            AbstractBlock.Settings.create().mapColor(MapColor.WHITE).solid().dynamicBounds().nonOpaque().strength(1.0F, 1.0F).sounds(BlockSoundGroup.BAMBOO_WOOD),
            "plastic_road_sign", true, StencilSignItem::new);

    /** Paint sprayed through a stencil on a block face (no item: made by stencils and the stencil gun). */
    public static final Block STENCIL_PAINT = Blocks.register(
            RegistryKey.of(RegistryKeys.BLOCK, Steveparty.id("stencil_paint")),
            new StencilPaintBlock(AbstractBlock.Settings.create().replaceable().noCollision().nonOpaque().breakInstantly().dropsNothing()
                    .pistonBehavior(PistonBehavior.DESTROY).sounds(BlockSoundGroup.WOOL)));

    @SuppressWarnings({"unused", "SameParameterValue"})
    private static Block register(
            Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings, String name, boolean shouldRegisterItem,
            BiFunction<Block, Item.Settings, Item> blockItemFactory
    ) {
        Identifier identifier = Steveparty.id(name);
        RegistryKey<Block> registryKey = RegistryKey.of(RegistryKeys.BLOCK, identifier);

        Block block = Blocks.register(registryKey, factory.apply(settings));
        // Registered under the block's id (and linked to it, for block items)
        Items.register(block, blockItemFactory.apply(block, new Item.Settings()));
        return block;
    }

    @SuppressWarnings({"unused", "SameParameterValue"})
    private static Block register(Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings, String name, boolean shouldRegisterItem) {
        return register(factory, settings, name, shouldRegisterItem, BlockItem::new);
    }

    /** A planted Acorn: grows like a crop on farmland, hatches into a young Glandouille when ripe (no block item). */
    public static final Block ACORN_CROP = Blocks.register(RegistryKey.of(RegistryKeys.BLOCK, Steveparty.id("acorn_crop")),
            new fr.lordfinn.steveparty.blocks.custom.glandouille.AcornCropBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.BROWN)
                    .noCollision()
                    .offset(AbstractBlock.OffsetType.XZ)
                    .ticksRandomly()
                    .breakInstantly()
                    .sounds(BlockSoundGroup.CROP)
                    .pistonBehavior(PistonBehavior.DESTROY)));

    public static void initialize() {
    }
}
