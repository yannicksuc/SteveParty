package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlockEntity;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * The step controller's model. Looked at with the Wrench or a mini-game page in hand, it says over it which mini-game
 * pages it is linked to (like a podium says what a pulse does to it).
 */
public class StepControllerBlockEntityRenderer extends GeoBlockRenderer<StepControllerBlockEntity> {
    private final net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher dispatcher;

    public StepControllerBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        super(new DefaultedBlockGeoModel<>(Steveparty.id("step_controller")));
        this.dispatcher = context.getRenderDispatcher();
    }

    @Override
    public void render(StepControllerBlockEntity entity, float partialTick, net.minecraft.client.util.math.MatrixStack matrices,
                       VertexConsumerProvider bufferSource, int packedLight, int packedOverlay) {
        super.render(entity, partialTick, matrices, bufferSource, packedLight, packedOverlay);
        java.util.List<java.util.UUID> pages = entity.getLinkedPages();
        if (pages.isEmpty() || !holdingLinkTool()
                || !WorldLabels.lookingAtColumn(entity.getPos().getX(), entity.getPos().getZ(), entity.getPos().getY(), entity.getPos().getY() + 1)) return;
        float line = 0;
        for (java.util.UUID id : pages) {
            MiniGamePageData page = MiniGamePageClient.page(id);
            net.minecraft.text.Text name = page != null && page.hasTitle() ? net.minecraft.text.Text.literal(page.title())
                    : net.minecraft.text.Text.translatable("item.steveparty.mini_game_page");
            WorldLabels.draw(matrices, bufferSource, dispatcher, 0.5, 1.45, 0.5,
                    net.minecraft.text.Text.translatable("label.steveparty.step_controller.linked", name), 0xFFFFE08A, 0x60000000, line, 1f / 80f);
            line++;
        }
    }

    /** The Wrench, or a mini-game page (what links it), in a hand of the local player. */
    private static boolean holdingLinkTool() {
        if (WorldLabels.holdingWrench()) return true;
        net.minecraft.entity.player.PlayerEntity player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player == null) return false;
        for (net.minecraft.item.ItemStack stack : player.getHandItems()) {
            if (stack.getItem() instanceof MiniGamePageItem) return true;
        }
        return false;
    }

    @Override
    public @Nullable RenderLayer getRenderType(StepControllerBlockEntity animatable, Identifier texture, @Nullable VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityTranslucent(getTextureLocation(animatable));
    }
}
