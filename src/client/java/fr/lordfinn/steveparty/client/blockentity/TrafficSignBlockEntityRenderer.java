package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.TrafficSignBlockEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;

import java.util.*;

import static java.lang.Math.PI;

public class TrafficSignBlockEntityRenderer implements BlockEntityRenderer<TrafficSignBlockEntity> {
    private static final Map<String, Identifier> SYMBOLS = new HashMap<>();
    private final BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();

    public TrafficSignBlockEntityRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
    }

    @Override
    public void render(TrafficSignBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (entity == null || entity.getCachedState() == null) return;

        int rotationIndex = entity.getRotation();
        float rotationDegrees = rotationIndex * -22.5F + 180; // Each index step is 22.5 degrees
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationDegrees));
        matrices.translate(-0.5, 0, -0.5);

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getCutout());
        BlockRenderView world = entity.getWorld();
        BlockPos pos = entity.getPos();
        BakedModel model = blockRenderManager.getModel(entity.getCachedState());

        blockRenderManager.getModelRenderer().renderSmooth(world, model, entity.getCachedState(), pos, matrices, vertexConsumer, true, Random.create(), 42L, overlay);

        // Get the symbol texture and apply dye color and glow
        Identifier symbolTextureId = getSymbolTexture(entity.getSignName());
        int color = entity.getColor().getMapColor().color; // Get the color from the dye
        renderSymbol(matrices, vertexConsumers, light, overlay, symbolTextureId, color, entity.isGlowing());
        matrices.pop();
    }

    private Identifier getSymbolTexture(String name) {
        if (SYMBOLS.containsKey(name))
            return SYMBOLS.get(name);
        Identifier id = Steveparty.id("block/traffic_sign/symbols/" + name);
        SYMBOLS.put(name, id);
        return id;
    }

    private void renderSymbol(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Identifier symbolTextureId, int color, boolean isGlowing) {
        // Bind the texture
        Sprite sprite = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(symbolTextureId);
        if (sprite == null || !(sprite.getContents() instanceof SpriteContents content) || content.getId().getPath().contains("missingno")) {
            return; // Skip rendering
        }

        // Slightly offset the quad forward to prevent z-fighting
        matrices.translate(0.5, 0.5f, 0);

        // Use a glowing render layer if the sign is glowing
        RenderLayer renderLayer = RenderLayer.getCutout();
        if (isGlowing) {
            light = 0x0F0F0F;
        }

        VertexConsumer symbolConsumer = vertexConsumers.getBuffer(renderLayer);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();
        matrix.rotate((float) ((-45f - 22.5f)  / 180f * PI), 1, 0, 0);
        matrix.translate(0, -0.738f, 0.093f);

        // Extract RGB components from the color
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        // Draw a quad on the front face with the applied color
        symbolConsumer.vertex(matrix, -0.5f,  0.5f, 0.5f).color(red, green, blue, 255).texture(sprite.getMaxU(), sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        symbolConsumer.vertex(matrix,  0.5f,  0.5f, 0.5f).color(red, green, blue, 255).texture(sprite.getMinU(), sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        symbolConsumer.vertex(matrix,  0.5f, 0.5f, -0.5f).color(red, green, blue, 255).texture(sprite.getMinU(), sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        symbolConsumer.vertex(matrix, -0.5f, 0.5f, -0.5f).color(red, green, blue, 255).texture(sprite.getMaxU(), sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
    }
}