package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllers;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrencyText;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResults;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.podium.Podiums;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The end of a party over the podiums linked to its Party Controller: a gold plate high over them, facing the camera,
 * with the ranking (place, name, stars, coins), shown until the next party starts. Same plate as a board space's panel
 * (TilePanel: one pass, the plate first, the text and icons in front of it), drawn after the entities.
 */
public final class EndPanel {
    /** Text pixels: the padding, a row, an icon, the gap after it. */
    private static final int PAD = 7, ROW = 12, ICON = 9, GAP = 3, COLUMN_GAP = 8;
    private static final float TITLE_SCALE = 1.5f;
    private static final int TITLE_HEIGHT = Math.round(8 * TITLE_SCALE);
    /** Shown from this far (blocks). */
    private static final double DISTANCE = 64;
    /** Text pixel size near by (block); it grows with the distance, up to {@link #MAX_GROW} times. */
    private static final float SCALE = 1f / 40f, MAX_GROW = 3f;
    /** Over the podiums' tops, room for the pawns standing on them (blocks). */
    private static final double ABOVE = 2.2;
    /** The podium columns looked for around a linked one (the client has no podium groups). */
    private static final int MAX_COLUMNS = 16;
    private static final int TITLE_COLOR = 0xFF7A5200, RULE = 0x553F3F3F;
    private static final int[] PLACE_COLORS = {0xFFB8860B, 0xFF6E6E6E, 0xFF9A5B2E};
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private EndPanel() {
    }

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(EndPanel::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        MatrixStack matrices = context.matrixStack();
        if (world == null || matrices == null || client.options.hudHidden) return;
        Camera camera = context.camera();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        for (PartyControllerEntity controller : PartyControllers.onClient()) {
            if (controller.getWorld() != world || controller.isRemoved()) continue;
            List<PartyResults.Place> places = controller.getEndResults();
            if (places.isEmpty() || controller.getPodiumLinks().isEmpty() || controller.getPartyData().isStarted()) continue;
            Vec3d anchor = anchor(world, controller.getPodiumLinks());
            if (anchor == null || anchor.squaredDistanceTo(camera.getPos()) > DISTANCE * DISTANCE) continue;
            double distance = Math.sqrt(anchor.squaredDistanceTo(camera.getPos()));
            float scale = SCALE * (float) Math.clamp(distance / 8.0, 1.0, MAX_GROW);
            draw(matrices, consumers, camera, world, anchor, places,
                    PartyCurrencyText.item(controller, PartyCurrency.STAR), PartyCurrencyText.item(controller, PartyCurrency.COIN), scale);
        }
    }

    /** The middle over the tops of the linked podium columns and those touching them, {@link #ABOVE} higher. */
    private static Vec3d anchor(ClientWorld world, List<BlockPos> linked) {
        Set<BlockPos> bottoms = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : linked) {
            if (!world.isChunkLoaded(pos) || !PodiumBlock.isPodium(world.getBlockState(pos))) continue;
            BlockPos bottom = PodiumBlock.bottomOf(world, pos);
            if (bottoms.add(bottom)) queue.add(bottom);
        }
        while (!queue.isEmpty() && bottoms.size() < MAX_COLUMNS) {
            BlockPos column = queue.poll();
            for (Direction side : Direction.Type.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = column.offset(side).up(dy);
                    if (!PodiumBlock.isPodium(world.getBlockState(next))) continue;
                    BlockPos bottom = PodiumBlock.bottomOf(world, next);
                    if (bottoms.add(bottom)) queue.add(bottom);
                }
            }
        }
        if (bottoms.isEmpty()) return null;
        double x = 0, z = 0, top = Double.NEGATIVE_INFINITY;
        for (BlockPos bottom : bottoms) {
            x += bottom.getX() + 0.5;
            z += bottom.getZ() + 0.5;
            BlockPos topBlock = PodiumBlock.topOf(world, bottom);
            boolean full = world.getBlockState(topBlock).get(PodiumBlock.FULL);
            top = Math.max(top, topBlock.getY() + (full ? 1.0 : 0.5));
        }
        return new Vec3d(x / bottoms.size(), top + ABOVE, z / bottoms.size());
    }

    /** The panel, its bottom edge's middle at {@code bottom}. */
    private static void draw(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Camera camera, ClientWorld world,
                             Vec3d bottom, List<PartyResults.Place> places, ItemStack star, ItemStack coin, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;
        Text title = Text.translatable("hud.steveparty.party_end.title");
        // Columns: the place, the name, then the stars and the coins (an icon and a number each)
        int placeWidth = 0, nameWidth = 0, starWidth = 0, coinWidth = 0;
        for (PartyResults.Place place : places) {
            placeWidth = Math.max(placeWidth, font.getWidth(Podiums.placeText(place.place())));
            nameWidth = Math.max(nameWidth, font.getWidth(place.name()));
            starWidth = Math.max(starWidth, font.getWidth(String.valueOf(place.stars())));
            coinWidth = Math.max(coinWidth, font.getWidth(String.valueOf(place.coins())));
        }
        int rowWidth = placeWidth + GAP * 2 + nameWidth + COLUMN_GAP + ICON + 1 + starWidth + COLUMN_GAP + ICON + 1 + coinWidth;
        int titleWidth = Math.round(font.getWidth(title) * TITLE_SCALE);
        int width = Math.max(rowWidth, titleWidth) + 2 * PAD;
        int height = PAD + TITLE_HEIGHT + PAD / 2 + 4 + places.size() * ROW + PAD - 3;
        matrices.push();
        matrices.translate(bottom.x - camera.getPos().x, bottom.y + height * scale / 2 - camera.getPos().y, bottom.z - camera.getPos().z);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);
        float left = -width / 2f, top = -height / 2f;
        WorldDraw.plate(matrices, consumers, WorldDraw.Plate.GOLD, left, top, -left, -top);
        // The title, centred and bigger
        matrices.push();
        matrices.translate(-titleWidth / 2f, top + PAD, 0);
        matrices.scale(TITLE_SCALE, TITLE_SCALE, 1);
        font.draw(title, 0, 0, TITLE_COLOR, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
        matrices.pop();
        float y = top + PAD + TITLE_HEIGHT + PAD / 2f;
        WorldDraw.rule(matrices, consumers, left + PAD, -left - PAD, y, RULE);
        y += 4;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float x0 = left + PAD;
        for (PartyResults.Place place : places) {
            int color = place.place() <= PLACE_COLORS.length ? PLACE_COLORS[place.place() - 1] : WorldDraw.PLATE_TEXT;
            Text rank = Podiums.placeText(place.place());
            // The place right-aligned in its column, the name after it
            font.draw(rank, x0 + placeWidth - font.getWidth(rank), y, color, false, matrix, consumers,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
            float x = x0 + placeWidth + GAP * 2;
            font.draw(place.name(), x, y, WorldDraw.PLATE_TEXT, false, matrix, consumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
            x += nameWidth + COLUMN_GAP;
            x = amount(matrices, consumers, world, font, star, place.stars(), x, y, starWidth);
            amount(matrices, consumers, world, font, coin, place.coins(), x + COLUMN_GAP, y, coinWidth);
            y += ROW;
        }
        consumers.draw();
        matrices.pop();
    }

    /** {@code icon} then {@code value}, right-aligned on {@code width}. @return where the next column starts */
    private static float amount(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, ClientWorld world, TextRenderer font,
                                ItemStack icon, int value, float x, float y, int width) {
        matrices.push();
        matrices.translate(x + ICON / 2f, y + 3.5f, 0.5f);
        matrices.scale(ICON, -ICON, 0.5f);
        MinecraftClient.getInstance().getItemRenderer().renderItem(icon, ModelTransformationMode.GUI, LIGHT, OverlayTexture.DEFAULT_UV,
                matrices, consumers, world, 0);
        matrices.pop();
        String text = String.valueOf(value);
        float textX = x + ICON + 1 + width - font.getWidth(text);
        font.draw(text, textX, y, WorldDraw.PLATE_TEXT, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
        return x + ICON + 1 + width;
    }
}
