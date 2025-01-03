package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.CustomizableMerchant;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;



public class CustomizableMerchantRenderer extends GeoEntityRenderer<CustomizableMerchant> {

    public CustomizableMerchantRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Identifier.of(Steveparty.MOD_ID, "customizable_merchant")));
        addRenderLayer(new CustomizableMerchantRenderLayer(this));
    }
}