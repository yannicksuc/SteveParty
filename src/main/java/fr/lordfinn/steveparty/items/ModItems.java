package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleVariant;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.FrousseuxCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.GlandouilleCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.KeyGateCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.MistigriCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ReplayCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.StarCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ThresholdCartridgeItem;
import fr.lordfinn.steveparty.items.custom.glandouille.AcornHatItem;
import fr.lordfinn.steveparty.powerups.PowerUp;
import fr.lordfinn.steveparty.powerups.PowerUps;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.AliasedBlockItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.DyeColor;
import fr.lordfinn.steveparty.items.custom.*;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.StartCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.StopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static fr.lordfinn.steveparty.blocks.ModBlocks.*;

public class ModItems {
    public static final Item DOUBLE_DICE = register("double_dice", DoubleDiceItem::new);

    public static final Item STENCIL = register("stencil", StencilItem::new);
    public static final Item STENCIL_GUN = registerUnstackable("stencil_gun", StencilGunItem::new);
    public static final Item WRENCH = registerUnstackable("wrench", WrenchItem::new);
    public static final Item TILE_LINKER_BRUSH = registerUnstackable("tile_linker_brush", TileLinkerBrushItem::new);
    /** Worn on the head, its lamp lit: the board view at all times, with the details of each space (see ExplorerHelmet). */
    public static final Item EXPLORER_HELMET = registerUnstackable("explorer_helmet", ExplorerHelmetItem::new);
    public static final Item BOARD_SPACE_BEHAVIOR = register("board_space_behavior", CartridgeItem::new);
    public static final Item TILE_BEHAVIOR_START = register("tile_behavior_start", StartCartridgeItem::new);
    /** Its tile's face is anthracite until dyed. */
    public static final Item BOARD_SPACE_BEHAVIOR_STOP = register("board_space_behavior_stop", StopCartridgeItem::new,
            new Item.Settings().component(ModComponents.COLOR,
                    StopCartridgeItem.COLOR));
    public static final Item TOKENIZER_WAND = register("tokenizer_wand", TokenizerWandItem::new, new Item.Settings().maxCount(1));
    public static final Item PLUNGER = register("plunger", PlungerItem::new);
    public static final Item DEFAULT_DICE = register("default_dice", DefaultDiceItem::new);
    public static final Item TRIPLE_DICE = register("triple_dice", TripleDiceItem::new);
    /** The dice faces: the blank one first ({@link #blankDiceFace}), then kind by kind. */
    public static final List<Item> DICE_FACES = new ArrayList<>();
    /** The dice module items, one per module of DiceModules. */
    public static final List<Item> DICE_MODULES = new ArrayList<>();
    /** The power-up items, one per power-up of PowerUps. */
    public static final List<Item> POWER_UPS = new ArrayList<>();
    /**
     * Every Steve Party spawn egg ({@link #spawnEgg}), in registration order. They are all drawn per mob
     * (textures/item/*_spawn_egg.png): the client registers them untinted at once.
     */
    public static final List<Item> SPAWN_EGGS = new ArrayList<>();
    /** The base every dice module is crafted from, with seven star fragments of the module's colour. */
    public static final Item BLANK_DICE_MODULE = register("blank_dice_module", Item::new);

    public static Item blankDiceFace() {
        return DICE_FACES.getFirst();
    }
    public static final Item LAPIS_CRYSTAL_BALL = register("lapis_crystal_ball", LapisCrystalBallItem::new);
    public static final Item MINI_GAMES_CATALOGUE = registerUnstackable("mini_games_catalogue", MiniGamesCatalogueItem::new);
    public static final Item TOKEN = register("token", TokenItem::new);
    public static final Item INVENTORY_CARTRIDGE = register("inventory_cartridge", InventoryCartridgeItem::new);
    /** Shop Cartridge: a shop stop (a check point pauses the passing token, a tile opens the shop on landing). */
    public static final Item SHOP_CARTRIDGE = register("shop_cartridge", ShopCartridgeItem::new,
            new Item.Settings().component(ModComponents.COLOR,
                    ShopCartridgeItem.COLOR));
    public static final Item ADVANCE_BACK_CARTRIDGE = register("advance_back_cartridge", AdvanceBackCartridgeItem::new);
    /** Its tile's face is cyan until dyed. */
    public static final Item REPLAY_CARTRIDGE = register("replay_cartridge", ReplayCartridgeItem::new,
            new Item.Settings().component(ModComponents.COLOR,
                    ReplayCartridgeItem.COLOR));
    public static final Item TELEPORT_CARTRIDGE = register("teleport_cartridge", TeleportCartridgeItem::new);
    /** Star Cartridge: a star space, where the party's star may stand and be bought (yellow, like the star). */
    public static final Item STAR_CARTRIDGE = register("star_cartridge", StarCartridgeItem::new,
            new Item.Settings().component(ModComponents.COLOR,
                    StarCartridgeItem.COLOR));
    /** Its tile's face is brown until dyed; pushes 3 spaces on by default. */
    public static final Item GLANDOUILLE_CARTRIDGE = register("glandouille_cartridge", GlandouilleCartridgeItem::new,
            new Item.Settings().component(ModComponents.COLOR,
                    GlandouilleCartridgeItem.COLOR));
    /** Its tile's face is night indigo until dyed; steals 15 coins by default. */
    public static final Item FROUSSEUX_CARTRIDGE = register("frousseux_cartridge", FrousseuxCartridgeItem::new,
            new Item.Settings().component(ModComponents.COLOR,
                    FrousseuxCartridgeItem.COLOR));
    /** Its tile's face is witch plum until dyed; draws the Mistigri's sentences by their weights. */
    public static final Item MISTIGRI_CARTRIDGE = register("mistigri_cartridge", MistigriCartridgeItem::new,
            new Item.Settings().component(ModComponents.COLOR,
                    MistigriCartridgeItem.COLOR));
    /** Its tile's face is steel blue until dyed; a token reaching it goes on only if its roll meets the condition. */
    public static final Item THRESHOLD_CARTRIDGE = register("threshold_cartridge", ThresholdCartridgeItem::new,
            new Item.Settings().component(ModComponents.COLOR, ThresholdCartridgeItem.COLOR));
    /** Its tile's face is straw until dyed; passing tokens feed its pot, the token stopping on it wins it. */
    public static final Item POT_CARTRIDGE = register("pot_cartridge", PotCartridgeItem::new,
            new Item.Settings().component(ModComponents.COLOR, PotCartridgeItem.COLOR));
    /** The Magpie's nest (ModBlocks.MAGPIE_NEST): a decorative block, a Common pot's nest when set near its space. */
    public static final Item MAGPIE_NEST = ModBlocks.MAGPIE_NEST.asItem();
    /** Its tile's face and its gates are teal until dyed; a token leaving it by a locked exit needs a Gate Key. */
    public static final Item KEY_GATE_CARTRIDGE = register("key_gate_cartridge", KeyGateCartridgeItem::new,
            new Item.Settings().component(ModComponents.COLOR, KeyGateCartridgeItem.COLOR));
    /** The Gate Key: opens a Key gate on the board, used up (KeyGates). */
    public static final Item GATE_KEY = register("gate_key", GateKeyItem::new,
            new Item.Settings().maxCount(16));
    public static final Item MINI_GAME_PAGE = register("mini_game_page", MiniGamePageItem::new);
    public static final Item MINI_GAME_REMOTE = registerUnstackable("mini_game_remote", MiniGameRemoteItem::new);
    public static final Item SHOPKEEPER_KEY = registerUnstackable("shopkeeper_key", ShopkeeperKeyItem::new);
    public static final Item FLAG = register("flag", FlagItem::new);
    public static final TripleJumpShoesItem TRIPLE_JUMP_SHOES = register("triple_jump_shoes", TripleJumpShoesItem::new);
    public static final Item MULA_SPAWN_EGG = spawnEgg("mula_spawn_egg", ModEntities.MULA_ENTITY,
            0x77c7fc, 0x2486c7);
    /** Spawns a Boxed Trader in a random box (see BoxedTraderBoxes). */
    public static final Item BOXED_TRADER_SPAWN_EGG = spawnEgg("boxed_trader_spawn_egg",
            ModEntities.BOXED_TRADER_ENTITY, 0xB8945F, 0x3AAFA9);
    // The Glandouille: its acorn (planted on farmland, it hatches into a young one; eaten, half a drumstick), its lost
    // cap (worn on the head), a spawn egg per variant (the egg sets the variant)
    public static final Item ACORN = Registry.register(Registries.ITEM, Steveparty.id("acorn"),
            new AliasedBlockItem(ModBlocks.ACORN_CROP, new Item.Settings()
                    .food(new FoodComponent.Builder().nutrition(1).saturationModifier(0.1f).build())));
    public static final Item ACORN_HAT = registerUnstackable("acorn_hat", AcornHatItem::new);
    public static final Item GLANDOUILLE_SPAWN_EGG = glandouilleEgg("glandouille_spawn_egg",
            GlandouilleVariant.CLASSIC, 0xB86C30, 0x5A3A22);
    public static final Item YOUNG_GLANDOUILLE_SPAWN_EGG = glandouilleEgg("young_glandouille_spawn_egg",
            GlandouilleVariant.YOUNG, 0x8DB84A, 0x4E6B2A);
    public static final Item MOSSY_GLANDOUILLE_SPAWN_EGG = glandouilleEgg("mossy_glandouille_spawn_egg",
            GlandouilleVariant.MOSSY, 0x7A5A3A, 0x5E8E38);
    public static final Item FROSTY_GLANDOUILLE_SPAWN_EGG = glandouilleEgg("frosty_glandouille_spawn_egg",
            GlandouilleVariant.FROSTY, 0xA8BCD8, 0xF4F8FF);
    /** The Frousseux's egg: a colour drawn at random, as a wild one's. */
    public static final Item FROUSSEUX_SPAWN_EGG = spawnEgg("frousseux_spawn_egg",
            ModEntities.FROUSSEUX, 0xFFF3D6, 0xFFAA3C);
    /** The Mistigri's egg: drawn per mob (textures/item/mistigri_spawn_egg.png). */
    public static final Item MISTIGRI_SPAWN_EGG = spawnEgg("mistigri_spawn_egg",
            ModEntities.MISTIGRI, 0x14111A, 0x9BCB2C);
    /** The Boomcart's egg: iron grey, TNT red. */
    public static final Item BOOMCART_SPAWN_EGG = spawnEgg("boomcart_spawn_egg",
            ModEntities.BOOMCART, 0x8C8C90, 0xDB2F1F);
    /** The Trichaudron's egg: drawn per mob (textures/item/trichaudron_spawn_egg.png), basalt and lava. */
    public static final Item TRICHAUDRON_SPAWN_EGG = spawnEgg("trichaudron_spawn_egg",
            ModEntities.TRICHAUDRON, 0x3A2A28, 0xE0601C);
    /** The wild Pie's egg: a colour drawn at random (textures/item/magpie_spawn_egg.png). */
    public static final Item MAGPIE_SPAWN_EGG = spawnEgg("magpie_spawn_egg",
            ModEntities.WILD_MAGPIE, 0x1C1E29, 0xE9E7E1);
    /**
     * The Candle Saucer, a little gold tray for the Frousseux candle holder to stand on: a block of its own
     * (ModBlocks.CANDLE_SAUCER), crafted onto the candle holder too (CandleSaucerRecipe).
     */
    public static final Item CANDLE_SAUCER = ModBlocks.CANDLE_SAUCER.asItem();
    public static final Item[] GLANDOUILLE_SPAWN_EGGS = {GLANDOUILLE_SPAWN_EGG, YOUNG_GLANDOUILLE_SPAWN_EGG,
            MOSSY_GLANDOUILLE_SPAWN_EGG, FROSTY_GLANDOUILLE_SPAWN_EGG};
    public static final Item BLUE_STAR_FRAGMENT = register("blue_star_fragment", Item::new);
    public static final Item PURPLE_STAR_FRAGMENT = register("purple_star_fragment", Item::new);
    public static final Item RED_STAR_FRAGMENT = register("red_star_fragment", Item::new);
    public static final Item YELLOW_STAR_FRAGMENT = register("yellow_star_fragment", Item::new);
    public static final Item GREEN_STAR_FRAGMENT = register("green_star_fragment", Item::new);
    public static final Item BLACK_STAR_FRAGMENT = register("black_star_fragment", Item::new);
    public static final Item WHITE_STAR_FRAGMENT = register("white_star_fragment", Item::new);
    public static final Item ORANGE_STAR_FRAGMENT = register("orange_star_fragment", Item::new);
    public static final Item MAGENTA_STAR_FRAGMENT = register("magenta_star_fragment", Item::new);
    public static final Item LIGHT_BLUE_STAR_FRAGMENT = register("light_blue_star_fragment", Item::new);
    public static final Item LIME_STAR_FRAGMENT = register("lime_star_fragment", Item::new);
    public static final Item PINK_STAR_FRAGMENT = register("pink_star_fragment", Item::new);
    public static final Item GRAY_STAR_FRAGMENT = register("gray_star_fragment", Item::new);
    public static final Item LIGHT_GRAY_STAR_FRAGMENT = register("light_gray_star_fragment", Item::new);
    public static final Item CYAN_STAR_FRAGMENT = register("cyan_star_fragment", Item::new);
    public static final Item BROWN_STAR_FRAGMENT = register("brown_star_fragment", Item::new);
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
    public static final Item PARTY_STAR = register("party_star", PartyStarItem::new);
    /** The coin: the default Pièce currency of a party, minted from a gold nugget. */
    public static final Item COIN = register("coin", Item::new);
    public static final Item PLASTIC_PELLETS = register("plastic_pellets", Item::new);
    /** Plastic sticks: the plastic fences are made of them, like wooden fences of sticks. */
    public static final Item PLASTIC_STICK = register("plastic_stick", Item::new);
    /** The Boxed Trader's bandana (stolen with shears), wearable on the head. */
    public static final Item BANDANA = register("bandana", BandanaItem::new, new Item.Settings().maxCount(1));
    public static final Item BOX_COSTUME = register("box_costume", BoxCostumeItem::new, new Item.Settings().maxCount(1));
    public static final Item PARTY_CARD_TURNS = registerCard(PartyCardItem.CardType.TURNS);
    public static final Item PARTY_CARD_MINIGAME = registerCard(PartyCardItem.CardType.MINIGAME);
    public static final Item PARTY_CARD_EVENT = registerCard(PartyCardItem.CardType.EVENT);
    public static final Item PARTY_CARD_REPEAT = registerCard(PartyCardItem.CardType.REPEAT);
    public static final Item PARTY_CARD_SEQUENCE_START = registerCard(PartyCardItem.CardType.SEQUENCE_START);


    /**
     * Registers a stackable item. Items whose state lives in immutable data components (cartridges, stencils,
     * mini game pages...) may stack: only identical stacks (same components) merge, so no state is lost.
     */
    public static <T extends Item> T register(String id, Function<Item.Settings, T> factory) {
        return register(id, factory, new Item.Settings());
    }

    /**
     * Registers an item whose per-stack state is edited in place over time (catalogue, books, wrench, wand...):
     * it must not stack, otherwise editing one item would silently edit the whole stack.
     */
    public static <T extends Item> T registerUnstackable(String id, Function<Item.Settings, T> factory) {
        return register(id, factory, new Item.Settings().maxCount(1));
    }

    /**
     * Registers a spawn egg of {@code type} and lists it in {@link #SPAWN_EGGS} (drawn untinted). Its two colours stay
     * the egg's colours for what reads them (e.g. MobTextureColors), not for its icon.
     */
    public static Item spawnEgg(String id, EntityType<? extends MobEntity> type, int primary, int secondary) {
        return spawnEgg(id, type, primary, secondary, new Item.Settings());
    }

    private static Item spawnEgg(String id, EntityType<? extends MobEntity> type, int primary, int secondary,
                                 Item.Settings settings) {
        Item egg = Registry.register(Registries.ITEM, Steveparty.id(id), new SpawnEggItem(type, primary, secondary, settings));
        SPAWN_EGGS.add(egg);
        return egg;
    }

    /** A Glandouille spawn egg giving {@code variant} (its entity data), in its two colours. */
    private static Item glandouilleEgg(String id, GlandouilleVariant variant,
                                       int primary, int secondary) {
        NbtCompound data = new NbtCompound();
        data.putString("id", ModEntities.GLANDOUILLE_KEY.getValue().toString());
        data.putInt("Variant", variant.ordinal());
        return spawnEgg(id, ModEntities.GLANDOUILLE, primary, secondary,
                new Item.Settings().component(DataComponentTypes.ENTITY_DATA,
                        NbtComponent.of(data)));
    }

    public static <T extends Item> T register(String id, Function<Item.Settings, T> factory, Item.Settings settings) {
        T item = factory.apply(settings);
        Registry.register(Registries.ITEM, RegistryKey.of(RegistryKeys.ITEM, Steveparty.id(id)), item);
        return item;
    }

    private static Item registerCard(PartyCardItem.CardType type) {
        String id = "party_card_" + type.getName();
        Item item = new PartyCardItem(type, new Item.Settings());
        return Registry.register(Registries.ITEM, RegistryKey.of(RegistryKeys.ITEM, Steveparty.id(id)), item);
    }

    public static void initialize() {
        // The dice faces, the blank one first, then kind by kind in the order of their values:
        // numbers (0 to 10), premium, cursed, coins (+1 to +10), debts (-1 to -10), swap
        DICE_FACES.add(register("blank_dice_face", Item::new));
        for (int i = 0; i <= 10; i++) DICE_FACES.add(register("dice_face_" + i, Item::new));
        for (int i = 1; i <= 10; i++) DICE_FACES.add(register("premium_dice_face_" + i, Item::new));
        for (int i = 1; i <= 3; i++) DICE_FACES.add(register("cursed_dice_face_" + i, Item::new));
        for (int i = 1; i <= DiceFacesComponent.DiceFace.MAX_COINS; i++)
            DICE_FACES.add(register("coin_dice_face_" + i, Item::new));
        for (int i = 1; i <= DiceFacesComponent.DiceFace.MAX_COINS; i++)
            DICE_FACES.add(register("debt_dice_face_" + i, Item::new));
        DICE_FACES.add(register("swap_dice_face", Item::new));

        // One item per dice module (see DiceModules)
        for (DiceModule module : DiceModules.all()) {
            Item item = new DiceModuleItem(module, new Item.Settings());
            DICE_MODULES.add(Registry.register(Registries.ITEM, RegistryKey.of(RegistryKeys.ITEM, Steveparty.id(module.itemPath())), item));
        }

        // One item per power-up (see PowerUps)
        for (PowerUp powerUp : PowerUps.all()) {
            Item item = powerUp.createItem(new Item.Settings());
            POWER_UPS.add(Registry.register(Registries.ITEM, RegistryKey.of(RegistryKeys.ITEM, Steveparty.id(powerUp.itemPath())), item));
        }

        // The creative tabs
        ModItemGroups.initialize();
    }
}
