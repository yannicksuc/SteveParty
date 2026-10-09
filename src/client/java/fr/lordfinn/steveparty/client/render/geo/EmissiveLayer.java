package fr.lordfinn.steveparty.client.render.geo;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A glow texture drawn over the whole model, emissive ({@link RenderLayer#getEyes}) and full bright: eyes, veins...
 * Drawn only while {@code visible}, tinted {@code tint} (a fully transparent tint, 0, skips it); {@link #animated()}
 * when its texture has frames (a .png.mcmeta GeckoLib animates).
 */
public class EmissiveLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    /** The glow's ARGB tint for this frame. */
    @FunctionalInterface
    public interface Tint<T> {
        int argb(T animatable, float partialTick);
    }

    /** No tint: the texture as painted. */
    public static final int WHITE = 0xFFFFFFFF;

    private final Function<T, Identifier> texture;
    private final Predicate<T> visible;
    private final Tint<T> tint;
    private boolean animated;

    public EmissiveLayer(GeoRenderer<T> renderer, Function<T, Identifier> texture, Predicate<T> visible, Tint<T> tint) {
        super(renderer);
        this.texture = texture;
        this.visible = visible;
        this.tint = tint;
    }

    /** Its texture glows untinted. */
    public EmissiveLayer(GeoRenderer<T> renderer, Function<T, Identifier> texture, Predicate<T> visible) {
        this(renderer, texture, visible, (animatable, partialTick) -> WHITE);
    }

    /** Its texture has frames: GeckoLib steps them before each draw. */
    public EmissiveLayer<T> animated() {
        this.animated = true;
        return this;
    }

    @Override
    public void render(MatrixStack poseStack, T animatable, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                       VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        if (!visible.test(animatable)) return;
        int argb = tint.argb(animatable, partialTick);
        if (argb == 0) return;
        Identifier id = texture.apply(animatable);
        if (animated) AnimatableTexture.setAndUpdate(id);
        RenderLayer layer = RenderLayer.getEyes(id);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, layer, bufferSource.getBuffer(layer),
                partialTick, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, argb);
    }
}
