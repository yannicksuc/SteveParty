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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import static fr.lordfinn.steveparty.blocks.custom.DiceForgeBlock.ACTIVATED;
import static net.minecraft.particle.ParticleTypes.END_ROD;

public class DiceForgeBlockEntityRenderer extends GeoBlockRenderer<DiceForgeBlockEntity> {

    private static final float[] SPEEDS = {1, -1, -1, 1, 1, 1, -1, 1, -1};

    public DiceForgeBlockEntityRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
        super(new DefaultedBlockGeoModel<>(Steveparty.id("dice_forge")));
    }

    @Override
    public void render(DiceForgeBlockEntity blockEntity, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight, int packedOverlay) {
        super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        if (!blockEntity.getCachedState().get(ACTIVATED)) return;

        renderCore(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        renderInventoryItems(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }

    /**
     * Renders the central gravity core item with a slow rotation.
     */
    private void renderCore(DiceForgeBlockEntity blockEntity, float partialTick,
                            MatrixStack poseStack, VertexConsumerProvider bufferSource,
                            int packedLight, int packedOverlay) {

        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        ItemStack gravityCore = new ItemStack(ModBlocks.GRAVITY_CORE);

        poseStack.push();
        poseStack.translate(0.5, 1.9, 0.5);

        // Rotate over time
        float ticks = blockEntity.getWorld() != null ? blockEntity.getWorld().getTime() + partialTick : 0;
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ticks % 360));

        // Scale and render
        poseStack.scale(2f, 2f, 2f);
        itemRenderer.renderItem(gravityCore, ModelTransformationMode.GROUND,
                packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getWorld(), 0);

        poseStack.pop();
    }

    /**
     * Renders all items orbiting around the block.
     */
    private void renderInventoryItems(DiceForgeBlockEntity blockEntity, float partialTick,
                                      MatrixStack poseStack, VertexConsumerProvider bufferSource,
                                      int packedLight, int packedOverlay) {

        DefaultedList<ItemStack> inventory = blockEntity.getInventory();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack itemStack = inventory.get(i);
            if (itemStack.isEmpty()) continue;

            // Base orbit speed (radians per tick)
            double baseSpeed = 0.02;

            // Orbit radius grows slightly with slot index
            float radius = 0.8f + i * 0.5f;

            double speed = baseSpeed * (1.0 / radius) * ((i % 2 == 0) ? 1 : -1);


            // Every few items spawn a trail
            boolean spawnTrail = (i % 3 == 0);

            // Angle offset per slot to spread them apart
            double angleOffset = Math.toRadians(i * 67); // 45° apart

            renderOrbitingItem(itemStack, radius, speed, angleOffset, partialTick,
                    blockEntity, poseStack, bufferSource,
                    packedLight, packedOverlay, 0.8f, spawnTrail);
        }
    }


    /**
     * Renders a single orbiting item with optional trail.
     */
    private void renderOrbitingItem(ItemStack stack, float radius, double speed, double angleOffset,
                                    float partialTick, DiceForgeBlockEntity blockEntity,
                                    MatrixStack poseStack, VertexConsumerProvider bufferSource,
                                    int packedLight, int packedOverlay, float scale, boolean spawnTrail) {

        if (blockEntity.getWorld() == null) return;

        double ticks = blockEntity.getRotationTicks() + partialTick;
        double angle = (ticks * speed) + angleOffset;

        // Orbit coordinates
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;

        // World-space position
        double worldX = blockEntity.getPos().getX() + 0.5 + x;
        double worldY = blockEntity.getPos().getY() + 2.5;
        double worldZ = blockEntity.getPos().getZ() + 0.5 + z;

        // Render item
        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        poseStack.push();
        poseStack.translate(0.5 + x, 2.5, 0.5 + z);

        // Rotate item to face inward
        float yaw = (float) Math.toDegrees(Math.atan2(-z, -x));
        poseStack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(yaw - 90));

        poseStack.scale(scale, scale, scale);
        itemRenderer.renderItem(stack, ModelTransformationMode.FIXED,
                packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getWorld(), 0);
        poseStack.pop();

        // Particle trail
        if (spawnTrail) {
            spawnTrailParticle(blockEntity, worldX, worldY, worldZ);
        }
    }


    /**
     * Spawns a particle trail behind orbiting items.
     */
    private void spawnTrailParticle(DiceForgeBlockEntity blockEntity, double x, double y, double z) {
        if (!blockEntity.getWorld().isClient) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && client.world.random.nextFloat() < 0.4f) {
            client.world.addParticle(END_ROD, x, y, z, 0, 0, 0);
        }
    }

    @Override
    public @Nullable RenderLayer getRenderType(DiceForgeBlockEntity animatable, Identifier texture,
                                               @Nullable VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityTranslucent(getTextureLocation(animatable));
    }
}
