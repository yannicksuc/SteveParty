package fr.lordfinn.steveparty.client.model;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Swaps the solid switcher block models for {@link ConnectedPlasticModel}. */
public class ConnectedPlasticModelPlugin implements ModelLoadingPlugin {
    @Override
    public void initialize(Context context) {
        Map<Identifier, String> colorByModel = new HashMap<>();
        for (String color : ModBlocks.COLORS) {
            colorByModel.put(Steveparty.id("block/" + color + "_switcher_block"), color);
        }

        context.modifyModelAfterBake().register((originalModel, ctx) -> {
            Identifier resourceId = ctx.resourceId();
            String color = resourceId == null ? null : colorByModel.get(resourceId);
            if (color == null) return originalModel;

            Sprite[] sprites = new Sprite[16];
            for (int mask = 0; mask < 16; mask++) {
                sprites[mask] = ctx.textureGetter().apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
                        Steveparty.id("block/switcher_block/connected/" + color + "_" + mask)));
            }
            return new ConnectedPlasticModel(originalModel, sprites);
        });
    }
}
