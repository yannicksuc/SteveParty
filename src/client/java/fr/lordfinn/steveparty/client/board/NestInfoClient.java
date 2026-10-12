package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlockEntity;
import fr.lordfinn.steveparty.client.gui.FloatingInfo;
import fr.lordfinn.steveparty.client.gui.HudDepth;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.gui.UiText;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What the Magpie Nest looked at holds (in a party or not): a small panel over it, as a board space's
 * ({@link TilePanel}), its kinds in their slots' order, each its icon and its count, {@link #PAGE} at a time. Looked
 * at longer, it turns to the next ones now and then, round and round, dots under the row telling where it is. No
 * words. Farther than {@link #PANEL_DISTANCE} blocks or out of sight, its card on the screen instead
 * ({@link FloatingInfo}). An empty nest shows nothing.
 * <p>
 * What it holds comes with the nest ({@link MagpieNestBlockEntity#getShownContents}): nothing is asked. The aim is
 * worked out once per tick; a frame only draws.
 */
public final class NestInfoClient {
    /** The nest is looked at up to this far; its panel drawn in the world up to {@link #PANEL_DISTANCE}. */
    private static final double AIM_DISTANCE = 24, PANEL_DISTANCE = 16;
    /** Kinds shown at once. */
    static final int PAGE = 4;
    /** Ticks looked at before the first turn, then between two. */
    private static final int FIRST_TURN = 50, TURN = 40;
    /** Text pixels: the padding, an icon, from an icon to its count, between two kinds, the dots' size and gap. */
    private static final int PAD = 5, ICON = 9, ICON_GAP = 2, CELL_GAP = 6, DOT = 2, DOT_GAP = 3, DOTS_GAP = 3;
    /** Text pixel size near by (block); it grows with the distance, up to {@link #MAX_GROW} times. */
    private static final float SCALE = 1f / 48f, MAX_GROW = 2.5f;
    /** Above the nest's block (a Pie on its rim stays clear). */
    private static final double ABOVE = 1.15;
    private static final int DOT_ON = 0xFF3F3F3F, DOT_OFF = 0x553F3F3F;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private record CardKey(BlockPos pos) {
    }

    private static @Nullable BlockPos looked;
    private static int lookedTicks;

    private NestInfoClient() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(NestInfoClient::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(NestInfoClient::render);
        HudRenderCallback.EVENT.register(NestInfoClient::renderHud);
    }

    // ---------------------------------------------------------------- per tick

    private static void tick(MinecraftClient client) {
        BlockPos now = aimed(client);
        if (now != null && now.equals(looked)) {
            lookedTicks++;
        } else {
            looked = now;
            lookedTicks = 0;
        }
    }

    /** The nest under the crosshair (blocks only: a Pie on it does not hide it), or null. */
    private static @Nullable BlockPos aimed(MinecraftClient client) {
        Entity viewer = client.getCameraEntity();
        if (client.world == null || viewer == null) return null;
        HitResult hit = viewer.raycast(AIM_DISTANCE, 1f, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return null;
        return client.world.getBlockEntity(block.getBlockPos()) instanceof MagpieNestBlockEntity ? block.getBlockPos().toImmutable() : null;
    }

    /** What the nest looked at shows (null: none, or empty). */
    private static @Nullable List<ItemStack> contents(MinecraftClient client) {
        BlockPos pos = looked;
        if (pos == null || client.world == null || !(client.world.getBlockEntity(pos) instanceof MagpieNestBlockEntity nest)) return null;
        List<ItemStack> contents = nest.getShownContents();
        return contents.isEmpty() ? null : contents;
    }

    private static int pages(List<ItemStack> contents) {
        return (contents.size() + PAGE - 1) / PAGE;
    }

    /** The page shown: the first, then the next ones in turn while it is looked at. */
    private static int page(List<ItemStack> contents) {
        int pages = pages(contents);
        if (pages <= 1 || lookedTicks < FIRST_TURN) return 0;
        return (1 + (lookedTicks - FIRST_TURN) / TURN) % pages;
    }

    /** A count, short: up to 9999 as is, then in thousands (« 12k »). */
    static String count(int count) {
        return count < 10_000 ? Integer.toString(count) : count / 1000 + "k";
    }

    /** Text pixels: a kind's icon and count. */
    private static int cellWidth(TextRenderer font, ItemStack stack) {
        return ICON + ICON_GAP + font.getWidth(count(stack.getCount()));
    }

    /** Text pixels: the row of the widest page (so that turning pages does not resize it). */
    private static int rowWidth(TextRenderer font, List<ItemStack> contents) {
        int widest = 0;
        for (int start = 0; start < contents.size(); start += PAGE) {
            int width = -CELL_GAP;
            for (int i = start; i < Math.min(contents.size(), start + PAGE); i++) width += cellWidth(font, contents.get(i)) + CELL_GAP;
            widest = Math.max(widest, width);
        }
        return widest;
    }

    private static int height(List<ItemStack> contents) {
        return PAD + 8 + (pages(contents) > 1 ? DOTS_GAP + DOT : 0) + PAD;
    }

    // ---------------------------------------------------------------- in the world

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        List<ItemStack> contents = contents(client);
        BlockPos pos = looked;
        if (contents == null || pos == null || matrices == null || client.options.hudHidden) return;
        TextRenderer font = client.textRenderer;
        Camera camera = context.camera();
        Vec3d anchor = new Vec3d(pos.getX() + 0.5, pos.getY() + ABOVE, pos.getZ() + 0.5);
        double distance = Math.sqrt(anchor.squaredDistanceTo(camera.getPos()));
        float scale = SCALE * (float) Math.clamp(distance / 7.0, 1.0, MAX_GROW);
        int width = rowWidth(font, contents) + 2 * PAD, height = height(contents);
        Vec3d middle = anchor.add(0, height * scale / 2, 0);
        if (!FloatingInfo.inSight(camera, middle, PANEL_DISTANCE)) return;
        CardKey key = new CardKey(pos);
        FloatingInfo.drawnInWorld(key);
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        matrices.push();
        matrices.translate(middle.x - camera.getPos().x, middle.y - camera.getPos().y, middle.z - camera.getPos().z);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);
        float left = -width / 2f, top = -height / 2f;
        WorldDraw.plate(matrices, consumers, WorldDraw.Plate.TEAL, left, top, -left, -top);
        int page = page(contents), pages = pages(contents);
        float x = left + PAD, y = top + PAD;
        for (int i = page * PAGE; i < Math.min(contents.size(), (page + 1) * PAGE); i++) {
            ItemStack stack = contents.get(i);
            matrices.push();
            matrices.translate(x + ICON / 2f, y + 3.5f, 0.5f);
            matrices.scale(ICON, -ICON, 0.5f);
            client.getItemRenderer().renderItem(stack, ModelTransformationMode.GUI, LIGHT, OverlayTexture.DEFAULT_UV, matrices,
                    consumers, client.world, 0);
            matrices.pop();
            font.draw(count(stack.getCount()), x + ICON + ICON_GAP, y, WorldDraw.PLATE_TEXT, false, matrices.peek().getPositionMatrix(),
                    consumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
            x += cellWidth(font, stack) + CELL_GAP;
        }
        if (pages > 1) {
            float dotsLeft = -(pages * (DOT + DOT_GAP) - DOT_GAP) / 2f, dotsTop = y + 8 + DOTS_GAP;
            for (int p = 0; p < pages; p++) {
                float dx = dotsLeft + p * (DOT + DOT_GAP);
                WorldDraw.fill(matrices, consumers, dx, dotsTop, dx + DOT, dotsTop + DOT, 0.1f, p == page ? DOT_ON : DOT_OFF);
            }
        }
        consumers.draw();
        matrices.pop();
    }

    // ---------------------------------------------------------------- on the screen

    /** The nest looked at, its panel out of sight (too far...): its card on the screen, the same row. */
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<ItemStack> contents = contents(client);
        BlockPos pos = looked;
        if (contents == null || pos == null || client.options.hudHidden || client.currentScreen != null) return;
        CardKey key = new CardKey(pos);
        if (!FloatingInfo.needsCard(key)) return;
        TextRenderer font = client.textRenderer;
        int width = Math.min(rowWidth(font, contents) + 2 * FloatingInfo.PAD, FloatingInfo.MAX_WIDTH);
        int pages = pages(contents), page = page(contents);
        int height = FloatingInfo.PAD + 8 + (pages > 1 ? DOTS_GAP + DOT : 0) + FloatingInfo.PAD;
        FloatingInfo.card(context, key, width, height, ToolHud.Plate.TEAL, 1f, 0, card -> {
            int x = FloatingInfo.PAD, y = FloatingInfo.PAD;
            for (int i = page * PAGE; i < Math.min(contents.size(), (page + 1) * PAGE); i++) {
                ItemStack stack = contents.get(i);
                int ix = x;
                card.getMatrices().push();
                card.getMatrices().translate(ix, y - 1, 0);
                card.getMatrices().scale(ICON / 16f, ICON / 16f, 1);
                HudDepth.item(card, () -> card.drawItem(stack, 0, 0));
                card.getMatrices().pop();
                String text = count(stack.getCount());
                HudDepth.onTop(card, () -> UiText.line(card, font, text, ix + ICON + ICON_GAP, y, font.getWidth(text), ToolHud.TEXT, false));
                x += cellWidth(font, stack) + CELL_GAP;
            }
            if (pages > 1) {
                int dotsLeft = width / 2 - (pages * (DOT + DOT_GAP) - DOT_GAP) / 2, dotsTop = y + 8 + DOTS_GAP;
                HudDepth.onTop(card, () -> {
                    for (int p = 0; p < pages; p++) {
                        int dx = dotsLeft + p * (DOT + DOT_GAP);
                        card.fill(dx, dotsTop, dx + DOT, dotsTop + DOT, p == page ? DOT_ON : DOT_OFF);
                    }
                });
            }
        });
    }
}
