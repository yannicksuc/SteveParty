package fr.lordfinn.steveparty.client.pipe;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeGeometry;
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
    private record Pipe(PipeKind kind, int color) {}

    private static final String[] PARTS = {"outer", "inner", "rim"};

    @Override
    public void initialize(Context context) {
        Map<Identifier, Pipe> pipes = new HashMap<>();
        for (PipeKind kind : PipeKind.values()) {
            for (int color = 0; color < kind.count(); color++) {
                String name = kind.id(ModBlocks.COLORS, color);
                pipes.put(Steveparty.id("block/pipe/" + name), new Pipe(kind, color));
                pipes.put(Steveparty.id("item/" + name), new Pipe(kind, color));
            }
        }
        context.modifyModelAfterBake().register((original, ctx) -> {
            Identifier id = ctx.resourceId();
            Pipe pipe = id == null ? null : pipes.get(id);
            if (pipe == null) return original;
            Sprite[] sprites = new Sprite[PARTS.length];
            for (int part = 0; part < PARTS.length; part++) {
                sprites[part] = ctx.textureGetter().apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, texture(pipe.kind, pipe.color, part)));
            }
            return new PipeBakedModel(original, pipe.kind, sprites);
        });
    }

    /**
     * The texture of a part ({@link PipeGeometry#OUTER} .. {@link PipeGeometry#RIM}) of a pipe: plastic pipes have
     * their own wall sheets ({@code block/pipe/<kind>/<color>_outer} and {@code _inner}, 4 x 4 tiles), the rims the
     * plastic block's; glass pipes are a clean glass, rim only, without the streaks of the vanilla glass
     * ({@code block/pipe/glass/clear} or the stained colour).
     */
    public static Identifier texture(PipeKind kind, int color, int part) {
        return switch (kind) {
            case GLASS -> Steveparty.id("block/pipe/glass/clear");
            case STAINED_GLASS -> Steveparty.id("block/pipe/glass/" + ModBlocks.COLORS[color]);
            case COPPER, IRON, GOLDEN -> Steveparty.id("block/pipe/" + kind.folder + "/metal_" + PARTS[part]);
            default -> part == PipeGeometry.RIM ? Steveparty.id("block/plastic_block/" + ModBlocks.COLORS[color] + "_plastic_block")
                    : Steveparty.id("block/pipe/" + kind.folder + "/" + ModBlocks.COLORS[color] + "_" + PARTS[part]);
        };
    }
}
