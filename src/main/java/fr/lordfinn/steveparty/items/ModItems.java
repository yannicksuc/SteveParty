package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.signs.MaterialSignItems;
import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.SignMaterial;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.DyeColor;
import fr.lordfinn.steveparty.items.custom.*;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.StartCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.StopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.Steveparty.MOD_ID;
import static fr.lordfinn.steveparty.blocks.ModBlocks.*;

public class ModItems {
    /** Enchanting table enchantability of the tokenizer wand (it can only receive steveparty:game_master). */
    private static final int TOKENIZER_WAND_ENCHANTABILITY = 10;

    public static final Item DOUBLE_DICE = register(DoubleDiceItem.class, "double_dice");

    public static final Item STENCIL = register(StencilItem.class, "stencil");
    public static final Item STENCIL_GUN = registerUnstackable(StencilGunItem.class, "stencil_gun");
    public static final Item WRENCH = registerUnstackable(WrenchItem.class, "wrench");
    public static final Item BOARD_SPACE_BEHAVIOR = register(CartridgeItem.class, "board_space_behavior");
    public static final Item TILE_BEHAVIOR_START = register(StartCartridgeItem.class, "tile_behavior_start");
    /** Its tile's face is anthracite until dyed. */
    public static final Item BOARD_SPACE_BEHAVIOR_STOP = register(StopCartridgeItem.class, "board_space_behavior_stop",
            new Item.Settings().component(fr.lordfinn.steveparty.components.ModComponents.COLOR,
                    fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StopBoardSpaceBehavior.COLOR));
    public static final Item TOKENIZER_WAND = register(TokenizerWandItem.class, "tokenizer_wand", new Item.Settings().maxCount(1).enchantable(TOKENIZER_WAND_ENCHANTABILITY));
    public static final Item PLUNGER = register(PlungerItem.class, "plunger");
    public static final Item DEFAULT_DICE = register(DefaultDiceItem.class,"default_dice");
    public static final Item TRIPLE_DICE = register(TripleDiceItem.class, "triple_dice");
    /** The dice faces: the blank one first ({@link #blankDiceFace}), then kind by kind. */
    public static final List<Item> DICE_FACES = new ArrayList<>();
    /** The dice module items, one per module of DiceModules. */
    public static final List<Item> DICE_MODULES = new ArrayList<>();

    public static Item blankDiceFace() {
        return DICE_FACES.getFirst();
    }
    public static final Item GARNET_CRYSTAL_BALL = register(GarnetCrystalBallItem.class,"garnet_crystal_ball");
    public static final Item MINI_GAMES_CATALOGUE = registerUnstackable(MiniGamesCatalogueItem.class,"mini_games_catalogue");
    public static final Item TOKEN = register(TokenItem.class, "token");
    public static final Item INVENTORY_CARTRIDGE = register(InventoryCartridgeItem.class, "inventory_cartridge");
    /** Shop Cartridge: a shop stop (a check point pauses the passing token, a tile opens the shop on landing). */
    public static final Item SHOP_CARTRIDGE = register(fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem.class, "shop_cartridge");
    public static final Item ADVANCE_BACK_CARTRIDGE = register(fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem.class, "advance_back_cartridge");
    /** Its tile's face is cyan until dyed. */
    public static final Item REPLAY_CARTRIDGE = register(fr.lordfinn.steveparty.items.custom.cartridges.ReplayCartridgeItem.class, "replay_cartridge",
            new Item.Settings().component(fr.lordfinn.steveparty.components.ModComponents.COLOR,
                    fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ReplayBoardSpaceBehavior.COLOR));
    public static final Item TELEPORT_CARTRIDGE = register(TeleportCartridgeItem.class, "teleport_cartridge");
    public static final Item MINI_GAME_PAGE = register(MiniGamePageItem.class, "mini_game_page");
    public static final Item SHOPKEEPER_KEY = registerUnstackable(ShopkeeperKeyItem.class, "shopkeeper_key");
    public static final Item FLAG = register(FlagItem.class, "flag");
    public static final TripleJumpShoesItem TRIPLE_JUMP_SHOES = register(TripleJumpShoesItem.class, "triple_jump_shoes");
    public static final Item MULA_SPAWN_EGG = register(MulaSpawnEggItem.class, "mula_spawn_egg");
    public static final Item BOXED_TRADER_SPAWN_EGG = register(fr.lordfinn.steveparty.items.custom.BoxedTraderSpawnEggItem.class, "boxed_trader_spawn_egg");
    public static final Item BLUE_STAR_FRAGMENT = register(Item.class, "blue_star_fragment");
    public static final Item PURPLE_STAR_FRAGMENT = register(Item.class, "purple_star_fragment");
    public static final Item RED_STAR_FRAGMENT = register(Item.class, "red_star_fragment");
    public static final Item YELLOW_STAR_FRAGMENT = register(Item.class, "yellow_star_fragment");
    public static final Item GREEN_STAR_FRAGMENT = register(Item.class, "green_star_fragment");
    public static final Item BLACK_STAR_FRAGMENT = register(Item.class, "black_star_fragment");
    public static final Item WHITE_STAR_FRAGMENT = register(Item.class, "white_star_fragment");
    public static final Item ORANGE_STAR_FRAGMENT = register(Item.class, "orange_star_fragment");
    public static final Item MAGENTA_STAR_FRAGMENT = register(Item.class, "magenta_star_fragment");
    public static final Item LIGHT_BLUE_STAR_FRAGMENT = register(Item.class, "light_blue_star_fragment");
    public static final Item LIME_STAR_FRAGMENT = register(Item.class, "lime_star_fragment");
    public static final Item PINK_STAR_FRAGMENT = register(Item.class, "pink_star_fragment");
    public static final Item GRAY_STAR_FRAGMENT = register(Item.class, "gray_star_fragment");
    public static final Item LIGHT_GRAY_STAR_FRAGMENT = register(Item.class, "light_gray_star_fragment");
    public static final Item CYAN_STAR_FRAGMENT = register(Item.class, "cyan_star_fragment");
    public static final Item BROWN_STAR_FRAGMENT = register(Item.class, "brown_star_fragment");
    /**
     * The 16 star fragments in dye order (index = DyeColor id). Blue, purple, red, yellow, green and black are dropped
     * by the Mulas; the 10 others are crafted by mixing fragments, like dyes (white = red + blue + yellow).
     */
    public static final List<Item> STAR_FRAGMENTS = List.of(
            WHITE_STAR_FRAGMENT, ORANGE_STAR_FRAGMENT, MAGENTA_STAR_FRAGMENT, LIGHT_BLUE_STAR_FRAGMENT,
            YELLOW_STAR_FRAGMENT, LIME_STAR_FRAGMENT, PINK_STAR_FRAGMENT, GRAY_STAR_FRAGMENT,
            LIGHT_GRAY_STAR_FRAGMENT, CYAN_STAR_FRAGMENT, PURPLE_STAR_FRAGMENT, BLUE_STAR_FRAGMENT,
            BROWN_STAR_FRAGMENT, GREEN_STAR_FRAGMENT, RED_STAR_FRAGMENT, BLACK_STAR_FRAGMENT);

    public static Item starFragment(DyeColor dye) {
        return STAR_FRAGMENTS.get(dye.getId());
    }

    public static boolean isStarFragment(Item item) {
        return STAR_FRAGMENTS.contains(item);
    }
    public static final Item POWER_STAR = register(PowerStarItem.class, "power_star");
    /** The coin: the default Pièce currency of a party, minted from a gold nugget. */
    public static final Item COIN = register(Item.class, "coin");
    public static final Item PLASTIC_PELLETS = register(Item.class, "plastic_pellets");
    /** Plastic sticks: the plastic fences are made of them, like wooden fences of sticks. */
    public static final Item PLASTIC_STICK = register(Item.class, "plastic_stick");
    /** The Boxed Trader's bandana (stolen with shears), wearable on the head. */
    public static final Item BANDANA = register(BandanaItem.class, "bandana", new Item.Settings().maxCount(1));
    public static final Item BOX_COSTUME = register(fr.lordfinn.steveparty.items.custom.BoxCostumeItem.class, "box_costume", new Item.Settings().maxCount(1));
    public static final Item PARTY_CARD_TURNS = registerCard(PartyCardItem.CardType.TURNS);
    public static final Item PARTY_CARD_MINIGAME = registerCard(PartyCardItem.CardType.MINIGAME);
    public static final Item PARTY_CARD_EVENT = registerCard(PartyCardItem.CardType.EVENT);
    public static final Item PARTY_CARD_REPEAT = registerCard(PartyCardItem.CardType.REPEAT);
    public static final RegistryKey<ItemGroup> CUSTOM_ITEM_GROUP_KEY = RegistryKey.of(Registries.ITEM_GROUP.getKey(), Identifier.of(MOD_ID, "item_group"));
    public static final ItemGroup CUSTOM_ITEM_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(PARTY_CONTROLLER))
            .displayName(Text.translatable("itemgroup.steveparty"))
            .build();


    /**
     * Registers a stackable item. Items whose state lives in immutable data components (cartridges, stencils,
     * mini game pages...) may stack: only identical stacks (same components) merge, so no state is lost.
     */
    public static <T extends Item> T register(Class<T> itemClass, String id) {
        return register(itemClass, id, new Item.Settings());
    }

    /**
     * Registers an item whose per-stack state is edited in place over time (catalogue, books, wrench, wand...):
     * it must not stack, otherwise editing one item would silently edit the whole stack.
     */
    public static <T extends Item> T registerUnstackable(Class<T> itemClass, String id) {
        return register(itemClass, id, new Item.Settings().maxCount(1));
    }

    public static <T extends Item> T register(Class<T> itemClass, String id, Item.Settings settings) {
        try {
            T item = itemClass.getConstructor(Item.Settings.class).newInstance(getSettings(settings, id));
            Identifier itemID = Steveparty.id(id);
            RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, itemID);
            Registry.register(Registries.ITEM, key, item);
            return item;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create and register item: " + itemClass, e);
        }
    }

    private static Item registerCard(PartyCardItem.CardType type) {
        String id = "party_card_" + type.getName();
        Item item = new PartyCardItem(type, getSettings(new Item.Settings(), id));
        return Registry.register(Registries.ITEM, RegistryKey.of(RegistryKeys.ITEM, Steveparty.id(id)), item);
    }

    public static Item.Settings getSettings(Item.Settings itemSettings, String id) {
        Identifier itemID = Steveparty.id(id);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, itemID);
        return itemSettings.registryKey(key);

    }

    /** Blocks of a sign material kind, from the tags of the running game (modded blocks included). */
    private static List<Block> blocksOf(RegistryWrapper.WrapperLookup lookup, SignMaterial kind) {
        List<Block> blocks = new ArrayList<>();
        lookup.getOrThrow(RegistryKeys.BLOCK).getOptional(kind.tag())
                .ifPresent(list -> list.forEach(entry -> blocks.add(entry.value())));
        if (blocks.isEmpty()) blocks.add(kind.defaultBlock());
        return blocks;
    }

    public static void initialize() {
        // Register the group.
        Registry.register(Registries.ITEM_GROUP, CUSTOM_ITEM_GROUP_KEY, CUSTOM_ITEM_GROUP);

        // The dice faces, the blank one first, then kind by kind in the order of their values:
        // numbers (0 to 10), premium, cursed, coins (+1 to +10), debts (-1 to -10), swap
        DICE_FACES.add(register(Item.class, "blank_dice_face"));
        for (int i = 0; i <= 10; i++) DICE_FACES.add(register(Item.class, "dice_face_" + i));
        for (int i = 1; i <= 10; i++) DICE_FACES.add(register(Item.class, "premium_dice_face_" + i));
        for (int i = 1; i <= 3; i++) DICE_FACES.add(register(Item.class, "cursed_dice_face_" + i));
        for (int i = 1; i <= fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace.MAX_COINS; i++)
            DICE_FACES.add(register(Item.class, "coin_dice_face_" + i));
        for (int i = 1; i <= fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace.MAX_COINS; i++)
            DICE_FACES.add(register(Item.class, "debt_dice_face_" + i));
        DICE_FACES.add(register(Item.class, "swap_dice_face"));

        // One item per dice module (see DiceModules)
        for (fr.lordfinn.steveparty.dice.DiceModule module : fr.lordfinn.steveparty.dice.DiceModules.all()) {
            Item item = new fr.lordfinn.steveparty.items.custom.DiceModuleItem(module, getSettings(new Item.Settings(), module.itemPath()));
            DICE_MODULES.add(Registry.register(Registries.ITEM, RegistryKey.of(RegistryKeys.ITEM, Steveparty.id(module.itemPath())), item));
        }

        // Register items to the custom item group.
        ItemGroupEvents.modifyEntriesEvent(CUSTOM_ITEM_GROUP_KEY).register(itemGroup -> {
            for (int color = 0; color < BandanaItem.COLOR_NAMES.length; color++) {
                itemGroup.add(BandanaItem.create(color));
            }
            itemGroup.add(new ItemStack(BOX_COSTUME));
            // Every tile in its 3 sizes
            for (net.minecraft.item.ItemConvertible tile : List.of(TILE, ADVANCED_TILE)) {
                for (fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize size : fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize.values()) {
                    itemGroup.add(fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize.with(new ItemStack(tile), size));
                }
            }
            itemGroup.add(CHECK_POINT);
            itemGroup.add(BOARD_SPACE_REDSTONE_ROUTER);
            itemGroup.add(WRENCH);
            itemGroup.add(BOARD_SPACE_BEHAVIOR);
            itemGroup.add(BOARD_SPACE_BEHAVIOR_STOP);
            itemGroup.add(TILE_BEHAVIOR_START);
            itemGroup.add(INVENTORY_CARTRIDGE);
            itemGroup.add(SHOP_CARTRIDGE);
            itemGroup.add(ADVANCE_BACK_CARTRIDGE);
            itemGroup.add(fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem.withSteps(-3));
            itemGroup.add(REPLAY_CARTRIDGE);
            itemGroup.add(TELEPORT_CARTRIDGE);
            for (fr.lordfinn.steveparty.components.TeleportNetwork network : fr.lordfinn.steveparty.components.TeleportNetwork.values()) {
                if (network == fr.lordfinn.steveparty.components.TeleportNetwork.VIOLET) continue;
                itemGroup.add(fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem.withNetwork(new ItemStack(TELEPORT_CARTRIDGE), network));
            }
            itemGroup.add(TOKENIZER_WAND);
            itemGroup.add(TOKEN);
            itemGroup.add(PLUNGER);
            itemGroup.add(GARNET_CRYSTAL_BALL);
            itemGroup.add(PARTY_CONTROLLER);
            itemGroup.add(STEP_CONTROLLER);
            itemGroup.add(MINI_GAME_CONTROLLER);
            itemGroup.add(PARTY_CARD_TURNS);
            itemGroup.add(PARTY_CARD_MINIGAME);
            itemGroup.add(PARTY_CARD_EVENT);
            itemGroup.add(PARTY_CARD_REPEAT);
            itemGroup.add(PARTY_BELL);
            itemGroup.add(GOLD_PODIUM);
            itemGroup.add(SILVER_PODIUM);
            itemGroup.add(BRONZE_PODIUM);
            itemGroup.add(PODIUM);
            itemGroup.add(PIGGY_BANK);
            itemGroup.add(MINI_GAMES_CATALOGUE);
            itemGroup.add(MINI_GAME_PAGE);
            itemGroup.add(VILLAGER_BLOCK);
            itemGroup.add(TRADING_STALL);
            itemGroup.add(CASH_REGISTER);
            itemGroup.add(SHOPKEEPER_KEY);
            // The 10 fixed-wood easel signs stay in the game for the worlds that have them, but are no longer
            // listed: the material easel sign covers every planks, modded ones included
            RegistryWrapper.WrapperLookup lookup = itemGroup.getContext().lookup();
            for (Block planks : blocksOf(lookup, SignMaterial.WOOD)) {
                itemGroup.add(MaterialSignItems.withMaterial(EASEL_SIGN, planks));
            }
            for (Block planks : blocksOf(lookup, SignMaterial.WOOD)) {
                itemGroup.add(MaterialSignItems.withMaterial(WOODEN_PANEL, planks));
            }
            for (Block planks : blocksOf(lookup, SignMaterial.WOOD)) {
                itemGroup.add(MaterialSignItems.withMaterial(WOODEN_CUTOUT_PANEL, planks));
            }
            for (Block rock : blocksOf(lookup, SignMaterial.ROCK)) {
                itemGroup.add(MaterialSignItems.withMaterial(ROCK_SIGN, rock));
            }
            for (PlasticRoadSignBlock.Plate plate : PlasticRoadSignBlock.Plate.values()) {
                ItemStack sign = MaterialSignItems.withPlateColor(PLASTIC_ROAD_SIGN, plate == PlasticRoadSignBlock.Plate.ROUND ? DyeColor.RED : DyeColor.YELLOW);
                sign.set(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT.with(PlasticRoadSignBlock.PLATE, plate));
                itemGroup.add(sign);
            }
            for (DyeColor color : DyeColor.values()) {
                itemGroup.add(MaterialSignItems.withPlateColor(PLASTIC_ROAD_SIGN, color));
            }
            itemGroup.add(STENCIL);
            for (StencilPatterns.Pattern pattern : StencilPatterns.all()) {
                itemGroup.add(StencilItem.of(pattern));
            }
            itemGroup.add(STENCIL_GUN);
            itemGroup.add(STENCIL_MAKER);
            itemGroup.add(HOP_SWITCH);
            itemGroup.add(PLASTIC_PELLETS);
            itemGroup.add(PLASTIC_STICK);
            for (Block plasticBlock : ModBlocks.PLASTIC_BLOCKS) {
                itemGroup.add(plasticBlock);
            }
            for (Block stud : ModBlocks.PLASTIC_STUDS) {
                itemGroup.add(stud);
            }
            for (Block fence : ModBlocks.PLASTIC_FENCES) {
                itemGroup.add(fence);
            }
            for (Block[] shapes : new Block[][]{ModBlocks.PLASTIC_SLABS, ModBlocks.PLASTIC_STAIRS, ModBlocks.PLASTIC_WALLS}) {
                for (Block shape : shapes) itemGroup.add(shape);
            }
            for (Block[] pipes : ModBlocks.PIPES) {
                for (Block pipe : pipes) itemGroup.add(pipe);
            }
            itemGroup.add(GOAL_POLE_BASE);
            itemGroup.add(GOAL_POLE);
            itemGroup.add(FLAG);
            itemGroup.add(TRIPLE_JUMP_SHOES);

            for (Block polishedTerracottaBlock : POLISHED_TERRACOTTA_BLOCKS) {
                itemGroup.add(polishedTerracottaBlock);
            }
            for (Block polishedTerracottaBricksBlock : POLISHED_TERRACOTTA_BRICKS_BLOCKS) {
                itemGroup.add(polishedTerracottaBricksBlock);
            }

            for (Block block : ModBlocks.POLISHED_TERRACOTTA_STAIRS) {
                itemGroup.add(block);
            }
            for (Block block : ModBlocks.POLISHED_TERRACOTTA_SLABS) {
                itemGroup.add(block);
            }
            for (Block block : ModBlocks.POLISHED_TERRACOTTA_WALLS) {
                itemGroup.add(block);
            }

            for (Block block : ModBlocks.POLISHED_TERRACOTTA_BRICKS_STAIRS) {
                itemGroup.add(block);
            }
            for (Block block : ModBlocks.POLISHED_TERRACOTTA_BRICKS_SLABS) {
                itemGroup.add(block);
            }
            for (Block block : ModBlocks.POLISHED_TERRACOTTA_BRICKS_WALLS) {
                itemGroup.add(block);
            }

            for (Block[] blocks : new Block[][]{
                    ModBlocks.POLISHED_CONCRETE_BLOCKS, ModBlocks.POLISHED_CONCRETE_BRICKS_BLOCKS,
                    ModBlocks.POLISHED_CONCRETE_STAIRS, ModBlocks.POLISHED_CONCRETE_SLABS, ModBlocks.POLISHED_CONCRETE_WALLS,
                    ModBlocks.POLISHED_CONCRETE_BRICKS_STAIRS, ModBlocks.POLISHED_CONCRETE_BRICKS_SLABS, ModBlocks.POLISHED_CONCRETE_BRICKS_WALLS}) {
                for (Block block : blocks) {
                    itemGroup.add(block);
                }
            }
            for (fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesBlock tiles : ModBlocks.POLISHED_TILES) {
                for (ItemStack stack : tiles.creativeStacks()) itemGroup.add(stack);
            }
            itemGroup.add(LOOTING_BOX);
            itemGroup.add(MULA_SPAWN_EGG);
            itemGroup.add(BOXED_TRADER_SPAWN_EGG);
            // The 16 colours, in dye order: the blocks, then the fragments
            for (Block block : STAR_FRAGMENTS_BLOCKS) itemGroup.add(block);
            for (Item fragment : STAR_FRAGMENTS) itemGroup.add(fragment);
            itemGroup.add(POWER_STAR);
            itemGroup.add(COIN);
            itemGroup.add(GRAVITY_CORE);
            itemGroup.add(DICE_FORGE);
            itemGroup.add(TELESCOPE);
            for (Item item : DICE_FACES) {
                itemGroup.add(item);
            }
            for (Item item : DICE_MODULES) {
                itemGroup.add(item);
            }
            itemGroup.add(DEFAULT_DICE);
            itemGroup.add(DOUBLE_DICE);
            itemGroup.add(TRIPLE_DICE);
        });
    }
}
