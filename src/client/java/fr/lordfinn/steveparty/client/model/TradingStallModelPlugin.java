package fr.lordfinn.steveparty.client.model;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.blockentity.TradingStallBakedModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.render.model.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Function;

public class TradingStallModelPlugin implements ModelLoadingPlugin {

    private static final Identifier MODEL_ID = Steveparty.id("block/trading_stall");

    @Override
    public void initialize(Context context) {
        context.addModels(List.of(
                MODEL_ID,
                Steveparty.id("block/trading_stall_ab"),
                Steveparty.id("block/trading_stall_ba"),
                Steveparty.id("block/trading_stall_bb")
        ));

        context.modifyModelAfterBake().register((originalModel, ctx) -> {
            Identifier resourceId = ctx.resourceId();
            if (resourceId == null || !resourceId.equals(MODEL_ID)) {
                return originalModel;
            }

            Baker baker = ctx.baker();
            Function<SpriteIdentifier, Sprite> spriteGetter = ctx.textureGetter();
            ModelBakeSettings settings = ctx.settings();

            // Ne PAS refaire baker.bake(MODEL_ID, settings) ici, pour éviter récursion
            BakedModel base = originalModel; // Utilise le modèle déjà baked

            // Bake uniquement les variantes
            BakedModel ab = baker.bake(Steveparty.id("block/trading_stall_ab"), settings);
            BakedModel ba = baker.bake(Steveparty.id("block/trading_stall_ba"), settings);
            BakedModel bb = baker.bake(Steveparty.id("block/trading_stall_bb"), settings);

            Sprite particle = spriteGetter.apply(
                    new SpriteIdentifier(
                            SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
                            Identifier.ofVanilla("block/barrel_bottom")
                    )
            );

            return new TradingStallBakedModel(particle, base, ab, ba, bb);
        });
    }
}
