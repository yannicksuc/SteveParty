package fr.lordfinn.steveparty.client.items;

import fr.lordfinn.steveparty.client.utils.StencilRenderUtils;
import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.DynamicItemRenderer;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public class StencilItemRenderer implements DynamicItemRenderer {
    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        byte[] shape = StencilItem.getShape(stack);
        StencilResourceManager.StencilTextures textures = StencilResourceManager.getStencilShape(shape);

        if (textures == null || textures.metalStencil() == null) return;

        matrices.push();
        //matrices.scale(0.75f, 0.75f, 0.75f);
        matrices.translate(0, 0, 0);

        Identifier textureId = textures.metalStencil();

        StencilRenderUtils.renderSymbol(
                matrices,
                vertexConsumers,
                light,
                overlay,
                textureId,
                0xFFFFFF,
                false,
                (s) -> {
                    MatrixStack.Entry entry = s.peek();
                    Matrix4f matrix = entry.getPositionMatrix();
                    //matrix.rotate((float) Math.toRadians(180f), 0, 1, 0);
                    //matrix.rotate((float) Math.toRadians(-90f), 1, 0, 0);
                    // Common base rotation
                    // Additional transformations per mode
                    switch (mode) {
                        case GUI -> {
                            matrix.translate(0f, 0.5f, 0f);
                            //matrix.scale(0.88f);
                            matrix.rotate((float) Math.toRadians(180f), 0, 1, 0);
                            matrix.rotate((float) Math.toRadians(-90f), 1, 0, 0);
                        }
                        case FIRST_PERSON_LEFT_HAND -> {
                            matrix.translate(-0.8f, 0.3f, -1.2f);
                            matrix.rotate((float) Math.toRadians(80f), 1, 0, 0);
                            matrix.rotate((float) Math.toRadians(170f), 0, 1, 0);
                            matrix.rotate((float) Math.toRadians(-5f), 0, 0, 1);
                        }
                        case FIRST_PERSON_RIGHT_HAND -> {
                            matrix.translate(0.8f, 0.3f, -1.2f);
                            matrix.rotate((float) Math.toRadians(80f), 1, 0, 0);
                            matrix.rotate((float) Math.toRadians(190f), 0, 1, 0);
                            matrix.rotate((float) Math.toRadians(5f), 0, 0, 1);
                            //matrix.rotate((float) Math.toRadians(60f), 1, 0, 0);
                        }
                        case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                            matrix.translate(0f, 0.77f, -0.05f);
                            matrix.rotate((float) Math.toRadians(180f), 0, 0, 1);
                            matrix.rotate((float) Math.toRadians(60f), 1, 0, 0);
                            matrix.scale(0.5f);
                        }
                        case GROUND -> {
                            matrix.translate(0, 0.2f, 0f);
                            matrix.scale(0.5f, 0.5f, 0.5f);
                        }
                        case FIXED -> {
                            matrix.translate(0, 0.5f, 0.5f);
                            matrix.rotate((float) Math.toRadians(-90f), 1, 0, 0);
                        }
                        default -> {
                        }
                    }

                }
        );
        matrices.pop();
    }

}
