package fr.lordfinn.steveparty.client.model.sign;

import fr.lordfinn.steveparty.Steveparty;
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
 *     <li>{@code block/wooden_panel}: the board (the post is the fence below);</li>
 *     <li>{@code block/wooden_cutout_panel}: particles and item display only, the board is built from the stencil;</li>
 *     <li>{@code block/rock_sign}: the stone standing upright (leant by the code), without its front which is built
 *     from the engraving; stone / smooth stone placeholders for sides / tops; + the pebbles behind it
 *     ({@code block/rock_sign_pebbles_back_0..3}) and in front of it ({@code block/rock_sign_pebbles_front_0..2});</li>
 *     <li>{@code block/plastic_road_sign}: particles and item display only, the plate is built from its shape.</li>
 * </ul>
 */
public class StencilSignModelPlugin implements ModelLoadingPlugin {
    private static final Set<String> LEGACY_WOODS = Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "crimson", "warped");
    private static final int BACK_PEBBLES = 4, FRONT_PEBBLES = 3;

    private static Identifier pebbles(String side, int index) {
        return Steveparty.id("block/rock_sign_pebbles_" + side + "_" + index);
    }

    @Override
    public void initialize(Context context) {
        List<Identifier> extra = new ArrayList<>();
        for (int i = 0; i < BACK_PEBBLES; i++) extra.add(pebbles("back", i));
        for (int i = 0; i < FRONT_PEBBLES; i++) extra.add(pebbles("front", i));
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
                case "block/wooden_panel", "item/wooden_panel" -> new StencilSignModels.Panel(original);
                case "block/wooden_cutout_panel", "item/wooden_cutout_panel" ->
                        new StencilSignModels.CutoutPanel(original, blockSprite(sprites, StencilSignModels.OAK_PLANKS));
                case "block/rock_sign", "item/rock_sign" -> {
                    BakedModel[] back = new BakedModel[BACK_PEBBLES], front = new BakedModel[FRONT_PEBBLES];
                    for (int i = 0; i < BACK_PEBBLES; i++) back[i] = baker.bake(pebbles("back", i), ctx.settings());
                    for (int i = 0; i < FRONT_PEBBLES; i++) front[i] = baker.bake(pebbles("front", i), ctx.settings());
                    yield new StencilSignModels.RockSign(original, back, front, blockSprite(sprites, StencilSignModels.ROCK_SIDE));
                }
                case "block/plastic_road_sign", "item/plastic_road_sign" -> {
                    Sprite[] plastic = new Sprite[DyeColor.values().length];
                    for (DyeColor color : DyeColor.values()) {
                        plastic[color.getId()] = blockSprite(sprites, Steveparty.id("block/plastic_block/" + color.getName() + "_plastic_block"));
                    }
                    yield new StencilSignModels.PlasticRoadSign(original, plastic);
                }
                default -> original;
            };
        });
    }

    private static Sprite blockSprite(Function<SpriteIdentifier, Sprite> sprites, Identifier id) {
        return sprites.apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, id));
    }
}
