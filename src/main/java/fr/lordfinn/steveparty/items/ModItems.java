package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.tilebehaviors.StartTileBehavior;
import fr.lordfinn.steveparty.items.tilebehaviors.TileBehavior;
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

import static fr.lordfinn.steveparty.blocks.ModBlocks.PARTY_CONTROLLER;
import static fr.lordfinn.steveparty.blocks.ModBlocks.TILE;
import static fr.lordfinn.steveparty.Steveparty.MOD_ID;

public class ModItems {
    public static final Item WRENCH = register(
            new Wrench(getSettings(new Wrench.Settings().maxCount(1).fireproof(), "wrench")),
            "wrench"
    );
    public static final Item TILE_BEHAVIOR = register(
            new TileBehavior(getSettings(new TileBehavior.Settings(), "tile_behavior")),
            "tile_behavior"
    );
    public static final Item TILE_BEHAVIOR_START = register(
            new StartTileBehavior(getSettings(new StartTileBehavior.Settings(), "tile_behavior_start")),
            "tile_behavior_start"
    );
    public static final Item TOKENIZER_WAND = register(
            new TokenizerWand(getSettings(new TokenizerWand.Settings(), "tokenizer_wand")),
            "tokenizer_wand"
    );

    public static final Item PLUNGER = register(
            new Plunger(getSettings(new Plunger.Settings(), "plunger")),
            "plunger"
    );

    public static final Item DEFAULT_DICE = register(
            new DefaultDice(getSettings(new DefaultDice.Settings(), "default_dice")),
            "default_dice"
    );

    public static final RegistryKey<ItemGroup> CUSTOM_ITEM_GROUP_KEY = RegistryKey.of(Registries.ITEM_GROUP.getKey(), Identifier.of(MOD_ID, "item_group"));
    public static final ItemGroup CUSTOM_ITEM_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(TILE))
            .displayName(Text.translatable("itemgroup.steveparty"))
            .build();


    public static Item register(Item item, String id) {
        Identifier itemID = Identifier.of(Steveparty.MOD_ID, id);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, itemID);

        return Registry.register(Registries.ITEM, key, item);

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
            itemGroup.add(WRENCH);
            itemGroup.add(TILE_BEHAVIOR);
            itemGroup.add(TILE_BEHAVIOR_START);
            itemGroup.add(TOKENIZER_WAND);
            itemGroup.add(PLUNGER);
            itemGroup.add(PARTY_CONTROLLER);
            itemGroup.add(DEFAULT_DICE);
        });
    }
}
