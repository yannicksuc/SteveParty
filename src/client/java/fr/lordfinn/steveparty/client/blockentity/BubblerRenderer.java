package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.BubblerBlock;
import fr.lordfinn.steveparty.blocks.custom.BubblerBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Util;

/**
 * A Bubbler's tiny bubbles (its plate is its block model): rising from its grille, forever, as high as it sends its
 * bubbles ({@link BubblerBlock#launchHeight}, its redstone signal): where they pop is where a bubble stops.
 */
public class BubblerRenderer implements BlockEntityRenderer<BubblerBlockEntity> {
    private static final RenderLayer LAYER = RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    /** About one tiny bubble for this many blocks of the column, anywhere within this many blocks of its middle (the grille). */
    private static final float STEP = 0.3f, SCATTER = 4.5f / 16;

    public BubblerRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(BubblerBlockEntity bubbler, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
                       int light, int overlay) {
        // On the clock, in ticks, wrapped each hour (see SpawnBubbleRenderer)
        float time = (Util.getMeasuringTimeMs() % 3_600_000L) / 50f;
        float low = (float) BubblerBlock.TOP;
        // up to the bottom of a bubble hovering there
        float high = 1 + BubblerBlock.launchHeight(bubbler.getCachedState());
        BubbleFilm.trail(matrices.peek(), consumers.getBuffer(LAYER), 0.5f, 0.5f, low, high, high - low, STEP, SCATTER, time,
                bubbler.getPos().hashCode(), light);
    }

    @Override
    public boolean rendersOutsideBoundingBox(BubblerBlockEntity bubbler) {
        return true;
    }
}
