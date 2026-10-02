package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

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
    /**
     * The settings of a Teleport Cartridge (network colour, arrival...). Keeps the id of the former per-arrival lists:
     * a cartridge saved with one loads in the default network.
     */
    public static final ComponentType<TeleportSettingsComponent> TELEPORT_SETTINGS =
            registerComponent("teleport-targets", TeleportSettingsComponent.CODEC);
    public static final ComponentType<BlockOriginComponent> BLOCK_ORIGIN_COMPONENT =
            registerComponent("block-origin-component", BlockOriginComponent.CODEC);
    /** Mode, chain, edited slot and auto link of a Wrench (see WrenchState). */
    public static final ComponentType<fr.lordfinn.steveparty.board.WrenchState> WRENCH_STATE =
            registerComponent("wrench-state", fr.lordfinn.steveparty.board.WrenchState.CODEC);
    /**
     * Legacy: token selected by the Tokenizer Wand when it could move tokens. No longer written; kept registered so
     * wands saved by older versions still load (an unknown component would drop the item), and stripped from them
     * by {@code TokenizerWandItem#inventoryTick}.
     */
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
    /** Colour of a Bandana item (0-4: teal, blue, pink, orange, yellow, like the Boxed Trader's BandanaColor). */
    public static final ComponentType<Integer> BANDANA_COLOR =
            registerComponent("bandana-color", Codec.intRange(0, 4));
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
    /** Move Forward / Back cartridge: spaces a token landing on its tile moves on (1..6 forward, -1..-6 back). */
    public static final ComponentType<Integer> ADVANCE_BACK_STEPS =
            registerComponent("advance-back-steps", Codec.intRange(-6, 6));
    public static final ComponentType<Integer> SELECTION_STATE =
            registerComponent("selection-state", Codec.INT);
    public static final ComponentType<Integer> STATE =
            registerComponent("state", Codec.INT);
    public static final ComponentType<ItemStack> CURRENT_MINIGAME =
            registerComponent("current-minigame", ItemStack.CODEC);
    public static final ComponentType<TeamDisposition> TEAM_DISPOSITION =
            registerComponent("team-disposition", TeamDisposition.CODEC);
    /** Mini-game page: the id of its content, kept by the server (see MiniGamePages). */
    public static final ComponentType<MiniGamePageRef> MINI_GAME_PAGE =
            registerComponent("mini-game-page", MiniGamePageRef.CODEC);
    public static final ComponentType<List<Byte>> STENCIL_PIXELS =
            registerComponent("stencil-pixels", Codec.list(Codec.BYTE));
    /** Block (planks, rock...) a material sign is made of. */
    public static final ComponentType<Identifier> SIGN_MATERIAL =
            registerComponent("sign-material", Identifier.CODEC);
    /** Symbol painted on a stencil sign, kept by its item. */
    public static final ComponentType<StencilCanvasComponent> STENCIL_CANVAS =
            registerComponent("stencil-canvas", StencilCanvasComponent.CODEC);
    /** Stencils and dyes loaded in a stencil gun. */
    public static final ComponentType<InventoryComponent> STENCIL_GUN_CONTENTS =
            registerComponent("stencil-gun-contents", InventoryComponent.CODEC);
    public static final ComponentType<StencilGunSelection> STENCIL_GUN_SELECTION =
            registerComponent("stencil-gun-selection", StencilGunSelection.CODEC);
    public static final ComponentType<CarpetColorComponent> CARPET_COLORS =
            registerComponent("carpet-colors", CarpetColorComponent.CODEC);
    public static final ComponentType<UUID> SHOPKEEPER_UUID =
            registerComponent("shopkeeper-uuid", Codec.STRING.xmap(UUID::fromString, UUID::toString));

    /** A look stamped on a tile (the tile keeps it when empty, else its cartridge: see TileStamping). */
    public static final ComponentType<TileStampComponent> TILE_STAMP =
            registerComponent("tile-stamp", TileStampComponent.CODEC);

    /** The size a tile item places (none: the standard size). */
    public static final ComponentType<fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize> TILE_SIZE =
            registerComponent("tile-size", fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize.CODEC);

    /** How many items a player may buy during a stop at a Shop Cartridge's space (1 by default). */
    public static final ComponentType<Integer> SHOP_PURCHASES =
            registerComponent("shop-purchases", Codec.intRange(1, fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem.MAX_PURCHASES));
    /** The shop chosen for a Shop Cartridge with the Wrench (none: the nearest merchant). */
    public static final ComponentType<ShopLinkComponent> SHOP_LINK =
            registerComponent("shop-link", ShopLinkComponent.CODEC);

    /** Block a Box Costume looks like: the one the Boxed Trader's box looked like when it was taken from him. */
    public static final ComponentType<net.minecraft.block.BlockState> BOX_BLOCK =
            registerComponent("box-block", net.minecraft.block.BlockState.CODEC);

    public static void initialize() {
        Steveparty.LOGGER.info("Registering {} components", Steveparty.MOD_ID);
        DiceFacesComponent.initialize();
        fr.lordfinn.steveparty.dice.DiceModulesComponent.initialize();
    }
}
