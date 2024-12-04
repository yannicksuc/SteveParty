package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.Steveparty;
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

import static fr.lordfinn.steveparty.blocks.ModBlocks.TILE;
import static fr.lordfinn.steveparty.Steveparty.MOD_ID;

public class ModItems {
    public static final Item WRENCH = register(
            new WrenchItem(getSettings(new WrenchItem.Settings().maxCount(1).fireproof(), "wrench")),
            "wrench"
    );
    public static final Item TILE_BEHAVIOR = register(
            new TileBehavior(getSettings(new TileBehavior.Settings(), "tile-behavior")),
            "tile-behavior"
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
        });
    }
}
