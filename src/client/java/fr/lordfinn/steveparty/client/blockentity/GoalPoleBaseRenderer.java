package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * With a wrench in hand, near a goal pole base, looking at it or at its pole: a small panel, on the side facing the player, telling what the
 * redstone does (the back plug, a pulse on any other side, a comparator reading the base) and whether the base counts. Nothing is drawn
 * otherwise.
 */
public class GoalPoleBaseRenderer implements BlockEntityRenderer<GoalPoleBaseBlockEntity> {
    private static final String KEY = "hint.steveparty.goal_pole_base.";
    private static final double DETAIL_DISTANCE_SQ = 10 * 10;
    private static final int BACKGROUND = 0x90000000;
    /** How high above the base looking at its pole still counts as looking at the base. */
    private static final int COLUMN_REACH = 64;

    private final BlockEntityRenderDispatcher dispatcher;

    public GoalPoleBaseRenderer(BlockEntityRendererFactory.Context context) {
        this.dispatcher = context.getRenderDispatcher();
    }

    @Override
    public void render(GoalPoleBaseBlockEntity base, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
                       int light, int overlay) {
        if (dispatcher.camera == null || !WorldLabels.holdingWrench()) return;
        // Only the base looked at (itself or its pole): panels of bases side by side would cover each other
        BlockPos pos = base.getPos();
        if (!WorldLabels.lookingAtColumn(pos.getX(), pos.getZ(), pos.getY(), pos.getY() + COLUMN_REACH)) return;
        Vec3d camera = dispatcher.camera.getPos();
        Vec3d center = Vec3d.ofCenter(base.getPos());
        if (camera.squaredDistanceTo(center) > DETAIL_DISTANCE_SQ) return;
        BlockState state = base.getCachedState();
        if (!state.contains(GoalPoleBaseBlock.FACING)) return;
        Direction front = state.get(GoalPoleBaseBlock.FACING);

        // The panel stands between the base and the player, so that neither the base nor the pole hides it
        double dx = camera.x - center.x, dz = camera.z - center.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double x = 0.5 + (length > 0.01 ? dx / length * 0.9 : 0), z = 0.5 + (length > 0.01 ? dz / length * 0.9 : 0);

        String mode = base.getRedstoneMode().name().toLowerCase(Locale.ROOT);
        boolean powered = state.get(GoalPoleBaseBlock.POWERED);
        Text back = Text.translatable(KEY + "side.back").formatted(Formatting.GRAY).append(" ")
                .append(Text.translatable(KEY + "back." + mode).formatted(Formatting.WHITE)).append(" ")
                .append(Text.translatable(KEY + (powered ? "powered" : "unpowered")).formatted(Formatting.GRAY));
        Text reset = Text.translatable(KEY + "side.other").formatted(Formatting.GRAY).append(" ")
                .append(Text.translatable(KEY + "reset").formatted(Formatting.WHITE));
        Text output = Text.translatable(KEY + "side.comparator").formatted(Formatting.GRAY).append(" ")
                .append(Text.translatable(KEY + "output." + base.getOutputMode().name().toLowerCase(Locale.ROOT)).formatted(Formatting.WHITE));
        Text status = Text.translatable(KEY + (base.isActive() ? "counting" : "paused"), base.getTotal())
                .formatted(base.isActive() ? Formatting.GREEN : Formatting.GOLD);
        WorldLabels.draw(matrices, consumers, dispatcher, x, 1.05, z, status, 0xFFFFFFFF, BACKGROUND, -3);
        WorldLabels.draw(matrices, consumers, dispatcher, x, 1.05, z, back, 0xFFFFFFFF, BACKGROUND, -2);
        WorldLabels.draw(matrices, consumers, dispatcher, x, 1.05, z, reset, 0xFFFFFFFF, BACKGROUND, -1);
        WorldLabels.draw(matrices, consumers, dispatcher, x, 1.05, z, output, 0xFFFFFFFF, BACKGROUND, 0);
    }

    @Override
    public int getRenderDistance() {
        return 16;
    }
}
