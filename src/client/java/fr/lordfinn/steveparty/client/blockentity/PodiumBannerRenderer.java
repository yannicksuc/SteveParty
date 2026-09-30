package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The pattern tagged on a podium's banner (see {@code PodiumBanner}), drawn in the dye's colour over the front face of
 * the column's top block: 8x8 in the middle of the hanging banner of a full block, 4x3 on the label of a slab. The look
 * is kept by the column's bottom block.
 */
public class PodiumBannerRenderer implements BlockEntityRenderer<PodiumBlockEntity> {
    private static final float OUT = 0.002f;
    private static final int MAX_CACHED = 64;

    private record Key(ByteBuffer shape, int rgb, boolean slab) {
    }

    private static final Map<Key, Identifier> TEXTURES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Identifier> eldest) {
            if (size() <= MAX_CACHED) return false;
            MinecraftClient.getInstance().getTextureManager().destroyTexture(eldest.getValue());
            return true;
        }
    };

    public PodiumBannerRenderer(BlockEntityRendererFactory.Context context) {
    }

    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Steveparty.id("podium_banner_textures");
            }

            @Override
            public void reload(ResourceManager manager) {
                TEXTURES.values().forEach(id -> MinecraftClient.getInstance().getTextureManager().destroyTexture(id));
                TEXTURES.clear();
            }
        });
    }

    @Override
    public void render(PodiumBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        World world = entity.getWorld();
        BlockState state = entity.getCachedState();
        if (world == null || !PodiumBlock.isPodium(state) || !state.get(PodiumBlock.TOP)) return;
        PodiumBlockEntity master = PodiumBlock.master(world, entity.getPos());
        TileStampComponent stamp = master == null ? null : master.getBannerStamp();
        if (stamp == null) return;
        boolean slab = !state.get(PodiumBlock.FULL);
        Identifier texture = texture(stamp, slab);
        Direction front = state.get(PodiumBlock.FACING);
        int frontLight = WorldRenderer.getLightmapCoordinates(world, entity.getPos().offset(front));

        // Seen from the front, the texture's left edge is on the side the facing turns clockwise to
        Direction left = front.rotateYClockwise();
        float height = slab ? 0.5f : 1f;
        float fx = 0.5f + front.getOffsetX() * (0.5f + OUT), fz = 0.5f + front.getOffsetZ() * (0.5f + OUT);
        float lx = left.getOffsetX() * 0.5f, lz = left.getOffsetZ() * 0.5f;
        float vMax = slab ? 0.5f : 1f;
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(texture));
        MatrixStack.Entry entry = matrices.peek();
        float nx = front.getOffsetX(), nz = front.getOffsetZ();
        vertex(consumer, entry, fx + lx, 0, fz + lz, 0, vMax, frontLight, nx, nz);
        vertex(consumer, entry, fx - lx, 0, fz - lz, 1, vMax, frontLight, nx, nz);
        vertex(consumer, entry, fx - lx, height, fz - lz, 1, 0, frontLight, nx, nz);
        vertex(consumer, entry, fx + lx, height, fz + lz, 0, 0, frontLight, nx, nz);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z, float u, float v,
                               int light, float nx, float nz) {
        consumer.vertex(entry.getPositionMatrix(), x, y, z).color(255, 255, 255, 255).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, 0, nz);
    }

    private static Identifier texture(TileStampComponent stamp, boolean slab) {
        byte[] shape = stamp.shapeArray();
        int rgb = stamp.color().getEntityColor();
        return TEXTURES.computeIfAbsent(new Key(ByteBuffer.wrap(shape), rgb, slab), key -> register(shape, rgb, slab));
    }

    /** A 16x16 texture of the front face, transparent but for the pattern shrunk into the banner's middle. */
    private static Identifier register(byte[] shape, int rgb, boolean slab) {
        NativeImage image = new NativeImage(16, 16, true);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) image.setColorArgb(x, y, 0);
        // full: cols 4..11 x rows 4..11 (hanging banner); slab: cols 6..9 x rows 4..6 (inside the label's border)
        int x0 = slab ? 6 : 4, y0 = 4, w = slab ? 4 : 8, h = slab ? 3 : 8;
        for (int cx = 0; cx < w; cx++) {
            for (int cy = 0; cy < h; cy++) {
                if (anyIn(shape, cx * StencilShape.SIDE / w, (cx + 1) * StencilShape.SIDE / w,
                        cy * StencilShape.SIDE / h, (cy + 1) * StencilShape.SIDE / h))
                    image.setColorArgb(x0 + cx, y0 + cy, 0xFF000000 | rgb);
            }
        }
        return MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("podium_banner", new NativeImageBackedTexture(image));
    }

    private static boolean anyIn(byte[] shape, int xFrom, int xTo, int yFrom, int yTo) {
        for (int x = xFrom; x < xTo; x++) for (int y = yFrom; y < yTo; y++) if (shape[StencilShape.index(x, y)] != 0) return true;
        return false;
    }
}
