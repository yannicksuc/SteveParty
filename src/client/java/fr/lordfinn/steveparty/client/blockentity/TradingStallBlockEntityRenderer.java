package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public class TradingStallBlockEntityRenderer implements BlockEntityRenderer<TradingStallBlockEntity> {
    private final ItemRenderer itemRenderer;

    public TradingStallBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(TradingStallBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        World world = entity.getWorld();
        if (world == null) return;
        // A merchant summoned by a Shop space in front of it: his offers, for as long as he is there (its own untouched)
        List<ItemStack> displayItems = summonedOffers(world, entity.getPos());
        if (displayItems == null) {
            displayItems = new ArrayList<>();
            for (int i = 18; i < 27; i++) {
                ItemStack stack = entity.getStack(i);
                if (!stack.isEmpty()) displayItems.add(stack);
            }
        }
        if (displayItems.isEmpty()) return;
        matrices.push();
        // Center of the block, turned the way it faces
        matrices.translate(0.5, 0, 0.5);
        Direction direction = entity.getCachedState().get(HorizontalFacingBlock.FACING);
        matrices.multiply(new Quaternionf().rotateY((float) Math.toRadians(direction.asRotation())));
        renderOffers(itemRenderer, displayItems, matrices, vertexConsumers, light, overlay, world);
        matrices.pop();
    }

    /** What the merchant summoned in front of the stall at {@code pos} sells, null if there is none. */
    private static @Nullable List<ItemStack> summonedOffers(World world, BlockPos pos) {
        for (BoxedTraderEntity trader : world.getEntitiesByClass(BoxedTraderEntity.class, new Box(pos).expand(2),
                trader -> pos.equals(trader.getShopStall()))) {
            return trader.getShopItems();
        }
        return null;
    }

    /**
     * The items a stall shows, laid out on its top: {@code matrices} at the middle of the stall's bottom, turned the
     * way a stall facing south is (its front toward +Z). Also the summoned merchant's own stall (BoxedTraderEntityRenderer).
     */
    public static void renderOffers(ItemRenderer itemRenderer, List<ItemStack> displayItems, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int light, int overlay, @Nullable World world) {
        int count = displayItems.size();
        if (count == 0) return;
        matrices.push();
        matrices.translate(0, 1.05, 0);
        matrices.multiply(new Quaternionf().rotateY((float) Math.PI));
        float scale = getScaleForCount(count);
        matrices.scale(scale, scale, scale);
        List<Vec3d> positions = getLayoutPositions(count);
        for (int i = 0; i < count; i++) {
            matrices.push();
            Vec3d pos = positions.get(i);
            ItemStack stack = displayItems.get(i);
            if (!(stack.getItem() instanceof BlockItem)) {
                matrices.translate(pos.getX(), pos.getY() + 0.02, pos.getZ());
                matrices.scale(0.8f, 0.8f, 0.8f);
            } else {
                matrices.translate(pos.getX(), pos.getY() - 0.10, pos.getZ());
            }
            itemRenderer.renderItem(stack, ModelTransformationMode.GROUND, light, overlay, matrices, vertexConsumers, world, 0);
            matrices.pop();
        }
        matrices.pop();
    }

    private static float getScaleForCount(int count) {
        return switch (count) {
            case 1 -> 1.3f;
            case 2, 4 -> 1.1f;
            case 3 -> 0.9f;
            case 5, 6 -> 0.8f;
            case 7, 8, 9 -> 0.7f;
            default -> 0.7f;
        };
    }

    private static List<Vec3d> getLayoutPositions(int count) {
        List<Vec3d> positions = new ArrayList<>();

        float spacing = 0.4f; // distance between items

        switch (count) {
            case 1 -> positions.add(new Vec3d(0f, 0f, 0f));
            case 2 -> {
                positions.add(new Vec3d(-spacing / 2, 0f, 0f));
                positions.add(new Vec3d(spacing / 2, 0f, 0f));
            }
            case 3 -> {
                positions.add(new Vec3d(-spacing / 2, 0f, spacing / 2));
                positions.add(new Vec3d(spacing / 2, 0f, spacing / 2));
                positions.add(new Vec3d(0f, 0f, -spacing / 2));
            }
            case 4 -> {
                positions.add(new Vec3d(-spacing / 2, 0f, -spacing / 2));
                positions.add(new Vec3d(spacing / 2, 0f, -spacing / 2));
                positions.add(new Vec3d(-spacing / 2, 0f, spacing / 2));
                positions.add(new Vec3d(spacing / 2, 0f, spacing / 2));
            }
            case 5, 6, 7, 8, 9 -> {
                int rows = (count + 2) / 3;
                for (int i = 0; i < count; i++) {
                    int row = i / 3;
                    int col = i % 3;
                    float x = (col - 1) * spacing;
                    float z = (row - (rows - 1) / 2f) * spacing;
                    positions.add(new Vec3d(x, 0f, z));
                }
            }
        }

        return positions;
    }
}

