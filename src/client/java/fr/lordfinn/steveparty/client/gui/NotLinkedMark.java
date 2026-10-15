package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.board.PartyLinked;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * « Linked to no party », shared by every {@link PartyLinked} block (the Piggy Bank, the Party Rewinder, the Party
 * Antenna...): the Tile Linker Brush's icon with the red cross ({@link ConsolePaint#NOT_LINKED}, 8 icon pixels, strokes
 * 2 px wide, outlined dark) on its bottom-right corner, on the icon's own pixel grid; its tooltip in the block's
 * grammatical gender ({@link PartyLinked#notLinkedKey}).
 * <ul>
 *     <li>in a screen: {@link #screen} (a 16 px item slot), {@link #hovered} and {@link #tooltip};</li>
 *     <li>on a HUD card whose icons are scaled: {@link #crossOnIcon} in the icon's own 16 px space;</li>
 *     <li>on a world plate (label space, +z toward the camera): {@link #worldCross}, the cross over an icon already
 *     drawn. Opaque pixels written in one batch drawn at once, a small step toward the camera over the icon
 *     (minecraft-rendering: a detail on a plate is opaque).</li>
 * </ul>
 */
public final class NotLinkedMark {
    public static final ItemStack BRUSH = new ItemStack(ModItems.TILE_LINKER_BRUSH);
    /** The cross's size in icon pixels (an icon is 16). */
    public static final int SIZE = ConsolePaint.NOT_LINKED.length;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private NotLinkedMark() {
    }

    // ---------------------------------------------------------------- screens

    /** The crossed brush in a 16 px slot whose corner is (x, y), over what the screen drew there. */
    public static void screen(DrawContext context, int x, int y) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        context.drawItem(BRUSH, x, y);
        context.getMatrices().translate(0, 0, 200);
        crossOnIcon(context, x, y);
        context.getMatrices().pop();
    }

    /** The cross alone on the bottom-right corner of a 16 px icon whose corner is (x, y), in the icon's space. */
    public static void crossOnIcon(DrawContext context, int x, int y) {
        ConsolePaint.notLinked(context, x + 16 - SIZE, y + 16 - SIZE);
    }

    /** Whether the mouse is over the crossed brush drawn at (x, y) by {@link #screen}. */
    public static boolean hovered(double mouseX, double mouseY, int x, int y) {
        return HitArea.contains(mouseX, mouseY, x, y, 16, 16);
    }

    /** « Liée à aucune partie : relie-la au Pinceau lieur », in the gender of {@code block}'s name. */
    public static Text tooltip(@Nullable PartyLinked block) {
        return Text.translatable(block == null ? PartyLinked.NOT_LINKED_KEY : block.notLinkedKey());
    }

    /** {@link #tooltip} of the block at {@code pos} (the shared masculine one if it is gone). */
    public static Text tooltip(@Nullable World world, @Nullable BlockPos pos) {
        return tooltip(world != null && pos != null && world.getBlockEntity(pos) instanceof PartyLinked linked ? linked : null);
    }

    // ---------------------------------------------------------------- world plates

    /**
     * The red cross whose bottom-right corner is (right, bottom) on a world plate, {@code pixel} plate units per icon
     * pixel, at depth {@code z}: opaque pixels, one batch drawn at once (left to the caller's batch when
     * {@code consumers} is not an {@code Immediate}: a block entity renderer's).
     */
    public static void worldCross(MatrixStack matrices, VertexConsumerProvider consumers, float right, float bottom,
                                  float pixel, float z) {
        RenderLayer layer = RenderLayer.getTextBackground();
        VertexConsumer consumer = consumers.getBuffer(layer);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        String[] rows = ConsolePaint.NOT_LINKED;
        float x = right - SIZE * pixel, y = bottom - SIZE * pixel;
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                Integer argb = ConsolePaint.NOT_LINKED_COLOURS.get(rows[row].charAt(column));
                if (argb == null) continue;
                float x0 = x + column * pixel, y0 = y + row * pixel;
                consumer.vertex(matrix, x0, y0, z).color(argb).light(LIGHT);
                consumer.vertex(matrix, x0, y0 + pixel, z).color(argb).light(LIGHT);
                consumer.vertex(matrix, x0 + pixel, y0 + pixel, z).color(argb).light(LIGHT);
                consumer.vertex(matrix, x0 + pixel, y0, z).color(argb).light(LIGHT);
            }
        }
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(layer);
    }
}
