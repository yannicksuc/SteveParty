package fr.lordfinn.steveparty.client.tokenspell;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.utils.DominantColorPicker;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Token colour of a mob, computed from its actual texture (textures only exist on the client): the most present
 * colour cluster of the texture, see {@link DominantColorPicker}. Candidates are cached per texture (cleared on
 * resource reload); when several clusters are tied, one of them is picked at random on each call.
 * <p>
 * Falls back to the primary colour of the mob's spawn egg when the texture can't be read (dynamic texture, renderer
 * without texture...). Render thread only.
 */
public final class MobTextureColors {
    private static final int[] NONE = new int[0];
    private static final Map<Identifier, int[]> CACHE = new HashMap<>();

    private MobTextureColors() {
    }

    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Steveparty.id("token_texture_colors");
            }

            @Override
            public void reload(ResourceManager manager) {
                CACHE.clear();
            }
        });
    }

    /** @return the token colour of {@code entity} (0xRRGGBB), or {@link DominantColorPicker#NO_COLOR}. */
    public static int pickColor(Entity entity) {
        return DominantColorPicker.pick(candidates(entity), ThreadLocalRandom.current());
    }

    /** @return the tied dominant colours of the entity's texture (0xRRGGBB), possibly empty. */
    public static int[] candidates(Entity entity) {
        Identifier texture = textureOf(entity);
        if (texture != null) {
            int[] candidates = CACHE.computeIfAbsent(texture, MobTextureColors::compute);
            if (candidates.length > 0) return candidates;
        }
        SpawnEggItem egg = SpawnEggItem.forEntity(entity.getType());
        return egg == null ? NONE : new int[]{egg.getColor(0) & 0xFFFFFF};
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    private static Identifier textureOf(Entity entity) {
        try {
            EntityRenderer renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (renderer instanceof GeoEntityRenderer geoRenderer && entity instanceof GeoAnimatable animatable) {
                return geoRenderer.getTextureLocation(animatable);
            }
            if (renderer instanceof LivingEntityRenderer livingRenderer) {
                // 1.21.3 render state API: the texture may depend on the state (variant, age, saddle...)
                LivingEntityRenderState state = (LivingEntityRenderState) livingRenderer.getAndUpdateRenderState(entity, 1.0F);
                return livingRenderer.getTexture(state);
            }
            return null;
        } catch (RuntimeException e) {
            Steveparty.LOGGER.debug("No texture for {}", entity, e);
            return null;
        }
    }

    private static int[] compute(Identifier texture) {
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) return NONE;
        try (InputStream stream = resource.get().getInputStream(); NativeImage image = NativeImage.read(stream)) {
            int width = image.getWidth(), height = image.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = image.getColorArgb(x, y);
                }
            }
            return DominantColorPicker.candidates(pixels);
        } catch (Exception e) {
            Steveparty.LOGGER.warn("Could not read the texture {} to compute a token colour", texture, e);
            return NONE;
        }
    }
}
