package fr.lordfinn.steveparty.client.pipe;

import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeGeometry;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeShape;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A pipe of one kind and colour: the quads of each shape ({@link PipeGeometry}), built the first time that shape is
 * drawn and kept. Plain quads (the vanilla way), so that chunks, items, falling blocks and every renderer draw it.
 * The item is a pipe standing on the ground (a mouth on top, capped at the bottom: a travel pipe).
 */
public class PipeBakedModel implements BakedModel {
    private static final int ITEM_KEY = PipeShape.key(0, Direction.DOWN);
    private static final Direction[] FACES = Direction.values();

    private final BakedModel base;
    private final PipeKind kind;
    /** By {@link PipeGeometry#OUTER} .. {@link PipeGeometry#RIM}. */
    private final Sprite[] sprites;
    /** Quads of each shape key: [cull face ordinal] then [6] for the unculled ones. */
    private final Map<Integer, List<BakedQuad>[]> quads = new ConcurrentHashMap<>();

    public PipeBakedModel(BakedModel base, PipeKind kind, Sprite[] sprites) {
        this.base = base;
        this.kind = kind;
        this.sprites = sprites;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random) {
        int key = state != null && state.getBlock() instanceof PipeBlock ? PipeShape.key(state) : ITEM_KEY;
        List<BakedQuad>[] lists = quads.computeIfAbsent(key, this::build);
        return lists[face == null ? 6 : face.ordinal()];
    }

    @SuppressWarnings("unchecked")
    private List<BakedQuad>[] build(int key) {
        List<BakedQuad>[] lists = new List[7];
        for (int i = 0; i < 7; i++) lists[i] = new ArrayList<>();
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) return lists;
        MeshBuilder builder = renderer.meshBuilder();
        QuadEmitter emitter = builder.getEmitter();
        PipeGeometry.build(key, kind, (facing, corners, uvs, sprite, cull) -> {
            for (int i = 0; i < 4; i++) {
                emitter.pos(i, corners[i][0] / 16f, corners[i][1] / 16f, corners[i][2] / 16f);
                emitter.uv(i, uvs[i][0] / 16f, uvs[i][1] / 16f);
            }
            emitter.color(-1, -1, -1, -1);
            emitter.nominalFace(facing);
            emitter.cullFace(cull);
            emitter.colorIndex(-1);
            emitter.tag(sprite);
            emitter.spriteBake(sprites[sprite], MutableQuadView.BAKE_NORMALIZED);
            emitter.emit();
        });
        Mesh mesh = builder.build();
        mesh.forEach(quad -> {
            Direction cull = quad.cullFace();
            lists[cull == null ? 6 : cull.ordinal()].add(quad.toBakedQuad(sprites[quad.tag()]));
        });
        for (int i = 0; i < 7; i++) lists[i] = List.copyOf(lists[i]);
        return lists;
    }

    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean hasDepth() { return true; }
    @Override public boolean isSideLit() { return true; }
    @Override public boolean isBuiltin() { return false; }
    @Override public Sprite getParticleSprite() { return base.getParticleSprite(); }
    @Override public ModelTransformation getTransformation() { return base.getTransformation(); }
    @Override public ModelOverrideList getOverrides() { return base.getOverrides(); }
}
