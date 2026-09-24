package fr.lordfinn.steveparty.client.model.sign;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Swaps the JSON models of the stencil signs (blocks and items) for the {@link SignModel}s that turn them in 16
 * directions and give them their material.
 * <p>
 * Models, all editable in Blockbench (front of the sign facing north):
 * <ul>
 *     <li>{@code block/<wood>_traffic_sign}, {@code block/traffic_sign_generic} (oak placeholder textures);</li>
 *     <li>{@code block/wooden_panel} (post) + {@code block/wooden_panel_board};</li>
 *     <li>{@code block/wooden_cutout_panel} (post; the board is built from the stencil);</li>
 *     <li>{@code block/rock_sign} (stone; stone / smooth stone placeholders for sides / tops) +
 *     {@code block/rock_sign_pebbles_0..3};</li>
 *     <li>{@code block/plastic_road_sign} (pole) + {@code block/plastic_road_sign_plate_<plate>}
 *     (white plastic placeholder).</li>
 * </ul>
 */
public class StencilSignModelPlugin implements ModelLoadingPlugin {
    private static final Set<String> LEGACY_WOODS = Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "crimson", "warped");
    private static final Identifier PANEL_BOARD = Steveparty.id("block/wooden_panel_board");
    private static final int PEBBLE_SETS = 4;
    private static final Identifier PLASTIC_PLACEHOLDER = Steveparty.id("block/plastic_block/connected/white_15");

    private static Identifier pebbles(int index) {
        return Steveparty.id("block/rock_sign_pebbles_" + index);
    }

    private static Identifier plate(PlasticRoadSignBlock.Plate plate) {
        return Steveparty.id("block/plastic_road_sign_plate_" + plate.asString());
    }

    @Override
    public void initialize(Context context) {
        List<Identifier> extra = new ArrayList<>();
        extra.add(PANEL_BOARD);
        for (int i = 0; i < PEBBLE_SETS; i++) extra.add(pebbles(i));
        for (PlasticRoadSignBlock.Plate plate : PlasticRoadSignBlock.Plate.values()) extra.add(plate(plate));
        context.addModels(extra);

        context.modifyModelAfterBake().register((original, ctx) -> {
            Identifier id = ctx.resourceId();
            if (id == null || !id.getNamespace().equals(Steveparty.MOD_ID)) return original;
            String path = id.getPath();
            Baker baker = ctx.baker();
            Function<SpriteIdentifier, Sprite> sprites = ctx.textureGetter();

            if (path.startsWith("block/") && path.endsWith("_traffic_sign")
                    && LEGACY_WOODS.contains(path.substring("block/".length(), path.length() - "_traffic_sign".length()))) {
                return new StencilSignModels.TrafficSign(original, false);
            }
            return switch (path) {
                case "block/traffic_sign_generic", "item/traffic_sign" -> new StencilSignModels.TrafficSign(original, true);
                case "block/wooden_panel", "item/wooden_panel" ->
                        new StencilSignModels.Panel(original, baker.bake(PANEL_BOARD, ctx.settings()));
                case "block/wooden_cutout_panel", "item/wooden_cutout_panel" ->
                        new StencilSignModels.CutoutPanel(original, blockSprite(sprites, StencilSignModels.OAK_PLANKS));
                case "block/rock_sign", "item/rock_sign" -> {
                    BakedModel[] sets = new BakedModel[PEBBLE_SETS];
                    for (int i = 0; i < PEBBLE_SETS; i++) sets[i] = baker.bake(pebbles(i), ctx.settings());
                    yield new StencilSignModels.RockSign(original, sets);
                }
                case "block/plastic_road_sign", "item/plastic_road_sign" -> {
                    PlasticRoadSignBlock.Plate[] kinds = PlasticRoadSignBlock.Plate.values();
                    BakedModel[] plates = new BakedModel[kinds.length];
                    for (PlasticRoadSignBlock.Plate plate : kinds) plates[plate.ordinal()] = baker.bake(plate(plate), ctx.settings());
                    Sprite[] plastic = new Sprite[DyeColor.values().length];
                    for (DyeColor color : DyeColor.values()) {
                        plastic[color.getId()] = blockSprite(sprites, Steveparty.id("block/plastic_block/connected/" + color.getName() + "_15"));
                    }
                    yield new StencilSignModels.PlasticRoadSign(original, plates, plastic, PLASTIC_PLACEHOLDER);
                }
                default -> original;
            };
        });
    }

    private static Sprite blockSprite(Function<SpriteIdentifier, Sprite> sprites, Identifier id) {
        return sprites.apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, id));
    }
}
