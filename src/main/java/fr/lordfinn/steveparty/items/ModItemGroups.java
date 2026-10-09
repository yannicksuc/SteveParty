package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.signs.MaterialSignItems;
import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.SignMaterial;
import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesBlock;
import fr.lordfinn.steveparty.compat.CartridgeApplications;
import fr.lordfinn.steveparty.components.TeleportNetwork;
import fr.lordfinn.steveparty.items.custom.BandanaItem;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static fr.lordfinn.steveparty.blocks.ModBlocks.*;
import static fr.lordfinn.steveparty.items.ModItems.*;

/**
 * The mod's creative tabs, in the order a player meets the mod: the board and the party, the mini-games, the creatures,
 * then building and decoration. Every listed item is in exactly one tab (CreativeTabsGameTests).
 */
public final class ModItemGroups {
    /** Board and party. Keeps the former single tab's id (the guide book opens it). */
    public static final RegistryKey<ItemGroup> BOARD = key("item_group");
    public static final RegistryKey<ItemGroup> MINI_GAMES = key("mini_games");
    public static final RegistryKey<ItemGroup> CREATURES = key("creatures");
    public static final RegistryKey<ItemGroup> BUILDING = key("building");
    public static final List<RegistryKey<ItemGroup>> ALL = List.of(BOARD, MINI_GAMES, CREATURES, BUILDING);

    private ModItemGroups() {
    }

    private static RegistryKey<ItemGroup> key(String path) {
        return RegistryKey.of(RegistryKeys.ITEM_GROUP, Steveparty.id(path));
    }

    private static void register(RegistryKey<ItemGroup> key, String name, Supplier<ItemStack> icon,
                                 ItemGroup.EntryCollector entries) {
        Registry.register(Registries.ITEM_GROUP, key, FabricItemGroup.builder()
                .icon(icon)
                .displayName(Text.translatable("itemGroup.steveparty." + name))
                .entries(entries)
                .build());
    }

    public static void initialize() {
        register(BOARD, "board", () -> new ItemStack(PARTY_CONTROLLER), ModItemGroups::board);
        register(MINI_GAMES, "mini_games", () -> new ItemStack(MINI_GAME_CONTROLLER), ModItemGroups::miniGames);
        register(CREATURES, "creatures", () -> new ItemStack(GLANDOUILLE_SPAWN_EGG), ModItemGroups::creatures);
        register(BUILDING, "building", () -> new ItemStack(PLASTIC_BLOCKS[DyeColor.RED.getId()]),
                ModItemGroups::building);
    }

    /** The party, the board spaces and their cartridges, the pawns, the shop, coins and stars, the dice, power-ups. */
    private static void board(ItemGroup.DisplayContext context, ItemGroup.Entries entries) {
        addAll(entries, PARTY_CONTROLLER, PARTY_CARD_TURNS, PARTY_CARD_MINIGAME, PARTY_CARD_EVENT,
                PARTY_CARD_SEQUENCE_START, PARTY_CARD_REPEAT, PARTY_BELL, PIGGY_BANK);
        // The tiles: plain, one per cartridge, then their other sizes (same order as in REI)
        entries.addAll(CartridgeApplications.tileEntries());
        addAll(entries, CHECK_POINT, BOARD_SPACE_REDSTONE_ROUTER, WRENCH, TILE_LINKER_BRUSH, EXPLORER_HELMET);
        addAll(entries, BOARD_SPACE_BEHAVIOR, BOARD_SPACE_BEHAVIOR_STOP, TILE_BEHAVIOR_START, INVENTORY_CARTRIDGE,
                SHOP_CARTRIDGE, ADVANCE_BACK_CARTRIDGE);
        entries.add(AdvanceBackCartridgeItem.withSteps(-3));
        addAll(entries, REPLAY_CARTRIDGE, TELEPORT_CARTRIDGE);
        for (TeleportNetwork network : TeleportNetwork.values()) {
            if (network == TeleportNetwork.VIOLET) continue;
            entries.add(TeleportCartridgeItem.withNetwork(new ItemStack(TELEPORT_CARTRIDGE), network));
        }
        addAll(entries, STAR_CARTRIDGE, GLANDOUILLE_CARTRIDGE, FROUSSEUX_CARTRIDGE, MISTIGRI_CARTRIDGE,
                THRESHOLD_CARTRIDGE, POT_CARTRIDGE, KEY_GATE_CARTRIDGE, GATE_KEY);
        addAll(entries, TOKENIZER_WAND, LAPIS_CRYSTAL_BALL, TOKEN, PLUNGER);
        addAll(entries, TRADING_STALL, CASH_REGISTER, SHOPKEEPER_KEY);
        addAll(entries, COIN, PARTY_STAR);
        // The 16 colours, in dye order: the blocks, then the fragments
        for (Block block : STAR_FRAGMENTS_BLOCKS) entries.add(block);
        for (Item fragment : STAR_FRAGMENTS) entries.add(fragment);
        addAll(entries, DICE_FORGE, DEFAULT_DICE, DOUBLE_DICE, TRIPLE_DICE);
        for (Item face : DICE_FACES) entries.add(face);
        entries.add(BLANK_DICE_MODULE);
        for (Item module : DICE_MODULES) entries.add(module);
        for (Item powerUp : POWER_UPS) entries.add(powerUp);
    }

    /** The mini-games: their controller and pages, the pipes, what ends them (goal poles, podiums), their gadgets. */
    private static void miniGames(ItemGroup.DisplayContext context, ItemGroup.Entries entries) {
        addAll(entries, MINI_GAME_CONTROLLER, MINI_GAME_REMOTE, MINI_GAMES_CATALOGUE, MINI_GAME_PAGE, STEP_CONTROLLER);
        for (Block[] pipes : ModBlocks.PIPES) {
            for (Block pipe : pipes) entries.add(pipe);
        }
        addAll(entries, GOAL_POLE_BASE, GOAL_POLE, FLAG, GOLD_PODIUM, SILVER_PODIUM, BRONZE_PODIUM, PODIUM);
        addAll(entries, TRIPLE_JUMP_SHOES, HOP_SWITCH, LOOTING_BOX, GRAVITY_CORE);
    }

    /** The mobs, each egg followed by what belongs to it. */
    private static void creatures(ItemGroup.DisplayContext context, ItemGroup.Entries entries) {
        addAll(entries, MULA_SPAWN_EGG, TELESCOPE, BOXED_TRADER_SPAWN_EGG, VILLAGER_BLOCK);
        for (int color = 0; color < BandanaItem.COLOR_NAMES.length; color++) {
            entries.add(BandanaItem.create(color));
        }
        entries.add(BOX_COSTUME);
        for (Item egg : GLANDOUILLE_SPAWN_EGGS) entries.add(egg);
        addAll(entries, ACORN, ACORN_HAT, FROUSSEUX_SPAWN_EGG, ModItems.CANDLE_SAUCER, MISTIGRI_SPAWN_EGG,
                BOOMCART_SPAWN_EGG, FUMAROLE_SPAWN_EGG, ModItems.MAGPIE_NEST);
    }

    /** Stencils, signs, plastic and polished blocks. */
    private static void building(ItemGroup.DisplayContext context, ItemGroup.Entries entries) {
        addAll(entries, STENCIL_MAKER, STENCIL_GUN, STENCIL);
        for (StencilPatterns.Pattern pattern : StencilPatterns.all()) {
            entries.add(StencilItem.of(pattern));
        }
        // The 10 fixed-wood easel signs stay in the game for the worlds that have them, but are no longer
        // listed: the material easel sign covers every planks, modded ones included
        RegistryWrapper.WrapperLookup lookup = context.lookup();
        for (Block planks : blocksOf(lookup, SignMaterial.WOOD)) {
            entries.add(MaterialSignItems.withMaterial(EASEL_SIGN, planks));
        }
        for (Block planks : blocksOf(lookup, SignMaterial.WOOD)) {
            entries.add(MaterialSignItems.withMaterial(WOODEN_PANEL, planks));
        }
        for (Block planks : blocksOf(lookup, SignMaterial.WOOD)) {
            entries.add(MaterialSignItems.withMaterial(WOODEN_CUTOUT_PANEL, planks));
        }
        for (Block rock : blocksOf(lookup, SignMaterial.ROCK)) {
            entries.add(MaterialSignItems.withMaterial(ROCK_SIGN, rock));
        }
        for (PlasticRoadSignBlock.Plate plate : PlasticRoadSignBlock.Plate.values()) {
            ItemStack sign = MaterialSignItems.withPlateColor(PLASTIC_ROAD_SIGN,
                    plate == PlasticRoadSignBlock.Plate.ROUND ? DyeColor.RED : DyeColor.YELLOW);
            sign.set(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT.with(PlasticRoadSignBlock.PLATE, plate));
            entries.add(sign);
        }
        for (DyeColor color : DyeColor.values()) {
            entries.add(MaterialSignItems.withPlateColor(PLASTIC_ROAD_SIGN, color));
        }
        addAll(entries, PLASTIC_PELLETS, PLASTIC_STICK);
        for (Block[] blocks : new Block[][]{PLASTIC_BLOCKS, PLASTIC_STUDS, PLASTIC_FENCES,
                PLASTIC_SLABS, PLASTIC_STAIRS, PLASTIC_WALLS,
                POLISHED_TERRACOTTA_BLOCKS, POLISHED_TERRACOTTA_BRICKS_BLOCKS,
                POLISHED_TERRACOTTA_STAIRS, POLISHED_TERRACOTTA_SLABS, POLISHED_TERRACOTTA_WALLS,
                POLISHED_TERRACOTTA_BRICKS_STAIRS, POLISHED_TERRACOTTA_BRICKS_SLABS, POLISHED_TERRACOTTA_BRICKS_WALLS,
                POLISHED_CONCRETE_BLOCKS, POLISHED_CONCRETE_BRICKS_BLOCKS,
                POLISHED_CONCRETE_STAIRS, POLISHED_CONCRETE_SLABS, POLISHED_CONCRETE_WALLS,
                POLISHED_CONCRETE_BRICKS_STAIRS, POLISHED_CONCRETE_BRICKS_SLABS, POLISHED_CONCRETE_BRICKS_WALLS}) {
            for (Block block : blocks) entries.add(block);
        }
        for (PolishedTilesBlock tiles : POLISHED_TILES) {
            for (ItemStack stack : tiles.creativeStacks()) entries.add(stack);
        }
    }

    private static void addAll(ItemGroup.Entries entries, ItemConvertible... items) {
        for (ItemConvertible item : items) entries.add(item);
    }

    /** Blocks of a sign material kind, from the tags of the running game (modded blocks included). */
    private static List<Block> blocksOf(RegistryWrapper.WrapperLookup lookup, SignMaterial kind) {
        List<Block> blocks = new ArrayList<>();
        lookup.getWrapperOrThrow(RegistryKeys.BLOCK).getOptional(kind.tag())
                .ifPresent(list -> list.forEach(entry -> blocks.add(entry.value())));
        if (blocks.isEmpty()) blocks.add(kind.defaultBlock());
        return blocks;
    }
}
