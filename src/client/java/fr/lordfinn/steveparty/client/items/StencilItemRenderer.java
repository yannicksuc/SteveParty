package fr.lordfinn.steveparty.client.items;

import fr.lordfinn.steveparty.client.utils.StencilRenderUtils;
import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.DynamicItemRenderer;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

public class StencilItemRenderer implements DynamicItemRenderer {
    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        byte[] shape = StencilItem.getShape(stack);
        Identifier textureId = StencilResourceManager.getTexture(shape, StencilResourceManager.Kind.METAL);
        if (textureId == null) return;

        matrices.push();

        StencilRenderUtils.renderPlate(
                matrices,
                vertexConsumers,
                light,
                overlay,
                textureId,
                shape,
                StencilResourceManager.Kind.METAL.margin(),
                (s) -> {
                    // On the stack (not only the position matrix) so the normals turn too: the plate's sides are lit
                    switch (mode) {
                        case GUI -> {
                            s.translate(0f, 0.5f, 0f);
                            s.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(180f), 0, 1, 0));
                            s.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(-90f), 1, 0, 0));
                        }
                        case FIRST_PERSON_LEFT_HAND -> {
                            s.translate(-0.8f, 0.3f, -1.2f);
                            s.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(80f), 1, 0, 0));
                            s.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(170f), 0, 1, 0));
                            s.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(-5f), 0, 0, 1));
                        }
                        case FIRST_PERSON_RIGHT_HAND -> {
                            s.translate(0.8f, 0.3f, -1.2f);
                            s.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(80f), 1, 0, 0));
                            s.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(190f), 0, 1, 0));
                            s.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(5f), 0, 0, 1));
                        }
                        case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                            s.translate(0f, 0.77f, -0.05f);
                            s.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(180f), 0, 0, 1));
                            s.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(60f), 1, 0, 0));
                            s.scale(0.5f, 0.5f, 0.5f);
                        }
                        case GROUND -> {
                            s.translate(0, 0.2f, 0f);
                            s.scale(0.5f, 0.5f, 0.5f);
                        }
                        case FIXED -> {
                            s.translate(0, 0.5f, 0.5f);
                            s.multiply(new Quaternionf().rotateAxis((float) Math.toRadians(-90f), 1, 0, 0));
                        }
                        default -> {
                        }
                    }

                }
        );
        matrices.pop();
    }

}
