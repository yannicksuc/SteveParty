package fr.lordfinn.steveparty.client.pipe;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** The pipe models (block and item): each one's JSON model becomes a {@link PipeBakedModel} of its colour and kind. */
public class PipeModelPlugin implements ModelLoadingPlugin {
    private record Pipe(PipeKind kind, String color) {}

    @Override
    public void initialize(Context context) {
        Map<Identifier, Pipe> pipes = new HashMap<>();
        for (PipeKind kind : PipeKind.values()) {
            for (String color : ModBlocks.COLORS) {
                String name = color + "_" + kind.suffix;
                pipes.put(Steveparty.id("block/pipe/" + name), new Pipe(kind, color));
                pipes.put(Steveparty.id("item/" + name), new Pipe(kind, color));
            }
        }
        context.modifyModelAfterBake().register((original, ctx) -> {
            Identifier id = ctx.resourceId();
            Pipe pipe = id == null ? null : pipes.get(id);
            if (pipe == null) return original;
            Sprite[] sprites = new Sprite[4];
            sprites[PipeGeometry.BODY] = ctx.textureGetter().apply(sprite(bodyTexture(pipe.kind, pipe.color, "body")));
            sprites[PipeGeometry.CAP] = ctx.textureGetter().apply(sprite(bodyTexture(pipe.kind, pipe.color, "cap")));
            sprites[PipeGeometry.INNER] = ctx.textureGetter().apply(sprite(bodyTexture(pipe.kind, pipe.color, "inner")));
            sprites[PipeGeometry.RIM] = ctx.textureGetter().apply(sprite(Steveparty.id("block/plastic_block/" + pipe.color + "_plastic_block")));
            return new PipeBakedModel(original, sprites);
        });
    }

    /** {@code block/pipe/<kind>/<color>_<part>} (body, cap or inner). */
    public static Identifier bodyTexture(PipeKind kind, String color, String part) {
        return Steveparty.id("block/pipe/" + kind.folder + "/" + color + "_" + part);
    }

    private static SpriteIdentifier sprite(Identifier texture) {
        return new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, texture);
    }
}
