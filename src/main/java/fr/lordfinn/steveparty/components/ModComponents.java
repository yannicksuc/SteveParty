package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.items.custom.teleportation_books.TeleportingTarget;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.util.List;

public class ModComponents {

    // Static method to register a component type
    public static <T> ComponentType<T> registerComponent(String name, Codec<T> codec) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Steveparty.id(name),
                ComponentType.<T>builder().codec(codec).build()
        );
    }

    // Component definitions using the registerComponent method
    public static final ComponentType<DestinationsComponent> DESTINATIONS_COMPONENT =
            registerComponent("tile-behavior-component", DestinationsComponent.CODEC);
    public static final ComponentType<BlockOriginComponent> BLOCK_ORIGIN_COMPONENT =
            registerComponent("block-origin-component", BlockOriginComponent.CODEC);
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
    public static final ComponentType<Integer> COLOR =
            registerComponent("color", Codec.INT);
    public static final ComponentType<ItemStack> SOCKETED_STORY =
            registerComponent("socketed-story", ItemStack.CODEC);
    public static final ComponentType<ItemStack> CATALOGUE =
            registerComponent("catalogue", ItemStack.CODEC);
    public static final ComponentType<Boolean> IS_NEGATIVE =
            registerComponent("is-negative", Codec.BOOL);
    public static final ComponentType<InventoryComponent> INVENTORY_COMPONENT =
            registerComponent("inventory-cartridge", InventoryComponent.CODEC);
    public static final ComponentType<BlockPos> INVENTORY_POS =
            registerComponent("inventory-pos", BlockPos.CODEC);
    public static final ComponentType<Integer> SELECTION_STATE =
            registerComponent("selection-state", Codec.INT);
    public static final ComponentType<Integer> STATE =
            registerComponent("state", Codec.INT);
    public static final ComponentType<List<TeleportingTarget>> TP_TARGETS =
            registerComponent("teleporting-targets", Codec.list(TeleportingTarget.CODEC));
    public static final ComponentType<ItemStack> CURRENT_MINIGAME =
            registerComponent("current-minigame", ItemStack.CODEC);
    public static final ComponentType<TeamDisposition> TEAM_DISPOSITION =
            registerComponent("team-disposition", TeamDisposition.CODEC);
    public static final ComponentType<List<Byte>> STENCIL_PIXELS =
            registerComponent("stencil-pixels", Codec.list(Codec.BYTE));
    public static final ComponentType<CarpetColorComponent> CARPET_COLORS =
            registerComponent("carpet-colors", CarpetColorComponent.CODEC);
    public static void initialize() {
        Steveparty.LOGGER.info("Registering {} components", Steveparty.MOD_ID);
    }
}
