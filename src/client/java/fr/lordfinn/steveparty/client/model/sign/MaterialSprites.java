package fr.lordfinn.steveparty.client.model.sign;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the textures of a material block from its own block model, so that any block works, modded ones included:
 * <ul>
 *     <li>wood: the planks, and the side / top of the matching (stripped) log, found by name next to the planks
 *     ({@code x_planks} → {@code stripped_x_log}, {@code stripped_x_stem}, {@code stripped_x_block}, {@code x_log},
 *     {@code x_stem}...), the planks when there is none;</li>
 *     <li>rock: the side and top of the block.</li>
 * </ul>
 * Cached per material, cleared on resource reload. Read from chunk building threads.
 */
public final class MaterialSprites {
    public record Wood(Sprite planks, Sprite log, Sprite logTop) {
    }

    public record Rock(Sprite side, Sprite top) {
    }

    private static final Map<Identifier, Wood> WOODS = new ConcurrentHashMap<>();
    private static final Map<Identifier, Rock> ROCKS = new ConcurrentHashMap<>();

    private MaterialSprites() {
    }

    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Steveparty.id("sign_material_sprites");
            }

            @Override
            public Collection<Identifier> getFabricDependencies() {
                return List.of(ResourceReloadListenerKeys.MODELS);
            }

            @Override
            public void reload(ResourceManager manager) {
                WOODS.clear();
                ROCKS.clear();
            }
        });
    }

    public static Wood wood(Identifier planksId) {
        return WOODS.computeIfAbsent(planksId, id -> {
            Block planks = Registries.BLOCK.get(id);
            Sprite planksSprite = side(planks);
            Block log = findLog(id);
            if (log == null) return new Wood(planksSprite, planksSprite, planksSprite);
            return new Wood(planksSprite, side(log), top(log));
        });
    }

    public static Rock rock(Identifier rockId) {
        return ROCKS.computeIfAbsent(rockId, id -> {
            Block rock = Registries.BLOCK.get(id);
            return new Rock(side(rock), top(rock));
        });
    }

    private static @Nullable Block findLog(Identifier planksId) {
        String path = planksId.getPath();
        String wood = path.endsWith("_planks") ? path.substring(0, path.length() - "_planks".length()) : path;
        for (String candidate : new String[]{"stripped_" + wood + "_log", "stripped_" + wood + "_stem", "stripped_" + wood + "_block",
                wood + "_log", wood + "_stem", wood + "_block"}) {
            Block block = Registries.BLOCK.getOptionalValue(Identifier.of(planksId.getNamespace(), candidate)).orElse(null);
            if (block != null) return block;
        }
        return null;
    }

    private static Sprite side(Block block) {
        return sprite(block, Direction.NORTH);
    }

    private static Sprite top(Block block) {
        return sprite(block, Direction.UP);
    }

    /** Sprite of the block's default state model on that face, or its particle sprite. */
    private static Sprite sprite(Block block, Direction face) {
        BlockState state = block.getDefaultState();
        BakedModel model = MinecraftClient.getInstance().getBakedModelManager().getBlockModels().getModel(state);
        Random random = Random.create(42L);
        List<BakedQuad> quads = model.getQuads(state, face, random);
        if (quads.isEmpty()) quads = model.getQuads(state, null, random);
        for (BakedQuad quad : quads) {
            if (quad.getFace() == face) return quad.getSprite();
        }
        return quads.isEmpty() ? model.getParticleSprite() : quads.getFirst().getSprite();
    }
}
