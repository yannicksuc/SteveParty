package fr.lordfinn.steveparty.client.model;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Swaps the plastic block models for {@link ConnectedPlasticModel}, the models of the plastic slabs, stairs and wall
 * parts for {@link ConnectedPlasticShapeModel}, and the plastic fence parts for {@link ConnectedPlasticFenceModel}.
 */
public class ConnectedPlasticModelPlugin implements ModelLoadingPlugin {
    /** Model suffixes of the slab, stairs and wall parts (not their inventory models, drawn as items). */
    private static final String[] SHAPES = {"slab", "slab_top", "stairs", "stairs_inner", "stairs_outer",
            "wall_post", "wall_side", "wall_side_tall"};

    @Override
    public void initialize(Context context) {
        Map<Identifier, String> colorByModel = new HashMap<>();
        Map<Identifier, String> colorByShape = new HashMap<>();
        Map<Identifier, String> colorByFencePart = new HashMap<>();
        for (String color : ModBlocks.COLORS) {
            colorByModel.put(Steveparty.id("block/" + color + "_plastic_block"), color);
            for (String shape : SHAPES) colorByShape.put(Steveparty.id("block/" + color + "_plastic_" + shape), color);
            colorByFencePart.put(Steveparty.id("block/" + color + "_plastic_fence_post"), color);
            colorByFencePart.put(Steveparty.id("block/" + color + "_plastic_fence_side"), color);
        }

        context.modifyModelAfterBake().register((originalModel, ctx) -> {
            Identifier resourceId = ctx.resourceId();
            if (resourceId == null) return originalModel;
            String fenceColor = colorByFencePart.get(resourceId);
            if (fenceColor != null) {
                Sprite[] sprites = new Sprite[16];
                for (int mask = 0; mask < 16; mask++) {
                    sprites[mask] = ctx.textureGetter().apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
                            Steveparty.id("block/plastic_fence/connected/" + fenceColor + "_" + mask)));
                }
                return new ConnectedPlasticFenceModel(originalModel, sprites);
            }
            String shapeColor = colorByShape.get(resourceId);
            if (shapeColor != null) return new ConnectedPlasticShapeModel(originalModel, blockSprites(ctx, shapeColor));
            String color = colorByModel.get(resourceId);
            if (color == null) return originalModel;
            return new ConnectedPlasticModel(originalModel, blockSprites(ctx, color));
        });
    }

    /** The 47 connected sprites of a colour's plastic block, by mask. */
    private static Sprite[] blockSprites(ModelModifier.AfterBake.Context ctx, String color) {
        Sprite[] sprites = new Sprite[256];
        for (int mask = 0; mask < 256; mask++) {
            if (!ConnectedPlasticModel.isValidMask(mask)) continue;
            sprites[mask] = ctx.textureGetter().apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
                    Steveparty.id("block/plastic_block/connected/" + color + "_" + mask)));
        }
        return sprites;
    }
}
