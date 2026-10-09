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
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
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
    public static final Item DOUBLE_DICE = register(DoubleDiceItem.class, "double_dice");

    public static final Item STENCIL = register(StencilItem.class, "stencil");
    public static final Item STENCIL_GUN = registerUnstackable(StencilGunItem.class, "stencil_gun");
    public static final Item WRENCH = registerUnstackable(WrenchItem.class, "wrench");
    public static final Item TILE_LINKER_BRUSH = registerUnstackable(fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem.class, "tile_linker_brush");
    /** Worn on the head, its lamp lit: the board view at all times, with the details of each space (see ExplorerHelmet). */
    public static final Item EXPLORER_HELMET = registerUnstackable(fr.lordfinn.steveparty.items.custom.ExplorerHelmetItem.class, "explorer_helmet");
    public static final Item BOARD_SPACE_BEHAVIOR = register(CartridgeItem.class, "board_space_behavior");
    public static final Item TILE_BEHAVIOR_START = register(StartCartridgeItem.class, "tile_behavior_start");
    /** Its tile's face is anthracite until dyed. */
    public static final Item BOARD_SPACE_BEHAVIOR_STOP = register(StopCartridgeItem.class, "board_space_behavior_stop",
            new Item.Settings().component(fr.lordfinn.steveparty.components.ModComponents.COLOR,
                    fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StopBoardSpaceBehavior.COLOR));
    public static final Item TOKENIZER_WAND = register(TokenizerWandItem.class, "tokenizer_wand", new Item.Settings().maxCount(1));
    public static final Item PLUNGER = register(PlungerItem.class, "plunger");
    public static final Item DEFAULT_DICE = register(DefaultDiceItem.class,"default_dice");
    public static final Item TRIPLE_DICE = register(TripleDiceItem.class, "triple_dice");
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
    public static final Item BLANK_DICE_MODULE = register(Item.class, "blank_dice_module");

    public static Item blankDiceFace() {
        return DICE_FACES.getFirst();
    }
    public static final Item LAPIS_CRYSTAL_BALL = register(LapisCrystalBallItem.class,"lapis_crystal_ball");
    public static final Item MINI_GAMES_CATALOGUE = registerUnstackable(MiniGamesCatalogueItem.class,"mini_games_catalogue");
    public static final Item TOKEN = register(TokenItem.class, "token");
    public static final Item INVENTORY_CARTRIDGE = register(InventoryCartridgeItem.class, "inventory_cartridge");
    /** Shop Cartridge: a shop stop (a check point pauses the passing token, a tile opens the shop on landing). */
    public static final Item SHOP_CARTRIDGE = register(fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem.class, "shop_cartridge",
            new Item.Settings().component(fr.lordfinn.steveparty.components.ModComponents.COLOR,
                    fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem.COLOR));
    public static final Item ADVANCE_BACK_CARTRIDGE = register(fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem.class, "advance_back_cartridge");
    /** Its tile's face is cyan until dyed. */
    public static final Item REPLAY_CARTRIDGE = register(fr.lordfinn.steveparty.items.custom.cartridges.ReplayCartridgeItem.class, "replay_cartridge",
            new Item.Settings().component(fr.lordfinn.steveparty.components.ModComponents.COLOR,
                    fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ReplayBoardSpaceBehavior.COLOR));
    public static final Item TELEPORT_CARTRIDGE = register(TeleportCartridgeItem.class, "teleport_cartridge");
    /** Star Cartridge: a star space, where the party's star may stand and be bought (yellow, like the star). */
    public static final Item STAR_CARTRIDGE = register(fr.lordfinn.steveparty.items.custom.cartridges.StarCartridgeItem.class, "star_cartridge",
            new Item.Settings().component(fr.lordfinn.steveparty.components.ModComponents.COLOR,
                    fr.lordfinn.steveparty.items.custom.cartridges.StarCartridgeItem.COLOR));
    /** Its tile's face is brown until dyed; pushes 3 spaces on by default. */
    public static final Item GLANDOUILLE_CARTRIDGE = register(fr.lordfinn.steveparty.items.custom.cartridges.GlandouilleCartridgeItem.class, "glandouille_cartridge",
            new Item.Settings().component(fr.lordfinn.steveparty.components.ModComponents.COLOR,
                    fr.lordfinn.steveparty.items.custom.cartridges.GlandouilleCartridgeItem.COLOR));
    /** Its tile's face is night indigo until dyed; steals 15 coins by default. */
    public static final Item FROUSSEUX_CARTRIDGE = register(fr.lordfinn.steveparty.items.custom.cartridges.FrousseuxCartridgeItem.class, "frousseux_cartridge",
            new Item.Settings().component(fr.lordfinn.steveparty.components.ModComponents.COLOR,
                    fr.lordfinn.steveparty.items.custom.cartridges.FrousseuxCartridgeItem.COLOR));
    /** Its tile's face is witch plum until dyed; draws the Mistigri's sentences by their weights. */
    public static final Item MISTIGRI_CARTRIDGE = register(fr.lordfinn.steveparty.items.custom.cartridges.MistigriCartridgeItem.class, "mistigri_cartridge",
            new Item.Settings().component(fr.lordfinn.steveparty.components.ModComponents.COLOR,
                    fr.lordfinn.steveparty.items.custom.cartridges.MistigriCartridgeItem.COLOR));
    public static final Item MINI_GAME_PAGE = register(MiniGamePageItem.class, "mini_game_page");
    public static final Item MINI_GAME_REMOTE = registerUnstackable(fr.lordfinn.steveparty.items.custom.MiniGameRemoteItem.class, "mini_game_remote");
    public static final Item SHOPKEEPER_KEY = registerUnstackable(ShopkeeperKeyItem.class, "shopkeeper_key");
    public static final Item FLAG = register(FlagItem.class, "flag");
    public static final TripleJumpShoesItem TRIPLE_JUMP_SHOES = register(TripleJumpShoesItem.class, "triple_jump_shoes");
    public static final Item MULA_SPAWN_EGG = spawnEgg("mula_spawn_egg", fr.lordfinn.steveparty.entities.ModEntities.MULA_ENTITY,
            0x77c7fc, 0x2486c7);
    /** Spawns a Boxed Trader in a random box (see BoxedTraderBoxes). */
    public static final Item BOXED_TRADER_SPAWN_EGG = spawnEgg("boxed_trader_spawn_egg",
            fr.lordfinn.steveparty.entities.ModEntities.BOXED_TRADER_ENTITY, 0xB8945F, 0x3AAFA9);
    // The Glandouille: its acorn (planted on farmland, it hatches into a young one; eaten, half a drumstick), its lost
    // cap (worn on the head), a spawn egg per variant (the egg sets the variant)
    public static final Item ACORN = Registry.register(Registries.ITEM, Steveparty.id("acorn"),
            new net.minecraft.item.AliasedBlockItem(ModBlocks.ACORN_CROP, new Item.Settings()
                    .food(new net.minecraft.component.type.FoodComponent.Builder().nutrition(1).saturationModifier(0.1f).build())));
    public static final Item ACORN_HAT = registerUnstackable(fr.lordfinn.steveparty.items.custom.glandouille.AcornHatItem.class, "acorn_hat");
    public static final Item GLANDOUILLE_SPAWN_EGG = glandouilleEgg("glandouille_spawn_egg",
            fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleVariant.CLASSIC, 0xB86C30, 0x5A3A22);
    public static final Item YOUNG_GLANDOUILLE_SPAWN_EGG = glandouilleEgg("young_glandouille_spawn_egg",
            fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleVariant.YOUNG, 0x8DB84A, 0x4E6B2A);
    public static final Item MOSSY_GLANDOUILLE_SPAWN_EGG = glandouilleEgg("mossy_glandouille_spawn_egg",
            fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleVariant.MOSSY, 0x7A5A3A, 0x5E8E38);
    public static final Item FROSTY_GLANDOUILLE_SPAWN_EGG = glandouilleEgg("frosty_glandouille_spawn_egg",
            fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleVariant.FROSTY, 0xA8BCD8, 0xF4F8FF);
    /** The Frousseux's egg: a colour drawn at random, as a wild one's. */
    public static final Item FROUSSEUX_SPAWN_EGG = spawnEgg("frousseux_spawn_egg",
            fr.lordfinn.steveparty.entities.ModEntities.FROUSSEUX, 0xFFF3D6, 0xFFAA3C);
    /** The Mistigri's egg: drawn per mob (textures/item/mistigri_spawn_egg.png). */
    public static final Item MISTIGRI_SPAWN_EGG = spawnEgg("mistigri_spawn_egg",
            fr.lordfinn.steveparty.entities.ModEntities.MISTIGRI, 0x14111A, 0x9BCB2C);
    /** The Loaded Die, the Mistigri's loot: curses the next roll of the player it is used on (1 to 3 only). */
    public static final Item LOADED_DIE = register(fr.lordfinn.steveparty.items.custom.LoadedDieItem.class, "loaded_die",
            new Item.Settings().maxCount(16));
    /** The Boomcart's egg: iron grey, TNT red. */
    public static final Item BOOMCART_SPAWN_EGG = spawnEgg("boomcart_spawn_egg",
            fr.lordfinn.steveparty.entities.ModEntities.BOOMCART, 0x8C8C90, 0xDB2F1F);
    /** The Fumarole's egg: drawn per mob (textures/item/fumarole_spawn_egg.png), basalt and lava. */
    public static final Item FUMAROLE_SPAWN_EGG = spawnEgg("fumarole_spawn_egg",
            fr.lordfinn.steveparty.entities.ModEntities.FUMAROLE, 0x3A2A28, 0xE0601C);
    /**
     * The Candle Saucer, a little gold tray for the Frousseux candle holder to stand on: a block of its own
     * (ModBlocks.CANDLE_SAUCER), crafted onto the candle holder too (CandleSaucerRecipe).
     */
    public static final Item CANDLE_SAUCER = ModBlocks.CANDLE_SAUCER.asItem();
    public static final Item[] GLANDOUILLE_SPAWN_EGGS = {GLANDOUILLE_SPAWN_EGG, YOUNG_GLANDOUILLE_SPAWN_EGG,
            MOSSY_GLANDOUILLE_SPAWN_EGG, FROSTY_GLANDOUILLE_SPAWN_EGG};
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
    public static final Item PARTY_STAR = register(PartyStarItem.class, "party_star");
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
    public static final Item PARTY_CARD_SEQUENCE_START = registerCard(PartyCardItem.CardType.SEQUENCE_START);
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
    private static Item glandouilleEgg(String id, fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleVariant variant,
                                       int primary, int secondary) {
        net.minecraft.nbt.NbtCompound data = new net.minecraft.nbt.NbtCompound();
        data.putString("id", fr.lordfinn.steveparty.entities.ModEntities.GLANDOUILLE_KEY.getValue().toString());
        data.putInt("Variant", variant.ordinal());
        return spawnEgg(id, fr.lordfinn.steveparty.entities.ModEntities.GLANDOUILLE, primary, secondary,
                new Item.Settings().component(net.minecraft.component.DataComponentTypes.ENTITY_DATA,
                        net.minecraft.component.type.NbtComponent.of(data)));
    }

    public static <T extends Item> T register(Class<T> itemClass, String id, Item.Settings settings) {
        try {
            T item = itemClass.getConstructor(Item.Settings.class).newInstance(settings);
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
        Item item = new PartyCardItem(type, new Item.Settings());
        return Registry.register(Registries.ITEM, RegistryKey.of(RegistryKeys.ITEM, Steveparty.id(id)), item);
    }

    /** Blocks of a sign material kind, from the tags of the running game (modded blocks included). */
    private static List<Block> blocksOf(RegistryWrapper.WrapperLookup lookup, SignMaterial kind) {
        List<Block> blocks = new ArrayList<>();
        lookup.getWrapperOrThrow(RegistryKeys.BLOCK).getOptional(kind.tag())
                .ifPresent(list -> list.forEach(entry -> blocks.add(entry.value())));
        if (blocks.isEmpty()) blocks.add(kind.defaultBlock());
        return blocks;
    }

    public static void initialize() {
        // Former ids of renamed items, so the ones already in saved worlds keep loading
        fr.lordfinn.steveparty.registry.RegistryAliases.add(Steveparty.id("power_star"), Steveparty.id("party_star"));
        fr.lordfinn.steveparty.registry.RegistryAliases.add(Steveparty.id("garnet_crystal_ball"), Steveparty.id("lapis_crystal_ball"));
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
            Item item = new fr.lordfinn.steveparty.items.custom.DiceModuleItem(module, new Item.Settings());
            DICE_MODULES.add(Registry.register(Registries.ITEM, RegistryKey.of(RegistryKeys.ITEM, Steveparty.id(module.itemPath())), item));
        }

        // One item per power-up (see PowerUps)
        for (fr.lordfinn.steveparty.powerups.PowerUp powerUp : fr.lordfinn.steveparty.powerups.PowerUps.all()) {
            Item item = new PowerUpItem(powerUp, new Item.Settings());
            POWER_UPS.add(Registry.register(Registries.ITEM, RegistryKey.of(RegistryKeys.ITEM, Steveparty.id(powerUp.itemPath())), item));
        }

        // Register items to the custom item group.
        ItemGroupEvents.modifyEntriesEvent(CUSTOM_ITEM_GROUP_KEY).register(itemGroup -> {
            for (int color = 0; color < BandanaItem.COLOR_NAMES.length; color++) {
                itemGroup.add(BandanaItem.create(color));
            }
            itemGroup.add(new ItemStack(BOX_COSTUME));
            // The tiles: plain, one per cartridge, then their other sizes (same order as in REI)
            itemGroup.addAll(fr.lordfinn.steveparty.compat.CartridgeApplications.tileEntries());
            itemGroup.add(CHECK_POINT);
            itemGroup.add(BOARD_SPACE_REDSTONE_ROUTER);
            itemGroup.add(WRENCH);
            itemGroup.add(TILE_LINKER_BRUSH);
            itemGroup.add(EXPLORER_HELMET);
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
            itemGroup.add(STAR_CARTRIDGE);
            itemGroup.add(GLANDOUILLE_CARTRIDGE);
            itemGroup.add(FROUSSEUX_CARTRIDGE);
            itemGroup.add(MISTIGRI_CARTRIDGE);
            itemGroup.add(TOKENIZER_WAND);
            itemGroup.add(TOKEN);
            itemGroup.add(PLUNGER);
            itemGroup.add(LAPIS_CRYSTAL_BALL);
            itemGroup.add(PARTY_CONTROLLER);
            itemGroup.add(STEP_CONTROLLER);
            itemGroup.add(MINI_GAME_CONTROLLER);
            itemGroup.add(MINI_GAME_REMOTE);
            itemGroup.add(PARTY_CARD_TURNS);
            itemGroup.add(PARTY_CARD_MINIGAME);
            itemGroup.add(PARTY_CARD_EVENT);
            itemGroup.add(PARTY_CARD_SEQUENCE_START);
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
            for (Item egg : GLANDOUILLE_SPAWN_EGGS) itemGroup.add(egg);
            itemGroup.add(FROUSSEUX_SPAWN_EGG);
            itemGroup.add(BOOMCART_SPAWN_EGG);
            itemGroup.add(MISTIGRI_SPAWN_EGG);
            itemGroup.add(LOADED_DIE);
            itemGroup.add(FUMAROLE_SPAWN_EGG);
            itemGroup.add(CANDLE_SAUCER);
            itemGroup.add(ACORN);
            itemGroup.add(ACORN_HAT);
            // The 16 colours, in dye order: the blocks, then the fragments
            for (Block block : STAR_FRAGMENTS_BLOCKS) itemGroup.add(block);
            for (Item fragment : STAR_FRAGMENTS) itemGroup.add(fragment);
            itemGroup.add(PARTY_STAR);
            itemGroup.add(COIN);
            itemGroup.add(GRAVITY_CORE);
            itemGroup.add(DICE_FORGE);
            itemGroup.add(TELESCOPE);
            for (Item item : DICE_FACES) {
                itemGroup.add(item);
            }
            itemGroup.add(BLANK_DICE_MODULE);
            for (Item item : DICE_MODULES) {
                itemGroup.add(item);
            }
            itemGroup.add(DEFAULT_DICE);
            itemGroup.add(DOUBLE_DICE);
            itemGroup.add(TRIPLE_DICE);
            for (Item item : POWER_UPS) {
                itemGroup.add(item);
            }
        });
    }
}
