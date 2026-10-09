package fr.lordfinn.steveparty.client.utils;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Textures made at run time (one per key), kept while used: at most {@code max}, the least recently used freed first
 * ({@link ClientTextures#destroy}, never {@code TextureManager.destroyTexture}). Render thread only.
 */
public final class DynamicTextureCache<K> {
    private final Map<K, Identifier> textures;

    public DynamicTextureCache(int max) {
        textures = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Identifier> eldest) {
                if (size() <= max) return false;
                ClientTextures.destroy(eldest.getValue());
                return true;
            }
        };
    }

    /** The texture of {@code key}, made by {@code create} on first use. */
    public Identifier get(K key, Function<? super K, Identifier> create) {
        return textures.computeIfAbsent(key, create);
    }

    /** Frees and forgets every texture. */
    public void clear() {
        textures.values().forEach(ClientTextures::destroy);
        textures.clear();
    }

    /** Runs {@code onReload} on each client resource reload (F3+T, resource pack change), as the {@code name} listener. */
    public static void onResourceReload(String name, Runnable onReload) {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Steveparty.id(name);
            }

            @Override
            public void reload(ResourceManager manager) {
                onReload.run();
            }
        });
    }
}
