package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.board.PartyLinked;
import fr.lordfinn.steveparty.client.gui.FloatingInfo;
import fr.lordfinn.steveparty.client.gui.HudDepth;
import fr.lordfinn.steveparty.client.gui.IconBounds;
import fr.lordfinn.steveparty.client.gui.NotLinkedMark;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.screens.AntennaIcons;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
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
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The one panel of every brush-linked block (the Piggy Bank, the Party Rewinder, the Party Antenna...): looked at, a
 * gold plate over it showing what it does in one row of icons, no words; out of sight (too far...), the same row on a
 * card on the screen ({@link FloatingInfo}). Each block only says what its row holds ({@link Source}); the layout is
 * written here once, so every panel lines up the same way.
 * <p>
 * <b>Grid.</b> Everything is laid out in panel pixels, integers, from the plate's top-left corner: one panel pixel is
 * one pixel of an icon (an icon is 16 x 16); the plate's frame, the text and the dividers use pixels of
 * {@link #TEXEL} panel pixels (the frame's 4 texture pixels are {@link #BORDER} wide). Every icon sits in a
 * {@link #ROW} square, {@link #MARGIN} from the plate's face on all four sides; a 3D icon is moved by whole icon pixels
 * so that its visible pixels are centred ({@link IconBounds}). A divider spans the face from its top to its bottom.
 * <p>
 * Rendering (minecraft-rendering): a world panel in {@code AFTER_ENTITIES}, main target, everything opaque or cutout
 * writing depth: the plate first (drawn at once), then on it, each a step toward the camera, the icons (solid layers,
 * lit from above whichever way the plate turns), their crosses and badges, the text (polygon offset) and the dividers
 * (opaque, {@link WorldDraw#fill}); the batch is drawn at once.
 */
public final class LinkedPanels {
    // ---------------------------------------------------------------- layout (panel pixels)

    /** A pixel of the frame, of the text and of a divider. */
    public static final int TEXEL = 2;
    /**
     * The plate's frame (4 texture pixels), the face's margin around the row, the row's height (an icon). The margin
     * is odd: the icons (1 px pixels) sit at odd places, the text and the dividers ({@link #TEXEL} px pixels) at even
     * ones, on the frame's grid; the text, 7 of its pixels high, is then {@code MARGIN + 1} from the face.
     */
    public static final int BORDER = 4 * TEXEL, MARGIN = 5, ROW = 16;
    /** On each side of a divider, the divider itself. */
    public static final int DIVIDER_GAP = 5, DIVIDER = TEXEL;
    /**
     * An icon to its text, a text to the next icon (odd: from an icon's grid to the text's); a text to a « / » (even:
     * both on the text's grid) and the « / » to the next icon.
     */
    public static final int ICON_TEXT = 3, ITEM_GAP = 7, TEXT_SLASH = 4, SLASH_ICON = 3;
    /** The text's top in the row: its 7 pixels high glyphs ({@link #TEXEL} each) centred in it. */
    private static final int TEXT_Y = (ROW - 7 * TEXEL) / 2;
    /** Block per panel pixel at 7 blocks or closer; farther, it grows up to {@link #MAX_GROW} times. */
    private static final float UNIT = 1f / 96f, MAX_GROW = 2.5f;
    /**
     * Depths (panel pixels, +z toward the camera): the plate, the dividers, then (the text at 0, polygon offset) the
     * icons and their crosses and badges. Small: a layer nearer the camera shows a little off its place on the plate
     * (perspective), and a whole panel pixel already moved an icon by a fraction of a pixel.
     */
    private static final float PLATE_Z = -0.2f, DIVIDER_Z = -0.1f, ICON_Z = 0.1f, MARK_Z = 0.2f;
    private static final double AIM_DISTANCE = 24, PANEL_DISTANCE = 16;
    /** Toward the camera from the block's centre (blocks). */
    private static final double AHEAD = 0.3;
    /** The dividers and the « / »: a dark gold on the face. */
    public static final int SOFT = 0xFF8A6A1A;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    // ---------------------------------------------------------------- content

    /** What a row is made of. */
    public sealed interface Part {
        int width(TextRenderer font);
    }

    /**
     * An item's icon, {@code scale} times its size (an item drawn small in its own icon: the die), centred in its
     * square; {@code crossed}: the red « linked to nothing » cross on its corner; {@code badge}: a cell of the antenna's
     * sheet laid over it (-1: none).
     */
    public record Icon(ItemStack stack, float scale, boolean crossed, int badge) implements Part {
        public Icon(ItemStack stack) {
            this(stack, iconScale(stack), false, -1);
        }

        @Override
        public int width(TextRenderer font) {
            return ROW;
        }
    }

    /** A cell of the antenna's sheet ({@link AntennaIcons#SHEET}): its filter's sign, its hourglass. */
    public record SheetIcon(int cell) implements Part {
        @Override
        public int width(TextRenderer font) {
            return ROW;
        }
    }

    /** A short text (« +5 », « 3 »), its glyphs {@link #TEXEL} pixels each, centred in the row's height. */
    public record Label(String text, int colour) implements Part {
        @Override
        public int width(TextRenderer font) {
            // The font's width ends with one pixel of spacing: not part of the text
            return (font.getWidth(text) - 1) * TEXEL;
        }
    }

    /** A divider between what the block is and what it does, the face's full height. */
    public record Divider() implements Part {
        @Override
        public int width(TextRenderer font) {
            return DIVIDER_GAP + DIVIDER + DIVIDER_GAP;
        }
    }

    /** Empty room. */
    public record Space(int width) implements Part {
        @Override
        public int width(TextRenderer font) {
            return width;
        }
    }

    /** A panel: its row, its bottom's height above the block (blocks). */
    public record Panel(List<Part> parts, double above) {
    }

    /** What the block looked at shows, or null: the first source with an answer wins. */
    @FunctionalInterface
    public interface Source {
        @Nullable Panel panel(BlockEntity blockEntity);
    }

    /** The crossed Tile Linker Brush, then a divider before what follows: how every unlinked block's row starts. */
    public static void notLinked(List<Part> parts) {
        parts.add(new Icon(NotLinkedMark.BRUSH, 1, true, -1));
        parts.add(new Divider());
    }

    /** How big an item's icon is drawn: the die is drawn small in its own icon. */
    public static float iconScale(ItemStack stack) {
        return stack.isOf(fr.lordfinn.steveparty.items.ModItems.DEFAULT_DICE) ? 1.5f : 1f;
    }

    private static final List<Source> SOURCES = new ArrayList<>();
    /** Any other unlinked brush-linked block: the crossed brush alone. */
    private static final Source DEFAULT = blockEntity -> blockEntity instanceof PartyLinked linked && !linked.showsNotLinkedItself()
            && !linked.isPartyLinked() ? new Panel(List.of(new Icon(NotLinkedMark.BRUSH, 1, true, -1)), 1.1) : null;

    private record CardKey(BlockPos pos) {
    }

    private LinkedPanels() {
    }

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(LinkedPanels::render);
        HudRenderCallback.EVENT.register(LinkedPanels::renderHud);
        IconBounds.initialize();
    }

    /** Adds a block's panel (before the default crossed brush). */
    public static void register(Source source) {
        SOURCES.add(source);
    }

    private record Aimed(BlockPos pos, Panel panel) {
    }

    /** The block under the crosshair and its panel, or null. */
    private static @Nullable Aimed aimed(MinecraftClient client) {
        Entity viewer = client.getCameraEntity();
        if (client.world == null || viewer == null) return null;
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return null;
        if (block.getPos().squaredDistanceTo(viewer.getEyePos()) > AIM_DISTANCE * AIM_DISTANCE) return null;
        BlockEntity blockEntity = client.world.getBlockEntity(block.getBlockPos());
        if (blockEntity == null) return null;
        for (Source source : SOURCES) {
            Panel panel = source.panel(blockEntity);
            if (panel != null) return panel.parts().isEmpty() ? null : new Aimed(block.getBlockPos().toImmutable(), panel);
        }
        Panel panel = DEFAULT.panel(blockEntity);
        return panel == null ? null : new Aimed(block.getBlockPos().toImmutable(), panel);
    }

    /**
     * The plate's width (panel pixels): the row and its margins, one more after a row ending with text (its margin as
     * above and under it), so that the plate ends on its frame's grid.
     */
    public static int plateWidth(TextRenderer font, List<Part> parts) {
        int width = rowWidth(font, parts) + 2 * (BORDER + MARGIN);
        return width % TEXEL == 0 ? width : width + 1;
    }

    /** The row's width (panel pixels). */
    public static int rowWidth(TextRenderer font, List<Part> parts) {
        int width = 0;
        for (Part part : parts) width += part.width(font);
        return width;
    }

    // ---------------------------------------------------------------- in the world

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        if (matrices == null || client.options.hudHidden) return;
        Aimed aimed = aimed(client);
        if (aimed == null) return;
        TextRenderer font = client.textRenderer;
        BlockPos pos = aimed.pos();
        Camera camera = context.camera();
        Vec3d toCamera = new Vec3d(camera.getPos().x - pos.getX() - 0.5, 0, camera.getPos().z - pos.getZ() - 0.5);
        toCamera = toCamera.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : toCamera.normalize().multiply(AHEAD);
        Vec3d anchor = new Vec3d(pos.getX() + 0.5, pos.getY() + aimed.panel().above(), pos.getZ() + 0.5).add(toCamera);
        double distance = Math.sqrt(anchor.squaredDistanceTo(camera.getPos()));
        float scale = UNIT * (float) Math.clamp(distance / 7.0, 1.0, MAX_GROW);
        int inset = BORDER + MARGIN;
        int width = plateWidth(font, aimed.panel().parts()), height = ROW + 2 * inset;
        Vec3d middle = anchor.add(0, height * scale / 2, 0);
        if (!FloatingInfo.inSight(camera, middle, PANEL_DISTANCE)) return;
        FloatingInfo.drawnInWorld(new CardKey(pos));
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        matrices.push();
        matrices.translate(middle.x - camera.getPos().x, middle.y - camera.getPos().y, middle.z - camera.getPos().z);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);
        // From here on, panel pixels from the plate's top-left corner
        matrices.translate(-width / 2f, -height / 2f, 0);
        matrices.push();
        matrices.scale(TEXEL, TEXEL, 1);
        WorldDraw.plate(matrices, consumers, WorldDraw.Plate.GOLD, 0, 0, width / (float) TEXEL, height / (float) TEXEL, PLATE_Z);
        matrices.pop();
        int x = inset;
        for (Part part : aimed.panel().parts()) {
            drawInWorld(client, font, matrices, consumers, part, x, inset, height);
            x += part.width(font);
        }
        consumers.draw();
        matrices.pop();
    }

    private static void drawInWorld(MinecraftClient client, TextRenderer font, MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
                                    Part part, int x, int y, int height) {
        switch (part) {
            case Icon icon -> {
                int[] offset = IconBounds.offset(icon.stack(), icon.scale());
                float size = ROW * icon.scale();
                matrices.push();
                matrices.translate(x + ROW / 2f + offset[0], y + ROW / 2f + offset[1], ICON_Z);
                matrices.scale(size, -size, 0.01f);
                client.getItemRenderer().renderItem(icon.stack(), ModelTransformationMode.GUI, LIGHT, OverlayTexture.DEFAULT_UV, matrices,
                        layer -> new LitFromAbove(consumers.getBuffer(AntennaIcons.solid(layer))), client.world, 0);
                matrices.pop();
                consumers.draw();
                if (icon.badge() >= 0) sheet(consumers, matrices.peek().getPositionMatrix(), icon.badge(), x, y, MARK_Z);
                if (icon.crossed()) NotLinkedMark.worldCross(matrices, consumers, x + ROW, y + ROW, 1, MARK_Z);
            }
            case SheetIcon sheet -> sheet(consumers, matrices.peek().getPositionMatrix(), sheet.cell(), x, y, ICON_Z);
            case Label label -> {
                matrices.push();
                matrices.translate(x, y + TEXT_Y, 0);
                matrices.scale(TEXEL, TEXEL, 1);
                font.draw(label.text(), 0, 0, label.colour(), false, matrices.peek().getPositionMatrix(), consumers,
                        TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
                matrices.pop();
            }
            case Divider divider -> WorldDraw.fill(matrices, consumers, x + DIVIDER_GAP, BORDER, x + DIVIDER_GAP + DIVIDER, height - BORDER,
                    DIVIDER_Z, SOFT);
            case Space space -> {
            }
        }
    }

    /** A 16 x 16 cell of the antenna's sheet, its top-left corner at (x, y). */
    private static void sheet(VertexConsumerProvider consumers, Matrix4f matrix, int cell, float x, float y, float z) {
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getText(AntennaIcons.SHEET));
        float u0 = (cell % 16) * 16 / 256f, v0 = (cell / 16) * 16 / 64f, u1 = u0 + 16 / 256f, v1 = v0 + 16 / 64f;
        buffer.vertex(matrix, x, y, z).color(0xFFFFFFFF).texture(u0, v0).light(LIGHT);
        buffer.vertex(matrix, x, y + ROW, z).color(0xFFFFFFFF).texture(u0, v1).light(LIGHT);
        buffer.vertex(matrix, x + ROW, y + ROW, z).color(0xFFFFFFFF).texture(u1, v1).light(LIGHT);
        buffer.vertex(matrix, x + ROW, y, z).color(0xFFFFFFFF).texture(u1, v0).light(LIGHT);
    }

    /** A buffer whose normals all point up: an icon gets the world's full light, never its shaded side. */
    private record LitFromAbove(VertexConsumer delegate) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(0, 1, 0);
            return this;
        }
    }

    // ---------------------------------------------------------------- on the screen

    /** Its panel out of sight (too far...): its card on the screen, the same row at half its size (card pixels). */
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null) return;
        Aimed aimed = aimed(client);
        if (aimed == null) return;
        CardKey key = new CardKey(aimed.pos());
        if (!FloatingInfo.needsCard(key)) return;
        TextRenderer font = client.textRenderer;
        List<Part> parts = aimed.panel().parts();
        int width = Math.min(rowWidth(font, parts) / 2 + 2 * FloatingInfo.PAD, FloatingInfo.MAX_WIDTH);
        int height = ROW / 2 + 2 * FloatingInfo.PAD;
        FloatingInfo.card(context, key, width, height, ToolHud.Plate.GOLD, 1f, 0, card -> {
            MatrixStack matrices = card.getMatrices();
            matrices.push();
            matrices.translate(FloatingInfo.PAD, FloatingInfo.PAD, 0);
            matrices.scale(0.5f, 0.5f, 1);
            int x = 0;
            for (Part part : parts) {
                drawOnCard(card, font, part, x);
                x += part.width(font);
            }
            matrices.pop();
        });
    }

    private static void drawOnCard(DrawContext card, TextRenderer font, Part part, int x) {
        MatrixStack matrices = card.getMatrices();
        switch (part) {
            case Icon icon -> {
                int[] offset = IconBounds.offset(icon.stack(), icon.scale());
                matrices.push();
                matrices.translate(x + ROW / 2f + offset[0], ROW / 2f + offset[1], 0);
                matrices.scale(icon.scale(), icon.scale(), 1);
                HudDepth.item(card, () -> card.drawItem(icon.stack(), -8, -8));
                matrices.pop();
                if (icon.badge() >= 0) HudDepth.onTop(card, () -> AntennaIcons.cell(card, icon.badge(), x, 0));
                if (icon.crossed()) HudDepth.onTop(card, () -> NotLinkedMark.crossOnIcon(card, x, 0));
            }
            case SheetIcon sheet -> HudDepth.onTop(card, () -> AntennaIcons.cell(card, sheet.cell(), x, 0));
            case Label label -> {
                matrices.push();
                matrices.translate(x, TEXT_Y, 0);
                matrices.scale(TEXEL, TEXEL, 1);
                HudDepth.onTop(card, () -> UiText.line(card, font, label.text(), 0, 0, font.getWidth(label.text()), label.colour(), false));
                matrices.pop();
            }
            case Divider divider -> HudDepth.onTop(card, () -> card.fill(x + DIVIDER_GAP, -MARGIN, x + DIVIDER_GAP + DIVIDER, ROW + MARGIN, SOFT));
            case Space space -> {
            }
        }
    }
}
