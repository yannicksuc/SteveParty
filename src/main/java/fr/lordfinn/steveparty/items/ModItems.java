package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.*;
import fr.lordfinn.steveparty.items.custom.tilebehaviors.StartTileBehaviorItem;
import fr.lordfinn.steveparty.items.custom.tilebehaviors.BoardSpaceBehaviorItem;
import fr.lordfinn.steveparty.items.custom.tilebehaviors.StopBoardSpaceBehaviorItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static fr.lordfinn.steveparty.Steveparty.MOD_ID;
import static fr.lordfinn.steveparty.blocks.ModBlocks.*;

public class ModItems {
    public static final Item WRENCH = register(WrenchItem.class, "wrench");
    public static final Item BOARD_SPACE_BEHAVIOR = register(BoardSpaceBehaviorItem.class, "board_space_behavior");
    public static final Item TILE_BEHAVIOR_START = register(StartTileBehaviorItem.class, "tile_behavior_start");
    public static final Item BOARD_SPACE_BEHAVIOR_STOP = register(StopBoardSpaceBehaviorItem.class, "board_space_behavior_stop");
    public static final Item TOKENIZER_WAND = register(TokenizerWandItem.class, "tokenizer_wand");
    public static final Item PLUNGER = register(PlungerItem.class, "plunger");
    public static final Item DEFAULT_DICE = register(DefaultDiceItem.class,"default_dice");
    public static final Item GARNET_CRYSTAL_BALL = register(GarnetCrystalBallItem.class,"garnet_crystal_ball");
    public static final Item MINI_GAMES_CATALOGUE = register(MiniGamesCatalogueItem.class,"mini_games_catalogue");
    public static final Item TOKEN = register(TokenItem.class, "token");

    public static final RegistryKey<ItemGroup> CUSTOM_ITEM_GROUP_KEY = RegistryKey.of(Registries.ITEM_GROUP.getKey(), Identifier.of(MOD_ID, "item_group"));
    public static final ItemGroup CUSTOM_ITEM_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(PARTY_CONTROLLER))
            .displayName(Text.translatable("itemgroup.steveparty"))
            .build();


    public static <T extends Item> T register(Class<T> itemClass, String id) {
        try {
            T item = itemClass.getConstructor(Item.Settings.class).newInstance(getSettings(new T.Settings(), id));
            Identifier itemID = Identifier.of(Steveparty.MOD_ID, id);
            RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, itemID);
            Registry.register(Registries.ITEM, key, item);
            return item;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create and register item: " + itemClass, e);
        }
    }

    public static Item.Settings getSettings(Item.Settings itemSettings, String id) {
        Identifier itemID = Identifier.of(Steveparty.MOD_ID, id);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, itemID);
        return itemSettings.registryKey(key);

    }

    public static void initialize() {
        // Register the group.
        Registry.register(Registries.ITEM_GROUP, CUSTOM_ITEM_GROUP_KEY, CUSTOM_ITEM_GROUP);

        // Register items to the custom item group.
        ItemGroupEvents.modifyEntriesEvent(CUSTOM_ITEM_GROUP_KEY).register(itemGroup -> {
            itemGroup.add(TILE);
            itemGroup.add(TRIGGER_POINT);
            itemGroup.add(WRENCH);
            itemGroup.add(BOARD_SPACE_BEHAVIOR);
            itemGroup.add(BOARD_SPACE_BEHAVIOR_STOP);
            itemGroup.add(TILE_BEHAVIOR_START);
            itemGroup.add(TOKENIZER_WAND);
            itemGroup.add(TOKEN);
            itemGroup.add(PLUNGER);
            itemGroup.add(BIG_BOOK);
            itemGroup.add(DEFAULT_DICE);
            itemGroup.add(GARNET_CRYSTAL_BALL);
            itemGroup.add(PARTY_CONTROLLER);
            itemGroup.add(STEP_CONTROLLER);
            itemGroup.add(MINI_GAMES_CATALOGUE);
            itemGroup.add(VILLAGER_BLOCK);
            itemGroup.add(CASH_REGISTER);
        });
    }
}
