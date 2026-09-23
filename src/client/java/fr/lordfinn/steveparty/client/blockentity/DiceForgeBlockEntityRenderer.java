package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.Map;
import java.util.WeakHashMap;

import static fr.lordfinn.steveparty.blocks.custom.DiceForgeBlock.ACTIVATED;

public class DiceForgeBlockEntityRenderer extends GeoBlockRenderer<DiceForgeBlockEntity> {
    private final DiceForgeOrbitRenderer orbitRenderer = new DiceForgeOrbitRenderer();
    private final DiceForgeConvergenceRenderer convergenceRenderer = new DiceForgeConvergenceRenderer();
    // Created once instead of every frame (read-only, only rendered)
    private final ItemStack gravityCore = new ItemStack(ModBlocks.GRAVITY_CORE);
    // Last world tick at which trail particles were spawned, per forge (render thread only)
    private final Map<DiceForgeBlockEntity, Long> lastParticleTick = new WeakHashMap<>();

    public DiceForgeBlockEntityRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
        super(new DefaultedBlockGeoModel<>(Steveparty.id("dice_forge")));
    }

    @Override
    public void render(DiceForgeBlockEntity blockEntity, float partialTick,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource,
                       int packedLight, int packedOverlay) {
        super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        if (!blockEntity.getCachedState().get(ACTIVATED)) return;

        renderCore(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        // Trail particles are spawned once per game tick (not per frame) so the density is FPS independent
        boolean spawnParticles = false;
        if (blockEntity.getWorld() != null) {
            long time = blockEntity.getWorld().getTime();
            Long last = lastParticleTick.put(blockEntity, time);
            spawnParticles = last == null || last != time;
        }

        // Render orbiting faces and collect orbit info
        var orbitFaces = orbitRenderer.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay, 0.4f, spawnParticles);

        // Render convergence on top if crafting
        if (blockEntity.isCrafting()) {
            convergenceRenderer.render(blockEntity, partialTick, poseStack, bufferSource,
                    packedLight, packedOverlay, orbitFaces);
        }
    }

    private void renderCore(DiceForgeBlockEntity blockEntity, float partialTick,
                            MatrixStack poseStack, VertexConsumerProvider bufferSource,
                            int packedLight, int packedOverlay) {

        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();

        poseStack.push();
        poseStack.translate(0.5, 1.9, 0.5);

        float ticks = blockEntity.getWorld() != null ? blockEntity.getWorld().getTime() + partialTick : 0;
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ticks % 360));

        poseStack.scale(2f, 2f, 2f);
        itemRenderer.renderItem(gravityCore, ModelTransformationMode.GROUND,
                packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getWorld(), 0);
        poseStack.pop();
    }

    /** The core and the orbiting faces are drawn well above / around the block. */
    @Override
    public boolean rendersOutsideBoundingBox(DiceForgeBlockEntity blockEntity) {
        return true;
    }

    @Override
    public @Nullable RenderLayer getRenderType(DiceForgeBlockEntity animatable, Identifier texture,
                                               @Nullable VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityTranslucent(getTextureLocation(animatable));
    }
}
