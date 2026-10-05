package fr.lordfinn.steveparty.client.pipe;

import fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlockEntity;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The notch of the mini-game pipe: a dark slot with a small ledge on one of its plain sides
 * ({@link MiniGamePipeBlock#notchSide}), and in it the page the pipe is programmed with. Also the hint shown when a
 * mini-game pipe is looked at: what it can be programmed with, or the mini-game it leads to.
 */
public class MiniGamePipeNotchRenderer implements BlockEntityRenderer<MiniGamePipeBlockEntity> {
    /** The pipe's body is 14 pixels across: its side is 7 pixels from the middle. */
    private static final double SIDE = 7 / 16.0;
    private final ItemRenderer itemRenderer;

    public MiniGamePipeNotchRenderer(BlockEntityRendererFactory.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(MiniGamePipeBlockEntity pipe, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        Direction side = MiniGamePipeBlock.notchSide(pipe.getCachedState());
        if (side == null || pipe.getWorld() == null) return;
        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);
        // Local +Z comes out of the notch's side; +Y is up on the pipe's sides
        if (side.getAxis().isHorizontal()) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side.asRotation()));
        else matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(side == Direction.UP ? -90 : 90));
        VertexConsumer boxes = vertexConsumers.getBuffer(RenderLayer.getDebugFilledBox());
        // The slot, and the ledge the page stands on
        WorldRenderer.renderFilledBox(matrices, boxes, -0.27, -0.33, SIDE, 0.27, 0.33, SIDE + 0.02, 0.20F, 0.13F, 0.03F, 1F);
        WorldRenderer.renderFilledBox(matrices, boxes, -0.30, -0.40, SIDE, 0.30, -0.33, SIDE + 0.09, 0.62F, 0.42F, 0.08F, 1F);
        ItemStack page = pipe.getPage();
        if (!page.isEmpty()) {
            matrices.translate(0, 0, SIDE + 0.045);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrices.scale(0.58F, 0.58F, 0.58F);
            int lit = WorldRenderer.getLightmapCoordinates(pipe.getWorld(), pipe.getPos().offset(side));
            itemRenderer.renderItem(page, ModelTransformationMode.FIXED, lit, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, pipe.getWorld(), 0);
        }
        matrices.pop();
    }

    // ------------------------------------------------------------------ the hint when it is looked at

    private static @Nullable BlockPos lookedAt;
    private static int lookedFor;

    /** Shows, above the hotbar, what the mini-game pipe under the crosshair is for (again every two seconds while it is looked at). */
    public static void registerHint() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BlockPos pos = client.player != null && client.world != null && client.currentScreen == null
                    && client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
            if (pos == null || !(client.world.getBlockEntity(pos) instanceof MiniGamePipeBlockEntity pipe)) {
                lookedAt = null;
                return;
            }
            if (pos.equals(lookedAt) && ++lookedFor % 40 != 0) return;
            if (!pos.equals(lookedAt)) lookedFor = 0;
            lookedAt = pos.toImmutable();
            client.inGameHud.setOverlayMessage(hint(pipe.getPage()), false);
        });
    }

    private static Text hint(ItemStack page) {
        if (page.isEmpty()) return Text.translatable("message.steveparty.minigame_pipe.hint");
        // The title the page has now, when this client knows it; else the one the item carries
        UUID id = MiniGamePages.idOf(page);
        MiniGamePageData data = id == null ? null : MiniGamePageClient.page(id);
        Text title = data != null && data.hasTitle() ? Text.literal(data.title()) : page.getName();
        return Text.translatable("message.steveparty.minigame_pipe.hint.programmed", title);
    }

    /** The page in its notch is small: not drawn from far. */
    @Override
    public int getRenderDistance() {
        return 48;
    }
}
