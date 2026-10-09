package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlockEntity;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * The step controller's model. Looked at with the Wrench or a mini-game page in hand, it says over it which mini-game
 * pages it is linked to (like a podium says what a pulse does to it).
 */
public class StepControllerBlockEntityRenderer extends GeoBlockRenderer<StepControllerBlockEntity> {
    private final BlockEntityRenderDispatcher dispatcher;

    public StepControllerBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        super(new DefaultedBlockGeoModel<>(Steveparty.id("step_controller")));
        this.dispatcher = context.getRenderDispatcher();
    }

    @Override
    public void render(StepControllerBlockEntity entity, float partialTick, MatrixStack matrices,
                       VertexConsumerProvider bufferSource, int packedLight, int packedOverlay) {
        super.render(entity, partialTick, matrices, bufferSource, packedLight, packedOverlay);
        List<UUID> pages = entity.getLinkedPages();
        if (pages.isEmpty() || !holdingLinkTool()
                || !WorldLabels.lookingAtColumn(entity.getPos().getX(), entity.getPos().getZ(), entity.getPos().getY(), entity.getPos().getY() + 1)) return;
        float line = 0;
        for (UUID id : pages) {
            MiniGamePageData page = MiniGamePageClient.page(id);
            Text name = page != null && page.hasTitle() ? Text.literal(page.title())
                    : Text.translatable("item.steveparty.mini_game_page");
            WorldLabels.draw(matrices, bufferSource, dispatcher, 0.5, 1.45, 0.5,
                    Text.translatable("label.steveparty.step_controller.linked", name), 0xFFFFE08A, 0x60000000, line, 1f / 80f);
            line++;
        }
    }

    /** The Wrench, or a mini-game page (what links it), in a hand of the local player. */
    private static boolean holdingLinkTool() {
        if (WorldLabels.holdingWrench()) return true;
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        for (ItemStack stack : player.getHandItems()) {
            if (stack.getItem() instanceof MiniGamePageItem) return true;
        }
        return false;
    }

    @Override
    public @Nullable RenderLayer getRenderType(StepControllerBlockEntity animatable, Identifier texture, @Nullable VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityTranslucent(getTextureLocation(animatable));
    }
}
