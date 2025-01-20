package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class ModComponents {

    // Static method to register a component type
    public static <T> ComponentType<T> registerComponent(String name, Codec<T> codec) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of(Steveparty.MOD_ID, name),
                ComponentType.<T>builder().codec(codec).build()
        );
    }

    // Component definitions using the registerComponent method
    public static final ComponentType<BoardSpaceBehaviorComponent> BOARD_SPACE_BEHAVIOR_COMPONENT =
            registerComponent("tile-behavior-component", BoardSpaceBehaviorComponent.CODEC);
    public static final ComponentType<MobEntityComponent> MOB_ENTITY_COMPONENT =
            registerComponent("mob-entity-component", MobEntityComponent.CODEC);
    public static final ComponentType<EntityDataComponent> ENTITY_DATA_COMPONENT =
            registerComponent("entity-data-component", EntityDataComponent.CODEC);
    public static final ComponentType<BlockPos> BLOCK_POS =
            registerComponent("block-pos", BlockPos.CODEC);
    public static final ComponentType<Integer> ROLLING_VALUE =
            registerComponent("rolling-value", Codec.INT);
    public static final ComponentType<Boolean> IS_ROLLING =
            registerComponent("is-rolling", Codec.BOOL);
    public static final ComponentType<String> TB_START_BOUND_ENTITY =
            registerComponent("bound-entity", Codec.STRING);
    public static final ComponentType<String> TB_START_OWNER =
            registerComponent("owner", Codec.STRING);
    public static final ComponentType<Integer> TB_START_COLOR =
            registerComponent("color", Codec.INT);
    public static final ComponentType<ItemStack> SOCKETED_STORY =
            registerComponent("socketed-story", ItemStack.CODEC);
    public static final ComponentType<ItemStack> CATALOGUE =
            registerComponent("catalogue", ItemStack.CODEC);
    public static final ComponentType<Boolean> IS_NEGATIVE =
            registerComponent("is-negative", Codec.BOOL);
    public static final ComponentType<PersistentInventoryComponent> INVENTORY_CARTRIDGE_COMPONENT =
            registerComponent("inventory-cartridge", PersistentInventoryComponent.CODEC);
    public static final ComponentType<BlockPos> INVENTORY_POS =
            registerComponent("inventory-pos", BlockPos.CODEC);
    public static final ComponentType<Integer> SELECTION_STATE =
            registerComponent("selection-state", Codec.INT);
    public static void initialize() {
        Steveparty.LOGGER.info("Registering {} components", Steveparty.MOD_ID);
    }
}
