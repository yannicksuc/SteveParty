package fr.lordfinn.steveparty.client.hammer;

import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The stencil the Stencil Hammer will print, drawn on the drum skins of its head (the front one strikes, the back one
 * is the one its holder sees in first person), in the
 * selected paint (a dark print when it only engraves, nothing without a stencil): like a stamp's face.
 * <p>
 * Drawn in the item model's own space, right after the model (so it follows every display transform, the strike
 * animation included): the drum faces of the user's geometry are x 0..16, y 2..18 at z = -3.5 (the front one, facing
 * -z, the way the head strikes) and z = 19.5, one pattern pixel per model pixel, a hair in front of the skin, each
 * read the right way up from its own side.
 */
public final class StencilHammerFace {
    private static final float FRONT_Z = (-3.5F - 0.06F) / 16F;
    private static final float BACK_Z = (19.5F + 0.06F) / 16F;
    private static final float X0 = 0F, X1 = 1F, Y0 = 2F / 16F, Y1 = 18F / 16F;
    /**
     * Engraved: the look of a dark, see-through print (like an engraved sign's symbol) on the pastel yellow skin, but
     * opaque. A see-through print is drawn before the hammer itself (its own buffer is flushed first) and writes depth:
     * the skin behind it was hidden, and the world showed through the hammer.
     */
    private static final int ENGRAVED_COLOR = 0xFF988544;

    /** What was worked out for a hammer's contents and selection (the components are immutable: identity is enough). */
    private static final class Entry {
        @Nullable InventoryComponent contents;
        @Nullable StencilGunSelection selection;
        @Nullable Identifier texture;
        int argb;
        int generation = -1;
    }

    private static final Entry[] CACHE = new Entry[16];
    private static int next;

    static {
        for (int i = 0; i < CACHE.length; i++) CACHE[i] = new Entry();
    }

    private StencilHammerFace() {
    }

    public static void render(ItemStack stack, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (!(stack.getItem() instanceof StencilGunItem)) return;
        Entry entry = lookup(stack);
        if (entry.texture == null) return;
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(entry.texture));
        MatrixStack.Entry peek = matrices.peek();
        Matrix4f matrix = peek.getPositionMatrix();
        // The striking face, seen from the front (-z): the pattern's left is +x, its top is +y
        vertex(consumer, matrix, peek, X1, Y1, FRONT_Z, 0, 0, entry.argb, light, -1);
        vertex(consumer, matrix, peek, X1, Y0, FRONT_Z, 0, 1, entry.argb, light, -1);
        vertex(consumer, matrix, peek, X0, Y0, FRONT_Z, 1, 1, entry.argb, light, -1);
        vertex(consumer, matrix, peek, X0, Y1, FRONT_Z, 1, 0, entry.argb, light, -1);
        // And the other drum, the one the holder sees in first person (seen from +z: left is x = 0)
        vertex(consumer, matrix, peek, X0, Y1, BACK_Z, 0, 0, entry.argb, light, 1);
        vertex(consumer, matrix, peek, X0, Y0, BACK_Z, 0, 1, entry.argb, light, 1);
        vertex(consumer, matrix, peek, X1, Y0, BACK_Z, 1, 1, entry.argb, light, 1);
        vertex(consumer, matrix, peek, X1, Y1, BACK_Z, 1, 0, entry.argb, light, 1);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry, float x, float y, float z,
                               float u, float v, int argb, int light, float normalZ) {
        consumer.vertex(matrix, x, y, z).color(argb).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(entry, 0, 0, normalZ);
    }

    /** Cached per contents + selection (no allocation while they stay the same), refreshed after a texture reload. */
    private static Entry lookup(ItemStack stack) {
        InventoryComponent contents = stack.get(ModComponents.STENCIL_GUN_CONTENTS);
        StencilGunSelection selection = stack.get(ModComponents.STENCIL_GUN_SELECTION);
        int generation = StencilResourceManager.generation();
        for (Entry entry : CACHE) {
            if (entry.contents == contents && entry.selection == selection && entry.generation == generation) return entry;
        }
        Entry entry = CACHE[next];
        next = (next + 1) % CACHE.length;
        entry.contents = contents;
        entry.selection = selection;
        entry.generation = generation;
        StencilGunItem.Load load = StencilGunItem.selectedLoad(stack);
        entry.texture = load.shape() == null ? null : StencilResourceManager.getTexture(load.shape(), StencilResourceManager.Kind.FLAT);
        entry.argb = load.color() == null ? ENGRAVED_COLOR : 0xFF000000 | load.color().getSignColor();
        return entry;
    }
}
