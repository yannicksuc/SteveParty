package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllers;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.client.pawn.PlayerStatue;
import fr.lordfinn.steveparty.client.utils.SkinUtils;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.UUID;

/**
 * What a start tile shows on its pedestal (the front of the tile): a statue of its player sitting on it, its legs spread
 * out in front, clear of the pawn standing in the middle of the tile and behind the plate, and in
 * front of it a little wooden rack holding a cream letter plate, like a word game's tile rack: « Départ 2 · LordFinn »,
 * the number of the start tile among those linked to its Party Controller. A start tile without player shows Steve and
 * « Départ libre ».
 * <p>
 * Opaque only (the statue's skin cutout, the plate solid, its text just in front of it), drawn by the tile's block
 * entity renderer; the text only within {@link #TEXT_DISTANCE} blocks.
 */
final class StartTileStatue {
    /**
     * The statue's height (blocks, on a standard tile): a small figurine, the pawn standing in the middle of the tile
     * stays the star; on the pedestal it keeps clear of the pawn.
     */
    private static final float STATUE_HEIGHT = 1.1f;
    private static final float STATUE_SCALE = STATUE_HEIGHT * 16f / PlayerStatue.HEIGHT_PIXELS;
    /** Where the statue sits, toward the front from the tile's middle (blocks): the pedestal's middle. */
    private static final float STATUE_FORWARD = 9f / 16;
    /** The plate's text: block per text pixel at most, the widest the plate gets (blocks, on a standard tile). */
    private static final float TEXT_SCALE = 1f / 60f, MAX_WIDTH = 1.6f;
    /** A longer player name is cut (« … »): the plate stays small and its text big enough. */
    private static final int MAX_NAME = 12;
    private static final double TEXT_DISTANCE = 24;
    /** Text pixels: around the text on the plate, the rack's lip under it. */
    private static final float PAD_X = 4, PAD_Y = 3, LIP = 2;
    private static final int WOOD = 0xFF9A6A3C, WOOD_DARK = 0xFF6B4426, WOOD_SHADOW = 0xFF3A2414, CREAM = 0xFFF1E2C0, CREAM_EDGE = 0xFFCDB68B, INK = 0xFF3B2A1A;
    /** The plate leans back toward the statue (degrees). */
    private static final float LEAN = 25;
    /** Posed by LordFinn in Blockbench (Workshop art/bbmodels/statue_depart). */
    private static final PlayerPawnPose POSE = PlayerPawnPose.SIT_SPREAD;

    private StartTileStatue() {
    }

    /**
     * @param dir       the tile's rotation (8 steps of 45°)
     * @param onSupport the transformation of the level tile onto its support
     */
    static void render(BoardSpaceBlockEntity entity, ItemStack cartridge, int dir, boolean small, Matrix4f onSupport,
                       MatrixStack matrices, VertexConsumerProvider consumers, int light, ModelPart wide, ModelPart slim) {
        UUID owner = owner(cartridge);
        float size = small ? TileSize.SMALL_SCALE : 1f;
        Vector3f front = front(dir);
        float yaw = -dir * 45f;
        // The statue sitting in the middle of the pedestal, its legs toward the plate on its front edge (the pedestal:
        // 12 px, 9 px forward, 3 px high)
        Vector4f statue = at(onSupport, front, STATUE_FORWARD * size, 3f / 16);
        Vector4f plate = at(onSupport, front, 14.5f / 16 * size, 3f / 16);

        SkinTextures skin = owner != null ? SkinUtils.getSkinTextures(owner) : DefaultSkinHelper.getSkinTextures(new UUID(0, 0));
        Identifier texture = owner != null ? skin.texture() : DefaultSkinHelper.getTexture();
        ModelPart root = owner != null && skin.model() == SkinTextures.Model.SLIM ? slim : wide;
        root.traverse().forEach(ModelPart::resetTransform);
        PlayerPawnPose pose = POSE;
        PlayerStatue.pose(root, pose);
        matrices.push();
        matrices.translate(statue.x, statue.y, statue.z);
        // Facing the tile's front (the model looks toward -z before the flip)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 + yaw));
        float scale = STATUE_SCALE * size;
        matrices.scale(-scale, -scale, scale);
        PlayerStatue.transform(matrices, pose);
        matrices.translate(0, -1.501, 0);
        root.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture)), light, OverlayTexture.DEFAULT_UV);
        matrices.pop();

        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        if (camera.squaredDistanceTo(Vec3d.of(entity.getPos()).add(plate.x, plate.y, plate.z)) > TEXT_DISTANCE * TEXT_DISTANCE) return;
        plate(matrices, consumers, light, plate, yaw, size, label(entity, cartridge, owner));
    }

    /** The point {@code forward} blocks toward the tile's front from its middle, {@code up} above its base, on its support. */
    private static Vector4f at(Matrix4f onSupport, Vector3f front, float forward, float up) {
        return new Vector4f(0.5f + front.x * forward, up, 0.5f + front.z * forward, 1).mul(onSupport);
    }

    /** The rack and its letter plate, standing at {@code base} (its bottom edge's middle), facing the front. */
    private static void plate(MatrixStack matrices, VertexConsumerProvider consumers, int light, Vector4f base, float yaw, float size, Text text) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int textWidth = font.getWidth(text);
        float scale = Math.min(TEXT_SCALE, MAX_WIDTH / (textWidth + 2 * PAD_X)) * size;
        float w = textWidth + 2 * PAD_X, h = 8 + 2 * PAD_Y;
        matrices.push();
        matrices.translate(base.x, base.y, base.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-LEAN));
        // Text space: x right, y down, the plate's bottom edge at y = 0, its face toward +z
        matrices.scale(scale, -scale, scale);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer fill = consumers.getBuffer(RenderLayer.getTextBackground());
        // The rack: a wooden lip along the bottom, its back behind the plate
        quad(fill, matrix, -w / 2 - 1, -h - 1, w / 2 + 1, LIP, -0.6f, WOOD_DARK, light);
        quad(fill, matrix, -w / 2 - 1, -LIP, w / 2 + 1, LIP - 1, 0.6f, WOOD, light);
        quad(fill, matrix, -w / 2 - 1, LIP - 1, w / 2 + 1, LIP, 0.6f, WOOD_SHADOW, light);
        // The cream plate, a darker edge at its bottom (a letter tile's bevel)
        quad(fill, matrix, -w / 2, -h, w / 2, -LIP, -0.3f, CREAM_EDGE, light);
        quad(fill, matrix, -w / 2, -h, w / 2, -LIP - 1, -0.2f, CREAM, light);
        font.draw(text, -textWidth / 2f, -h + PAD_Y - LIP / 2f + 1, INK, false, matrix, consumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        matrices.pop();
    }

    /** A rectangle (x0, y0)-(x1, y1) at depth {@code z} (text space: more is nearer the front), both sides. */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z, int argb, int light) {
        consumer.vertex(matrix, x0, y0, z).color(argb).light(light);
        consumer.vertex(matrix, x0, y1, z).color(argb).light(light);
        consumer.vertex(matrix, x1, y1, z).color(argb).light(light);
        consumer.vertex(matrix, x1, y0, z).color(argb).light(light);
        consumer.vertex(matrix, x1, y0, z).color(argb).light(light);
        consumer.vertex(matrix, x1, y1, z).color(argb).light(light);
        consumer.vertex(matrix, x0, y1, z).color(argb).light(light);
        consumer.vertex(matrix, x0, y0, z).color(argb).light(light);
    }

    /** « Départ 2 · LordFinn », « Départ · LordFinn » (linked to no controller), « Départ libre ». */
    private static Text label(BoardSpaceBlockEntity entity, ItemStack cartridge, @Nullable UUID owner) {
        if (owner == null) return Text.translatable("block.steveparty.start_plaque.free");
        String name = cartridge.get(ModComponents.TB_START_OWNER_NAME);
        if (name == null || name.isEmpty()) name = onlineName(owner);
        if (name.length() > MAX_NAME) name = name.substring(0, MAX_NAME - 1) + "…";
        int number = number(entity.getPos());
        return number > 0 ? Text.translatable("block.steveparty.start_plaque.owned", number, name)
                : Text.translatable("block.steveparty.start_plaque.owned_no_number", name);
    }

    private static String onlineName(UUID owner) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        PlayerListEntry entry = handler == null ? null : handler.getPlayerListEntry(owner);
        return entry == null ? "?" : entry.getProfile().getName();
    }

    /** Its number among the start tiles linked to a Party Controller around (1 for the first), 0 for none. */
    private static int number(BlockPos pos) {
        for (PartyControllerEntity controller : PartyControllers.onClient()) {
            int index = controller.getStartLinks().indexOf(pos);
            if (index >= 0) return index + 1;
        }
        return 0;
    }

    private static @Nullable UUID owner(ItemStack cartridge) {
        String owner = cartridge.get(ModComponents.TB_START_OWNER);
        if (owner == null || owner.isEmpty()) return null;
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Side the tile faces, in 8 steps of 45°: rotation 0 = placed while looking north, faces south; the rotation turns
     * clockwise (2 = west, 4 = north, 6 = east), like the blockstate "y"; odd ones are the diagonals of tile_start_45.
     */
    private static Vector3f front(int rot) {
        double angle = Math.toRadians(rot * 45);
        return new Vector3f((float) -Math.sin(angle), 0, (float) Math.cos(angle));
    }
}
