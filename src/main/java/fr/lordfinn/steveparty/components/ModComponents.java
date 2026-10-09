package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.dice.DiceModulesComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import net.minecraft.block.BlockState;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
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
    /**
     * Legacy: mode and chain of the Wrench when it linked board spaces (the Tile Linker Brush does now). No longer
     * written; kept registered so wrenches saved with it still load (an unknown component would drop the item), and
     * stripped from them by {@code WrenchItem#inventoryTick}.
     */
    public static final ComponentType<NbtCompound> WRENCH_STATE =
            registerComponent("wrench-state", NbtCompound.CODEC);
    /** The level (0-15) of a Tile Linker Brush: the slot its links go in; absent: the powered one (see TileLinkerBrush). */
    public static final ComponentType<Integer> LINK_LEVEL =
            registerComponent("link-level", Codec.INT);
    /** The kind of Cartridge a Tile Linker Brush puts in the tiles it links; absent: the plain Cartridge. */
    public static final ComponentType<Item> LINK_CARTRIDGE =
            registerComponent("link-cartridge", Registries.ITEM.getCodec());
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
    /** Legacy: the one container of an Inventory Cartridge before it held a list (read as its list of one, see CartridgeContainers). */
    public static final ComponentType<BlockPos> INVENTORY_POS =
            registerComponent("inventory-pos", BlockPos.CODEC);
    /** Move Forward / Back cartridge: spaces a token landing on its tile moves on (1..6 forward, -1..-6 back). */
    public static final ComponentType<Integer> ADVANCE_BACK_STEPS =
            registerComponent("advance-back-steps", Codec.intRange(-6, 6));
    /**
     * Glandouille Cartridge: how many spaces ahead its tower pushes the tokens, back along the path if negative (0: no
     * destination, nothing happens).
     */
    public static final ComponentType<Integer> GLANDOUILLE_DISTANCE =
            registerComponent("glandouille-distance", Codec.intRange(-50, 50));
    /** Glandouille Cartridge: how many Glandouilles in its tower (one falls off for each token it starts pushing). */
    public static final ComponentType<Integer> GLANDOUILLE_TOWER =
            registerComponent("glandouille-tower", Codec.intRange(1, 25));
    /** Glandouille Cartridge: a lone Glandouille that tries to push and can't, instead of a tower. */
    public static final ComponentType<Boolean> GLANDOUILLE_LONE =
            registerComponent("glandouille-lone", Codec.BOOL);
    /** Frousseux Cartridge: its Frousseux steals stars rather than coins. */
    public static final ComponentType<Boolean> FROUSSEUX_STARS =
            registerComponent("frousseux-stars", Codec.BOOL);
    /** Frousseux Cartridge: how many coins its Frousseux steals (1 to 99). */
    public static final ComponentType<Integer> FROUSSEUX_COINS =
            registerComponent("frousseux-coins", Codec.intRange(1, 99));
    /** Frousseux Cartridge: how many stars its Frousseux steals (1 to 5). */
    public static final ComponentType<Integer> FROUSSEUX_STAR_COUNT =
            registerComponent("frousseux-star-count", Codec.intRange(1, 5));
    /**
     * Mistigri Cartridge: its settings by name, the weight of each sentence ({@code w_<sentence>}, 0 to 9: 0 never
     * drawn) and the amounts ({@code coins_small}, {@code coins_big}, {@code everyone}, {@code back}); missing ones are
     * the defaults (see MistigriCartridgeItem).
     */
    public static final ComponentType<Map<String, Integer>> MISTIGRI_SETTINGS =
            registerComponent("mistigri-settings", Codec.unboundedMap(Codec.STRING, Codec.INT));
    /**
     * The settings of the Threshold obstacle, Common pot, Key gate and Trap cartridges, by name (a missing one is its
     * default; see each cartridge).
     */
    public static final ComponentType<Map<String, Integer>> BOARD_CARTRIDGE_SETTINGS =
            registerComponent("board-cartridge-settings", Codec.unboundedMap(Codec.STRING, Codec.INT));
    /**
     * What a Common pot or a Key gate cartridge remembers in its board space (the coins in the pot, until when the gate
     * stays open...): saved with the tile that holds it.
     */
    public static final ComponentType<Map<String, Integer>> BOARD_CARTRIDGE_STATE =
            registerComponent("board-cartridge-state", Codec.unboundedMap(Codec.STRING, Codec.INT));
    /** The items the Pie stole into a Common pot (kept in its cartridge, given to the pot's winner). */
    public static final ComponentType<List<ItemStack>> POT_ITEMS =
            registerComponent("pot-items", ItemStack.CODEC.listOf());
    /** The trap set on a Trap cartridge's space (kept in the cartridge, so in its tile). */
    public static final ComponentType<TrapSetComponent> TRAP_SET =
            registerComponent("trap-set", TrapSetComponent.CODEC);
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
    public static final ComponentType<TileSize> TILE_SIZE =
            registerComponent("tile-size", TileSize.CODEC);

    /** How many items a player may buy during a stop at a Shop Cartridge's space (1 by default). */
    public static final ComponentType<Integer> SHOP_PURCHASES =
            registerComponent("shop-purchases", Codec.intRange(1, ShopCartridgeItem.MAX_PURCHASES));
    /** Price and options of a Star Cartridge (see StarSettingsComponent). */
    public static final ComponentType<StarSettingsComponent> STAR_SETTINGS =
            registerComponent("star-settings", StarSettingsComponent.CODEC);
    /** The shop chosen for a Shop Cartridge with the Wrench (none: the nearest merchant). */
    public static final ComponentType<ShopLinkComponent> SHOP_LINK =
            registerComponent("shop-link", ShopLinkComponent.CODEC);

    /** The Mini-game Controller a Mini-game Remote is linked to (see MiniGameRemoteItem). */
    public static final ComponentType<GlobalPos> MINI_GAME_REMOTE_LINK =
            registerComponent("mini-game-remote-link", GlobalPos.CODEC);
    /** A Mini-game Page in zone mode: its clicks draw the zone of its page (see PageZoneTool). */
    public static final ComponentType<PageZoneMode> PAGE_ZONE_MODE =
            registerComponent("page-zone-mode", PageZoneMode.CODEC);
    /** Legacy: the dimension of {@link #INVENTORY_POS}. */
    public static final ComponentType<RegistryKey<World>> INVENTORY_DIMENSION =
            registerComponent("inventory-dimension", World.CODEC);
    /**
     * The containers of an Inventory Cartridge, in order (at most CartridgeContainers.MAX): a board space takes from
     * and gives to them in this order, a Party Controller pays its gains from them in this order.
     */
    public static final ComponentType<List<GlobalPos>> INVENTORY_CONTAINERS =
            registerComponent("inventory-containers", GlobalPos.CODEC.listOf());

    /** Block a Box Costume looks like: the one the Boxed Trader's box looked like when it was taken from him. */
    public static final ComponentType<BlockState> BOX_BLOCK =
            registerComponent("box-block", BlockState.CODEC);

    public static void initialize() {
        Steveparty.LOGGER.info("Registering {} components", Steveparty.MOD_ID);
        DiceFacesComponent.initialize();
        DiceModulesComponent.initialize();
    }
}
