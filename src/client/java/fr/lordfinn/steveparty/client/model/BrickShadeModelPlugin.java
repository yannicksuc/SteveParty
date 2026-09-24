package fr.lordfinn.steveparty.client.model;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

/**
 * Swaps the models of the polished terracotta and concrete bricks (blocks, slabs, stairs, walls and their items) for
 * {@link BrickShadeModel}.
 */
public class BrickShadeModelPlugin implements ModelLoadingPlugin {
    /** Textures a zone of bricks may draw: the block's own and 3 variants. */
    private static final int VARIANTS = 4;

    @Override
    public void initialize(Context context) {
        context.modifyModelAfterBake().register((originalModel, ctx) -> {
            Identifier id = ctx.resourceId();
            if (id == null || !isBrickModel(id)) return originalModel;
            // The block's own texture, then its variants: textures/block/polished_bricks/<texture>_<n>.png
            Sprite own = originalModel.getParticleSprite();
            Identifier texture = own.getContents().getId();
            Sprite[] variants = new Sprite[VARIANTS];
            variants[0] = own;
            String name = texture.getPath().substring("block/".length());
            for (int n = 1; n < VARIANTS; n++) {
                variants[n] = ctx.textureGetter().apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
                        Identifier.of(texture.getNamespace(), "block/polished_bricks/" + name + "_" + n)));
            }
            return new BrickShadeModel(originalModel, variants);
        });
    }

    private static boolean isBrickModel(Identifier id) {
        String path = id.getPath();
        return id.getNamespace().equals(Steveparty.MOD_ID) && (path.startsWith("block/") || path.startsWith("item/"))
                && path.contains("polished_") && (path.contains("terracotta_bricks") || path.contains("concrete_bricks"));
    }
}
