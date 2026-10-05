package fr.lordfinn.steveparty.client.items;

import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.BandanaItem;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.util.Identifier;

/**
 * The worn bandana in the colour of its stack: a vanilla armour material has one texture, so each colour's layer
 * (textures/models/armor/bandana_&lt;colour&gt;_layer_1.png) is drawn here, on the head of the outer armour model.
 */
public final class BandanaArmorRenderer {
    /** The armour texture of each colour. */
    private static final Identifier[] TEXTURES = new Identifier[BandanaItem.COLOR_NAMES.length];
    private static BipedEntityModel<LivingEntity> model;

    static {
        for (int color = 0; color < TEXTURES.length; color++) {
            TEXTURES[color] = new ArmorMaterial.Layer(BandanaItem.textureLayer(color)).getTexture(false);
        }
    }

    private BandanaArmorRenderer() {
    }

    public static void register() {
        ArmorRenderer.register((matrices, vertexConsumers, stack, entity, slot, light, contextModel) -> {
            if (model == null) {
                model = new BipedEntityModel<>(MinecraftClient.getInstance().getEntityModelLoader()
                        .getModelPart(EntityModelLayers.PLAYER_OUTER_ARMOR));
            }
            contextModel.copyBipedStateTo(model);
            model.setVisible(false);
            model.head.visible = true;
            model.hat.visible = true;
            ArmorRenderer.renderPart(matrices, vertexConsumers, light, stack, model, TEXTURES[BandanaItem.getColor(stack)]);
        }, ModItems.BANDANA);
    }
}
