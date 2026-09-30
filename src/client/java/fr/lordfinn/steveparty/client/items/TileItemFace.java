package fr.lordfinn.steveparty.client.items;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.client.blockentity.TileBlockEntityRenderer;
import fr.lordfinn.steveparty.client.utils.TileColors;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.items.custom.TileBlockItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The face of a tile item, as the placed tile would show it (TileBlockEntityRenderer's faces): its cartridge's role
 * pictogram in its colour, or a stamped look (the cartridge's, else the tile's own), white and neutral when empty. A
 * tile holding several cartridges shows each in turn ({@link TileContents#CYCLE_MS}, in slot order).
 * <p>
 * Drawn in the item model's own space right after the model (ItemRendererTileFaceMixin): in the GUI, in hand, dropped
 * and in item frames, over the model's plain picture, at the height of a placed tile's face (1 to 2 px up):
 * <ul>
 *     <li>standard: the block model, the 32x32 face over its 2 blocks, as on a placed tile;</li>
 *     <li>small: the 16x16 face over its 16 px, in the proportion of a placed small tile (16 of its 18 px);</li>
 *     <li>large: the 16x16 face on each of the 4 pictures of its 2x2 model.</li>
 * </ul>
 */
public final class TileItemFace {
    /** The face turned so that it reads upright in the GUI (its top toward the back of the slot). */
    private static final int DIRECTION = 4;
    private static final float SMALL_SCALE = 16f / 18f;
    /** The 4 pictures of the large model (14.5 px wide, centred at 0.5 and 15.5 px), a hair wider than them. */
    private static final float LARGE_SCALE = 14.75f / 16f;
    private static final float[] LARGE_CENTRES = {0.5f / 16f, 15.5f / 16f};

    /** The cartridges of a container (the components are immutable: identity is enough). */
    private static final int CACHED = 8;
    private static final ContainerComponent[] CONTAINERS = new ContainerComponent[CACHED];
    @SuppressWarnings("unchecked")
    private static final List<ItemStack>[] CARTRIDGES = new List[CACHED];
    private static int next;

    private TileItemFace() {
    }

    public static void render(ItemStack stack, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (!(stack.getItem() instanceof TileBlockItem)) return;
        List<ItemStack> cartridges = cartridges(stack.get(DataComponentTypes.CONTAINER));
        ItemStack cartridge = cartridges.isEmpty() ? ItemStack.EMPTY : cartridges.get(TileContents.previewedIndex(cartridges.size()));
        TileSize size = TileSize.of(stack);
        boolean small = size != TileSize.STANDARD;
        Identifier face = face(stack, cartridge, small);
        if (face == null) return;
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(face));
        switch (size) {
            case STANDARD -> TileBlockEntityRenderer.renderFace(matrices, consumer, light, false, DIRECTION, 1f);
            case SMALL -> TileBlockEntityRenderer.renderFace(matrices, consumer, light, true, DIRECTION, SMALL_SCALE);
            case LARGE -> {
                for (float x : LARGE_CENTRES) {
                    for (float z : LARGE_CENTRES) {
                        matrices.push();
                        matrices.translate(x - 0.5f, 0, z - 0.5f);
                        TileBlockEntityRenderer.renderFace(matrices, consumer, light, true, DIRECTION, LARGE_SCALE);
                        matrices.pop();
                    }
                }
            }
        }
    }

    private static @Nullable Identifier face(ItemStack tile, ItemStack cartridge, boolean small) {
        BoardSpaceType type = cartridge.getItem() instanceof CartridgeItem item ? item.getBoardSpaceType() : BoardSpaceType.DEFAULT;
        int color = cartridge.isEmpty() ? TileColors.WHITE : cartridge.getOrDefault(ModComponents.COLOR, TileColors.WHITE);
        // As on a placed tile: the cartridge's look prevails, the tile's own shows while it holds none
        TileStampComponent stamp = cartridge.isEmpty() ? TileContents.ownStamp(tile) : cartridge.get(ModComponents.TILE_STAMP);
        return TileBlockEntityRenderer.faceTexture(type, cartridge, stamp, color, small);
    }

    private static List<ItemStack> cartridges(@Nullable ContainerComponent container) {
        if (container == null) return List.of();
        for (int i = 0; i < CACHED; i++) if (CONTAINERS[i] == container) return CARTRIDGES[i];
        List<ItemStack> cartridges = new ArrayList<>();
        for (ItemStack cartridge : container.iterateNonEmpty()) cartridges.add(cartridge);
        CONTAINERS[next] = container;
        CARTRIDGES[next] = cartridges;
        next = (next + 1) % CACHED;
        return cartridges;
    }
}
