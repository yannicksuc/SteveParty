package fr.lordfinn.steveparty.client.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.ExplorerHelmet;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.util.Identifier;

/**
 * The worn Explorer's Helmet: its own little model on the head (a khaki dome with a dark band, a cap on top, a wide
 * brim and a headlamp at the front), on textures/models/armor/explorer_helmet.png (64x32, laid out by
 * the art sources). The lamp is lit (full bright, with a faint halo) while the helmet shows the
 * board, dark when its key switched it off: everyone sees which.
 */
public final class ExplorerHelmetRenderer {
    public static final Identifier TEXTURE = Steveparty.id("textures/models/armor/explorer_helmet.png");
    private static ModelPart helmet, lampOff, lampOn, halo;

    private ExplorerHelmetRenderer() {
    }

    /** The model, in the head's space (pixels, y down, the face toward -z). */
    static TexturedModelData model() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData shell = root.addChild("helmet", ModelPartBuilder.create(), ModelTransform.NONE);
        shell.addChild("dome", ModelPartBuilder.create().uv(0, 0).cuboid(-4, -10.2f, -4, 8, 4, 8, new Dilation(0.8f)), ModelTransform.NONE);
        shell.addChild("cap", ModelPartBuilder.create().uv(32, 0).cuboid(-3, -12, -3, 6, 1, 6), ModelTransform.NONE);
        shell.addChild("brim", ModelPartBuilder.create().uv(0, 12).cuboid(-6.5f, -5.5f, -7, 13, 1, 14), ModelTransform.NONE);
        root.addChild("lamp_off", ModelPartBuilder.create().uv(54, 12).cuboid(-1.5f, -9.5f, -5.8f, 3, 3, 1), ModelTransform.NONE);
        root.addChild("lamp_on", ModelPartBuilder.create().uv(54, 16).cuboid(-1.5f, -9.5f, -5.8f, 3, 3, 1), ModelTransform.NONE);
        root.addChild("halo", ModelPartBuilder.create().uv(54, 20).cuboid(-2.5f, -10.5f, -5.9f, 5, 5, 0), ModelTransform.NONE);
        return TexturedModelData.of(data, 64, 32);
    }

    public static void register() {
        ArmorRenderer.register((matrices, vertexConsumers, stack, entity, slot, light, contextModel) -> {
            if (helmet == null) {
                ModelPart root = model().createModel();
                helmet = root.getChild("helmet");
                lampOff = root.getChild("lamp_off");
                lampOn = root.getChild("lamp_on");
                halo = root.getChild("halo");
            }
            if (!contextModel.head.visible) return;
            boolean lit = ExplorerHelmet.lit(stack);
            VertexConsumer solid = ItemRenderer.getArmorGlintConsumer(vertexConsumers, RenderLayer.getArmorCutoutNoCull(TEXTURE), stack.hasGlint());
            matrices.push();
            contextModel.head.rotate(matrices);
            helmet.render(matrices, solid, light, OverlayTexture.DEFAULT_UV);
            if (lit) {
                lampOn.render(matrices, solid, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
                halo.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE)),
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            } else {
                lampOff.render(matrices, solid, light, OverlayTexture.DEFAULT_UV);
            }
            matrices.pop();
        }, ModItems.EXPLORER_HELMET);
    }
}
