package fr.lordfinn.steveparty.registry;

import fr.lordfinn.steveparty.blocks.ModBlocks;

import static fr.lordfinn.steveparty.Steveparty.id;

/**
 * Every former id of a renamed block, item, block entity or entity ({@link RegistryAliases}), so the worlds saved
 * before the renames keep them. Keep them all: playtest worlds may still hold any of them.
 */
public final class LegacyIds {
    private LegacyIds() {
    }

    public static void initialize() {
        // The plastic blocks were the switcher blocks: "<color>_switcher_block" (blocks and items)
        for (String color : ModBlocks.COLORS) {
            RegistryAliases.add(id(color + "_switcher_block"), id(color + "_plastic_block"));
        }
        // The easel signs were the traffic signs: "traffic_sign" and "<wood>_traffic_sign" (blocks, items, and the
        // block entity, which had the block's id)
        RegistryAliases.add(id("traffic_sign"), id("easel_sign"));
        for (String wood : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped"}) {
            RegistryAliases.add(id(wood + "_traffic_sign"), id(wood + "_easel_sign"));
        }
        // The tiles (see TileMigration): simple_tile is now tile, and tile is now advanced_tile
        RegistryAliases.add(id("simple_tile"), id("tile")); // block and item
        RegistryAliases.add(id("simple_tile_entity"), id("tile"));
        RegistryAliases.add(id("tile_entity"), id("advanced_tile"));
        // Renamed items
        RegistryAliases.add(id("power_star"), id("party_star"));
        RegistryAliases.add(id("garnet_crystal_ball"), id("lapis_crystal_ball"));
        // The Boxed Trader was the Hiding Trader: worlds saved before the rename keep their merchants and spawn eggs
        RegistryAliases.add(id("hiding_trader"), id("boxed_trader"));
        RegistryAliases.add(id("hiding_trader_spawn_egg"), id("boxed_trader_spawn_egg"));
        // The names the iron and golden mini-game pipes (and their block entity) had
        RegistryAliases.add(id("super_golden_minigame_pipe"), id("iron_minigame_pipe"));
        RegistryAliases.add(id("mega_golden_minigame_pipe"), id("golden_minigame_pipe"));
        RegistryAliases.add(id("golden_pipe"), id("minigame_pipe"));
    }
}
