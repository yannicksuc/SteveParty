package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.client.pawn.PlayerStatue;
import fr.lordfinn.steveparty.client.utils.DynamicTextureCache;
import fr.lordfinn.steveparty.client.utils.SkinUtils;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnPose;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.podium.PodiumOccupant;
import fr.lordfinn.steveparty.podium.PodiumSignal;
import fr.lordfinn.steveparty.podium.Podiums;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.MutableText;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import fr.lordfinn.steveparty.stencil.StencilShape;
import fr.lordfinn.steveparty.utils.Easing;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.nio.ByteBuffer;

/**
 * What a podium column shows, drawn on its top block (the column's bottom block keeps it):
 * <ul>
 *     <li><b>Who is registered on it</b>: a small statue of the player (the player pawns' one, see
 *     {@code PlayerStatue}), with his skin, standing on the top in a pose picked by the server and facing the
 *     podium's front. It pops in when he registers and bobs a little; over it, his place and his name (in the
 *     colour of his team, with its name, in a team mini-game). During a mini-game the columns of the same height are
 *     one place: each shows the figure it was given (the player on the column he took, a member of the team on each
 *     column), or no figure at all, only the label of who holds the place (see {@code PodiumOccupant}).</li>
 *     <li>With the Wrench in hand: its mode (what a pulse or a click does). Just changed: the column it acts on lights
 *     up and a Steve statue stands a few seconds where a player would go ({@link PodiumPreviews}); opaque lines and an
 *     opaque figure, both writing depth: nothing see-through here.</li>
 *     <li><b>The pattern tagged on its banner</b> (see {@code PodiumBanner}), in the dye's colour over the front face
 *     of the column's top: 8x8 in the middle of the banner hanging over one block of height (across the top slab and
 *     the block under it when the column ends with a slab), 4x3 on the label of a slab alone (see
 *     {@link PodiumBlock#bannerHangs}).</li>
 * </ul>
 */
public class PodiumRenderer implements BlockEntityRenderer<PodiumBlockEntity> {
    /** The figure: half the size of a player. */
    private static final float FIGURE_SCALE = 0.5f;
    private static final float POP_TICKS = 8;
    /** The names are only shown from this close (blocks). */
    private static final double LABEL_DISTANCE = 24;

    private final ModelPart wide, slim;
    private final BlockEntityRenderDispatcher dispatcher;
    private static final float OUT = 0.002f;
    private static final int MAX_CACHED = 64;

    private record Key(ByteBuffer shape, int rgb, boolean slab) {
    }

    private static final DynamicTextureCache<Key> TEXTURES = new DynamicTextureCache<>(MAX_CACHED);

    public PodiumRenderer(BlockEntityRendererFactory.Context context) {
        this.wide = context.getLayerModelPart(EntityModelLayers.PLAYER);
        this.slim = context.getLayerModelPart(EntityModelLayers.PLAYER_SLIM);
        this.dispatcher = context.getRenderDispatcher();
    }

    /** The figure and its name stand above the block. */
    @Override
    public boolean rendersOutsideBoundingBox(PodiumBlockEntity entity) {
        return true;
    }

    private void renderOccupant(PodiumBlockEntity entity, PodiumBlockEntity master, BlockState state, float tickDelta,
                                MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        World world = entity.getWorld();
        PodiumOccupant occupant = master.getOccupant();
        float surface = state.get(PodiumBlock.FULL) ? 1f : 0.5f;
        Vec3d camera = dispatcher.camera.getPos();
        boolean near = camera.squaredDistanceTo(entity.getPos().toCenterPos()) <= LABEL_DISTANCE * LABEL_DISTANCE;
        float labelY = surface + 0.2f;
        PodiumOccupant.Figure figure = occupant == null ? null : occupant.figure();
        if (figure != null) {
            float age = (float) (world.getTime() - occupant.since()) + tickDelta;
            float pop = age < 0 || age >= POP_TICKS ? 1f : Easing.easeOutBack(age / POP_TICKS);
            float time = world.getTime() + tickDelta + (entity.getPos().getX() * 7 + entity.getPos().getZ() * 13) % 40;
            float bob = MathHelper.sin(time * 0.12f) * 0.025f;
            SkinTextures skin = SkinUtils.getSkinTextures(figure.player());
            drawFigure(entity, state, skin, master.getFigurePose(), surface + bob, pop, matrices, vertexConsumers);
            labelY = surface + 2 * FIGURE_SCALE + 0.16f;
        }
        if (occupant != null && near) {
            // The place, the team, then who stands here (a column without figure: who holds the place, a team by its name only)
            MutableText label = Podiums.placeText(occupant.place()).append(" ");
            int color = figure == null ? 0xFFC8C8C8 : 0xFFFFFFFF;
            String shown = figure != null ? figure.name() : occupant.team() < 0 ? occupant.name() : null;
            if (occupant.team() >= 0) {
                MiniGamePipeRole role = MiniGamePipeRole.ofTeam(occupant.team());
                label.append(role.text());
                if (shown != null) label.append(" · ");
                color = 0xFF000000 | role.color();
            }
            if (shown != null) label.append(shown);
            for (String other : occupant.more()) label.append(", ").append(other);
            WorldLabels.draw(matrices, vertexConsumers, dispatcher, 0.5, labelY, 0.5, label, color, 0x60000000, 0, 1f / 80f);
            labelY += 0.14f;
        }
        // The Wrench shows what a redstone pulse does to the column looked at
        if (near && WorldLabels.holdingWrench() && WorldLabels.lookingAtColumn(entity.getPos().getX(), entity.getPos().getZ(),
                PodiumBlock.bottomOf(world, entity.getPos()).getY(), entity.getPos().getY())) {
            WorldLabels.draw(matrices, vertexConsumers, dispatcher, 0.5, labelY, 0.5,
                    master.getSignal().text(PodiumBlock.answersRedstone(world, entity.getPos())), 0xFFFFE08A, 0x60000000, 0, 1f / 80f);
        }
    }

    /** A small statue of a player (half his size) standing on the top, facing the podium's front. */
    private void drawFigure(PodiumBlockEntity entity, BlockState state, SkinTextures skin, PlayerPawnPose pose, float y, float scaleBy,
                            MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        drawFigure(entity, state, skin, pose, y, scaleBy, 0xFFFFFFFF, matrices, vertexConsumers);
    }

    private void drawFigure(PodiumBlockEntity entity, BlockState state, SkinTextures skin, PlayerPawnPose pose, float y, float scaleBy, int color,
                            MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        ModelPart root = skin.model() == SkinTextures.Model.SLIM ? slim : wide;
        root.traverse().forEach(ModelPart::resetTransform);
        PlayerStatue.pose(root, pose);
        int light = WorldRenderer.getLightmapCoordinates(entity.getWorld(), entity.getPos().up());
        matrices.push();
        matrices.translate(0.5, y, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - state.get(PodiumBlock.FACING).asRotation()));
        float scale = FIGURE_SCALE * PlayerStatue.AS_HIGH_AS_A_PLAYER * scaleBy;
        matrices.scale(-scale, -scale, scale);
        PlayerStatue.transform(matrices, pose);
        matrices.translate(0, -1.501, 0);
        root.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(skin.texture())), light, OverlayTexture.DEFAULT_UV, color);
        matrices.pop();
    }

    private static SkinTextures steve;

    /** Steve's skin: the default skin found for the first id giving the wide Steve. */
    private static SkinTextures steve() {
        if (steve == null) {
            for (int i = 0; i < 64 && steve == null; i++) {
                SkinTextures skin = DefaultSkinHelper.getSkinTextures(new java.util.UUID(0, i));
                if (skin.texture().getPath().endsWith("wide/steve.png")) steve = skin;
            }
            if (steve == null) steve = DefaultSkinHelper.getSkinTextures(new java.util.UUID(0, 0));
        }
        return steve;
    }

    /** How long the statue takes to come and to go (ticks). */
    private static final float STATUE_IN = 8, STATUE_OUT = 6;

    /**
     * The mode just changed with the Wrench (see {@link PodiumPreviews}): the column lights up (an opaque outline round
     * the whole column, blinking slowly), and a Steve statue stands where a player would go, unless someone stands there.
     */
    private void renderPreview(PodiumBlockEntity entity, PodiumBlockEntity master, BlockState state, float tickDelta,
                               MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        World world = entity.getWorld();
        PodiumPreviews.Shown shown = PodiumPreviews.at(master.getPos(), world.getTime());
        if (shown == null) return;
        float surface = state.get(PodiumBlock.FULL) ? 1f : 0.5f;
        float age = world.getTime() - shown.since() + tickDelta, left = shown.until() - world.getTime() - tickDelta;
        PodiumSignal[] modes = PodiumSignal.values();
        int rgb = modes[Math.floorMod(shown.mode(), modes.length)].color();
        // Blinks: full colour, then a darker one, twice a second
        float blink = (MathHelper.sin(age * 0.6f) + 1) / 2;
        float shade = 0.65f + 0.35f * blink;
        double bottom = master.getPos().getY() - entity.getPos().getY();
        // A dark line behind the coloured one, so that a gold outline reads on a gold podium; its foot just above the ground
        WorldRenderer.drawBox(matrices, vertexConsumers.getBuffer(RenderLayer.getLines()), -0.035, bottom + 0.01, -0.035, 1.035, surface + 0.035, 1.035,
                0.16f, 0.16f, 0.16f, 1f);
        WorldRenderer.drawBox(matrices, vertexConsumers.getBuffer(RenderLayer.getLines()), -0.02, bottom + 0.02, -0.02, 1.02, surface + 0.02, 1.02,
                ((rgb >> 16) & 0xFF) / 255f * shade, ((rgb >> 8) & 0xFF) / 255f * shade, (rgb & 0xFF) / 255f * shade, 1f);
        PodiumOccupant occupant = master.getOccupant();
        if (!shown.statue() || (occupant != null && occupant.figure() != null)) return;
        float in = age >= STATUE_IN ? 1f : Easing.easeOutBack(Math.max(0, age) / STATUE_IN);
        float out = left >= STATUE_OUT ? 1f : Math.max(0, left) / STATUE_OUT;
        PlayerPawnPose pose = PlayerPawnPose.podiumPose((int) shown.since() * 31 + master.getPos().hashCode());
        // A statue, not a player: tinted in the mode's colour (opaque: nothing see-through here)
        int tint = ColorHelper.Argb.lerp(0.45f, 0xFFFFFFFF, 0xFF000000 | rgb);
        drawFigure(entity, state, steve(), pose, surface, in * out, tint, matrices, vertexConsumers);
    }

    public static void registerReloadListener() {
        DynamicTextureCache.onResourceReload("podium_banner_textures", TEXTURES::clear);
    }

    @Override
    public void render(PodiumBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        World world = entity.getWorld();
        BlockState state = entity.getCachedState();
        if (world == null || !PodiumBlock.isPodium(state) || !state.get(PodiumBlock.TOP)) return;
        PodiumBlockEntity master = PodiumBlock.master(world, entity.getPos());
        if (master == null) return;
        renderOccupant(entity, master, state, tickDelta, matrices, vertexConsumers);
        renderPreview(entity, master, state, tickDelta, matrices, vertexConsumers);
        TileStampComponent stamp = master.getBannerStamp();
        if (stamp == null) return;
        boolean slab = !PodiumBlock.bannerHangs(state);
        Identifier texture = texture(stamp, slab);
        Direction front = state.get(PodiumBlock.FACING);
        int frontLight = WorldRenderer.getLightmapCoordinates(world, entity.getPos().offset(front));

        // Seen from the front, the texture's left edge is on the side the facing turns clockwise to
        Direction left = front.rotateYClockwise();
        // One block of height under the top of the column, or the slab alone
        float top = state.get(PodiumBlock.FULL) ? 1f : 0.5f;
        float bottom = slab ? 0f : top - 1f;
        float fx = 0.5f + front.getOffsetX() * (0.5f + OUT), fz = 0.5f + front.getOffsetZ() * (0.5f + OUT);
        float lx = left.getOffsetX() * 0.5f, lz = left.getOffsetZ() * 0.5f;
        float vMax = slab ? 0.5f : 1f;
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(texture));
        MatrixStack.Entry entry = matrices.peek();
        float nx = front.getOffsetX(), nz = front.getOffsetZ();
        vertex(consumer, entry, fx + lx, bottom, fz + lz, 0, vMax, frontLight, nx, nz);
        vertex(consumer, entry, fx - lx, bottom, fz - lz, 1, vMax, frontLight, nx, nz);
        vertex(consumer, entry, fx - lx, top, fz - lz, 1, 0, frontLight, nx, nz);
        vertex(consumer, entry, fx + lx, top, fz + lz, 0, 0, frontLight, nx, nz);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z, float u, float v,
                               int light, float nx, float nz) {
        consumer.vertex(entry.getPositionMatrix(), x, y, z).color(255, 255, 255, 255).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, 0, nz);
    }

    private static Identifier texture(TileStampComponent stamp, boolean slab) {
        byte[] shape = stamp.shapeArray();
        int rgb = stamp.color().getEntityColor();
        return TEXTURES.get(new Key(ByteBuffer.wrap(shape), rgb, slab), key -> register(shape, rgb, slab));
    }

    /** A 16x16 texture of the front face, transparent but for the pattern shrunk into the banner's middle. */
    private static Identifier register(byte[] shape, int rgb, boolean slab) {
        NativeImage image = new NativeImage(16, 16, true);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) image.setColor(x, y, 0);
        // full: cols 4..11 x rows 4..11 (hanging banner); slab: cols 6..9 x rows 4..6 (inside the label's border)
        int x0 = slab ? 6 : 4, y0 = 4, w = slab ? 4 : 8, h = slab ? 3 : 8;
        for (int cx = 0; cx < w; cx++) {
            for (int cy = 0; cy < h; cy++) {
                if (anyIn(shape, cx * StencilShape.SIDE / w, (cx + 1) * StencilShape.SIDE / w,
                        cy * StencilShape.SIDE / h, (cy + 1) * StencilShape.SIDE / h))
                    image.setColor(x0 + cx, y0 + cy, ColorHelper.Abgr.toAbgr(0xFF000000 | rgb));
            }
        }
        return MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("podium_banner", new NativeImageBackedTexture(image));
    }

    private static boolean anyIn(byte[] shape, int xFrom, int xTo, int yFrom, int yTo) {
        for (int x = xFrom; x < xTo; x++) for (int y = yFrom; y < yTo; y++) if (shape[StencilShape.index(x, y)] != 0) return true;
        return false;
    }
}
