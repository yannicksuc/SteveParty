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
import fr.lordfinn.steveparty.items.custom.teleportation_books.HereWeComeBookItem;
import fr.lordfinn.steveparty.items.custom.teleportation_books.HereWeGoBookItem;
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
    public static final Item BOARD_SPACE_BEHAVIOR_STOP = register(StopCartridgeItem.class, "board_space_behavior_stop");
    public static final Item TOKENIZER_WAND = register(TokenizerWandItem.class, "tokenizer_wand", new Item.Settings().maxCount(1).enchantable(TOKENIZER_WAND_ENCHANTABILITY));
    public static final Item PLUNGER = register(PlungerItem.class, "plunger");
    public static final Item DEFAULT_DICE = register(DefaultDiceItem.class,"default_dice");
    public static final Item TRIPLE_DICE = register(TripleDiceItem.class, "triple_dice");
    public static final List<Item> DICE_FACES = new ArrayList<>();
    public static final Item GARNET_CRYSTAL_BALL = register(GarnetCrystalBallItem.class,"garnet_crystal_ball");
    public static final Item MINI_GAMES_CATALOGUE = registerUnstackable(MiniGamesCatalogueItem.class,"mini_games_catalogue");
    public static final Item TOKEN = register(TokenItem.class, "token");
    public static final Item INVENTORY_CARTRIDGE = register(InventoryCartridgeItem.class, "inventory_cartridge");
    public static final Item MINI_GAME_PAGE = register(MiniGamePageItem.class, "mini_game_page");
    public static final Item HERE_WE_GO_BOOK = registerUnstackable(HereWeGoBookItem.class, "here_we_go_book");
    public static final Item HERE_WE_COME_BOOK = registerUnstackable(HereWeComeBookItem.class, "here_we_come_book");
    public static final Item SHOPKEEPER_KEY = registerUnstackable(ShopkeeperKeyItem.class, "shopkeeper_key");
    public static final Item FLAG = register(FlagItem.class, "flag");
    public static final TripleJumpShoesItem TRIPLE_JUMP_SHOES = register(TripleJumpShoesItem.class, "triple_jump_shoes");
    public static final Item MULA_SPAWN_EGG = register(MulaSpawnEggItem.class, "mula_spawn_egg");
    public static final Item BLUE_STAR_FRAGMENT = register(Item.class, "blue_star_fragment");
    public static final Item PURPLE_STAR_FRAGMENT = register(Item.class, "purple_star_fragment");
    public static final Item RED_STAR_FRAGMENT = register(Item.class, "red_star_fragment");
    public static final Item YELLOW_STAR_FRAGMENT = register(Item.class, "yellow_star_fragment");
    public static final Item GREEN_STAR_FRAGMENT = register(Item.class, "green_star_fragment");
    public static final Item BLACK_STAR_FRAGMENT = register(Item.class, "black_star_fragment");
    public static final Item POWER_STAR = register(PowerStarItem.class, "power_star");
    public static final Item PLASTIC_PELLETS = register(Item.class, "plastic_pellets");
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

        //generates dices items face from 1 to 10
        DICE_FACES.add(register(Item.class, "blank_dice_face"));

        for (int i = 1; i <= 10; i++) {
            DICE_FACES.add(register(Item.class, "dice_face_" + i));
            DICE_FACES.add(register(Item.class, "premium_dice_face_" + i));
            if (i <= 3)
                DICE_FACES.add(register(Item.class, "cursed_dice_face_" + i));
        }
        DICE_FACES.sort(
                (item1, item2) -> {
                    String name1 = item1.getName().getString();
                    String name2 = item2.getName().getString();
                    return name1.compareTo(name2);
                }
        );

        // Register items to the custom item group.
        ItemGroupEvents.modifyEntriesEvent(CUSTOM_ITEM_GROUP_KEY).register(itemGroup -> {
            itemGroup.add(SIMPLE_TILE);
            itemGroup.add(TILE);
            itemGroup.add(CHECK_POINT);
            itemGroup.add(BOARD_SPACE_REDSTONE_ROUTER);
            itemGroup.add(WRENCH);
            itemGroup.add(BOARD_SPACE_BEHAVIOR);
            itemGroup.add(BOARD_SPACE_BEHAVIOR_STOP);
            itemGroup.add(TILE_BEHAVIOR_START);
            itemGroup.add(INVENTORY_CARTRIDGE);
            itemGroup.add(TOKENIZER_WAND);
            itemGroup.add(TOKEN);
            itemGroup.add(PLUNGER);
            itemGroup.add(TELEPORTATION_PAD);
            itemGroup.add(HERE_WE_GO_BOOK);
            itemGroup.add(HERE_WE_COME_BOOK);
            itemGroup.add(GARNET_CRYSTAL_BALL);
            itemGroup.add(PARTY_CONTROLLER);
            itemGroup.add(STEP_CONTROLLER);
            itemGroup.add(MINI_GAMES_CATALOGUE);
            itemGroup.add(MINI_GAME_PAGE);
            itemGroup.add(VILLAGER_BLOCK);
            itemGroup.add(TRADING_STALL);
            itemGroup.add(CASH_REGISTER);
            itemGroup.add(SHOPKEEPER_KEY);
            // The 10 fixed-wood traffic signs stay in the game for the worlds that have them, but are no longer
            // listed: the material traffic sign covers every planks, modded ones included
            RegistryWrapper.WrapperLookup lookup = itemGroup.getContext().lookup();
            for (Block planks : blocksOf(lookup, SignMaterial.WOOD)) {
                itemGroup.add(MaterialSignItems.withMaterial(TRAFFIC_SIGN, planks));
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
            for (Block plasticBlock : ModBlocks.PLASTIC_BLOCKS) {
                itemGroup.add(plasticBlock);
            }
            for (Block stud : ModBlocks.PLASTIC_STUDS) {
                itemGroup.add(stud);
            }
            for (Block fence : ModBlocks.PLASTIC_FENCES) {
                itemGroup.add(fence);
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
            itemGroup.add(LOOTING_BOX);
            itemGroup.add(MULA_SPAWN_EGG);
            itemGroup.add(BLUE_STAR_FRAGMENTS_BLOCK);
            itemGroup.add(PURPLE_STAR_FRAGMENTS_BLOCK);
            itemGroup.add(RED_STAR_FRAGMENTS_BLOCK);
            itemGroup.add(YELLOW_STAR_FRAGMENTS_BLOCK);
            itemGroup.add(GREEN_STAR_FRAGMENTS_BLOCK);
            itemGroup.add(BLACK_STAR_FRAGMENTS_BLOCK);
            itemGroup.add(BLUE_STAR_FRAGMENT);
            itemGroup.add(PURPLE_STAR_FRAGMENT);
            itemGroup.add(RED_STAR_FRAGMENT);
            itemGroup.add(YELLOW_STAR_FRAGMENT);
            itemGroup.add(GREEN_STAR_FRAGMENT);
            itemGroup.add(BLACK_STAR_FRAGMENT);
            itemGroup.add(POWER_STAR);
            itemGroup.add(GRAVITY_CORE);
            itemGroup.add(DICE_FORGE);
            for (Item item : DICE_FACES) {
                itemGroup.add(item);
            }
            itemGroup.add(DEFAULT_DICE);
            itemGroup.add(DOUBLE_DICE);
            itemGroup.add(TRIPLE_DICE);
        });
    }
}
