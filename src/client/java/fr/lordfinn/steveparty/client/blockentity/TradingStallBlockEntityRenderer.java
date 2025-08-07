package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
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
                       net.minecraft.client.render.VertexConsumerProvider vertexConsumers, int light, int overlay) {
        World world = entity.getWorld();
        if (world == null) return;

        List<ItemStack> displayItems = new ArrayList<>();
        for (int i = 19; i <= 27; i++) {
            ItemStack stack = entity.getStack(i);
            if (!stack.isEmpty()) displayItems.add(stack);
        }

        int count = displayItems.size();
        if (count == 0) return;
        float scale = getScaleForCount(count);

        matrices.push();

        // Center of the block (x, y, z)
        matrices.translate(0.5, 1.05, 0.5);
        // Rotate depending on block facing
        BlockState blockState = entity.getCachedState();
        Direction direction = blockState.get(HorizontalFacingBlock.FACING);
        float angleDegrees = direction.asRotation();
        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(angleDegrees + 180));

        matrices.multiply(rotation);

        matrices.scale(scale, scale, scale);

        List<Vec3d> positions = getLayoutPositions(count);

        for (int i = 0; i < count; i++) {
            matrices.push();
            Vec3d pos = positions.get(i);
            ItemStack stack = displayItems.get(i);
            boolean isBlock = stack.getItem() instanceof BlockItem;

            if (!isBlock) {
                matrices.translate(pos.getX(), pos.getY() + 0.02, pos.getZ());
                matrices.scale(0.8f, 0.8f, 0.8f);
            } else {
                matrices.translate(pos.getX(), pos.getY() - 0.10, pos.getZ());
            }
            this.itemRenderer.renderItem(displayItems.get(i), ModelTransformationMode.GROUND,
                    light, overlay, matrices, vertexConsumers, entity.getWorld(), 0);
            matrices.pop();
        }

        matrices.pop();
    }

    private float getScaleForCount(int count) {
        return switch (count) {
            case 1 -> 1.3f;
            case 2, 4 -> 1.1f;
            case 3 -> 0.9f;
            case 5, 6 -> 0.8f;
            case 7, 8, 9 -> 0.7f;
            default -> 0.7f;
        };
    }

    private double getYOffsetForScale(float scale) {
        return 0.8 / scale;
    }

    private List<Vec3d> getLayoutPositions(int count) {
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

