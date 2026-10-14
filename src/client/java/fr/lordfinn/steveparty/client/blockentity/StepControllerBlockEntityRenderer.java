package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.StepPulse;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlockEntity;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * The Party Rewinder's deck (its plate is a plain block model): LordFinn's tape deck, cutout. Its chosen button is lit
 * (full light: it glows in the dark and under shaders), the two others dimmed, so the mode reads at a glance whatever
 * the light; the texture stays his. Looked at with the Wrench or a mini-game page in hand, it says over it which mini-game
 * pages it is linked to (like a podium says what a pulse does to it).
 */
public class StepControllerBlockEntityRenderer extends GeoBlockRenderer<StepControllerBlockEntity> {
    private final BlockEntityRenderDispatcher dispatcher;

    public StepControllerBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        super(new DefaultedBlockGeoModel<>(Steveparty.id("magneto")));
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
        return RenderLayer.getEntityCutout(getTextureLocation(animatable));
    }

    /** A button's bone name, by the mode it chooses. */
    private static String button(StepPulse.Mode mode) {
        return "button_" + mode.id();
    }

    @Override
    public void renderRecursively(MatrixStack poseStack, StepControllerBlockEntity animatable, GeoBone bone, RenderLayer renderType,
                                  VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, int colour) {
        if (bone.getName().startsWith("button_")) {
            if (bone.getName().equals(button(animatable.getMode()))) packedLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            else colour = DIMMED;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    /** The colour of the buttons not chosen: three quarters of their light. */
    private static final int DIMMED = 0xFFBFBFBF;
}
